namespace UserAuthService.Domain.Interfaces.Services;

public record RefreshTokenIssue(string Token, DateTime ExpiresAt);

public enum ConsumeStatus { Ok, Invalid, Reused }
public record ConsumeResult(ConsumeStatus Status, Guid? UserId);

public interface IRefreshTokenService
{
    /// <summary>
    /// Tạo token mới, lưu hash, trả token gốc (chỉ trả 1 lần).
    /// </summary>
    /// <param name="userId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<RefreshTokenIssue> IssueAsync(Guid userId, CancellationToken ct = default);

    /// <summary>
    /// Thu hồi token hợp lệ (atomic) và trả UserId; phát hiện token bị dùng lại.
    /// </summary>
    /// <param name="token"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<ConsumeResult> ConsumeAsync(string token, CancellationToken ct = default);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="token"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task RevokeAsync(string token, CancellationToken ct = default);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task RevokeAllAsync(Guid userId, CancellationToken ct = default);
}