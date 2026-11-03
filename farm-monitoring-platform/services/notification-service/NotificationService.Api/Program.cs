
using Microsoft.EntityFrameworkCore;
using NotificationService.Api.Data;

var builder = WebApplication.CreateBuilder(args);

// 1. Đăng ký Controllers
builder.Services.AddControllers();

// 2. Đăng ký OpenAPI
builder.Services.AddOpenApi();

// 3. Đăng ký Entity Framework Core với SQL Server
var connectionString =
    builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException(
        "Chưa cấu hình ConnectionStrings:Default");

builder.Services.AddDbContext<NotificationDbContext>(options =>
    options.UseSqlServer(connectionString));

// 4. Khởi tạo ứng dụng
var app = builder.Build();

// 5. OpenAPI trong môi trường Development
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

// 6. Chỉ bật HTTPS khi đã cấu hình chứng chỉ
// app.UseHttpsRedirection();

// 7. Ánh xạ Controllers
app.MapControllers();

// 8. API kiểm tra Notification Service
app.MapGet("/health", () =>
{
    return Results.Ok(new
    {
        status = "ok",
        service = "notification-service",
        timestamp = DateTime.UtcNow
    });
});

// 9. Chạy ứng dụng
app.Run();
