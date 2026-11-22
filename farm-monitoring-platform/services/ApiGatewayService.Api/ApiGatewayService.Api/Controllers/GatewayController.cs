using ApiGatewayService.Application.Health;

using Farm.BuildingBlocks.Web;

using MediatR;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace ApiGatewayService.Api.Controllers
{
    [ApiController]
    [Route("api/v1/gateway")]
    public class GatewayController : ControllerBase
    {
        private readonly ISender _mediator;

        public GatewayController(ISender mediator)
        {
            _mediator = mediator;
        }

        #region Queries
        /// <summary>
        /// Trạng thái các service phía sau gateway
        /// </summary>
        [HttpGet("services")]
        [Authorize(Policy = "authenticated")]
        public async Task<IActionResult> GetServicesHealthAsync(CancellationToken ct)
        {
            var result = await _mediator.Send(new GetServicesHealthQuery(), ct);
            return result.ToActionResult();
        }
        #endregion
    }
}