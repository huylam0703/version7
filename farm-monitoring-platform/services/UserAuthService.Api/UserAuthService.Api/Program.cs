using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

using UserAuthService.Application;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure;
using UserAuthService.Infracstructure.Services;

var builder = WebApplication.CreateBuilder(args);
var jwtIssuer = builder.Configuration["CoreAppSetting:Jwt:Issuer"];
var jwtAudience = builder.Configuration["CoreAppSetting:Jwt:Audience"];

// Add services to the container.

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddApplicationService();
builder.Services.AddInfrastructureService();

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.MapInboundClaims = false;
    });

builder.Services.Configure<CoreAppSetting>(builder.Configuration.GetSection("CoreAppSetting"));
builder.Services
    .AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
    .Configure<JwtKeyProvider>((options, keys) =>
    {
        options.MetadataAddress = string.Empty;

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = keys.SigningKey,

            ValidateIssuer = true,
            ValidIssuer = jwtIssuer,

            ValidateAudience = true,
            ValidAudience = jwtAudience,

            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromMinutes(1),

            RoleClaimType = "role",
            NameClaimType = "email"
        };

        // Ghi log nguyên nhân nếu JWT bị từ chối
        options.Events = new JwtBearerEvents
        {
            OnAuthenticationFailed = context =>
            {
                var logger = context.HttpContext.RequestServices
                    .GetRequiredService<ILoggerFactory>()
                    .CreateLogger("JwtAuthentication");

                logger.LogError(
                    context.Exception,
                    "JWT Authentication Failed: {Message}",
                    context.Exception.Message
                );

                return Task.CompletedTask;
            }
        };
    });
var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}
await app.Services.InitializeUserAuthDatabaseAsync();
app.UseCorePackageBuilder();
app.UseHttpsRedirection();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
