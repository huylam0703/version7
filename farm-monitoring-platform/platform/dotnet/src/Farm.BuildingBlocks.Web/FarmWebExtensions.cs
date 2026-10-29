using System.Diagnostics;
using System.Text.Json;
using System.Text.Json.Serialization;

using Farm.BuildingBlocks.Abstractions;

using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Diagnostics.HealthChecks;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Microsoft.IdentityModel.Tokens;

using OpenTelemetry;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

namespace Farm.BuildingBlocks.Web;

public static class FarmWebExtensions
{
    /// configureJwt: cho phép user-auth dùng khóa nội bộ thay vì gọi chính mình qua JWKS.
    /// Hàm này chạy SAU cùng nên user-auth phải tự đặt IssuerSigningKeyResolver = null và IssuerSigningKey = khóa công khai.
    public static WebApplicationBuilder AddFarmWeb(
        this WebApplicationBuilder builder, string serviceName,
        Action<JwtBearerOptions>? configureJwt = null)
    {
        var services = builder.Services;
        var config = builder.Configuration;

        // 1. JSON theo quy ước: camelCase, enum viết HOA_GẠCH_DƯỚI (InMission -> IN_MISSION)
        services.AddControllers()
            .AddJsonOptions(o =>
            {
                o.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
                o.JsonSerializerOptions.Converters.Add(
                    new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseUpper, allowIntegerValues: false));
            });

        // 2. Lỗi theo envelope ApiResponse (mục 4 của PLATFORM-CONVENTIONS)
        services.AddProblemDetails(o => o.CustomizeProblemDetails = ctx =>
            ctx.ProblemDetails.Extensions["traceId"] =
                Activity.Current?.TraceId.ToString() ?? ctx.HttpContext.TraceIdentifier);
        services.AddExceptionHandler<GlobalExceptionHandler>();

        // 3. JWT: RS256, khóa lấy từ JWKS của user-auth (AUTH_JWKS_URI), role ở claim "role", sub là UserId
        var jwksUri = config["AUTH_JWKS_URI"];
        if (string.IsNullOrWhiteSpace(jwksUri) && configureJwt is null)
            throw new InvalidOperationException("Thiếu AUTH_JWKS_URI");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(o =>
            {
                o.MapInboundClaims = false;
                o.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidIssuer = "farm-auth",
                    ValidateAudience = true,
                    ValidAudience = "farm-api",
                    ValidateIssuerSigningKey = true,
                    RequireSignedTokens = true,
                    ValidAlgorithms = [SecurityAlgorithms.RsaSha256],
                    NameClaimType = "sub",
                    RoleClaimType = "role",
                    ClockSkew = TimeSpan.FromSeconds(30)
                };
            });

        if (!string.IsNullOrWhiteSpace(jwksUri))
        {
            services.AddSingleton(sp =>
                new JwksKeyResolver(jwksUri, sp.GetRequiredService<ILogger<JwksKeyResolver>>()));
            services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
                .Configure<JwksKeyResolver>((o, resolver) =>
                    o.TokenValidationParameters.IssuerSigningKeyResolver = resolver.Resolve);
        }

        if (configureJwt is not null)
            services.PostConfigure(JwtBearerDefaults.AuthenticationScheme, configureJwt);

        services.AddAuthorization();

        // 4. Health
        services.AddHealthChecks();

        // 5. OpenTelemetry (W3C traceparent tự lan truyền qua HttpClient)
        var otel = services.AddOpenTelemetry()
            .ConfigureResource(r => r.AddService(serviceName))
            .WithTracing(t => t.AddAspNetCoreInstrumentation().AddHttpClientInstrumentation())
            .WithMetrics(m => m.AddAspNetCoreInstrumentation().AddHttpClientInstrumentation());
        if (!string.IsNullOrWhiteSpace(config["OTEL_EXPORTER_OTLP_ENDPOINT"]))
            otel.UseOtlpExporter();

        return builder;
    }

    public static WebApplication UseFarmWeb(this WebApplication app)
    {
        app.UseExceptionHandler();
        app.UseStatusCodePages(async context =>
        {
            var http = context.HttpContext;
            var code = http.Response.StatusCode switch
            {
                StatusCodes.Status401Unauthorized => ResultCode.Unauthorized,
                StatusCodes.Status403Forbidden => ResultCode.WarningForbidden,
                StatusCodes.Status404NotFound => ResultCode.WarningNoContent,
                StatusCodes.Status405MethodNotAllowed => ResultCode.MethodNotAllowed,
                502 or 503 or 504 => ResultCode.ErrorServiceUnavailable,
                _ => ResultCode.ErrorFail
            };
            var body = ApiResponse.Fail(code, traceId: Activity.Current?.TraceId.ToString() ?? http.TraceIdentifier);
            await http.Response.WriteAsJsonAsync(body, FarmJson.Options, "application/json");
        });
        app.UseAuthentication();
        app.UseAuthorization();

        app.MapHealthChecks("/health/live", new HealthCheckOptions { Predicate = _ => false });
        app.MapHealthChecks("/health/ready");
        return app;
    }
}