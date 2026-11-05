using Microsoft.Extensions.DependencyInjection;
using NotificationService.Domain.Interfaces;
using NotificationService.Infrastructure.Services;

namespace NotificationService.Infrastructure;

public static class DependencyInjectionExtension
{
    public static IServiceCollection AddInfrastructureService(
        this IServiceCollection services)
    {
        // Đăng ký service đọc thông báo
        services.AddScoped<INotificationReadService, NotificationReadService>();

        // Đăng ký service tạo, cập nhật và xóa thông báo
        services.AddScoped<INotificationService, NotificationWriteService>();

        return services;
    }
}