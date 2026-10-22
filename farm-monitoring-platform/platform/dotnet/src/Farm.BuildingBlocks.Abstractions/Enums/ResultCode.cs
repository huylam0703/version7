using System.ComponentModel;

namespace Farm.BuildingBlocks.Abstractions;

public enum ResultCode
{
    /// <summary>
    /// Thành công
    /// </summary>
    [Description("Thành công.")]
    Ok = 200,

    // ===== System error =====
    /// <summary>
    /// Lỗi chứng thực
    /// </summary>
    [Description("Chứng thực thất bại.")]
    ErrorAuthenticationFail = -120,

    /// <summary>
    /// Lỗi input (Validation)
    /// </summary>
    [Description("Input đầu vào không hợp lệ.")]
    ErrorInputInvalid = -121,

    /// <summary>
    /// Access-token hết hạn
    /// </summary>
    [Description("Token đã hết hạn.")]
    ErrorTokenExpired = -123,

    /// <summary>
    /// Lỗi phân quyền
    /// </summary>
    [Description("Phân quyền thất bại.")]
    ErrorAuthorizationFail = -124,

    // ===== Error =====
    /// <summary>
    /// Lỗi chung business
    /// </summary>
    [Description("Yêu cầu không thành công.")]
    ErrorFail = -110,

    /// <summary>
    /// Lỗi exception / lỗi hệ thống
    /// </summary>
    [Description("Lỗi hệ thống.")]
    ErrorException = -130,

    /// <summary>
    /// Thông tin đã tồn tại
    /// </summary>
    [Description("Thông tin đã tồn tại trong hệ thống.")]
    ErrorConflict = -142,

    /// <summary>
    /// Gửi quá nhiều yêu cầu (rate limit)
    /// </summary>
    [Description("Gửi quá nhiều yêu cầu, vui lòng thử lại sau.")]
    ErrorRateLimited = -144,

    /// <summary>
    /// Dịch vụ tạm thời không khả dụng (502/503/504)
    /// </summary>
    [Description("Dịch vụ tạm thời không khả dụng.")]
    ErrorServiceUnavailable = -145,

    /// <summary>
    /// Service phía sau trả lỗi không đúng khung
    /// </summary>
    [Description("Dịch vụ phía sau trả lỗi.")]
    ErrorUpstream = -146,

    // ===== Warning =====
    /// <summary>
    /// Không có data / không tìm thấy
    /// </summary>
    [Description("Không tìm thấy thông tin.")]
    WarningNoContent = -302,

    /// <summary>
    /// Không đủ quyền truy cập
    /// </summary>
    [Description("Không đủ quyền truy cập.")]
    WarningForbidden = 403,

    /// <summary>
    /// Phương thức không được hỗ trợ
    /// </summary>
    [Description("Phương thức không được hỗ trợ.")]
    MethodNotAllowed = 405,

    /// <summary>
    /// Chưa đăng nhập
    /// </summary>
    [Description("Chưa đăng nhập hoặc token không hợp lệ.")]
    Unauthorized = 401,
}