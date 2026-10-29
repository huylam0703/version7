using System;
using System.Collections.Generic;
using System.IdentityModel.Tokens.Jwt;
using System.Text;

using Microsoft.AspNetCore.Http;

namespace UserAuthService.Infracstructure.Ultilities;

public class JwtHelper
{
    private readonly IHttpContextAccessor _httpContextAccessor;

    public JwtHelper(IHttpContextAccessor httpContextAccessor)
    {
        _httpContextAccessor = httpContextAccessor;
    }

    // Map tên ngắn -> URI chuẩn của claim
    private static readonly Dictionary<string, string> ClaimTypeMap = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { "sub", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier" },
            { "name", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name" },
            { "email", "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress" },
            { "role", "http://schemas.microsoft.com/ws/2008/06/identity/claims/role" },
            // 🔽 Bổ sung thêm
            { "userId", JwtRegisteredClaimNames.Sub },
            { "fullName", JwtRegisteredClaimNames.Name },
            { "phone", JwtRegisteredClaimNames.PhoneNumber },         
            { "email", JwtRegisteredClaimNames.Email },
            { "role", "role" },

        };

    public string GetValueByKey(string key)
    {
        var user = _httpContextAccessor.HttpContext?.User;
        if (user == null) return string.Empty;

        // Nếu key là tên ngắn thì map sang URI
        if (ClaimTypeMap.TryGetValue(key, out var claimType))
        {
            return user.Claims.FirstOrDefault(c => c.Type == claimType)?.Value ?? string.Empty;
        }

        // Nếu không có trong map thì coi như key chính là claim type
        return user.Claims.FirstOrDefault(c => c.Type == key)?.Value ?? string.Empty;
    }
}
