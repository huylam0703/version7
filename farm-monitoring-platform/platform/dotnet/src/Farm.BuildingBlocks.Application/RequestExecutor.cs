using System.Diagnostics;
using System.Reflection;
using Farm.BuildingBlocks.Abstractions;
using FluentValidation;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace Farm.BuildingBlocks.Application;

public interface IRequestExecutor
{
    Task<Result<TResponse>> Execute<TResponse>(ICommand<TResponse> request, CancellationToken ct = default);
    Task<Result<TResponse>> Execute<TResponse>(IQuery<TResponse> request, CancellationToken ct = default);
}

public sealed class RequestExecutor(IServiceProvider services, IUnitOfWork unitOfWork,
    ILogger<RequestExecutor> logger) : IRequestExecutor
{
    public Task<Result<TResponse>> Execute<TResponse>(ICommand<TResponse> request, CancellationToken ct = default)
        => ExecuteCore<TResponse>(request, true, ct);

    public Task<Result<TResponse>> Execute<TResponse>(IQuery<TResponse> request, CancellationToken ct = default)
        => ExecuteCore<TResponse>(request, false, ct);

    private Task<Result<TResponse>> ExecuteCore<TResponse>(object request, bool command, CancellationToken ct)
    {
        var method = typeof(RequestExecutor).GetMethod(nameof(ExecuteTyped), BindingFlags.Instance | BindingFlags.NonPublic)!;
        return (Task<Result<TResponse>>)method.MakeGenericMethod(request.GetType(), typeof(TResponse))
            .Invoke(this, [request, command, ct])!;
    }

    private async Task<Result<TResponse>> ExecuteTyped<TRequest, TResponse>(TRequest request,
        bool command, CancellationToken ct)
    {
        var stopwatch = Stopwatch.StartNew();
        var validations = await Task.WhenAll(services.GetServices<IValidator<TRequest>>()
            .Select(validator => validator.ValidateAsync(request, ct)));
        var failures = validations.SelectMany(v => v.Errors).Where(e => e is not null).ToList();
        Result<TResponse> result;
        if (failures.Count > 0)
        {
            var details = failures.GroupBy(e => e.PropertyName)
                .ToDictionary(g => g.Key, g => g.Select(e => e.ErrorMessage).ToArray());
            result = Error.Validation("VALIDATION_FAILED", "Dữ liệu không hợp lệ.", details);
        }
        else
        {
            result = await services.GetRequiredService<IRequestHandler<TRequest, TResponse>>().Handle(request, ct);
            if (command && result.IsSuccess) await unitOfWork.SaveChangesAsync(ct);
        }

        if (result.IsSuccess)
            logger.LogInformation("{Request} OK in {Ms} ms", typeof(TRequest).Name, stopwatch.ElapsedMilliseconds);
        else
            logger.LogWarning("{Request} FAILED {Code} in {Ms} ms", typeof(TRequest).Name,
                result.Error!.Code, stopwatch.ElapsedMilliseconds);
        return result;
    }
}
