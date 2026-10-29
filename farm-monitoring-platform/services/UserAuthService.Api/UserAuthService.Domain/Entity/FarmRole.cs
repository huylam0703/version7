using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Entity;

public static class FarmRole
{
    /// <summary>
    /// quản trị viên trang trại (Farm Adminstrator)
    /// </summary>
    public const string Admin = "ADMIN";
    /// <summary>
    /// Người vận hành UAV & thiết bị 
    /// </summary>
    public const string Operator = "OPERATOR";

    /// <summary>
    /// Role của token dịch vụ (service gọi service). Không gán được cho người dùng.
    /// </summary>
    public const string Service = "SERVICE";

    /// <summary>
    /// Vai trò hợp lệ để gán cho người dùng (không phân biệt hoa thường).
    /// </summary>
    /// <param name="role"></param>
    /// <returns></returns>
    public static bool IsAssignable(string? role)
        => string.Equals(role?.Trim(), Admin, StringComparison.OrdinalIgnoreCase)
        || string.Equals(role?.Trim(), Operator, StringComparison.OrdinalIgnoreCase);
}
