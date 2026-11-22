using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using UserAuthService.Domain.Interfaces.Services;
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
