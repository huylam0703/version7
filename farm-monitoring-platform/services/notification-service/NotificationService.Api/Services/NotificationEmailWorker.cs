using System.Net;
using System.Net.Mail;
using Microsoft.EntityFrameworkCore;
using NotificationService.Infrastructure.Data;

namespace NotificationService.Api.Services;

public sealed class NotificationEmailWorker(
    IConfiguration configuration,
    IServiceScopeFactory scopeFactory,
    ILogger<NotificationEmailWorker> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!configuration.GetValue("Email:Enabled", false)) return;
        var host = configuration["Email:Host"]
            ?? throw new InvalidOperationException("Email:Host is required");
        var from = new MailAddress(configuration["Email:From"]
            ?? throw new InvalidOperationException("Email:From is required"));
        var maxAttempts = Math.Clamp(configuration.GetValue("Email:MaxAttempts", 5), 1, 10);
        var retrySeconds = Math.Clamp(configuration.GetValue("Email:RetrySeconds", 10), 1, 300);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (await DeliverNextAsync(host, from, maxAttempts, retrySeconds, stoppingToken)) continue;
                await Task.Delay(TimeSpan.FromSeconds(2), stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception exception)
            {
                logger.LogWarning("Notification email worker encountered {Error}; retrying",
                    exception.GetType().Name);
                await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken);
            }
        }
    }

    private async Task<bool> DeliverNextAsync(string host, MailAddress from, int maxAttempts,
        int retrySeconds, CancellationToken cancellationToken)
    {
        using var scope = scopeFactory.CreateScope();
        var database = scope.ServiceProvider.GetRequiredService<NotificationDbContext>();
        var now = DateTime.UtcNow;
        await database.NotificationEmails.Where(e => e.SentAt == null && e.FailedAt == null &&
                e.Attempts >= maxAttempts && e.LeaseUntil <= now)
            .ExecuteUpdateAsync(setters => setters.SetProperty(e => e.FailedAt, now)
                .SetProperty(e => e.LastError, "DeliveryLeaseExpired")
                .SetProperty(e => e.LeaseId, (Guid?)null)
                .SetProperty(e => e.LeaseUntil, (DateTime?)null), cancellationToken);

        var pending = database.NotificationEmails.Where(e => e.SentAt == null && e.FailedAt == null &&
            e.Attempts < maxAttempts && e.NextAttemptAt <= now &&
            (e.LeaseUntil == null || e.LeaseUntil <= now));
        var id = await pending.OrderBy(e => e.NextAttemptAt).Select(e => (Guid?)e.Id)
            .FirstOrDefaultAsync(cancellationToken);
        if (id is null) return false;

        var lease = Guid.NewGuid();
        var claimed = await pending.Where(e => e.Id == id).ExecuteUpdateAsync(setters => setters
            .SetProperty(e => e.LeaseId, lease)
            .SetProperty(e => e.LeaseUntil, now.AddMinutes(2))
            .SetProperty(e => e.Attempts, e => e.Attempts + 1), cancellationToken);
        if (claimed == 0) return true;
        var email = await database.NotificationEmails.AsNoTracking()
            .SingleAsync(e => e.Id == id && e.LeaseId == lease, cancellationToken);

        string? error = null;
        try
        {
            using var smtp = new SmtpClient(host, configuration.GetValue("Email:Port", 587))
            {
                EnableSsl = configuration.GetValue("Email:EnableSsl", true),
                UseDefaultCredentials = false
            };
            if (!string.IsNullOrWhiteSpace(configuration["Email:Username"]))
                smtp.Credentials = new NetworkCredential(configuration["Email:Username"], configuration["Email:Password"]);
            using var message = new MailMessage(from, new MailAddress(email.Recipient))
            {
                Subject = email.Subject,
                Body = email.Body,
                IsBodyHtml = false,
                SubjectEncoding = System.Text.Encoding.UTF8,
                BodyEncoding = System.Text.Encoding.UTF8
            };
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(30));
            await smtp.SendMailAsync(message, timeout.Token);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception exception)
        {
            error = exception.GetType().Name;
            logger.LogWarning("Notification email {EmailId} failed on attempt {Attempt} with {Error}",
                email.Id, email.Attempts, error);
        }

        var finishedAt = DateTime.UtcNow;
        var nextAttempt = finishedAt.AddSeconds(Math.Min(300, retrySeconds * Math.Pow(2, email.Attempts - 1)));
        await database.NotificationEmails.Where(e => e.Id == id && e.LeaseId == lease)
            .ExecuteUpdateAsync(setters => setters
                .SetProperty(e => e.SentAt, error == null ? finishedAt : (DateTime?)null)
                .SetProperty(e => e.FailedAt, error != null && email.Attempts >= maxAttempts ? finishedAt : (DateTime?)null)
                .SetProperty(e => e.LastError, error)
                .SetProperty(e => e.NextAttemptAt, nextAttempt)
                .SetProperty(e => e.LeaseId, (Guid?)null)
                .SetProperty(e => e.LeaseUntil, (DateTime?)null), cancellationToken);
        return true;
    }
}
