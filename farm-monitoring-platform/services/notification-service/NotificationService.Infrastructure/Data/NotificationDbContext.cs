
using Microsoft.EntityFrameworkCore;
using NotificationService.Domain.Entities;

namespace NotificationService.Infrastructure.Data;

public class NotificationDbContext : DbContext
{
    public NotificationDbContext(
        DbContextOptions<NotificationDbContext> options)
        : base(options)
    {
    }

    public DbSet<Notification> Notifications => Set<Notification>();
    public DbSet<ProcessedNotificationEvent> ProcessedNotificationEvents => Set<ProcessedNotificationEvent>();
    public DbSet<NotificationEmail> NotificationEmails => Set<NotificationEmail>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<ProcessedNotificationEvent>(entity =>
        {
            entity.HasKey(e => e.EventId);
            entity.Property(e => e.EventType).HasMaxLength(100).IsRequired();
        });

        modelBuilder.Entity<NotificationEmail>(entity =>
        {
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Recipient).HasMaxLength(320).IsRequired();
            entity.Property(e => e.Subject).HasMaxLength(255).IsRequired();
            entity.Property(e => e.Body).IsRequired();
            entity.Property(e => e.LastError).HasMaxLength(100);
            entity.HasIndex(e => new { e.EventId, e.UserId }).IsUnique();
            entity.HasIndex(e => new { e.SentAt, e.FailedAt, e.NextAttemptAt });
        });

        modelBuilder.Entity<Notification>(entity =>
        {
            entity.ToTable("Notifications");

            entity.HasKey(n => n.Id);

            entity.Property(n => n.UserId)
                .IsRequired();

            entity.Property(n => n.Title)
                .HasMaxLength(255)
                .IsRequired();

            entity.Property(n => n.Message)
                .IsRequired();

            entity.Property(n => n.Type)
                .HasMaxLength(50)
                .IsRequired();

            entity.Property(n => n.IsRead)
                .HasDefaultValue(false);

            entity.Property(n => n.CreatedAt)
                .IsRequired();

            entity.HasIndex(n => n.UserId);
            entity.HasIndex(n => n.CreatedAt);
            entity.HasIndex(n => new { n.UserId, n.SourceEventId })
                .IsUnique()
                .HasFilter("[SourceEventId] IS NOT NULL");
        });
    }
}
