using System;
using System.Collections.Generic;
using System.Net.NetworkInformation;
using System.Text;

using UserAuthService.Domain.Entity;

namespace UserAuthService.Infracstructure.Queries;

/// <summary>
/// các lệnh đọc db
/// </summary>
public static class UserQuery
{
    /// <summary>
    /// Lấy thông tin người dùng bằng Email
    /// </summary>
    public const string GetByEmail = @"
        SELECT      U.UserId, 
                    U.RoleId, 
                    R.Name AS RoleName, 
                    U.FullName, 
                    U.HashSalt,
                    U.Email, 
                    U.PasswordHash, 
                    U.Phone, 
                    U.Status,
                    U.LastLoginAt, U.CreatedAt, U.UpdatedAt
        FROM        UA_Users AS U
        INNER JOIN  UA_Roles AS R ON R.RoleId = U.RoleId
        WHERE       U.Email = @pEmail
    ";

    /// <summary>
    /// Lấy thông tin người dùng bằng UserId
    /// </summary>
    public const string GetById = @"
        SELECT      U.UserId, 
                    U.RoleId, 
                    R.Name AS RoleName, 
                    U.FullName, 
                    U.HashSalt,
                    U.Email, 
                    U.PasswordHash, 
                    U.Phone, 
                    U.Status,
                    U.LastLoginAt, U.CreatedAt, U.UpdatedAt
        FROM        UA_Users AS U
        INNER JOIN  UA_Roles AS R ON R.RoleId = U.RoleId
        WHERE       U.UserId = @pUserId
    ";

    /// <summary>
    /// Kiểm tra email tồn tại
    /// </summary>
    public const string EmailExists = @"
        SELECT 
        CASE WHEN EXISTS (SELECT 1 FROM UA_Users WHERE Email = @pEmail) THEN 1 ELSE 0 END
    ";

    /// <summary>
    /// Kiểm tra số điện thoại tồn tại
    /// </summary>
    public const string PhoneExists = @"
        SELECT 
        CASE WHEN EXISTS (SELECT 1 FROM UA_Users WHERE Phone = @pPhone) THEN 1 ELSE 0 END
    ";

    /// <summary>
    /// Lấy RoleId bằng RoleName
    /// </summary>
    public const string GetRoleByName = @"
        SELECT RoleId, Name, Description FROM UA_Roles WHERE Name = @pName
    ";

    /// <summary>
    /// Lấy key mã hóa
    /// </summary>
    public const string GetHashSalt = @"
        SELECT  U.HashSalt
        FROM    UA_Users AS U 
        WHERE   U.UserId = @pUserId
    ";

    /// <summary>
    /// Kiểm tra password hash 
    /// </summary>
    public const string PasswordCheck = @"
        SELECT 
        CASE WHEN EXISTS (SELECT 1 FROM UA_Users WHERE PasswordHash = @pPasswordHash) THEN 1 ELSE 0 END
    ";

    /// <summary>
    /// Lấy danh sách user có phân trang
    /// </summary>
    public const string ListUsers = @"
        SELECT COUNT_BIG(*) 
        FROM UA_Users AS U 
        INNER JOIN UA_Roles AS R ON R.RoleId = U.RoleId
        WHERE   (@pKeyword IS NULL OR U.FullName LIKE '%' + @pSearchString + '%' OR U.Email LIKE '%' + @pSearchString + '%')
                AND(@pRole IS NULL OR R.Name = @pRole)
                AND(@pStatus IS NULL OR U.Status = @pStatus);

        SELECT  U.UserId, 
                U.RoleId, 
                R.Name AS RoleName, 
                U.FullName, 
                U.Email, 
                U.PasswordHash, 
                U.Phone, 
                U.Status,
                U.LastLoginAt, 
                U.CreatedAt, 
                U.UpdatedAt
        FROM UA_Users AS U
        INNER JOIN UA_Roles AS R ON R.RoleId = U.RoleId
        WHERE(@pKeyword IS NULL OR U.FullName LIKE '%' + @pSearchString + '%' OR U.Email LIKE '%' + @pSearchString + '%')
        AND(@pRole IS NULL OR R.Name = @pRole)
        AND(@pStatus IS NULL OR U.Status = @pStatus)
        ORDER BY U.CreatedAt DESC
        OFFSET @pOffset ROWS FETCH NEXT @pPageSize ROWS ONLY;";
}
