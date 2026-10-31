using System;
using System.Collections.Generic;
using System.Text;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.UserDto;
using UserAuthService.Domain.Entity;

namespace UserAuthService.Domain.Interfaces.Services
{
    public interface IUserService
    {
        ///-------------------
        ///-------query-------
        ///-------------------

        /// <summary>
        /// Lấy thông tin từ Email
        /// </summary>
        /// <param name="email"></param>
        /// <returns></returns>
        Task<UserInfoDto?> GetByEmailAsync(string email);

        /// <summary>
        /// Lấy thông tin từ UserId
        /// </summary>
        /// <param name="userId"></param>
        /// <returns></returns>
        Task<UserInfoDto?> GetByIdAsync(Guid userId);

        /// <summary>
        /// Kiểm tra Email tồn tại
        /// </summary>
        /// <param name="email"></param>
        /// <returns></returns>
        Task<bool> EmailExistsAsync(string email);

        /// <summary>
        /// Kiểm tra số điện thoại tồn tại
        /// </summary>
        /// <param name="phone"></param>
        /// <returns></returns>
        Task<bool> PhoneExistsAsync(string phone);

        /// <summary>
        /// Lấy quyền từ tên
        /// </summary>
        /// <param name="roleName"></param>
        /// <returns></returns>
        Task<Role?> GetRoleByNameAsync(string roleName);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="password"></param>
        /// <param name="saltBase64"></param>
        /// <returns></returns>
        Task<bool> PasswordCorrect(string password, Guid userId);

        /// <summary>
        /// Lấy danh sách user có phân trang
        /// </summary>
        /// <param name="filter"></param>
        /// <returns></returns>
        Task<PagedResult<GetUserDto>> ListUsersAsync(UserListFilter filter);

        ///---------------------
        ///-------command-------
        ///---------------------

        /// <summary>
        /// Tạo mới user
        /// </summary>
        /// <param name="user"></param>
        /// <returns></returns>
        Task CreateUserAsync(User user);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="userId"></param>
        /// <returns></returns>
        Task RecordLoginAsync(Guid userId);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="userId"></param>
        /// <param name="fullName"></param>
        /// <param name="phone"></param>
        /// <param name="roleId"></param>
        /// <returns></returns>
        Task UpdateAsync(Guid userId, string fullName, string? phone, Guid roleId);

        /// <summary>
        /// 
        /// </summary>
        /// <param name="userId"></param>
        /// <param name="status"></param>
        /// <returns></returns>
        Task SetStatusAsync(Guid userId, string status);
    }
}
