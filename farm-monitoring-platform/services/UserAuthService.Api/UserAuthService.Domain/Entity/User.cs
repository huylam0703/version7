using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Entity;

public class User
{
    /// <summary>
    /// 
    /// </summary>
    public Guid UserId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public Guid RoleId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string FullName { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string Email { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string PasswordHash { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string? HaslSalt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Phone { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string Status { get; set; } = UserStatus.Active;
    /// <summary>
    /// 
    /// </summary>
    public DateTime CreatedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime UpdatedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    /// <param name="roleId"></param>
    /// <param name="fullName"></param>
    /// <param name="email"></param>
    /// <param name="passwordHash"></param>
    /// <param name="hashSalt"></param>
    /// <param name="phone"></param>
    /// <returns></returns>
    public static User Create(Guid roleId, string fullName, string email, string passwordHash, string? hashSalt, string? phone)
    {
        var now = DateTime.UtcNow;
        return new User
        {
            UserId = Guid.CreateVersion7(),
            RoleId = roleId,
            FullName = fullName.Trim(),
            Email = email.Trim().ToLowerInvariant(),
            PasswordHash = passwordHash,
            HaslSalt = hashSalt,
            Phone = string.IsNullOrWhiteSpace(phone) ? null : phone.Trim(),
            Status = UserStatus.Active,
            CreatedAt = now,
            UpdatedAt = now
        };
    }
}
