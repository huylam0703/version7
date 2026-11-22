using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Auth;

public class IssueServiceTokenCommand : ICommand<ServiceTokenResponse>
{
    public string GrantType { get; set; } = "";
    public string ClientId { get; set; } = "";
    public string ClientSecret { get; set; } = "";
}
