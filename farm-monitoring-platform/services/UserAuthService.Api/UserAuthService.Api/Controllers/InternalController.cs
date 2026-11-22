using Farm.BuildingBlocks.Web;

using Farm.BuildingBlocks.Application;

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
        private readonly IRequestExecutor _executor;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="mediator"></param>
        public InternalUsersController(IRequestExecutor executor)
        {
            _executor = executor;
        }

        #region Queries
        /// <summary>
        /// Kiểm tra và lấy thông tin một người dùng theo UserId
        /// </summary>
        [HttpGet("{id:guid}")]
        public async Task<IActionResult> GetByIdAsync([FromRoute] Guid id, CancellationToken ct)
        {
            var result = await _executor.Execute(new GetUserByIdQuery { UserId = id }, ct);
            return result.ToActionResult();
        }
        #endregion
    }
}
