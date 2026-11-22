using ApiGatewayService.Application.Abstractions;
using ApiGatewayService.Infrastructure.Health;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using Microsoft.Extensions.DependencyInjection;

namespace ApiGatewayService.Infrastructure;

public static class GatewayInfrastructureExtensions
{
    public static IServiceCollection AddGatewayInfrastructure(this IServiceCollection services)
    {
        services.AddHttpClient("health", c => c.Timeout = TimeSpan.FromSeconds(3));
        services.AddScoped<IServiceHealthChecker, HttpServiceHealthChecker>();
        services.AddScoped<IUnitOfWork, NoOpUnitOfWork>();
        services.AddRequestHandlers(typeof(GatewayInfrastructureExtensions).Assembly);
        return services;
    }
}