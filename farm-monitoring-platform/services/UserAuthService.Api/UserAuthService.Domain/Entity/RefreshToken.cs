namespace UserAuthService.Domain.Entity;

/// <summary>
/// 
/// </summary>
public class RefreshToken
{
    /// <summary>
    /// 
    /// </summary>
    public Guid TokenId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public Guid UserId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string TokenHash { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public DateTime ExpiresAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime CreatedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime? RevokedAt { get; set; }
}