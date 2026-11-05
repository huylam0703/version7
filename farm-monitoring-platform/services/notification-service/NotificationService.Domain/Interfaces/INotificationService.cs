using NotificationService.Domain.Entities;

namespace NotificationService.Domain.Interfaces;

public interface INotificationService
{
    Task<Guid> CreateAsync(
        Notification notification,
        CancellationToken cancellationToken = default);

    Task<bool> MarkAsReadAsync(
        Guid notificationId,
        Guid userId,
        CancellationToken cancellationToken = default);

    Task<int> MarkAllAsReadAsync(
        Guid userId,
        CancellationToken cancellationToken = default);

    Task<bool> DeleteAsync(
        Guid notificationId,
        Guid userId,
        CancellationToken cancellationToken = default);
}