namespace UserAuthService.Domain.Settings;

/// Gắn với mục "CoreAppSetting" trong appsettings.json.
public class CoreAppSetting
{
    /// <summary>
    /// 
    /// </summary>
    public ConnectionStringSetting? ConnectionStringSetting { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public JwtSetting? Jwt { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public SeedSetting? Seed { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public List<ServiceClientSetting>? ServiceClients { get; set; }
}

public class ConnectionStringSetting
{
    /// <summary>
    /// 
    /// </summary>
    public string? UserAuthService { get; set; }
}

public class JwtSetting
{
    /// <summary>
    /// 
    /// </summary>
    public string? Issuer { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Audience { get; set; }
    /// <summary>
    /// Thời gian sống của token (mặc định là 7 ngày)
    /// </summary>
    public double ExpireTime { get; set; } = 10080;
    /// <summary>
    /// Thời hạn của token service gọi service
    /// </summary>
    public int ServiceTokenExpireTime { get; set; } = 15;
    /// <summary>
    /// Khóa riêng để ký
    /// </summary>
    public string? PrivateKeyPath { get; set; }
    /// <summary>
    /// 
    /// </summary>

    public string? KeyId { get; set; }
}

public class SeedSetting
{
    /// <summary>
    /// 
    /// </summary>
    public bool Enabled { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? AdminEmail { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? AdminPassword { get; set; }
}

public class ServiceClientSetting
{
    /// <summary>
    /// 
    /// </summary>
    public string? ClientId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Secret { get; set; }
}
