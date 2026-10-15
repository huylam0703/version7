namespace Farm.BuildingBlocks.Abstractions;

/// Envelope theo PLATFORM-CONVENTIONS, serialize JSON camelCase.
public sealed record EventEnvelope<T>(
    Guid EventId,
    string EventType,
    int Version,
    DateTimeOffset OccurredAt,
    string Producer,
    string? TraceId,
    T Payload);

public interface IEventBus
{
    Task PublishAsync<T>(string eventType, T payload, CancellationToken ct = default);
}

public interface IEventHandler<T>
{
    Task HandleAsync(EventEnvelope<T> envelope, CancellationToken ct);
}