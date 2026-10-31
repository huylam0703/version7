using System;
using System.Collections.Generic;
using System.Text;
using System.Text.Json.Serialization;

namespace UserAuthService.Domain.Dtos;

public sealed record GetUserDto(
    Guid UserId,
    string FullName,
    string Email,
    string? Phone,
    string Role,
    string Status,
    DateTimeOffset? LastLoginAt,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

public sealed record AdminDto(Guid UserId, string FullName, string Email);

public sealed record LoginResponse(string AccessToken, string TokenType, int ExpiresIn,
    string RefreshToken, DateTime RefreshTokenExpiresAt,
    GetUserDto User);

public sealed record PagedResult<T>(IReadOnlyList<T> Items, int Page, int PageSize, long Total);

public sealed record UserListFilter(string? Keyword, string? Role, string? Status, int Page, int PageSize);

public sealed record AccessTokenDto(string Token, int ExpiresInSeconds);

/// Khóa công khai dạng JWK để công bố ở /.well-known/jwks.json.
public sealed record JwkDto(string Kty, string Use, string Alg, string Kid, string N, string E);

/// Phản hồi chuẩn OAuth2 cho /connect/token, nên giữ tên trường dạng snake_case và KHÔNG bọc khung ApiResponse.
public sealed class ServiceTokenResponse
{
    [JsonPropertyName("access_token")]
    public string AccessToken { get; init; } = "";

    [JsonPropertyName("token_type")]
    public string TokenType { get; init; } = "Bearer";

    [JsonPropertyName("expires_in")]
    public int ExpiresIn { get; init; }
}
