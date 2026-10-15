using MediatR;

namespace Farm.BuildingBlocks.Abstractions;

public interface ICommand { }                                                  // marker
public interface ICommand<TResponse> : IRequest<Result<TResponse>>, ICommand { }
public interface IQuery<TResponse> : IRequest<Result<TResponse>> { }

public interface IUnitOfWork
{
    Task<int> SaveChangesAsync(CancellationToken ct = default);
}