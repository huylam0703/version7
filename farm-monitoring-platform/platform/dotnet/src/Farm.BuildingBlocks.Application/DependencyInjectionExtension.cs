using System.Reflection;
using FluentValidation;
using Microsoft.Extensions.DependencyInjection;

namespace Farm.BuildingBlocks.Application;

public static class DependencyInjectionExtension
{
    public static IServiceCollection AddFarmApplication(this IServiceCollection services, Assembly assembly)
    {
        services.AddValidatorsFromAssembly(assembly, includeInternalTypes: true);
        services.AddScoped<IRequestExecutor, RequestExecutor>();
        return services;
    }

    public static IServiceCollection AddRequestHandlers(this IServiceCollection services, Assembly assembly)
    {
        foreach (var type in assembly.GetTypes().Where(t => t.IsClass && !t.IsAbstract))
            foreach (var service in type.GetInterfaces().Where(i => i.IsGenericType &&
                i.GetGenericTypeDefinition() == typeof(IRequestHandler<,>)))
                services.AddScoped(service, type);
        return services;
    }
}
