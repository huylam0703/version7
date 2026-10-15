using System.Diagnostics;

using Farm.BuildingBlocks.Abstractions;

using FluentValidation;

using MediatR;

using Microsoft.Extensions.Logging;

namespace Farm.BuildingBlocks.Application;

public sealed class LoggingBehavior<TRequest, TResponse>(ILogger<LoggingBehavior<TRequest, TResponse>> logger)
    : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
    where TResponse : IResult
{
    public async Task<TResponse> Handle(TRequest request, RequestHandlerDelegate<TResponse> next, CancellationToken ct)
    {
        var name = typeof(TRequest).Name;
        var sw = Stopwatch.StartNew();
        var response = await next(ct);
        if (response.IsSuccess)
            logger.LogInformation("{Request} OK in {Ms} ms", name, sw.ElapsedMilliseconds);
        else
            logger.LogWarning("{Request} FAILED {Code} in {Ms} ms", name, response.Error!.Code, sw.ElapsedMilliseconds);
        return response;
    }
}

public sealed class ValidationBehavior<TRequest, TResponse>(IEnumerable<IValidator<TRequest>> validators)
    : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
    where TResponse : IResult, IResultFactory<TResponse>
{
    public async Task<TResponse> Handle(TRequest request, RequestHandlerDelegate<TResponse> next, CancellationToken ct)
    {
        if (!validators.Any()) return await next(ct);

        var context = new ValidationContext<TRequest>(request);
        var failures = (await Task.WhenAll(validators.Select(v => v.ValidateAsync(context, ct))))
            .SelectMany(r => r.Errors)
            .Where(f => f is not null)
            .ToList();

        if (failures.Count == 0) return await next(ct);

        var details = failures
            .GroupBy(f => f.PropertyName)
            .ToDictionary(g => g.Key, g => g.Select(f => f.ErrorMessage).ToArray());

        return TResponse.Failure(Error.Validation("VALIDATION_FAILED", "Dữ liệu không hợp lệ.", details));
    }
}

/// Chỉ áp dụng cho Command: handler thành công thì SaveChanges một lần.
public sealed class UnitOfWorkBehavior<TRequest, TResponse>(IUnitOfWork uow)
    : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
    where TResponse : IResult
{
    public async Task<TResponse> Handle(TRequest request, RequestHandlerDelegate<TResponse> next, CancellationToken ct)
    {
        if (request is not ICommand) return await next(ct);

        var response = await next(ct);
        if (response.IsSuccess) await uow.SaveChangesAsync(ct);
        return response;
    }
}