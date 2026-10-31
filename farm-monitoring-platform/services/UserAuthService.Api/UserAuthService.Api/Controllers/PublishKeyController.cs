using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Settings;

namespace UserAuthService.Api.Controllers
{
    /// <summary>
    /// Công bố khóa công khai để gateway (.NET) và các service Java kiểm tra chữ ký JWT.
    /// </summary>
    [ApiController]
    [Route(".well-known")]
    [AllowAnonymous]
    public class WellKnownController : ControllerBase
    {
        /// <summary>
        /// 
        /// </summary>
        private readonly IJwtService _jwt;
        /// <summary>
        /// 
        /// </summary>
        private readonly IOptions<CoreAppSetting> _options;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="jwt"></param>
        /// <param name="options"></param>
        public WellKnownController(IJwtService jwt, IOptions<CoreAppSetting> options)
        {
            _jwt = jwt;
            _options = options;
        }

        #region Queries
        /// <summary>
        /// jwk-set-uri của java
        /// </summary>
        /// <returns></returns>
        [HttpGet("jwks.json")]
        public IActionResult Jwks()
        {
            var jwk = _jwt.GetPublicKey();
            return new JsonResult(new
            {
                keys = new[]
                {
                    new { kty = jwk.Kty, use = jwk.Use, alg = jwk.Alg, kid = jwk.Kid, n = jwk.N, e = jwk.E }
                }
            });
        }

        /// <summary>
        /// AUTH_METADATA_URI của .NET gateway
        /// </summary>
        /// <returns></returns>
        [HttpGet("openid-configuration")]
        public IActionResult Discovery()
        {
            var issuer = _options.Value.Jwt?.Issuer;
            return new JsonResult(new
            {
                issuer = string.IsNullOrWhiteSpace(issuer) ? "farm-auth" : issuer,
                jwks_uri = $"{Request.Scheme}://{Request.Host}/.well-known/jwks.json"
            });
        }
        #endregion
    }
}
