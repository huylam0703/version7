using System.Net.Http.Headers;
using System.Net.Mail;
using System.Text.Json.Serialization;

namespace NotificationService.Api.Services;

public sealed record NotificationRecipient(Guid UserId, string? Email);

public sealed class NotificationRecipients(IConfiguration configuration, IHttpClientFactory httpClientFactory)
{
    public async Task<IReadOnlyList<NotificationRecipient>> GetAsync(CancellationToken cancellationToken)
    {
        NotificationRecipient[] recipients;
        var mode = configuration["Notifications:RecipientSource"] ?? "Configuration";
        if (mode == "UserAuth")
        {
            using var client = httpClientFactory.CreateClient();
            client.BaseAddress = new Uri(configuration["UserAuth:BaseUrl"]
                ?? throw new InvalidOperationException("UserAuth:BaseUrl is required"));
            client.Timeout = TimeSpan.FromSeconds(15);
            using var tokenResponse = await client.PostAsync("connect/token", new FormUrlEncodedContent(
                new Dictionary<string, string>
                {
                    ["grant_type"] = "client_credentials",
                    ["client_id"] = configuration["UserAuth:ClientId"] ?? "notification-service",
                    ["client_secret"] = configuration["UserAuth:ClientSecret"]
                        ?? throw new InvalidOperationException("UserAuth:ClientSecret is required")
                }), cancellationToken);
            tokenResponse.EnsureSuccessStatusCode();
            var token = await tokenResponse.Content.ReadFromJsonAsync<ServiceToken>(cancellationToken);
            if (string.IsNullOrWhiteSpace(token?.AccessToken))
                throw new InvalidOperationException("UserAuth returned an empty service token");
            client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token.AccessToken);
            var response = await client.GetFromJsonAsync<RecipientsResponse>(
                configuration["UserAuth:RecipientsPath"] ?? "internal/v1/users/notification-recipients",
                cancellationToken);
            recipients = response?.Result ?? throw new InvalidOperationException("Invalid recipient response");
        }
        else if (mode == "Configuration")
        {
            recipients = configuration.GetSection("Notifications:Recipients").Get<NotificationRecipient[]>() ?? [];
            if (recipients.Length == 0)
                recipients = (configuration.GetSection("Notifications:AdminUserIds").Get<Guid[]>() ?? [])
                    .Select(id => new NotificationRecipient(id, null)).ToArray();
        }
        else throw new InvalidOperationException("Unknown Notifications:RecipientSource");

        if (recipients.Length == 0 || recipients.Any(r => r.UserId == Guid.Empty))
            throw new InvalidOperationException("No valid notification recipients are available");
        if (configuration.GetValue("Email:Enabled", false) &&
            recipients.Any(r => string.IsNullOrWhiteSpace(r.Email) ||
                !MailAddress.TryCreate(r.Email, out var address) || address.Address != r.Email))
            throw new InvalidOperationException("Email delivery requires a valid address for every recipient");
        if (recipients.GroupBy(r => r.UserId).Any(g => g.Select(r => r.Email).Distinct().Count() > 1))
            throw new InvalidOperationException("Conflicting email addresses for the same recipient");
        return recipients.DistinctBy(r => r.UserId).ToArray();
    }

    private sealed record ServiceToken([property: JsonPropertyName("access_token")] string AccessToken);
    private sealed record RecipientsResponse(NotificationRecipient[] Result);
}
