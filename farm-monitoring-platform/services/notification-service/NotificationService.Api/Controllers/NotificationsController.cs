
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
    private const string InboxRoles = "ADMIN,OPERATOR";

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

        return Guid.TryParse(value, out var userId) && userId != Guid.Empty
            ? userId
            : null;
    }

    // 1. Kiểm tra JWT
    [HttpGet("test-auth")]
    [ProducesResponseType<NotificationAuthResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public ActionResult<NotificationAuthResponse> TestAuth()
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        return Ok(new NotificationAuthResponse("JWT authentication successful", userId.Value));
    }

    // 2. Lấy danh sách thông báo của người dùng
    [HttpGet("me")]
    [Authorize(Roles = InboxRoles)]
    [ProducesResponseType<NotificationResponse[]>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<NotificationResponse[]>> GetMyNotifications(
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var notifications = await _readService.GetByUserIdAsync(
            userId.Value, cancellationToken);

        return Ok(notifications.Select(notification => new NotificationResponse(
            notification.Id, notification.UserId, notification.Title, notification.Message,
            notification.Type, notification.IsRead, notification.CreatedAt, notification.ReadAt,
            notification.SourceEventId)).ToArray());
    }

    // 3. Đếm số thông báo chưa đọc
    [HttpGet("unread-count")]
    [Authorize(Roles = InboxRoles)]
    [ProducesResponseType<UnreadCountResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<UnreadCountResponse>> GetUnreadCount(
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var count = await _readService.GetUnreadCountAsync(
            userId.Value, cancellationToken);

        return Ok(new UnreadCountResponse(count));
    }

    // 4. Tạo thông báo (chỉ ADMIN)
    [HttpPost]
    [Authorize(Roles = "ADMIN")]
    [ProducesResponseType<NotificationCreatedResponse>(StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<NotificationCreatedResponse>> CreateNotification(
        [FromBody] CreateNotificationDto dto,
        CancellationToken cancellationToken)
    {
        if (GetCurrentUserId() == null)
            return Unauthorized();

        if (dto.UserId == Guid.Empty)
        {
            ModelState.AddModelError(nameof(dto.UserId), "UserId không hợp lệ.");
            return ValidationProblem(ModelState);
        }

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

        return StatusCode(StatusCodes.Status201Created,
            new NotificationCreatedResponse("Tạo thông báo thành công", id));
    }

    // 5. Đánh dấu một thông báo đã đọc
    [HttpPatch("{id:guid}/read")]
    [Authorize(Roles = InboxRoles)]
    [ProducesResponseType<NotificationReadResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<NotificationReadResponse>> MarkAsRead(
        Guid id,
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var updated = await _writeService.MarkAsReadAsync(
            id, userId.Value, cancellationToken);

        if (!updated)
            return Problem(statusCode: 404, title: "Không tìm thấy thông báo.");

        return Ok(new NotificationReadResponse("Đã đánh dấu đã đọc"));
    }

    // 6. Đánh dấu tất cả thông báo đã đọc
    [HttpPatch("read-all")]
    [Authorize(Roles = InboxRoles)]
    [ProducesResponseType<NotificationsReadResponse>(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    public async Task<ActionResult<NotificationsReadResponse>> MarkAllAsRead(
        CancellationToken cancellationToken)
    {
        var userId = GetCurrentUserId();

        if (userId == null)
            return Unauthorized();

        var count = await _writeService.MarkAllAsReadAsync(
            userId.Value, cancellationToken);

        return Ok(new NotificationsReadResponse("Đã đánh dấu tất cả đã đọc", count));
    }

    // 7. Xóa một thông báo
    [HttpDelete("{id:guid}")]
    [Authorize(Roles = InboxRoles)]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
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
            return Problem(statusCode: 404, title: "Không tìm thấy thông báo.");

        return NoContent();
    }
}
