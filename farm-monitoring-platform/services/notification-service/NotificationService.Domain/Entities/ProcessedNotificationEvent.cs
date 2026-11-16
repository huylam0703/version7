namespace NotificationService.Domain.Entities;

public sealed class ProcessedNotificationEvent
{
    public Guid EventId { get; set; }
    public string EventType { get; set; } = string.Empty;
    public DateTime ProcessedAt { get; set; }
}
