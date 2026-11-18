using System;
using System.Collections.Generic;
using System.Text;

using UserAuthService.Domain.Entity;

namespace UserAuthService.Domain.Dtos;

/// <summary>
/// 
/// </summary>
/// <param name="ClientId"></param>
/// <param name="DisplayName"></param>
/// <param name="AllowedScopes"></param>
/// <param name="IsActive"></param>
/// <param name="SecretRotatedAt"></param>
/// <param name="RevokedAt"></param>
/// <param name="CreatedAt"></param>
/// <param name="UpdatedAt"></param>
public sealed record SimulatorClientDto(
    string ClientId,
    string? DisplayName,
    IReadOnlyList<string> AllowedScopes,
    bool IsActive,
    DateTime? SecretRotatedAt,
    DateTime? RevokedAt,
    DateTime CreatedAt,
    DateTime UpdatedAt)
{
    /// <summary>
    /// 
    /// </summary>
    /// <param name="c"></param>
    /// <returns></returns>
    public static SimulatorClientDto From(SimulatorClient c) => new(
        c.ClientId, c.DisplayName, SimulatorScopes.Parse(c.AllowedScopes),
        c.IsActive, c.SecretRotatedAt, c.RevokedAt, c.CreatedAt, c.UpdatedAt);
}

/// <summary>
/// Chỉ trả về đúng 1 lần, lúc đăng ký hoặc rotate.
/// </summary>
/// <param name="ClientId"></param>
/// <param name="ClientSecret"></param>
/// <param name="Scopes"></param>
/// <param name="Note"></param>
public class SimulatorClientSecretDto
{
    /// <summary>
    /// 
    /// </summary>
    public string? ClientId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? ClientSecret { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public IReadOnlyList<string>? Scopes { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Note { get; set; }
}
