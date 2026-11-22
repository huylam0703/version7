using Farm.BuildingBlocks.Web;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

using UserAuthService.Application.Queries;
using UserAuthService.Application.Queries.Users;
using UserAuthService.Domain.Entity;

namespace UserAuthService.Api.Controllers
{
    /// <summary>
    /// Đường nội bộ cho service gọi service.
    /// </summary>
    [ApiController]
    [Route("internal/v1/users")]
    [Authorize(Roles = FarmRole.Service)]
    public class InternalUsersController : ControllerBase
    {
        /// <summary>
        /// 
        /// </summary>
        private readonly ISender _mediator;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="mediator"></param>
        public InternalUsersController(ISender mediator)
        {
            _mediator = mediator;
        }

        #region Queries
        /// <summary>
        /// Kiểm tra và lấy thông tin một người dùng theo UserId
        /// </summary>
        [HttpGet("{id:guid}")]
        public async Task<IActionResult> GetByIdAsync([FromRoute] Guid id, CancellationToken ct)
        {
            var result = await _mediator.Send(new GetUserByIdQuery { UserId = id }, ct);
            return result.ToActionResult();
        }
        #endregion
    }
}
