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
public class RefreshTokenValidator : AbstractValidator<RefreshTokenCommand>
{
    /// <summary>
    /// 
    /// </summary>
    public RefreshTokenValidator()
    {
        RuleFor(x => x.RefreshToken).NotEmpty().WithMessage("Refresh token không được để trống.");
    }
}

/// <summary>
/// 
/// </summary>
public class RefreshTokenCommandHandler : ICommandHandler<RefreshTokenCommand, RefreshResponse>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IUserService _userService;
    /// <summary>
    /// 
    /// </summary>
    private readonly IJwtService _jwtService;
    /// <summary>
    /// 
    /// </summary>
    private readonly IRefreshTokenService _refreshTokenService;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userService"></param>
    /// <param name="jwtService"></param>
    /// <param name="refreshTokenService"></param>
    public RefreshTokenCommandHandler(IUserService userService, IJwtService jwtService, IRefreshTokenService refreshTokenService)
    {
        _userService = userService;
        _jwtService = jwtService;
        _refreshTokenService = refreshTokenService;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<RefreshResponse>> Handle(RefreshTokenCommand request, CancellationToken ct)
    {
        var consumed = await _refreshTokenService.ConsumeAsync(request.RefreshToken, ct);
        if (consumed.Status != ConsumeStatus.Ok)
            return Error.Unauthorized(UserAuthErrors.InvalidCredentials,
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");

        var user = await _userService.GetByIdAsync(consumed.UserId!.Value);
        if (user is null || string.Equals(user.Status, UserStatus.Locked, StringComparison.OrdinalIgnoreCase))
        {
            await _refreshTokenService.RevokeAllAsync(consumed.UserId.Value, ct);
            return Error.Forbidden(UserAuthErrors.AccountLocked, "Tài khoản đã bị khóa.");
        }

        var access = _jwtService.CreateAccessToken(user);
        var next = await _refreshTokenService.IssueAsync(user.UserId, ct);

        return new RefreshResponse(access.Token, "Bearer", access.ExpiresInSeconds, next.Token, next.ExpiresAt);
    }
}