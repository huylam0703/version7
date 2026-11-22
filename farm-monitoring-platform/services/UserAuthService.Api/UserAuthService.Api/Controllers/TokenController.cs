using Farm.BuildingBlocks.Abstractions;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

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
    }

    /// <summary>
    /// 
    /// </summary>
    [ApiController]
    [Route("connect")]
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

        #region Commands
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
            var result = await _mediator.Send(new IssueServiceTokenCommand
            {
                GrantType = request.GrantType,
                ClientId = request.ClientId,
                ClientSecret = request.ClientSecret
            }, ct);

            if (result.IsSuccess)
                return Ok(result.Value);

            var status = result.Error!.Type == ErrorType.Unauthorized
                ? StatusCodes.Status401Unauthorized
                : StatusCodes.Status400BadRequest;

            return StatusCode(status, new
            {
                error = status == StatusCodes.Status401Unauthorized ? "invalid_client" : "invalid_request",
                error_description = result.Error.Message
            });
        }
        #endregion
    }

}

