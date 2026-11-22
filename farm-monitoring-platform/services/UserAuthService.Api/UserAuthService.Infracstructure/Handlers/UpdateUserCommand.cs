using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;
using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Users;
    public class UpdateUserCommandHandler : ICommandHandler<UpdateUserCommand, GetUserDto>
    {
        /// <summary>
        ///
        /// </summary>
        private readonly IUserService _userService;

        /// <summary>
        ///
        /// </summary>
        /// <param name="users"></param>
        public UpdateUserCommandHandler(IUserService users)
        {
            _userService = users;
        }

        /// <summary>
        ///
        /// </summary>
        /// <param name="request"></param>
        /// <param name="cancellationToken"></param>
        /// <returns></returns>
        public async Task<Result<GetUserDto>> Handle(UpdateUserCommand request, CancellationToken cancellationToken)
        {
            var user = await _userService.GetByIdAsync(request.UserId);
            if (user is null)
                return Error.NotFound(UserAuthErrors.UserNotFound, "Không tìm thấy người dùng.");

            var roleName = request.RoleName.Trim().ToUpperInvariant();
            var roleId = user.RoleId;

            if (!string.Equals(user.RoleName, roleName, StringComparison.OrdinalIgnoreCase))
            {
                // Tránh ADMIN tự hạ quyền mình rồi không còn ai quản trị được hệ thống.
                if (request.ActorId == user.UserId)
                    return Error.Forbidden(UserAuthErrors.CannotChangeOwnRole, "Không thể tự đổi vai trò của chính mình.");

                var role = await _userService.GetRoleByNameAsync(roleName);
                if (role is null)
                    return Error.NotFound(UserAuthErrors.RoleNotFound, "Không tìm thấy vai trò.");

                roleId = role.RoleId;
            }

            await _userService.UpdateAsync(user.UserId, request.FullName.Trim(), request.Phone, roleId);

            var updated = await _userService.GetByIdAsync(user.UserId);
            return UserMapper.ToDto(updated!);
        }
    }
