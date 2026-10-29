using Microsoft.Extensions.Logging;
using Microsoft.IdentityModel.Tokens;

namespace Farm.BuildingBlocks.Web;

/// Lấy khóa công khai từ JWKS của user-auth, lưu tạm 10 phút, tải lại khi gặp kid lạ (xoay khóa)
/// nhưng không quá 30 giây một lần. Nếu tải lỗi thì dùng tiếp khóa đã lưu.
public sealed class JwksKeyResolver
{
    private static readonly TimeSpan Ttl = TimeSpan.FromMinutes(10);
    private static readonly TimeSpan MinRetry = TimeSpan.FromSeconds(30);

    private readonly string _uri;
    private readonly ILogger<JwksKeyResolver> _log;
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(5) };
    private readonly object _lock = new();
    private IReadOnlyCollection<SecurityKey> _keys = Array.Empty<SecurityKey>();
    private DateTimeOffset _fetchedAt = DateTimeOffset.MinValue;
    private DateTimeOffset _lastAttempt = DateTimeOffset.MinValue;

    public JwksKeyResolver(string uri, ILogger<JwksKeyResolver> log)
    {
        _uri = uri;
        _log = log;
    }

    public IEnumerable<SecurityKey> Resolve(string token, SecurityToken securityToken, string kid,
        TokenValidationParameters parameters)
    {
        var keys = _keys;
        if (DateTimeOffset.UtcNow - _fetchedAt > Ttl) keys = Refresh();
        if (!string.IsNullOrEmpty(kid) && !keys.Any(k => k.KeyId == kid)) keys = Refresh();
        return keys;
    }

    private IReadOnlyCollection<SecurityKey> Refresh()
    {
        lock (_lock)
        {
            var now = DateTimeOffset.UtcNow;
            if (now - _lastAttempt < MinRetry) return _keys;
            _lastAttempt = now;
            try
            {
                var json = _http.GetStringAsync(_uri).GetAwaiter().GetResult();
                _keys = new JsonWebKeySet(json).GetSigningKeys().ToList();
                _fetchedAt = now;
            }
            catch (Exception ex)
            {
                _log.LogWarning(ex, "Không tải được JWKS từ {Uri}, dùng khóa đã lưu", _uri);
            }
            return _keys;
        }
    }
}