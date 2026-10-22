using Farm.BuildingBlocks.Abstractions;

namespace ApiGatewayService.Infrastructure;

public sealed class NoOpUnitOfWork : IUnitOfWork
{
    public Task<int> SaveChangesAsync(CancellationToken ct = default) => Task.FromResult(0);
}