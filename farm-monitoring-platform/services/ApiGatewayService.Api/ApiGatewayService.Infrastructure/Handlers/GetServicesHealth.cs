using ApiGatewayService.Application.Abstractions;
using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

namespace ApiGatewayService.Application.Health;

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
