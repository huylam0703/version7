using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Infracstructure.Queries;

public static class RefreshTokenQuery
{
    /// <summary>
    /// Lưu refresh token mới vào DB khi đăng nhập hoặc khi làm mới token.
    /// </summary>
    public const string ProvideRefreshToken = @"
        INSERT INTO UA_RefreshTokens (UserId, TokenHash, ExpiresAt)
        VALUES (@pUserId, @pHash, @pExpiresAt)
    ";
    /// <summary>
    /// Đánh dấu thu hồi và trả về UserId để cấp cặp token mới.
    /// </summary>
    public const string ConsumeRefreshToken = @"
        UPDATE UA_RefreshTokens
           SET RevokedAt = SYSUTCDATETIME()
        OUTPUT inserted.UserId
         WHERE TokenHash = @pHash
           AND RevokedAt IS NULL
           AND ExpiresAt > SYSUTCDATETIME()
    ";

    /// <summary>
    /// Tìm token đã bị thu hồi trước đó ( sau khi consume chạy thất bại)
    /// </summary>
    public const string FindRevokedRefreshToken = @"
        SELECT UserId
          FROM UA_RefreshTokens
         WHERE TokenHash = @pHash
           AND RevokedAt IS NOT NULL
    ";

    /// <summary>
    /// Thu hồi một refresh token cụ thể, dùng cho đăng xuất
    /// </summary>
    public const string RevokeRefreshToken = @"
        UPDATE UA_RefreshTokens
           SET RevokedAt = SYSUTCDATETIME()
         WHERE TokenHash = @pHash
           AND RevokedAt IS NULL
    ";

    /// <summary>
    ///  Thu hồi toàn bộ refresh token còn hiệu lực của một user
    /// </summary>
    public const string RevokeAllRefreshTokens = @"
        UPDATE UA_RefreshTokens
           SET RevokedAt = SYSUTCDATETIME()
         WHERE UserId = @pUserId
           AND RevokedAt IS NULL
    ";

    /// <summary>
    ///  Dọn dữ liệu cũ
    /// </summary>
    public const string CleanupRefreshTokens = @"
        DELETE FROM UA_RefreshTokens
         WHERE ExpiresAt < DATEADD(DAY, -7, SYSUTCDATETIME())
    ";
}
