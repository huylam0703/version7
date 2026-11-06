
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using NotificationService.Infrastructure;
using NotificationService.Infrastructure.Data;

var builder = WebApplication.CreateBuilder(args);

// ========================================
// 1. Đăng ký Dependency Injection
// ========================================

// Đăng ký các service của Notification
builder.Services.AddInfrastructureService();

// Đăng ký Controllers và OpenAPI
builder.Services.AddControllers();
builder.Services.AddOpenApi();

// ========================================
// 2. Cấu hình SQL Server
// ========================================

var connectionString =
    builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException(
        "Chưa cấu hình ConnectionStrings:Default");

builder.Services.AddDbContext<NotificationDbContext>(options =>
    options.UseSqlServer(connectionString));

// ========================================
// 3. Cấu hình JWT Authentication
// ========================================

var jwt = builder.Configuration.GetSection("Jwt");

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.MetadataAddress = jwt["MetadataAddress"]
            ?? throw new InvalidOperationException(
                "Chưa cấu hình Jwt:MetadataAddress");

        // Chỉ sử dụng HTTP trong môi trường Development
        options.RequireHttpsMetadata = false;

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidIssuer = jwt["Issuer"],

            ValidateAudience = true,
            ValidAudience = jwt["Audience"],

            ValidateLifetime = true,

            ValidateIssuerSigningKey = true,

            ClockSkew = TimeSpan.Zero
        };
    });

// ========================================
// 4. Cấu hình Authorization
// ========================================

builder.Services.AddAuthorization();

// ========================================
// 5. Build ứng dụng
// ========================================

var app = builder.Build();

// ========================================
// 6. OpenAPI
// ========================================

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

// ========================================
// 7. Middleware
// ========================================

app.UseAuthentication();
app.UseAuthorization();

// ========================================
// 8. Map Controllers
// ========================================

app.MapControllers();

// ========================================
// 9. Health Check
// ========================================

app.MapGet("/health", () =>
{
    return Results.Ok(new
    {
        status = "ok",
        service = "notification-service",
        timestamp = DateTime.UtcNow
    });
});

// ========================================
// 10. Chạy ứng dụng
// ========================================

app.Run();
