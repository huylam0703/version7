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
    public class LockUserCommandHandler : ICommandHandler<LockUserCommand, GetUserDto>
    {
        /// <summary>
        /// 
        /// </summary>
        private readonly IUserService _userService;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="users"></param>
        public LockUserCommandHandler(IUserService users)
        {
            _userService = users;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="request"></param>
        /// <param name="cancellationToken"></param>
        /// <returns></returns>
        public async Task<Result<GetUserDto>> Handle(LockUserCommand request, CancellationToken cancellationToken)
        {
            if (request.ActorId == request.UserId)
                return Error.Forbidden(UserAuthErrors.CannotLockSelf, "Không thể tự khóa tài khoản của chính mình.");

            var user = await _userService.GetByIdAsync(request.UserId);
            if (user is null)
                return Error.NotFound(UserAuthErrors.UserNotFound, "Không tìm thấy người dùng.");

            await _userService.SetStatusAsync(user.UserId, UserStatus.Locked);

            user.Status = UserStatus.Locked;
            return UserMapper.ToDto(user);
        }
    }
}
