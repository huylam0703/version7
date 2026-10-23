using Farm.BuildingBlocks.Application;

using Microsoft.Extensions.DependencyInjection;

namespace ApiGatewayService.Application;

public static class GatewayApplicationExtensions
{
    public static IServiceCollection AddGatewayApplication(this IServiceCollection services)
        => services.AddFarmApplication(typeof(GatewayApplicationExtensions).Assembly);
}