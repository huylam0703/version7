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

public class IssueServiceTokenValidator : AbstractValidator<IssueServiceTokenCommand>
{
    public IssueServiceTokenValidator()
    {
        RuleFor(x => x.ClientId).NotEmpty().WithMessage("client_id không được để trống.");
        RuleFor(x => x.ClientSecret).NotEmpty().WithMessage("client_secret không được để trống.");
    }
}

public class IssueServiceTokenCommandHandler : ICommandHandler<IssueServiceTokenCommand, ServiceTokenResponse>
{
    private readonly IServiceClientStore _clients;
    private readonly IJwtService _jwt;

    public IssueServiceTokenCommandHandler(IServiceClientStore clients, IJwtService jwt)
    {
        _clients = clients;
        _jwt = jwt;
    }

    public Task<Result<ServiceTokenResponse>> Handle(IssueServiceTokenCommand request, CancellationToken cancellationToken)
    {
        if (request.GrantType != "client_credentials")
            return Task.FromResult<Result<ServiceTokenResponse>>(
                Error.Validation(UserAuthErrors.UnsupportedGrantType, "Chỉ hỗ trợ grant_type = client_credentials."));

        if (!_clients.Validate(request.ClientId, request.ClientSecret))
            return Task.FromResult<Result<ServiceTokenResponse>>(
                Error.Unauthorized(UserAuthErrors.InvalidClient, "client_id hoặc client_secret không đúng."));

        var token = _jwt.CreateServiceToken(request.ClientId);
        var response = new ServiceTokenResponse { AccessToken = token.Token, ExpiresIn = token.ExpiresInSeconds };
        return Task.FromResult<Result<ServiceTokenResponse>>(response);
    }
}
