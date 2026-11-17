using System;
using System.Collections.Generic;
using System.Text;

using UserAuthService.Domain.Entity;

namespace UserAuthService.Domain.Interfaces.Services;

/// <summary>
/// 
/// </summary>
public enum ClientAuthStatus { 
    Ok, 
    NotFound, 
    InvalidSecret, 
    Disabled 
}

/// <summary>
/// 
/// </summary>
public interface ISimulatorClientService
{
    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="secret"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<ClientAuthResult> AuthenticateAsync(string clientId, string secret, CancellationToken ct = default);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<IReadOnlyList<SimulatorClient>> ListAsync(CancellationToken ct = default);

    /// <summary>
    /// null nếu client_id đã tồn tại
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="displayName"></param>
    /// <param name="scopes"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<SimulatorClientSecret?> RegisterAsync(string clientId, string? displayName, IReadOnlyList<string> scopes, CancellationToken ct = default);

    /// <summary>
    /// null nếu không tìm thấy
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<SimulatorClientSecret?> RotateSecretAsync(string clientId, CancellationToken ct = default);

    /// <summary>
    /// false nếu không tìm thấy
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="active"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    Task<bool> SetActiveAsync(string clientId, bool active, CancellationToken ct = default);
}

/// <summary>
/// 
/// </summary>
public class ClientAuthResult
{
    /// <summary>
    /// 
    /// </summary>
    public ClientAuthStatus Status { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public SimulatorClient? Client { get; set; }
}


/// <summary>
/// 
/// </summary>
public class SimulatorClientSecret
{
    /// <summary>
    /// 
    /// </summary>
    public string? ClientId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Secret { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public IReadOnlyList<string>? Scopes { get; set; }
}