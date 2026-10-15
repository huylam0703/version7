using System.Diagnostics;
using Farm.BuildingBlocks.Abstractions;
using Microsoft.AspNetCore.Mvc;
using MediatR;

namespace Farm.BuildingBlocks.Web;

public static class ResultExtensions
{
    public static IActionResult ToActionResult<T>(this Result<T> result, Func<T, IActionResult>? onSuccess = null)
    {
        if (result.IsFailure) return ToProblem(result.Error!);
        if (onSuccess is not null) return onSuccess(result.Value);
        if (typeof(T) == typeof(Unit)) return new NoContentResult();
        return new OkObjectResult(result.Value);
    }

    private static IActionResult ToProblem(Error error)
    {
        var status = error.Type switch
        {
            ErrorType.Validation   => 400,
            ErrorType.Unauthorized => 401,
            ErrorType.Forbidden    => 403,
            ErrorType.NotFound     => 404,
            ErrorType.Conflict     => 409,
            _                      => 400
        };

        var pd = new ProblemDetails
        {
            Status = status,
            Title = error.Message,
            Type = $"urn:farm:error:{error.Code}"
        };
        pd.Extensions["code"] = error.Code;
        pd.Extensions["traceId"] = Activity.Current?.TraceId.ToString();
        if (error.Details is not null) pd.Extensions["errors"] = error.Details;

        return new ObjectResult(pd) { StatusCode = status, ContentTypes = { "application/problem+json" } };
    }
}