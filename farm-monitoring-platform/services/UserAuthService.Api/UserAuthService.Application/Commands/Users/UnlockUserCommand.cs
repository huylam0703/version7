using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Users
{
    /// <summary>
    /// Mở khóa tài khoản (UC02, chỉ ADMIN)
    /// </summary>
    public class UnlockUserCommand : ICommand<GetUserDto>
    {
        /// <summary>
        /// 
        /// </summary>
        public Guid UserId { get; set; }
    }

    /// <summary>
    /// 
    /// </summary>

}
