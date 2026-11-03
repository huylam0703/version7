
using Microsoft.EntityFrameworkCore;
using NotificationService.Api.Models;

namespace NotificationService.Api.Data;

public class NotificationDbContext : DbContext
{
    public NotificationDbContext(
        DbContextOptions<NotificationDbContext> options)
        : base(options)
    {
    }

    public DbSet<Notification> Notifications => Set<Notification>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

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
        });
    }
}
