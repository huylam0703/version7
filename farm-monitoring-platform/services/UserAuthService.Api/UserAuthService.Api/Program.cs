using Microsoft.AspNetCore.Authentication.JwtBearer;

using UserAuthService.Application;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure;
using UserAuthService.Infracstructure.Services;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddApplicationService();
builder.Services.AddInfrastructureService();

builder.Services.Configure<CoreAppSetting>(builder.Configuration.GetSection("CoreAppSetting"));
builder.Services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
    .Configure<JwtKeyProvider>((options, keys) =>
    {
        options.MetadataAddress = string.Empty;
        options.TokenValidationParameters.IssuerSigningKey = keys.SigningKey;
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

app.UseAuthorization();

app.MapControllers();

app.Run();
