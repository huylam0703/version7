using NotificationService.Domain.Entities;

namespace NotificationService.Domain.Interfaces;

public interface INotificationReadService
{
    Task<IReadOnlyList<Notification>> GetByUserIdAsync(
        Guid userId,
        CancellationToken cancellationToken = default);

    Task<int> GetUnreadCountAsync(
        Guid userId,
        CancellationToken cancellationToken = default);
}