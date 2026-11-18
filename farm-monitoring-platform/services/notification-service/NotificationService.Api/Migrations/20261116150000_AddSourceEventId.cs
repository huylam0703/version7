using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Migrations;
using NotificationService.Infrastructure.Data;

namespace NotificationService.Api.Migrations;

[DbContext(typeof(NotificationDbContext))]
[Migration("20261116150000_AddSourceEventId")]
public sealed class AddSourceEventId : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.AddColumn<Guid>(
            name: "SourceEventId", table: "Notifications", type: "uniqueidentifier", nullable: true);
        // Deferred compilation also supports the generated SQL script's ALTER TABLE batch.
        migrationBuilder.Sql("""
            EXEC(N'CREATE UNIQUE INDEX [IX_Notifications_UserId_SourceEventId]
            ON [Notifications] ([UserId], [SourceEventId]) WHERE [SourceEventId] IS NOT NULL');
            """);
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropIndex("IX_Notifications_UserId_SourceEventId", "Notifications");
        migrationBuilder.DropColumn("SourceEventId", "Notifications");
    }
}
