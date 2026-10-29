using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;

using Microsoft.AspNetCore.Builder;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Interfaces.Utilities;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure.Database;
using UserAuthService.Infracstructure.Services;
using UserAuthService.Infracstructure.Services.UserService;
using UserAuthService.Infracstructure.Ultilities;

using static UserAuthService.Infracstructure.Ultilities.JwtHelper;

namespace UserAuthService.Infracstructure;

public static class DependencyInjectionExtension
{
    /// <summary>
    /// Đăng ký DI cho service liftime
    /// </summary>
    /// <param name="services"></param>
    /// <returns></returns>
    public static IServiceCollection AddInfrastructureService(this IServiceCollection services)
    {
        //DI
        services.AddSingleton<IQuery, SqlServer>();

        services.AddSingleton<JwtKeyProvider>();
        services.AddSingleton<IJwtService, JwtService>();
        services.AddSingleton<IHashUtilities, HashUtilities>();
        services.AddSingleton<IServiceClientStore, ConfigServiceClientStore>();

        services.AddScoped<IUserService, UserService>();
        services.AddScoped<IUnitOfWork, NoOpUnitOfWork>();


        return services;
    }

    public static IApplicationBuilder UseCorePackageBuilder(this IApplicationBuilder app)
    {
        var serviceProvider = app.ApplicationServices.GetRequiredService<IServiceProvider>();
        var coreAppSettings = serviceProvider.GetService<IOptions<CoreAppSetting>>();


        return app;
    }
}
