using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Entity;

public static class UserStatus
{
    /// <summary>
    /// 
    /// </summary>
    public const string Active = "ACTIVE";
    /// <summary>
    /// 
    /// </summary>
    public const string Locked = "LOCKED";
    
    /// <summary>
    /// 
    /// </summary>
    /// <param name="value"></param>
    /// <returns></returns>
    public static bool IsValid(string? value)
        => string.Equals(value?.Trim(), Active, StringComparison.OrdinalIgnoreCase)
        || string.Equals(value?.Trim(), Locked, StringComparison.OrdinalIgnoreCase);
}
