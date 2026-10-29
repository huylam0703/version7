using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Dtos.UserDto;

public class UserInfoDto
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
    public string RoleName { get; set; } = "";
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
    public string? HashSalt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Phone { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string Status { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public DateTime? LastLoginAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime CreatedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime UpdatedAt { get; set; }
}
