namespace NotificationService.Api.DTOs;

public sealed record NotificationDto(
    Guid Id,
    Guid UserId,
    string Title,
    string Message,
    string Type,
    bool IsRead,
    DateTime CreatedAt,
    DateTime? ReadAt,
    Guid? SourceEventId);
