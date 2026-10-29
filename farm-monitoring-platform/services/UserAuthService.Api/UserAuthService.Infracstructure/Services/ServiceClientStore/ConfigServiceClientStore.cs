using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Options;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Settings;

namespace UserAuthService.Infracstructure.Services;


/// Danh sách service được phép xin token dịch vụ, đọc từ CoreAppSetting.
public sealed class ConfigServiceClientStore : IServiceClientStore
{
    private readonly Dictionary<string, string> _clients;

    public ConfigServiceClientStore(IOptions<CoreAppSetting> options)
    {
        _clients = (options.Value.ServiceClients ?? [])
            .Where(c => !string.IsNullOrWhiteSpace(c.ClientId))
            .ToDictionary(c => c.ClientId!, c => c.Secret ?? "", StringComparer.OrdinalIgnoreCase);
    }

    public bool Validate(string clientId, string clientSecret)
    {
        if (!_clients.TryGetValue(clientId, out var expected) || expected.Length == 0)
            return false;

        return CryptographicOperations.FixedTimeEquals(
            Encoding.UTF8.GetBytes(expected),
            Encoding.UTF8.GetBytes(clientSecret));
    }
}
