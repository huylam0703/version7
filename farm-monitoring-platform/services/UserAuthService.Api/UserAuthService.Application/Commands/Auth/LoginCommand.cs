using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using Microsoft.AspNetCore.Identity;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Interfaces.Utilities;

namespace UserAuthService.Application.Commands.Auth
{
    /// <summary>
    /// Đăng nhập (UC01)
    /// </summary>
    public class LoginCommand : ICommand<LoginResponse>
    {
        /// <summary>
        /// 
        /// </summary>
        public string Email { get; set; } = "";
        /// <summary>
        /// 
        /// </summary>
        public string Password { get; set; } = "";
    }
    /// <summary>
    /// 
    /// </summary>
    public class LoginValidator : AbstractValidator<LoginCommand>
    {
        /// <summary>
        /// 
        /// </summary>
        public LoginValidator()
        {
            RuleFor(x => x.Email)
                .NotEmpty().WithMessage("Email không được để trống.")
                .EmailAddress().WithMessage("Email không hợp lệ.");

            RuleFor(x => x.Password)
                .NotEmpty().WithMessage("Mật khẩu không được để trống.");
        }
    }

    /// <summary>
    /// 
    /// </summary>
    public class LoginCommandHandler : ICommandHandler<LoginCommand, LoginResponse>
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
        private readonly IRefreshTokenService _refreshTokenService;
        /// <summary>
        /// 
        /// </summary>
        private readonly IJwtService _jwt;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="users"></param>
        /// <param name="hasher"></param>
        /// <param name="jwt"></param>
        /// <param name="refreshTokenService"></param>
        public LoginCommandHandler(IUserService users, IHashUtilities hasher, IJwtService jwt, IRefreshTokenService refreshTokenService)
        {
            _userService = users;
            _hashUtilities = hasher;
            _jwt = jwt;
            _refreshTokenService = refreshTokenService;
        }
        /// <summary>
        /// 
        /// </summary>
        /// <param name="request"></param>
        /// <param name="cancellationToken"></param>
        /// <returns></returns>
        public async Task<Result<LoginResponse>> Handle(LoginCommand request, CancellationToken cancellationToken)
        {
            var email = request.Email.Trim().ToLowerInvariant();
            var user = await _userService.GetByEmailAsync(email);
            

            if (user is null || !(await _userService.PasswordCorrect(request.Password, user!.UserId!)))
            {
                return Error.Unauthorized(UserAuthErrors.InvalidCredentials, "Email hoặc mật khẩu không đúng.");
            }
                

            if (string.Equals(user.Status, UserStatus.Locked, StringComparison.OrdinalIgnoreCase))
                return Error.Forbidden(UserAuthErrors.AccountLocked, "Tài khoản đã bị khóa.");

            await _userService.RecordLoginAsync(user.UserId);
            user.LastLoginAt = DateTime.UtcNow;

            var token = _jwt.CreateAccessToken(user);
            var refresh = await _refreshTokenService.IssueAsync(user.UserId, cancellationToken);
            return new LoginResponse(
                 token.Token, "Bearer", token.ExpiresInSeconds,
                 refresh.Token, refresh.ExpiresAt,
                 UserMapper.ToDto(user));
        }
    }
}
