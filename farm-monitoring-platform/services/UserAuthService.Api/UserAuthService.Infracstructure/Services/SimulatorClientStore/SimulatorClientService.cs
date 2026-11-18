using System.Data;
using System.Security.Cryptography;
using System.Text;

using Dapper;

using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure.Database;
using UserAuthService.Infracstructure.Queries;

namespace UserAuthService.Infracstructure.Services;

public sealed class SimulatorClientService : ISimulatorClientService
{
    /// <summary>
    /// Dùng khi client không tồn tại, để thời gian xử lý gần giống trường hợp sai secret
    /// </summary>
    private static readonly byte[] DummyHash = SHA256.HashData(Encoding.UTF8.GetBytes("farm-dummy-secret"));

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
    /// <param name="query"></param>
    /// <param name="options"></param>
    public SimulatorClientService(IQuery query, IOptions<CoreAppSetting> options)
    {
        _query = query;
        _options = options;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="secret"></param>
    /// <returns></returns>
    private static byte[] HashBytes(string secret) => SHA256.HashData(Encoding.UTF8.GetBytes(secret));
    /// <summary>
    /// 
    /// </summary>
    /// <param name="secret"></param>
    /// <returns></returns>
    private static string HashHex(string secret) => Convert.ToHexString(HashBytes(secret)).ToLowerInvariant();
    /// <summary>
    /// 
    /// </summary>
    /// <returns></returns>
    private static string NewSecret() => Base64UrlEncoder.Encode(RandomNumberGenerator.GetBytes(32));

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="secret"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<ClientAuthResult> AuthenticateAsync(string clientId, string secret, CancellationToken ct = default)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pClientId", clientId.Trim().ToLowerInvariant(), DbType.AnsiString, ParameterDirection.Input, 100);

        var client = (await _query.QueryAsync<SimulatorClient>(
            _options.Value.ConnectionStringSetting!.UserAuthService!,
            SimulatorClientQuery.FindByClientId,
            parameters
        )).FirstOrDefault();

        var expected = client is null ? DummyHash : Convert.FromHexString(client.SecretHash);
        var secretOk = CryptographicOperations.FixedTimeEquals(HashBytes(secret), expected);

        if (client is null) return new ClientAuthResult { Status = ClientAuthStatus.NotFound, Client = null };
        if (!secretOk) return new ClientAuthResult { Status = ClientAuthStatus.InvalidSecret, Client = null };
        if (!client.IsActive) return new ClientAuthResult{ Status = ClientAuthStatus.Disabled, Client = null };
        return new ClientAuthResult { Status = ClientAuthStatus.Ok, Client = client };
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<IReadOnlyList<SimulatorClient>> ListAsync(CancellationToken ct = default)
        => (await _query.QueryAsync<SimulatorClient>(
            _options.Value.ConnectionStringSetting!.UserAuthService!, 
            SimulatorClientQuery.ListAll
        )).ToList();

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="displayName"></param>
    /// <param name="scopes"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<SimulatorClientSecret?> RegisterAsync(string clientId, string? displayName, IReadOnlyList<string> scopes, CancellationToken ct = default)
    {
        var secret = NewSecret();
        var parameters = new DynamicParameters();
        parameters.Add("@pClientId", clientId.Trim().ToLowerInvariant(), DbType.AnsiString, ParameterDirection.Input);
        parameters.Add("@pDisplayName", displayName?.Trim(), DbType.String, ParameterDirection.Input, 150);
        parameters.Add("@pSecretHash", HashHex(secret), DbType.AnsiString, ParameterDirection.Input, 64);
        parameters.Add("@pScopes", string.Join(' ', scopes), DbType.AnsiString, ParameterDirection.Input, 500);

        var rows = await _query.ExecuteAsync(
            _options.Value.ConnectionStringSetting!.UserAuthService!, 
            SimulatorClientQuery.Insert, 
            parameters);

        return rows == 0 ? null : new SimulatorClientSecret { 
            ClientId = clientId.Trim().ToLowerInvariant(), 
            Secret = secret, 
            Scopes = scopes 
        };
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<SimulatorClientSecret?> RotateSecretAsync(string clientId, CancellationToken ct = default)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pClientId", clientId.Trim().ToLowerInvariant(), DbType.AnsiString, ParameterDirection.Input, 100);

        var client = (await _query.QueryAsync<SimulatorClient>(
            _options.Value.ConnectionStringSetting!.UserAuthService!,
            SimulatorClientQuery.FindByClientId,
            parameters
        )).FirstOrDefault();
        if (client is null) return null;

        var secret = NewSecret();
        parameters.Add("@pSecretHash", HashHex(secret), DbType.AnsiString, ParameterDirection.Input, 64);

        await _query.ExecuteAsync(
            _options.Value.ConnectionStringSetting!.UserAuthService!, 
            SimulatorClientQuery.RotateSecret, 
            parameters
        );

        return new SimulatorClientSecret { 
            ClientId = client.ClientId, 
            Secret = secret, 
            Scopes = SimulatorScopes.Parse(client.AllowedScopes) 
        };
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="active"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<bool> SetActiveAsync(string clientId, bool active, CancellationToken ct = default)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pClientId", clientId.Trim().ToLowerInvariant(), DbType.AnsiString, ParameterDirection.Input, 100);
        parameters.Add("@pActive", active, DbType.Boolean, ParameterDirection.Input);
        return await _query.ExecuteAsync(
            _options.Value.ConnectionStringSetting!.UserAuthService!, 
            SimulatorClientQuery.SetActive, 
            parameters
        ) > 0;
    }
}