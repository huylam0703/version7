using System.Reflection;

using FluentValidation;

using Microsoft.Extensions.DependencyInjection;

namespace Farm.BuildingBlocks.Application;

public static class DependencyInjectionExtension
{
    public static IServiceCollection AddFarmApplication(
        this IServiceCollection services, Assembly assembly, string? mediatrLicenseKey = null)
    {
        services.AddMediatR(cfg =>
        {
            cfg.RegisterServicesFromAssembly(assembly);                  // quét handler

            // Chỉ có ở MediatR 13 trở lên. Dùng bản 12.x thì XÓA dòng dưới.
            if (!string.IsNullOrWhiteSpace(mediatrLicenseKey)) cfg.LicenseKey = mediatrLicenseKey;

            // behavior đăng ký trước sẽ chạy ngoài cùng: Logging -> Validation -> UnitOfWork -> Handler
            cfg.AddOpenBehavior(typeof(LoggingBehavior<,>));
            cfg.AddOpenBehavior(typeof(ValidationBehavior<,>));
            cfg.AddOpenBehavior(typeof(UnitOfWorkBehavior<,>));
        });

        services.AddValidatorsFromAssembly(assembly, includeInternalTypes: true);
        return services;
    }
}