using System.IdentityModel.Tokens.Jwt;

using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.UserDto;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Settings;

namespace UserAuthService.Infracstructure.Services;

public class JwtService : IJwtService
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IOptions<CoreAppSetting> _options;
    /// <summary>
    /// 
    /// </summary>
    private readonly JwtKeyProvider _keys;
    /// <summary>
    /// 
    /// </summary>
    private readonly JwtSecurityTokenHandler _handler = new();

    /// <summary>
    /// 
    /// </summary>
    /// <param name="options"></param>
    /// <param name="keys"></param>
    public JwtService(IOptions<CoreAppSetting> options, JwtKeyProvider keys)
    {
        _options = options;
        _keys = keys;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="user"></param>
    /// <returns></returns>
    public AccessTokenDto CreateAccessToken(UserInfoDto user)
        => Create(
            new Dictionary<string, object>
            {
                ["sub"] = user.UserId.ToString(),
                ["email"] = user.Email,
                ["role"] = user.RoleName,
                ["jti"] = Guid.NewGuid().ToString()
            },
            TimeSpan.FromMinutes(
                _options.Value.Jwt!.ExpireTime > 0
                    ? _options.Value.Jwt!.ExpireTime
                    : 10080));

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <returns></returns>
    public AccessTokenDto CreateServiceToken(string clientId)
        => Create(
            new Dictionary<string, object>
            {
                ["sub"] = clientId,
                ["role"] = FarmRole.Service,
                ["jti"] = Guid.NewGuid().ToString()
            },
            TimeSpan.FromMinutes(
                _options.Value.Jwt!.ServiceTokenExpireTime > 0
                    ? _options.Value.Jwt!.ServiceTokenExpireTime
                    : 15));

    /// <summary>
    /// 
    /// </summary>
    /// <returns></returns>
    public JwkDto GetPublicKey()
    {
        var jwk = _keys.PublicJwk;

        return new JwkDto(
            jwk.Kty,
            "sig",
            "RS256",
            jwk.Kid,
            jwk.N,
            jwk.E);
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="claims"></param>
    /// <param name="lifetime"></param>
    /// <returns></returns>
    private AccessTokenDto Create(
        Dictionary<string, object> claims,
        TimeSpan lifetime)
    {
        var now = DateTime.UtcNow;

        var descriptor = new SecurityTokenDescriptor
        {
            Issuer = _options.Value.Jwt!.Issuer,
            Audience = _options.Value.Jwt!.Audience,
            IssuedAt = now,
            NotBefore = now,
            Expires = now.Add(lifetime),
            Claims = claims,
            SigningCredentials = new SigningCredentials(
                _keys.SigningKey,
                SecurityAlgorithms.RsaSha256)
        };

        var token = _handler.CreateToken(descriptor);

        return new AccessTokenDto(
            _handler.WriteToken(token),
            (int)lifetime.TotalSeconds);
    }
}