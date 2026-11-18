using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Clients;

/// <summary>
/// 
/// </summary>
public class RotateSimulatorSecretCommand : ICommand<SimulatorClientSecretDto>
{
    /// <summary>
    /// 
    /// </summary>
    public string ClientId { get; set; } = "";
}

/// <summary>
/// 
/// </summary>
public class RotateSimulatorClientSecretCommandHandler : ICommandHandler<RotateSimulatorSecretCommand, SimulatorClientSecretDto>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly ISimulatorClientService _simulatorClientService;
    /// <summary>
    /// 
    /// </summary>
    /// <param name="simulatorClientService"></param>
    public RotateSimulatorClientSecretCommandHandler(ISimulatorClientService simulatorClientService)
    {
        _simulatorClientService = simulatorClientService;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<SimulatorClientSecretDto>> Handle(RotateSimulatorSecretCommand request, CancellationToken ct)
    {
        var rotated = await _simulatorClientService.RotateSecretAsync(request.ClientId, ct);
        if (rotated is null)
            return Error.NotFound(UserAuthErrors.ClientNotFound, "Không tìm thấy client.");

        return new SimulatorClientSecretDto
        {
            ClientId = rotated.ClientId,
            ClientSecret = rotated.Secret,
            Scopes = rotated.Scopes,
            Note = "Secret cũ đã vô hiệu. Token đã cấp vẫn dùng được đến khi hết hạn."
        };
            
    }
}
