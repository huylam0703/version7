using System.Diagnostics;
using System.Text.Json;
using System.Threading.RateLimiting;


using ApiGatewayService.Application;
using ApiGatewayService.Infrastructure;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Web;

using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;

var builder = WebApplication.CreateBuilder(args);

builder.AddFarmWeb("api-gateway");
builder.Services.AddGatewayApplication();
builder.Services.AddGatewayInfrastructure();

var gateway = builder.Configuration.GetSection("Gateway");
var authEnabled = gateway.GetValue("Auth:Enabled", true);
if (!authEnabled && !builder.Environment.IsDevelopment())
    throw new InvalidOperationException("Gateway:Auth:Enabled=false chỉ dùng ở Development");

// authenticated
builder.Services.AddAuthorizationBuilder()
    .AddPolicy("authenticated", policy =>
    {
        if (authEnabled) policy.RequireAuthenticatedUser();
        else policy.RequireAssertion(_ => true);
    });

// CORS cho front-end chạy ở địa chỉ khác
var origins = gateway.GetSection("Cors:Origins").Get<string[]>() ?? [];
builder.Services.AddCors(o => o.AddPolicy("web", p => p
    .WithOrigins(origins)
    .AllowAnyHeader()
    .AllowAnyMethod()));

// Giới hạn số request mỗi phút theo địa chỉ IP
var permit = gateway.GetValue("RateLimit:PermitPerMinute", 120);
builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    o.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(ctx =>
        RateLimitPartition.GetFixedWindowLimiter(
            ctx.Connection.RemoteIpAddress?.ToString() ?? "unknown",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = permit,
                Window = TimeSpan.FromMinutes(1)
            }));
    o.OnRejected = async (context, ct) =>
    {
        var http = context.HttpContext;
        http.Response.StatusCode = StatusCodes.Status429TooManyRequests;

        var body = ApiResponse.Fail(ResultCode.ErrorRateLimited,
            traceId: Activity.Current?.TraceId.ToString());

        await http.Response.WriteAsJsonAsync(body, FarmJson.Options, "application/json", ct);
    };
});

builder.Services.AddReverseProxy()
    .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));

var app = builder.Build();

app.UseCors("web");
app.UseRateLimiter();
app.UseFarmWeb();       
app.MapControllers();    
app.MapReverseProxy();

app.Run();