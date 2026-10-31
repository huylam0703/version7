using System;
using System.Collections.Generic;
using System.Data;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using MediatR;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Interfaces.Utilities;

namespace UserAuthService.Application.Commands.Users;

/// <summary>
/// 
/// </summary>
public class CreateUserCommand : ICommand<GetUserDto>
{
    public string? FullName { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Email { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Phone { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string? Password { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public string RoleName { get; set; } = FarmRole.Operator;
}

/// <summary>
/// 
/// </summary>
public class CreateUserCommandValidator : AbstractValidator<CreateUserCommand>
{
    /// <summary>
    /// 
    /// </summary>
    public CreateUserCommandValidator()
    {
        RuleFor(x => x.FullName)
                .NotEmpty().WithMessage("Họ tên không được để trống.")
                .MaximumLength(100).WithMessage("Họ tên tối đa 100 ký tự.");

        RuleFor(x => x.Email)
            .NotEmpty().WithMessage("Email không được để trống.")
            .EmailAddress().WithMessage("Email không hợp lệ.")
            .MaximumLength(150).WithMessage("Email tối đa 150 ký tự.");

        RuleFor(x => x.Password)
            .NotEmpty().WithMessage("Mật khẩu không được để trống.")
            .MinimumLength(8).WithMessage("Mật khẩu phải có ít nhất 8 ký tự.");

        // Phone cho phép NULL trong DB nên chỉ kiểm tra khi có nhập
        RuleFor(x => x.Phone)
            .Matches(@"^(0|\+84)[0-9]{9,10}$").WithMessage("Số điện thoại không hợp lệ.")
            .When(x => !string.IsNullOrWhiteSpace(x.Phone));

        RuleFor(x => x.RoleName)
            .Must(FarmRole.IsAssignable).WithMessage("Vai trò phải là ADMIN hoặc OPERATOR.");
    }
}

/// <summary>
/// 
/// </summary>
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
    public async Task<Result<GetUserDto>> Handle(CreateUserCommand request, CancellationToken cancellationToken)
    {
        var email = request.Email!.Trim().ToLowerInvariant();
        if (await _userService.EmailExistsAsync(email))
        {
            return Error.Conflict(UserAuthErrors.EmailExists, "Email đã được sử dụng.");
        }

        var phone = request.Phone!.Trim();
        if (await _userService.PhoneExistsAsync(email))
        {
            return Error.Conflict(UserAuthErrors.PhoneExists, "Số điện thoại đã được sử dụng.");
        }
            

        var role = await _userService.GetRoleByNameAsync(request.RoleName.Trim().ToUpperInvariant());
        if (role is null)
        {
            return Error.NotFound(UserAuthErrors.RoleNotFound, "Không tìm thấy vai trò.");
        }
            

        var hashSalt = _hashUtilities.GenerateSalt();
        string saltBase64 = Convert.ToBase64String(hashSalt);
        var passwordHash = _hashUtilities.HashPassword(request.Password!, hashSalt);

        var user = User.Create(role.RoleId, request.FullName!, email, passwordHash, saltBase64, request.Phone);
        await _userService.CreateUserAsync(user);

        return UserMapper.ToDto(user, role.Name);
    }

}