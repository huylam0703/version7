using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Application;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

/// <summary>
/// 
/// </summary>
public class RefreshTokenCommand : ICommand<RefreshResponse>
{
    /// <summary>
    /// 
    /// </summary>
    public string RefreshToken { get; set; } = "";
}

/// <summary>
/// 
/// </summary>
/// <param name="AccessToken"></param>
/// <param name="TokenType"></param>
/// <param name="ExpiresIn"></param>
/// <param name="RefreshToken"></param>
/// <param name="RefreshTokenExpiresAt"></param>
public record RefreshResponse(
    string AccessToken, string TokenType, int ExpiresIn,
    string RefreshToken, DateTime RefreshTokenExpiresAt);

/// <summary>
/// 
/// </summary>


/// <summary>
/// 
/// </summary>
