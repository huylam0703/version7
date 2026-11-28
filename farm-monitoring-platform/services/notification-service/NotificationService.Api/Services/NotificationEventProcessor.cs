using System.Text.Json;
using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using NotificationService.Domain.Entities;
using NotificationService.Infrastructure.Data;

namespace NotificationService.Api.Services;

public sealed class InvalidNotificationEventException(string message, Exception? inner = null)
    : Exception(message, inner);

public sealed class NotificationEventProcessor(
    NotificationDbContext database,
    NotificationRecipients recipients,
    IConfiguration configuration)
{
    public async Task ProcessAsync(ReadOnlyMemory<byte> body, CancellationToken cancellationToken)
    {
        var notificationEvent = Parse(body);
        if (notificationEvent is null) return;
        if (await database.ProcessedNotificationEvents.AnyAsync(
                e => e.EventId == notificationEvent.EventId, cancellationToken)) return;

        var targets = await recipients.GetAsync(cancellationToken);
        database.ProcessedNotificationEvents.Add(new ProcessedNotificationEvent
        {
            EventId = notificationEvent.EventId,
            EventType = notificationEvent.EventType,
            ProcessedAt = DateTime.UtcNow
        });

        foreach (var target in targets)
        {
            database.Notifications.Add(new Notification
            {
                UserId = target.UserId,
                SourceEventId = notificationEvent.EventId,
                Title = notificationEvent.Title,
                Message = notificationEvent.Message,
                Type = notificationEvent.EventType == "alert.created" ? "ALERT" : "MISSION"
            });
            if (configuration.GetValue("Email:Enabled", false))
                database.NotificationEmails.Add(new NotificationEmail
                {
                    EventId = notificationEvent.EventId,
                    UserId = target.UserId,
                    Recipient = target.Email!,
                    Subject = notificationEvent.Title,
                    Body = notificationEvent.Message
                });
        }

        try
        {
            // One SaveChanges transaction commits the receipt, web notifications and email jobs together.
            await database.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException exception) when (exception.InnerException is SqlException { Number: 2601 or 2627 })
        {
            // Another consumer may have committed this event after our initial lookup.
            database.ChangeTracker.Clear();
            if (!await database.ProcessedNotificationEvents.AnyAsync(
                    e => e.EventId == notificationEvent.EventId, cancellationToken)) throw;
        }
    }

    private NotificationEvent? Parse(ReadOnlyMemory<byte> body)
    {
        var missionSource = configuration["Notifications:MissionSource"] ?? "Direct";
        if (missionSource is not ("Direct" or "Alert"))
            throw new InvalidOperationException("Notifications:MissionSource must be Direct or Alert");
        try
        {
            using var document = JsonDocument.Parse(body);
            var root = document.RootElement;
            var eventId = RequiredId(root, "eventId");
            if (root.GetProperty("version").GetInt32() != 1)
                throw new InvalidNotificationEventException("Unsupported event version");
            var eventType = RequiredText(root, "eventType");
            var payload = root.GetProperty("payload");

            if (eventType == "alert.created")
            {
                RequiredId(payload, "alertId");
                var type = RequiredText(payload, "type");
                // Older Alert deployments sent the sensor code as the alert type.
                var legacySensorCode = type is "AIR_TEMP" or "AIR_HUMIDITY" or "SOIL_MOISTURE" or "LIGHT" or "PH" or "WATER_LEVEL"
                    ? type
                    : null;
                if (legacySensorCode is not null)
                    type = "OUT_OF_RANGE";
                var severity = RequiredText(payload, "severity");
                var message = RequiredText(payload, "message");
                if (type is not ("OUT_OF_RANGE" or "DATA_TIMEOUT" or "LOW_BATTERY" or "COLLECTION_FAILED" or "MISSION_FAILED") ||
                    severity is not ("LOW" or "MEDIUM" or "HIGH" or "CRITICAL") || message.Length > 500)
                    throw new InvalidNotificationEventException("Invalid alert payload");
                // In Direct mode mission incidents are already handled by mission.status_changed.
                if (missionSource == "Direct" && (type == "MISSION_FAILED" ||
                    (type == "COLLECTION_FAILED" && payload.TryGetProperty("missionId", out var missionId) &&
                        missionId.ValueKind != JsonValueKind.Null))) return null;
                var title = legacySensorCode is null
                    ? $"Alert {severity}: {type}"
                    : $"Alert {severity}: {type} ({legacySensorCode})";
                return new NotificationEvent(eventId, eventType, title, message);
            }

            if (eventType == "mission.status_changed")
            {
                var missionId = RequiredId(payload, "missionId");
                RequiredId(payload, "farmId");
                RequiredId(payload, "uavId");
                RequiredId(payload, "gatewayId");
                payload.GetProperty("changedAt").GetDateTimeOffset();
                var status = RequiredText(payload, "newStatus");
                if (status is not ("DRAFT" or "PLANNED" or "RUNNING" or "COMPLETED" or "PARTIAL" or "FAILED" or "CANCELLED"))
                    throw new InvalidNotificationEventException("Unknown mission status");
                if (missionSource == "Alert" || status is not ("FAILED" or "PARTIAL")) return null;
                return new NotificationEvent(eventId, eventType, $"Mission {status}",
                    $"Mission {missionId} changed to {status}.");
            }

            throw new InvalidNotificationEventException("Unsupported event type");
        }
        catch (Exception exception) when (exception is JsonException or KeyNotFoundException or FormatException or InvalidOperationException)
        {
            throw new InvalidNotificationEventException("Malformed event payload", exception);
        }
    }

    private static Guid RequiredId(JsonElement element, string property)
    {
        if (!element.TryGetProperty(property, out var value) || value.ValueKind != JsonValueKind.String ||
            !value.TryGetGuid(out var id) || id == Guid.Empty)
            throw new InvalidNotificationEventException($"Invalid {property}");
        return id;
    }

    private static string RequiredText(JsonElement element, string property)
    {
        if (!element.TryGetProperty(property, out var value) || value.ValueKind != JsonValueKind.String ||
            string.IsNullOrWhiteSpace(value.GetString()))
            throw new InvalidNotificationEventException($"Invalid {property}");
        return value.GetString()!;
    }

    private sealed record NotificationEvent(Guid EventId, string EventType, string Title, string Message);
}
