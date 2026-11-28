namespace NotificationService.Api.DTOs;

public sealed record NotificationResponse(
    Guid Id,
    Guid UserId,
    string Title,
    string Message,
    string Type,
    bool IsRead,
    DateTime CreatedAt,
    DateTime? ReadAt,
    Guid? SourceEventId);

public sealed record NotificationAuthResponse(string Message, Guid UserId);

public sealed record UnreadCountResponse(int UnreadCount);

public sealed record NotificationCreatedResponse(string Message, Guid NotificationId);

public sealed record NotificationReadResponse(string Message);

public sealed record NotificationsReadResponse(string Message, int UpdatedCount);
