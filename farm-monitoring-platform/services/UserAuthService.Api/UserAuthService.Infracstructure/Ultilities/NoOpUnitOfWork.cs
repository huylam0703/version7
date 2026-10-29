using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;

namespace UserAuthService.Infracstructure;

public class NoOpUnitOfWork: IUnitOfWork
{
    public Task<int> SaveChangesAsync(CancellationToken ct = default) => Task.FromResult(0);
}
