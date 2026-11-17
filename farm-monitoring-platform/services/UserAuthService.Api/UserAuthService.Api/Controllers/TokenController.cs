using System.Text;

using Farm.BuildingBlocks.Abstractions;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

using UserAuthService.Application;
using UserAuthService.Application.Commands.Auth;

namespace UserAuthService.Api.Controllers
{
    /// <summary>
    /// 
    /// </summary>
    public class TokenRequest
    {
        /// <summary>
        /// 
        /// </summary>
        [FromForm(Name = "grant_type")]
        public string GrantType { get; set; } = "";

        /// <summary>
        /// 
        /// </summary>
        [FromForm(Name = "client_id")]
        public string ClientId { get; set; } = "";

        /// <summary>
        /// 
        /// </summary>
        [FromForm(Name = "client_secret")]
        public string ClientSecret { get; set; } = "";

        /// <summary>
        /// 
        /// </summary>
        [FromForm(Name = "scope")]
        public string? Scope { get; set; }
    }

    /// <summary>
    /// 
    /// </summary>
    [ApiController]
    [Route("connect")]
    [Route("oauth2")]
    public class TokenController : ControllerBase
    {
        /// <summary>
        /// 
        /// </summary>
        private readonly ISender _mediator;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="mediator"></param>
        public TokenController(ISender mediator)
        {
            _mediator = mediator;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="request"></param>
        /// <param name="ct"></param>
        /// <returns></returns>
        [HttpPost("token")]
        [AllowAnonymous]
        [Consumes("application/x-www-form-urlencoded")]
        public async Task<IActionResult> TokenAsync([FromForm] TokenRequest request, CancellationToken ct)
        {
            var (clientId, clientSecret) = ReadClientCredentials(request);

            var result = await _mediator.Send(new IssueServiceTokenCommand
            {
                GrantType = request.GrantType,
                ClientId = clientId,
                ClientSecret = clientSecret,
                Scope = request.Scope
            }, ct);

            Response.Headers.CacheControl = "no-store";
            Response.Headers.Pragma = "no-cache";

            if (result.IsSuccess)
                return Ok(result.Value);

            return OAuthError(result.Error!);
        }

        /// <summary>
        /// Ưu tiên HTTP Basic (client_id:client_secret), không có thì lấy từ form
        /// </summary>
        /// <param name="form"></param>
        /// <returns></returns>
        private (string ClientId, string ClientSecret) ReadClientCredentials(TokenRequest form)
        {
            var header = Request.Headers.Authorization.ToString();
            if (header.StartsWith("Basic ", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var raw = Encoding.UTF8.GetString(Convert.FromBase64String(header["Basic ".Length..].Trim()));
                    var i = raw.IndexOf(':');
                    if (i > 0)
                        return (Uri.UnescapeDataString(raw[..i]), Uri.UnescapeDataString(raw[(i + 1)..]));
                }
                catch (FormatException)
                {
                    return ("", "");
                }
            }
            return (form.ClientId, form.ClientSecret);
        }

        /// <summary>
        /// Lỗi theo chuẩn OAuth2: { error, error_description }, không bọc ApiResponse
        /// </summary>
        /// <param name="error"></param>
        /// <returns></returns>
        private IActionResult OAuthError(Error error)
        {
            var (status, code) = error.Code switch
            {
                UserAuthErrors.InvalidClient => (StatusCodes.Status401Unauthorized, "invalid_client"),
                UserAuthErrors.UnsupportedGrantType => (StatusCodes.Status400BadRequest, "unsupported_grant_type"),
                UserAuthErrors.InvalidScope => (StatusCodes.Status400BadRequest, "invalid_scope"),
                _ => error.Type == ErrorType.Unauthorized
                        ? (StatusCodes.Status401Unauthorized, "invalid_client")
                        : (StatusCodes.Status400BadRequest, "invalid_request")
            };

            if (status == StatusCodes.Status401Unauthorized)
                Response.Headers.WWWAuthenticate = "Basic realm=\"farm-auth\"";

            return StatusCode(status, new { error = code, error_description = error.Message });
        }
    }

}

