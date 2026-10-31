using Farm.BuildingBlocks.Web;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

using UserAuthService.Application.Commands.Users;
using UserAuthService.Application.Queries;
using UserAuthService.Application.Queries.Users;
using UserAuthService.Domain.Entity;

namespace UserAuthService.Api.Controllers;

[Route("api/v1/users")]
[ApiController]
[Authorize]
public class UsersController : ControllerBase
{
    /// <summary>
    /// 
    /// </summary>
    private readonly ISender _mediator;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="mediator"></param>
    public UsersController(ISender mediator)
    {
        _mediator = mediator;
    }

    /// <summary>
    /// 
    /// </summary>
    private Guid CurrentUserId => Guid.TryParse(User.FindFirst("sub")?.Value, out var id) ? id : Guid.Empty;

    #region Commands
    /// <summary>
    /// 
    /// </summary>
    /// <param name="command"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost]
    [Authorize(Roles = FarmRole.Admin)]
    public async Task<IActionResult> CreateAsync([FromBody] CreateUserCommand command, CancellationToken ct)
    {
        var result = await _mediator.Send(command, ct);
        return result.ToActionResult(message: "Tạo người dùng thành công.", successStatus: StatusCodes.Status201Created);
    }
    /// <summary>
    /// Cập nhật thông tin
    /// </summary>
    /// <param name="id"></param>
    /// <param name="command"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPut("{id:guid}")]
    [Authorize(Roles = FarmRole.Admin)]
    public async Task<IActionResult> UpdateAsync([FromRoute] Guid id, [FromBody] UpdateUserCommand command, CancellationToken ct)
    {
        command.UserId = id;
        command.ActorId = CurrentUserId;
        var result = await _mediator.Send(command, ct);
        return result.ToActionResult(message: "Cập nhật người dùng thành công.");
    }

    /// <summary>
    /// Khóa tài khoản
    /// </summary>
    /// <param name="id"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost("{id:guid}/lock")]
    [Authorize(Roles = FarmRole.Admin)]
    public async Task<IActionResult> LockAsync([FromRoute] Guid id, CancellationToken ct)
    {
        var result = await _mediator.Send(new LockUserCommand { UserId = id, ActorId = CurrentUserId }, ct);
        return result.ToActionResult(message: "Đã khóa tài khoản.");
    }

    /// <summary>
    /// Mở khóa tài khoản
    /// </summary>
    /// <param name="id"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost("{id:guid}/unlock")]
    [Authorize(Roles = FarmRole.Admin)]
    public async Task<IActionResult> UnlockAsync([FromRoute] Guid id, CancellationToken ct)
    {
        var result = await _mediator.Send(new UnlockUserCommand { UserId = id }, ct);
        return result.ToActionResult(message: "Đã mở khóa tài khoản.");
    }
    #endregion

    #region Queries
    /// <summary>
    /// Lấy thông tin tài khoản đang đăng nhập
    /// </summary>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpGet("me")]
    public async Task<IActionResult> GetMeAsync(CancellationToken ct)
    {
        var result = await _mediator.Send(new GetUserByIdQuery { UserId = CurrentUserId }, ct);
        return result.ToActionResult();
    }

    /// <summary>
    /// Lấy thông tin người dùng theo Id
    /// </summary>
    /// <param name="id"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpGet("{id:guid}")]
    [Authorize(Roles = FarmRole.Admin)]
    public async Task<IActionResult> GetByIdAsync([FromRoute] Guid id, CancellationToken ct)
    {
        var result = await _mediator.Send(new GetUserByIdQuery { UserId = id }, ct);
        return result.ToActionResult();
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="query"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpGet]
    [Authorize(Roles = FarmRole.Admin)]
    public async Task<IActionResult> ListAsync([FromQuery] ListUsersQuery query, CancellationToken ct)
    {
        var result = await _mediator.Send(query, ct);
        return result.ToActionResult();
    }


    #endregion
}
