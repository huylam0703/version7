namespace Farm.BuildingBlocks.Abstractions;

public interface IHasUpdatedAt { DateTime UpdatedAt { get; set; } }

public abstract class AuditableEntity : IHasUpdatedAt
{
    public DateTime CreatedAt { get; set; }
    public DateTime UpdatedAt { get; set; }
}