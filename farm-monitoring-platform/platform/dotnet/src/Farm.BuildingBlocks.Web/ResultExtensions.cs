using System.Diagnostics;

using Farm.BuildingBlocks.Abstractions;

using MediatR;

using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace Farm.BuildingBlocks.Web;

public static class ResultExtensions
{
    /// Thành công: { code: OK, message, result }. Lỗi: { code, message, result: null, errors, traceId }.
    public static IActionResult ToActionResult<T>(this Result<T> result, string? message = null,
        int successStatus = StatusCodes.Status200OK)
    {
        if (result.IsFailure) return ToFailure(result.Error!);

        object? data = typeof(T) == typeof(Unit) ? null : result.Value;
        return new ObjectResult(ApiResponse.Ok(data, message ?? "Thành công")) { StatusCode = successStatus };
    }

    private static IActionResult ToFailure(Error error)
    {
        var (status, code) = error.Type switch
        {
            ErrorType.Validation => (400, ResultCode.ErrorInputInvalid),
            ErrorType.Unauthorized => (401, ResultCode.Unauthorized),
            ErrorType.Forbidden => (403, ResultCode.WarningForbidden),
            ErrorType.NotFound => (404, ResultCode.WarningNoContent),
            ErrorType.Conflict => (409, ResultCode.ErrorConflict),
            _ => (400, ResultCode.ErrorFail)
        };

        var body = ApiResponse.Fail(code, error.Message, error.Details, Activity.Current?.TraceId.ToString());
        return new ObjectResult(body) { StatusCode = status };
    }
}