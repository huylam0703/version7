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


/// <summary>
/// 
/// </summary>
