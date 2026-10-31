using System.Data;
using System.Reflection.Metadata;
using System.Security.Cryptography;
using System.Text;

using Dapper;

using Farm.BuildingBlocks.Abstractions;

using Microsoft.AspNetCore.Connections;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure.Database;
using UserAuthService.Infracstructure.Queries;

namespace UserAuthService.Infracstructure.Services;

/// <summary>
/// 
/// </summary>
public class RefreshTokenService : IRefreshTokenService
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IQuery _query;  
    /// <summary>
    /// 
    /// </summary>
    private readonly IOptions<CoreAppSetting> _options;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="db"></param>
    /// <param name="options"></param>
    public RefreshTokenService(IQuery db, IOptions<CoreAppSetting> options)
    {
        _query = db;
        _options = options;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="token"></param>
    /// <returns></returns>
    private static string Hash(string token)
        => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token))).ToLowerInvariant();

    /// <summary>
    /// 
    /// </summary>
    private string ConnectionString => _options!.Value!.ConnectionStringSetting!.UserAuthService!;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<RefreshTokenIssue> IssueAsync(Guid userId, CancellationToken ct = default)
    {
        var token = Base64UrlEncoder.Encode(RandomNumberGenerator.GetBytes(32)); // 256-bit
        var days = _options.Value.Jwt!.RefreshTokenExpireDays > 0
            ? _options.Value.Jwt!.RefreshTokenExpireDays : 7;
        var expiresAt = DateTime.UtcNow.AddDays(days);
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);
        parameters.Add("@pHash", Hash(token), DbType.AnsiString, ParameterDirection.Input);
        parameters.Add("@pExpiresAt", expiresAt, DbType.DateTime2, ParameterDirection.Input);
        await _query.ExecuteAsync(ConnectionString, RefreshTokenQuery.ProvideRefreshToken, parameters);

        return new RefreshTokenIssue(token, expiresAt);
    }


    /// <summary>
    /// 
    /// </summary>
    /// <param name="token"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<ConsumeResult> ConsumeAsync(string token, CancellationToken ct = default)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pHash", Hash(token), DbType.AnsiString, ParameterDirection.Input);
        var userId = await _query.QueryAsync<Guid?>(ConnectionString, RefreshTokenQuery.ConsumeRefreshToken, parameters);

        if (userId is not null)
            return new ConsumeResult(ConsumeStatus.Ok, userId.FirstOrDefault());

        var reusedUser = (await _query.QueryAsync<Guid?>(
            
            ConnectionString, RefreshTokenQuery.FindRevokedRefreshToken, parameters)).FirstOrDefault();

        if (reusedUser is not null)
        {
            await RevokeAllAsync(reusedUser.Value, ct);
            return new ConsumeResult(ConsumeStatus.Reused, reusedUser);
        }

        return new ConsumeResult(ConsumeStatus.Invalid, null); // không tồn tại hoặc hết hạn
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="token"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task RevokeAsync(string token, CancellationToken ct = default)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pHash", Hash(token), DbType.AnsiString, ParameterDirection.Input);
        await _query.ExecuteAsync(
            connectionString: ConnectionString,
            sql: RefreshTokenQuery.RevokeRefreshToken,
            param: parameters
        );
    }


    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task RevokeAllAsync(Guid userId, CancellationToken ct = default)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);
        await _query.ExecuteAsync(
            connectionString: ConnectionString, 
            sql: RefreshTokenQuery.RevokeAllRefreshTokens, 
            param: parameters
        );
    }

}