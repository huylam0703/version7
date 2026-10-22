using System.Net.Http.Json;
using System.Text.Json;

using Farm.BuildingBlocks.Abstractions;

namespace Farm.BuildingBlocks.Infrastructure.Http;

public sealed class FarmApiException : Exception
{
    public ResultCode Code { get; }
    public int StatusCode { get; }

    public FarmApiException(ResultCode code, string message, int statusCode) : base(message) {
        Code = code;
        StatusCode = statusCode;
    }
}

public static class ApiResponseHttpExtensions
{

    /// Đọc khung ApiResponse và trả về phần result; lỗi thì ném FarmApiException có code và message của service kia.
    public static async Task<T> ReadResultAsync<T>(this HttpResponseMessage response, CancellationToken ct = default)
    {
        ApiResponse<T>? body = null;
        try { body = await response.Content.ReadFromJsonAsync<ApiResponse<T>>(FarmJson.Options, ct); }
        catch (JsonException) { /* body không phải khung chung (ví dụ lỗi từ proxy) */ }

        if (!response.IsSuccessStatusCode || body is null)
            throw new FarmApiException(
                body?.Code ?? ResultCode.ErrorUpstream,
                body?.Message ?? $"Service phía sau trả mã {(int)response.StatusCode}.",
                (int)response.StatusCode);

        return body.Result!;
    }
}