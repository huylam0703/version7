using System.Text.Json.Serialization;

using Farm.BuildingBlocks.Abstractions.Enums;

namespace Farm.BuildingBlocks.Abstractions;

public sealed record ApiResponse<T>(
    ResultCode Code,
    string Message,
    T? Result,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] IReadOnlyDictionary<string, string[]>? Errors = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? TraceId = null);

public static class ApiResponse
{
    public static ApiResponse<T> Ok<T>(T? result, string? message = null)
        => new(ResultCode.Ok, message ?? ResultCode.Ok.GetDescription(), result);

    public static ApiResponse<object?> Fail(ResultCode code, string? message = null,
        IReadOnlyDictionary<string, string[]>? errors = null, string? traceId = null)
        => new(code, message ?? code.GetDescription(), null, errors, traceId);
}