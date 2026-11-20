using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Entity;

/// <summary>
/// 
/// </summary>

public static class SimulatorScopes
{
    public const string MissionRead = "mission:read";
    public const string MissionAck = "mission:ack";
    public const string MissionProgress = "mission:progress";
    public const string MissionHeartbeat = "mission:heartbeat";
    public const string MissionResults = "mission:results";

    public static readonly IReadOnlyList<string> All =
    [
        MissionRead,
        MissionAck,
        MissionProgress,
        MissionHeartbeat,
        MissionResults
    ];

    public static bool IsKnown(string scope)
        => All.Contains(scope, StringComparer.Ordinal);

    public static List<string> Parse(string? scope)
        => (scope ?? "")
            .Split(
                ' ',
                StringSplitOptions.RemoveEmptyEntries |
                StringSplitOptions.TrimEntries
            )
            .Distinct(StringComparer.Ordinal)
            .ToList();
}


/// <summary>
/// Machine client (UAV Gateway Simulator). Không phải người dùng, không có role.
/// </summary>
public class SimulatorClient
{
    /// <summary>
    /// 
    /// </summary>
    public Guid ClientRowId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string ClientId { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string? DisplayName { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string SecretHash { get; set; } = "";      // SHA-256 hex, không lưu secret gốc
    /// <summary>
    /// 
    /// </summary>
    public string AllowedScopes { get; set; } = "";   // cách nhau bằng dấu cách
    /// <summary>
    /// 
    /// </summary>
    public bool IsActive { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime? SecretRotatedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime? RevokedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime CreatedAt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public DateTime UpdatedAt { get; set; }
}