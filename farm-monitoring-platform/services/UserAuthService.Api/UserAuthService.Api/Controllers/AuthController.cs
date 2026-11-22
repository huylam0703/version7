using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Web;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

using UserAuthService.Application.Commands.Auth;

namespace UserAuthService.Api.Controllers
{
    [ApiController]
    [Route("api/v1/auth")]
    public class AuthController : ControllerBase
    {
        private readonly ISender _mediator;

        public AuthController(ISender mediator)
        {
            _mediator = mediator;
        }

        #region Commands
        /// <summary>
        /// Đăng nhập (UC01). Trả access token có hiệu lực theo CoreAppSetting:Jwt:ExpireTime (mặc định 7 ngày).
        /// </summary>
        [HttpPost("login")]
        [AllowAnonymous]
        public async Task<IActionResult> LoginAsync([FromBody] LoginCommand command, CancellationToken ct)
        {
            var result = await _mediator.Send(command, ct);
            return result.ToActionResult(message: "Đăng nhập thành công.");
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="command"></param>
        /// <param name="ct"></param>
        /// <returns></returns>
        [HttpPost("refresh")]
        [AllowAnonymous]
        public async Task<IActionResult> RefreshAsync([FromBody] RefreshTokenCommand command, CancellationToken ct)
        {
            var result = await _mediator.Send(command, ct);
            return result.ToActionResult(message: "Làm mới token thành công.");
        }

        /// <summary>
        /// Đăng xuất: thu hồi refresh token. Access token hết hạn sau tối đa 30 phút
        /// </summary>
        /// <param name="command"></param>
        /// <param name="ct"></param>
        /// <returns></returns>
        [HttpPost("logout")]
        [AllowAnonymous] 
        public async Task<IActionResult> LogoutAsync([FromBody] LogoutCommand command, CancellationToken ct)
        {
            var result = await _mediator.Send(command, ct);
            return result.ToActionResult(message: "Đăng xuất thành công.");
        }
        #endregion
    }
}
