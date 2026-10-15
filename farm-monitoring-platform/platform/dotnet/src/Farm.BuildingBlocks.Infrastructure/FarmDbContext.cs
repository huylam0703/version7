using Farm.BuildingBlocks.Abstractions;

using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Farm.BuildingBlocks.Infrastructure;

/// DbContext của mọi service .NET kế thừa lớp này. SaveChangesAsync của EF tự khớp IUnitOfWork.
public abstract class FarmDbContext(DbContextOptions options) : DbContext(options), IUnitOfWork { }

public static class FarmDb
{
    /// Ưu tiên ConnectionStrings:Default (chạy local); không có thì ghép từ DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD.
    public static string BuildConnectionString(IConfiguration c)
    {
        var explicitCs = c["ConnectionStrings:Default"];
        if (!string.IsNullOrWhiteSpace(explicitCs)) return explicitCs;

        return new SqlConnectionStringBuilder
        {
            DataSource = $"{c["DB_HOST"]},{c["DB_PORT"] ?? "1433"}",
            InitialCatalog = c["DB_NAME"],
            UserID = c["DB_USER"],
            Password = c["DB_PASSWORD"],
            TrustServerCertificate = true
        }.ConnectionString;
    }

    public static IServiceCollection AddFarmDbContext<TContext>(
        this IServiceCollection services, IConfiguration config) where TContext : FarmDbContext
    {
        services.AddSingleton<AuditInterceptor>();
        services.AddDbContext<TContext>((sp, o) => o
            .UseSqlServer(BuildConnectionString(config), sql => sql.EnableRetryOnFailure())
            .AddInterceptors(sp.GetRequiredService<AuditInterceptor>()));
        services.AddScoped<IUnitOfWork>(sp => sp.GetRequiredService<TContext>());
        return services;
    }
}