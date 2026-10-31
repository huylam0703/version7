using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Infracstructure.Queries;

/// <summary>
/// Các lệnh ghi db
/// </summary>
public static class UserCommand
{
    /// <summary>
    /// Tạo mới user
    /// </summary>
    public const string InsertUser = @"
        INSERT INTO UA_Users    (UserId, RoleId, FullName, Email, PasswordHash, HashSalt, Phone, Status, CreatedAt, UpdatedAt)
        VALUES                  (@pUserId, @pRoleId, @pFullName, @pEmail, @pPasswordHash, @pHashSalt, @pPhone, @pStatus, @pCreatedAt, @pUpdatedAt)
    ";

    /// <summary>
    /// Cập nhật thông tin đăng nhập
    /// </summary>
    public const string RecordLogin = @"
        UPDATE UA_Users SET LastLoginAt = SYSUTCDATETIME() WHERE UserId = @pUserId";

    /// <summary>
    /// Cập nhật thông tin
    /// </summary>
    public const string UpdateUser = @"
        UPDATE  UA_Users
        SET     FullName = @pFullName, 
                Phone = @pPhone, 
                RoleId = @pRoleId, 
                UpdatedAt = SYSUTCDATETIME()
        WHERE   UserId = @pUserId";

    /// <summary>
    /// Cập nhật trạng thái
    /// </summary>
    public const string SetStatus = @"
        UPDATE  UA_Users
        SET     Status = @pStatus, 
                UpdatedAt = SYSUTCDATETIME()
        WHERE   UserId = @pUserId";
}
