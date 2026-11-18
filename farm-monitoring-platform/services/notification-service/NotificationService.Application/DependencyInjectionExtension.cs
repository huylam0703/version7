using Farm.BuildingBlocks.Application;
using Microsoft.Extensions.DependencyInjection;

namespace NotificationService.Application;

public static class DependencyInjectionExtension
{
    public static IServiceCollection AddApplicationService(
        this IServiceCollection services)
    {
        return services.AddFarmApplication(
            typeof(DependencyInjectionExtension).Assembly);
    }
}