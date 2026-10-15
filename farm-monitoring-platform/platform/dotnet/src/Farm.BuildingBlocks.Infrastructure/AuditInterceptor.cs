using Farm.BuildingBlocks.Abstractions;

using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;

namespace Farm.BuildingBlocks.Infrastructure;

public sealed class AuditInterceptor : SaveChangesInterceptor
{
    public override InterceptionResult<int> SavingChanges(
        DbContextEventData e, InterceptionResult<int> result)
    { Touch(e.Context); return result; }

    public override ValueTask<InterceptionResult<int>> SavingChangesAsync(
        DbContextEventData e, InterceptionResult<int> result, CancellationToken ct = default)
    { Touch(e.Context); return ValueTask.FromResult(result); }

    private static void Touch(DbContext? ctx)
    {
        if (ctx is null) return;
        var now = DateTime.UtcNow;
        foreach (var entry in ctx.ChangeTracker.Entries<IHasUpdatedAt>()
                     .Where(x => x.State is EntityState.Added or EntityState.Modified))
            entry.Entity.UpdatedAt = now;
    }
}