using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Application;

public static class UserAuthErrors
{
    /// <summary>
    /// 
    /// </summary>
    public const string InvalidCredentials = "INVALID_CREDENTIALS";
    /// <summary>
    /// 
    /// </summary>
    public const string AccountLocked = "ACCOUNT_LOCKED";
    /// <summary>
    /// 
    /// </summary>
    public const string EmailExists = "EMAIL_EXISTS";
    /// <summary>
    /// 
    /// </summary>
    public const string UserNotFound = "USER_NOT_FOUND";
    /// <summary>
    /// 
    /// </summary>
    public const string RoleNotFound = "ROLE_NOT_FOUND";
    /// <summary>
    /// 
    /// </summary>
    public const string CannotLockSelf = "CANNOT_LOCK_SELF";
    /// <summary>
    /// 
    /// </summary>
    public const string CannotChangeOwnRole = "CANNOT_CHANGE_OWN_ROLE";
    /// <summary>
    /// 
    /// </summary>
    public const string InvalidClient = "INVALID_CLIENT";
    /// <summary>
    /// 
    /// </summary>
    public const string UnsupportedGrantType = "UNSUPPORTED_GRANT_TYPE";
}
