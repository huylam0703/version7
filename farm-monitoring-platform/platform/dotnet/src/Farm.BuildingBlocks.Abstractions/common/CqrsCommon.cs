
namespace Farm.BuildingBlocks.Abstractions;

public interface ICommand { }// marker
public interface ICommand<TResponse> : ICommand { }
public interface IQuery<TResponse>  { }

public interface IUnitOfWork
{
    Task<int> SaveChangesAsync(CancellationToken ct = default);
}