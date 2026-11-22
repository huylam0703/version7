using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Users
{
    /// <summary>
    /// Sửa thông tin và vai trò người dùng (UC02, chỉ ADMIN). UserId và ActorId do controller gán, không lấy từ body.
    /// </summary>
    public class UpdateUserCommand : ICommand<GetUserDto>
    {
        /// <summary>
        /// 
        /// </summary>
        public Guid UserId { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public Guid ActorId { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public string FullName { get; set; } = "";
        /// <summary>
        /// 
        /// </summary>
        public string? Phone { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public string RoleName { get; set; } = "";
    }

    /// <summary>
    /// 
    /// </summary>


    /// <summary>
    /// 
    /// </summary>

}
