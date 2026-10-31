using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using MediatR;

using UserAuthService.Domain.Interfaces.Services;

/// <summary>
/// 
/// </summary>
public class LogoutCommand : ICommand<Unit>
{
    /// <summary>
    /// 
    /// </summary>
    public string RefreshToken { get; set; } = "";
}

/// <summary>
/// 
/// </summary>
public class LogoutCommandHandler : ICommandHandler<LogoutCommand, Unit>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IRefreshTokenService _refreshTokenService;
    /// <summary>
    /// 
    /// </summary>
    /// <param name="refreshTokenService"></param>
    public LogoutCommandHandler(IRefreshTokenService refreshTokenService)
    {
        _refreshTokenService = refreshTokenService;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<Unit>> Handle(LogoutCommand request, CancellationToken ct)
    {
        if (!string.IsNullOrWhiteSpace(request.RefreshToken))
            await _refreshTokenService.RevokeAsync(request.RefreshToken, ct);
        return Result.Success();
    }
}