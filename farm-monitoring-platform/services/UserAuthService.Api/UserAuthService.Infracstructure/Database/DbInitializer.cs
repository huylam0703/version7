using System.Data;

using Dapper;

using MediatR;

using Microsoft.AspNetCore.Identity;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Interfaces.Utilities;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure.Queries;

namespace UserAuthService.Infracstructure.Database;

/// Khi CoreAppSetting:Seed:Enabled = true: tạo bảng (nếu chưa có), 2 vai trò và tài khoản admin đầu tiên.
/// Database phải tồn tại sẵn; chỉ tạo bảng bên trong.
public sealed class DbInitializer
{
    private readonly IQuery _query;
    private readonly IOptions<CoreAppSetting> _options;
    private readonly IUserService _userService;
    private readonly IHashUtilities _hashUtilities;
    private readonly ILogger<DbInitializer> _logger;

    public DbInitializer(IQuery query, IOptions<CoreAppSetting> options, IUserService userService, IHashUtilities hashUtilities, ILogger<DbInitializer> logger)
    {
        _query = query;
        _options = options;
        _userService = userService;
        _hashUtilities = hashUtilities;
        _logger = logger;
    }

    public async Task RunAsync()
    {
        var seed = _options.Value.Seed;
        if (seed is null || !seed.Enabled) return;

        var connectionString = _options.Value.ConnectionStringSetting!.UserAuthService!;

        await _query.ExecuteAsync(connectionString, SchemaQuery.CreateTables);
        _logger.LogInformation("Đã kiểm tra và tạo bảng UA_Roles, UA_Users (nếu chưa có).");

        var roles = new[]
        {
            (Name: FarmRole.Admin, Description: "Quản trị viên nông trại"),
            (Name: FarmRole.Operator, Description: "Nhân viên vận hành UAV và thiết bị")
        };

        foreach (var (name, description) in roles)
        {
            var parameters = new DynamicParameters();
            parameters.Add("@pRoleId", Guid.CreateVersion7(), DbType.Guid, ParameterDirection.Input);
            parameters.Add("@pName", name, DbType.AnsiString, ParameterDirection.Input, 30);
            parameters.Add("@pDescription", description, DbType.String, ParameterDirection.Input, 255);
            await _query.ExecuteAsync(connectionString, SchemaQuery.SeedRole, parameters);
        }

        if (string.IsNullOrWhiteSpace(seed.AdminPassword))
        {
            _logger.LogWarning("Seed:AdminPassword đang trống nên không tạo tài khoản admin đầu tiên.");
            return;
        }

        var email = (seed.AdminEmail ?? "admin@farm.vn").Trim().ToLowerInvariant();
        if (await _userService.EmailExistsAsync(email)) return;

        var adminRole = await _userService.GetRoleByNameAsync(FarmRole.Admin);
        if (adminRole is null) return;

        var hashSalt = _hashUtilities.GenerateSalt();
        string saltBase64 = Convert.ToBase64String(hashSalt);
        var passwordHash = _hashUtilities.HashPassword(seed.AdminPassword, hashSalt);
        await _userService.CreateUserAsync(User.Create(adminRole.RoleId, "Quản trị viên", email, passwordHash, saltBase64, null));
        _logger.LogWarning("Đã tạo tài khoản admin đầu tiên: {Email}. Hãy đổi mật khẩu này khi triển khai thật.", email);
    }
}
