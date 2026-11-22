using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Users
{
    /// <summary>
    /// Khóa tài khoản (UC02, chỉ ADMIN). Người bị khóa không đăng nhập mới được nữa;
    /// token đã cấp vẫn dùng được đến khi hết hạn (JWT không thu hồi được nếu server không lưu gì).
    /// </summary>
    public class LockUserCommand : ICommand<GetUserDto>
    {
        /// <summary>
        /// 
        /// </summary>
        public Guid UserId { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public Guid ActorId { get; set; }
    }

    /// <summary>
    /// 
    /// </summary>

}
