using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;

using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Settings;

namespace UserAuthService.Infracstructure.Services
{
    public class JwtKeyProvider 
    {
        public RsaSecurityKey SigningKey { get; }
        public JsonWebKey PublicJwk { get; }

        public JwtKeyProvider(IOptions<CoreAppSetting> options, ILogger<JwtKeyProvider> logger)
        {
            var jwt = options.Value.Jwt;
            var path = jwt?.PrivateKeyPath;
            if (string.IsNullOrWhiteSpace(path)) path = "keys/jwt-private.pem";

            var rsa = RSA.Create(2048);   // giữ suốt vòng đời ứng dụng, không dispose
            if (File.Exists(path))
            {
                rsa.ImportFromPem(File.ReadAllText(path));
            }
            else
            {
                var directory = Path.GetDirectoryName(Path.GetFullPath(path));
                if (!string.IsNullOrEmpty(directory)) Directory.CreateDirectory(directory);
                File.WriteAllText(path, rsa.ExportPkcs8PrivateKeyPem());
                logger.LogWarning("Đã tạo khóa ký JWT mới tại {Path}.", path);
            }

            var keyId = jwt?.KeyId;
            if (string.IsNullOrWhiteSpace(keyId))
                keyId = Base64UrlEncoder.Encode(SHA256.HashData(rsa.ExportSubjectPublicKeyInfo()))[..16];

            SigningKey = new RsaSecurityKey(rsa) { KeyId = keyId };
            PublicJwk = JsonWebKeyConverter.ConvertFromRSASecurityKey(
                new RsaSecurityKey(rsa.ExportParameters(false)) { KeyId = keyId });
        }
    }

}

