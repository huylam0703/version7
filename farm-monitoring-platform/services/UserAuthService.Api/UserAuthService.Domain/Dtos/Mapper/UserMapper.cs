using System;
using System.Collections.Generic;
using System.Text;

using UserAuthService.Domain.Dtos.UserDto;
using UserAuthService.Domain.Entity;

namespace UserAuthService.Domain.Dtos.Mapper;

public class UserMapper
{
    /// <summary>
    /// 
    /// </summary>
    /// <param name="value"></param>
    /// <returns></returns>
    public static DateTimeOffset Utc(DateTime value)
    => new(DateTime.SpecifyKind(value, DateTimeKind.Utc));

    /// <summary>
    /// 
    /// </summary>
    /// <param name="u"></param>
    /// <returns></returns>
    public static GetUserDto ToDto(UserInfoDto u)
        => new(u.UserId, u.FullName, u.Email, u.Phone, u.RoleName, u.Status,
               u.LastLoginAt is null ? null : Utc(u.LastLoginAt.Value),
               Utc(u.CreatedAt), Utc(u.UpdatedAt));

    /// <summary>
    /// 
    /// </summary>
    /// <param name="user"></param>
    /// <param name="roleName"></param>
    /// <returns></returns>
    public static GetUserDto ToDto(User user, string roleName)
        => new(user.UserId, user.FullName, user.Email, user.Phone, roleName, user.Status,
               null, Utc(user.CreatedAt), Utc(user.UpdatedAt));
}
