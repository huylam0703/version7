using System;
using System.Collections.Generic;
using System.Text;
using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;
using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Auth;

public class IssueServiceTokenValidator : AbstractValidator<IssueServiceTokenCommand>
{
    public IssueServiceTokenValidator()
    {
        RuleFor(x => x.ClientId).NotEmpty().WithMessage("client_id không được để trống.");
        RuleFor(x => x.ClientSecret).NotEmpty().WithMessage("client_secret không được để trống.");
    }
}
