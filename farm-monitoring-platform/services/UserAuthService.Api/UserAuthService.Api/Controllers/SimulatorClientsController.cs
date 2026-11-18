using Farm.BuildingBlocks.Web;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

using UserAuthService.Application.Commands.Clients;
using UserAuthService.Application.Queries.Clients;
using UserAuthService.Domain.Entity;

namespace UserAuthService.Api.Controllers;

/// <summary>
/// 
/// </summary>
[ApiController]
[Route("api/v1/simulator-clients")]
[Authorize(Roles = FarmRole.Admin)]
public class SimulatorClientsController : ControllerBase
{
    /// <summary>
    /// 
    /// </summary>
    private readonly ISender _mediator;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="mediator"></param>
    public SimulatorClientsController(ISender mediator)
    {
        _mediator = mediator;
    }

    /// <summary>
    /// 
    /// </summary>
    private void NoStore()
    {
        Response.Headers.CacheControl = "no-store";
        Response.Headers.Pragma = "no-cache";
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="command"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost]
    public async Task<IActionResult> RegisterAsync([FromBody] RegisterSimulatorCommand command, CancellationToken ct)
    {
        var result = await _mediator.Send(command, ct);
        NoStore();
        return result.ToActionResult(message: "Đã đăng ký Simulator client.", successStatus: StatusCodes.Status201Created);
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost("{clientId}/rotate-secret")]
    public async Task<IActionResult> RotateSecretAsync([FromRoute] string clientId, CancellationToken ct)
    {
        var result = await _mediator.Send(new RotateSimulatorSecretCommand { ClientId = clientId }, ct);
        NoStore();
        return result.ToActionResult(message: "Đã tạo secret mới.");
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost("{clientId}/disable")]
    public async Task<IActionResult> DisableAsync([FromRoute] string clientId, CancellationToken ct)
    {
        var result = await _mediator.Send(new SetActiveCommand { ClientId = clientId, Active = false }, ct);
        return result.ToActionResult(message: "Đã vô hiệu hóa client.");
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpPost("{clientId}/enable")]
    public async Task<IActionResult> EnableAsync([FromRoute] string clientId, CancellationToken ct)
    {
        var result = await _mediator.Send(new SetActiveCommand { ClientId = clientId, Active = true }, ct);
        return result.ToActionResult(message: "Đã kích hoạt client.");
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="ct"></param>
    /// <returns></returns>
    [HttpGet]
    public async Task<IActionResult> ListAsync(CancellationToken ct)
    {
        var result = await _mediator.Send(new ListSimulatorsQuery(), ct);
        return result.ToActionResult();
    }
}
