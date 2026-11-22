using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;


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
