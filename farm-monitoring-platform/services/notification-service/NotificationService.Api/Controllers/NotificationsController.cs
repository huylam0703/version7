
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using NotificationService.Api.DTOs;
using NotificationService.Domain.Entities;
using NotificationService.Domain.Interfaces;

namespace NotificationService.Api.Controllers;

[ApiController]
[Route("api/notifications")]
[Authorize]
public class NotificationsController : ControllerBase
{
    private readonly INotificationReadService _readService;
    private readonly INotificationService _writeService;

    public NotificationsController(
        INotificationReadService readService,
        INotificationService writeService)
    {
        _readService = readService;
        _writeService = writeService;
    }

    private Guid? GetCurrentUserId()
    {
        var value = User.FindFirst("sub")?.Value
            ?? User.FindFirst(ClaimTypes.NameIdentifier)?.Value;

        return Guid.TryParse(value, out var userId)
            ? userId
            : null;
    }

    // 1. Kiểm tra JWT
    [HttpGet("test-auth")]
    public IActionResult TestAuth()
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        return Ok(new
        {
            message = "JWT authentication successful",
            userId
        });
    }

    // 2. Lấy danh sách thông báo của người dùng
    [HttpGet("me")]
    public async Task<IActionResult> GetMyNotifications(
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var notifications = await _readService.GetByUserIdAsync(
            userId.Value, cancellationToken);

        return Ok(notifications);
    }

    // 3. Đếm số thông báo chưa đọc
    [HttpGet("unread-count")]
    public async Task<IActionResult> GetUnreadCount(
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var count = await _readService.GetUnreadCountAsync(
            userId.Value, cancellationToken);

        return Ok(new { unreadCount = count });
    }

    // 4. Tạo thông báo (chỉ ADMIN)
    [HttpPost]
    [Authorize(Roles = "ADMIN")]
    public async Task<IActionResult> CreateNotification(
        [FromBody] CreateNotificationDto dto,
        CancellationToken cancellationToken)
    {
        if (dto.UserId == Guid.Empty)
            return BadRequest("UserId không hợp lệ.");

        var notification = new Notification
        {
            UserId = dto.UserId,
            Title = dto.Title,
            Message = dto.Message,
            Type = dto.Type,
            IsRead = false,
            CreatedAt = DateTime.UtcNow
        };

        var id = await _writeService.CreateAsync(
            notification, cancellationToken);

        return StatusCode(201, new
        {
            message = "Tạo thông báo thành công",
            notificationId = id
        });
    }

    // 5. Đánh dấu một thông báo đã đọc
    [HttpPatch("{id:guid}/read")]
    public async Task<IActionResult> MarkAsRead(
        Guid id,
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var updated = await _writeService.MarkAsReadAsync(
            id, userId.Value, cancellationToken);

        if (!updated)
            return NotFound("Không tìm thấy thông báo.");

        return Ok(new { message = "Đã đánh dấu đã đọc" });
    }

    // 6. Đánh dấu tất cả thông báo đã đọc
    [HttpPatch("read-all")]
    public async Task<IActionResult> MarkAllAsRead(
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var count = await _writeService.MarkAllAsReadAsync(
            userId.Value, cancellationToken);

        return Ok(new
        {
            message = "Đã đánh dấu tất cả đã đọc",
            updatedCount = count
        });
    }

    // 7. Xóa một thông báo
    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> DeleteNotification(
        Guid id,
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var deleted = await _writeService.DeleteAsync(
            id, userId.Value, cancellationToken);

        if (!deleted)
            return NotFound("Không tìm thấy thông báo.");

        return NoContent();
    }
}
