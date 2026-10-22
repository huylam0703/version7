using System.Diagnostics;
using Farm.BuildingBlocks.Abstractions;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace Farm.BuildingBlocks.Web;

internal sealed class GlobalExceptionHandler(ILogger<GlobalExceptionHandler> logger) : IExceptionHandler
{
    /// <summary>
    /// 
    /// </summary>
    /// <param name="ctx"></param>
    /// <param name="ex"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async ValueTask<bool> TryHandleAsync(HttpContext ctx, Exception ex, CancellationToken ct)
    {
        logger.LogError(ex, "Unhandled exception");
        ctx.Response.StatusCode = StatusCodes.Status500InternalServerError;

        var body = ApiResponse.Fail(ResultCode.ErrorException, traceId: Activity.Current?.TraceId.ToString() ?? ctx.TraceIdentifier);

        await ctx.Response.WriteAsJsonAsync(body, FarmJson.Options, "application/json", ct);
        return true;
    }
}