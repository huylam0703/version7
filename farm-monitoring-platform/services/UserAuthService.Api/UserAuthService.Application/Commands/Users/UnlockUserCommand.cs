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
    public class UnlockUserCommandHandler : ICommandHandler<UnlockUserCommand, GetUserDto>
    {
        /// <summary>
        /// 
        /// </summary>
        private readonly IUserService _userService;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="users"></param>
        public UnlockUserCommandHandler(IUserService users)
        {
            _userService = users;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="request"></param>
        /// <param name="cancellationToken"></param>
        /// <returns></returns>
        public async Task<Result<GetUserDto>> Handle(UnlockUserCommand request, CancellationToken cancellationToken)
        {
            var user = await _userService.GetByIdAsync(request.UserId);
            if (user is null)
                return Error.NotFound(UserAuthErrors.UserNotFound, "Không tìm thấy người dùng.");

            await _userService.SetStatusAsync(user.UserId, UserStatus.Active);

            user.Status = UserStatus.Active;
            return UserMapper.ToDto(user);
        }
    }
}
