using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Queries.Clients;

/// <summary>
/// 
/// </summary>
public class ListSimulatorsQuery : IQuery<IReadOnlyList<SimulatorClientDto>> 
{ 

}

/// <summary>
/// 
/// </summary>
public class ListSimulatorClientsQueryHandler : IQueryHandler<ListSimulatorsQuery, IReadOnlyList<SimulatorClientDto>>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly ISimulatorClientService _simulatorClientService;
    /// <summary>
    /// 
    /// </summary>
    /// <param name="simulatorClientService"></param>
    public ListSimulatorClientsQueryHandler(ISimulatorClientService simulatorClientService)
    {
        _simulatorClientService = simulatorClientService;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<IReadOnlyList<SimulatorClientDto>>> Handle(ListSimulatorsQuery request, CancellationToken ct)
    {
        var items = await _simulatorClientService.ListAsync(ct);
        IReadOnlyList<SimulatorClientDto> dtos = items.Select(SimulatorClientDto.From).ToList();
        return Result<IReadOnlyList<SimulatorClientDto>>.Success(dtos);
    }
}