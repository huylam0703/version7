using ApiGatewayService.Application.Health;

using Farm.BuildingBlocks.Web;

using Farm.BuildingBlocks.Application;

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace ApiGatewayService.Api.Controllers
{
    [ApiController]
    [Route("api/v1/gateway")]
    public class GatewayController : ControllerBase
    {
        private readonly IRequestExecutor _executor;

        public GatewayController(IRequestExecutor executor)
        {
            _executor = executor;
        }

        #region Queries
        /// <summary>
        /// Trạng thái các service phía sau gateway
        /// </summary>
        [HttpGet("services")]
        [Authorize(Policy = "authenticated")]
        public async Task<IActionResult> GetServicesHealthAsync(CancellationToken ct)
        {
            var result = await _executor.Execute(new GetServicesHealthQuery(), ct);
            return result.ToActionResult();
        }
        #endregion
    }
}