using System;
using System.Collections.Generic;
using System.Data;
using System.Text;
using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Interfaces.Utilities;

namespace UserAuthService.Application.Commands.Users;
public class CreateUserCommandHandler : ICommandHandler<CreateUserCommand, GetUserDto>
{
    /// <summary>
    ///
    /// </summary>
    private readonly IUserService _userService;
    /// <summary>
    ///
    /// </summary>
    private readonly IHashUtilities _hashUtilities;

    /// <summary>
    ///
    /// </summary>
    /// <param name="userService"></param>
    /// <param name="hashUtilities"></param>
    public CreateUserCommandHandler(IUserService userService, IHashUtilities hashUtilities)
    {
        _userService = userService;
        _hashUtilities = hashUtilities;
    }

    /// <summary>
    ///
    /// </summary>
    /// <param name="request"></param>
    /// <param name="cancellationToken"></param>
    /// <returns></returns>
    public async Task<Result<GetUserDto>> Handle(CreateUserCommand request,CancellationToken cancellationToken)
    {
        var email = request.Email!.Trim().ToLowerInvariant();

        if (await _userService.EmailExistsAsync(email))
        {
            return Error.Conflict(UserAuthErrors.EmailExists, "Email đã được sử dụng.");
        }

        var phone = string.IsNullOrWhiteSpace(request.Phone)? null: request.Phone.Trim();

        if (phone is not null && await _userService.PhoneExistsAsync(phone))
        {
            return Error.Conflict(UserAuthErrors.PhoneExists, "Số điện thoại đã được sử dụng.");
        }

        var role = await _userService.GetRoleByNameAsync(request.RoleName.Trim().ToUpperInvariant());

        if (role is null)
        {
            return Error.NotFound(UserAuthErrors.RoleNotFound, "Không tìm thấy vai trò.");
        }

        var hashSalt = _hashUtilities.GenerateSalt();
        var saltBase64 = Convert.ToBase64String(hashSalt);
        var passwordHash = _hashUtilities.HashPassword(request.Password!, hashSalt);

        var user = User.Create(
            role.RoleId,
            request.FullName!.Trim(),
            email,
            passwordHash,
            saltBase64,
            phone);

        await _userService.CreateUserAsync(user);

        return UserMapper.ToDto(user, role.Name);
    }

}
