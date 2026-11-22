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
        public string? Email { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public string Password { get; set; } = "";
    }
    /// <summary>
    /// 
    /// </summary>


    /// <summary>
    /// 
    /// </summary>

}
