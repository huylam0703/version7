using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using MediatR;

using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Clients;

/// <summary>
/// 
/// </summary>
public class SetActiveCommand : ICommand<Unit>
{
    /// <summary>
    /// 
    /// </summary>
    public string ClientId { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public bool Active { get; set; }
}


/// <summary>
/// 
/// </summary>
public class SetSimulatorClientActiveCommandHandler : ICommandHandler<SetActiveCommand, Unit>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly ISimulatorClientService _simulatorClientService;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="simulatorClientService"></param>
    public SetSimulatorClientActiveCommandHandler(ISimulatorClientService simulatorClientService)
    {
        _simulatorClientService = simulatorClientService;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<Unit>> Handle(SetActiveCommand request, CancellationToken ct)
    {
        var found = await _simulatorClientService.SetActiveAsync(request.ClientId, request.Active, ct);
        if (!found)
            return Error.NotFound(UserAuthErrors.ClientNotFound, "Không tìm thấy client.");

        return Result.Success();
    }
}
