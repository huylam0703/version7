using RabbitMQ.Client;
using RabbitMQ.Client.Events;

namespace NotificationService.Api.Services;

public sealed class NotificationEventConsumer(
    IConfiguration configuration,
    IServiceScopeFactory scopeFactory,
    ILogger<NotificationEventConsumer> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (!configuration.GetValue("RabbitMq:Enabled", false)) return;

        var factory = new ConnectionFactory
        {
            HostName = configuration["RabbitMq:Host"] ?? "localhost",
            Port = configuration.GetValue("RabbitMq:Port", 5672),
            VirtualHost = configuration["RabbitMq:VirtualHost"] ?? "/",
            UserName = configuration["RabbitMq:User"] ?? "guest",
            Password = configuration["RabbitMq:Password"] ?? "guest",
            AutomaticRecoveryEnabled = false
        };
        var queue = configuration["RabbitMq:Queue"] ?? "notification.events";

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await using var connection = await factory.CreateConnectionAsync(stoppingToken);
                await using var channel = await connection.CreateChannelAsync(
                    new CreateChannelOptions(publisherConfirmationsEnabled: true,
                        publisherConfirmationTrackingEnabled: true), stoppingToken);
                var disconnected = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
                channel.ChannelShutdownAsync += (_, _) =>
                {
                    disconnected.TrySetResult();
                    return Task.CompletedTask;
                };

                await channel.ExchangeDeclareAsync("farm.events", ExchangeType.Topic, durable: true,
                    cancellationToken: stoppingToken);
                await channel.QueueDeclareAsync(queue, durable: true, exclusive: false, autoDelete: false,
                    cancellationToken: stoppingToken);
                await channel.QueueDeclareAsync(queue + ".dlq", durable: true, exclusive: false,
                    autoDelete: false, cancellationToken: stoppingToken);
                await channel.QueueDeclareAsync(queue + ".retry", durable: true, exclusive: false,
                    autoDelete: false, arguments: new Dictionary<string, object?>
                    {
                        ["x-message-ttl"] = 10000,
                        ["x-dead-letter-exchange"] = "",
                        ["x-dead-letter-routing-key"] = queue
                    }, cancellationToken: stoppingToken);
                await channel.QueueBindAsync(queue, "farm.events", "alert.created", cancellationToken: stoppingToken);
                await channel.QueueBindAsync(queue, "farm.events", "mission.status_changed", cancellationToken: stoppingToken);
                await channel.BasicQosAsync(0, 1, false, stoppingToken);

                var consumer = new AsyncEventingBasicConsumer(channel);
                consumer.UnregisteredAsync += (_, _) =>
                {
                    disconnected.TrySetResult();
                    return Task.CompletedTask;
                };
                consumer.ReceivedAsync += async (_, delivery) =>
                {
                    try
                    {
                        using var scope = scopeFactory.CreateScope();
                        var processor = scope.ServiceProvider.GetRequiredService<NotificationEventProcessor>();
                        try
                        {
                            await processor.ProcessAsync(delivery.Body, stoppingToken);
                        }
                        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                        {
                            return;
                        }
                        catch (Exception exception)
                        {
                            var attempt = 1;
                            if (delivery.BasicProperties.Headers?.TryGetValue("x-notification-attempt", out var previous) == true &&
                                previous is int previousAttempt && previousAttempt is >= 1 and < 3)
                                attempt = previousAttempt + 1;
                            var invalid = exception is InvalidNotificationEventException;
                            var destination = invalid || attempt >= 3 ? queue + ".dlq" : queue + ".retry";
                            var properties = new BasicProperties
                            {
                                Persistent = true,
                                ContentType = "application/json",
                                MessageId = delivery.BasicProperties.MessageId,
                                Headers = new Dictionary<string, object?>
                                {
                                    ["x-notification-attempt"] = attempt,
                                    ["x-notification-error"] = exception.GetType().Name,
                                    ["x-original-routing-key"] = delivery.RoutingKey
                                }
                            };
                            // Confirm the replacement delivery before acknowledging the original.
                            await channel.BasicPublishAsync("", destination, mandatory: true, properties,
                                delivery.Body, stoppingToken);
                            logger.LogWarning("Notification event moved to {Queue} after attempt {Attempt}; reason {Error}",
                                destination, attempt, exception.GetType().Name);
                        }
                        await channel.BasicAckAsync(delivery.DeliveryTag, false, stoppingToken);
                    }
                    catch (Exception exception)
                    {
                        // Disposing the channel returns unacknowledged deliveries to RabbitMQ.
                        logger.LogWarning("Notification channel will reconnect after {Error}", exception.GetType().Name);
                        disconnected.TrySetResult();
                    }
                };
                await channel.BasicConsumeAsync(queue, autoAck: false, consumer, stoppingToken);
                logger.LogInformation("Listening for notification events on {Queue}", queue);
                await disconnected.Task.WaitAsync(stoppingToken);
            }
            catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
            {
                return;
            }
            catch (Exception exception)
            {
                logger.LogWarning("Notification event connection failed with {Error}; retrying",
                    exception.GetType().Name);
            }
            await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken);
        }
    }
}
