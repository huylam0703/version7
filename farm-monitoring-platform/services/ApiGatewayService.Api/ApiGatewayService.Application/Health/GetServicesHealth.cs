using ApiGatewayService.Application.Abstractions;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

namespace ApiGatewayService.Application.Health;

public sealed record ServiceHealth(string Name, string Status, int? HttpStatus, long ElapsedMs);

public sealed record ServicesHealthDto(string Status, IReadOnlyList<ServiceHealth> Services);

public class GetServicesHealthQuery : IQuery<ServicesHealthDto> { }

public class GetServicesHealthHandler : IQueryHandler<GetServicesHealthQuery, ServicesHealthDto>
{
    private readonly IServiceHealthChecker _checker;

    public GetServicesHealthHandler(IServiceHealthChecker checker)
    {
        _checker = checker;
    }

    public async Task<Result<ServicesHealthDto>> Handle(GetServicesHealthQuery request, CancellationToken cancellationToken)
    {
        var services = await _checker.CheckAllAsync(cancellationToken);
        var overall = services.All(s => s.Status == "UP") ? "UP" : "DEGRADED";
        return new ServicesHealthDto(overall, services);
    }
}