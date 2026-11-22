using MediatR;

namespace Farm.BuildingBlocks.Abstractions;

public enum ErrorType { Failure, Validation, NotFound, Conflict, Unauthorized, Forbidden }

public sealed record Error(
    string Code,
    string Message,
    ErrorType Type = ErrorType.Failure,
    IReadOnlyDictionary<string, string[]>? Details = null)
{
    public static Error NotFound(string code, string message) => new(code, message, ErrorType.NotFound);
    public static Error Conflict(string code, string message) => new(code, message, ErrorType.Conflict);
    public static Error Unauthorized(string code, string message) => new(code, message, ErrorType.Unauthorized);
    public static Error Forbidden(string code, string message) => new(code, message, ErrorType.Forbidden);
    public static Error Validation(string code, string message, IReadOnlyDictionary<string, string[]>? details = null)
        => new(code, message, ErrorType.Validation, details);
}

public interface IResult
{
    bool IsSuccess { get; }
    Error? Error { get; }
}

public interface IResultFactory<TSelf> where TSelf : IResultFactory<TSelf>
{
    static abstract TSelf Failure(Error error);
}

public sealed class Result<T> : IResult, IResultFactory<Result<T>>
{
    private readonly T? _value;

    private Result(T value) { IsSuccess = true; _value = value; }
    private Result(Error err) { Error = err; }

    public bool IsSuccess { get; }
    public bool IsFailure => !IsSuccess;
    public Error? Error { get; }

    public T Value => IsSuccess
        ? _value!
        : throw new InvalidOperationException("Result thất bại, không có Value.");

    public static Result<T> Success(T value) => new(value);
    public static Result<T> Failure(Error error) => new(error);

    public static implicit operator Result<T>(T value) => Success(value);
    public static implicit operator Result<T>(Error error) => Failure(error);
}

public static class Result
{
    /// Command không trả dữ liệu (dùng MediatR.Unit).
    public static Result<Unit> Success() => Result<Unit>.Success(Unit.Value);
}