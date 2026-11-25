using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Users;
/// <summary>
/// 
/// </summary>
public class UpdateMyProfileCommand : ICommand<GetUserDto>
{
    /// <summary>
    /// 
    /// </summary>
    public Guid UserId { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string FullName { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string? Phone { get; set; }
}

/// <summary>
/// 
/// </summary>
public class UpdateMyProfileValidator : AbstractValidator<UpdateMyProfileCommand>
{
    /// <summary>
    /// 
    /// </summary>
    public UpdateMyProfileValidator()
    {
        RuleFor(x => x.FullName)
            .NotEmpty().WithMessage("Họ tên không được để trống.")
            .MaximumLength(100).WithMessage("Họ tên tối đa 100 ký tự.");
        RuleFor(x => x.Phone)
            .Matches(@"^(0|\+84)[0-9]{9,10}$").WithMessage("Số điện thoại không hợp lệ.")
            .When(x => !string.IsNullOrWhiteSpace(x.Phone));
    }
}

/// <summary>
/// 
/// </summary>
public class UpdateMyProfileCommandHandler : ICommandHandler<UpdateMyProfileCommand, GetUserDto>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IUserService _userService;
    /// <summary>
    /// 
    /// </summary>
    /// <param name="users"></param>
    public UpdateMyProfileCommandHandler(IUserService users) => _userService = users;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<GetUserDto>> Handle(UpdateMyProfileCommand request, CancellationToken ct)
    {
        var user = await _userService.GetByIdAsync(request.UserId);
        if (user is null)
            return Error.NotFound(UserAuthErrors.UserNotFound, "Không tìm thấy người dùng.");

        // Giữ nguyên role hiện tại, người dùng không tự đổi được role
        await _userService.UpdateAsync(user.UserId, request.FullName.Trim(), request.Phone, user.RoleId);

        var updated = await _userService.GetByIdAsync(user.UserId);
        return UserMapper.ToDto(updated!);
    }
}