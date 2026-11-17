using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Auth;

/// <summary>
/// 
/// </summary>
public class IssueServiceTokenCommand : ICommand<ServiceTokenResponse>
{
    /// <summary>
    /// 
    /// </summary>
    public string GrantType { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string ClientId { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string ClientSecret { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string? Scope { get; set; }
}

/// <summary>
/// 
/// </summary>
public class IssueServiceTokenValidator : AbstractValidator<IssueServiceTokenCommand>
{
    /// <summary>
    /// 
    /// </summary>
    public IssueServiceTokenValidator()
    {
        RuleFor(x => x.ClientId).NotEmpty().WithMessage("client_id không được để trống.");
        RuleFor(x => x.ClientSecret).NotEmpty().WithMessage("client_secret không được để trống.");
    }
}

/// <summary>
/// 
/// </summary>
public class IssueServiceTokenCommandHandler : ICommandHandler<IssueServiceTokenCommand, ServiceTokenResponse>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IServiceClientStore _clients;
    /// <summary>
    /// 
    /// </summary>
    private readonly ISimulatorClientService _simulatorClientService;
    /// <summary>
    /// 
    /// </summary>
    private readonly IJwtService _jwt;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clients"></param>
    /// <param name="simulatorClientService"></param>
    /// <param name="jwt"></param>
    public IssueServiceTokenCommandHandler(IServiceClientStore clients, ISimulatorClientService simulatorClientService, IJwtService jwt)
    {
        _clients = clients;
        _simulatorClientService = simulatorClientService;
        _jwt = jwt;
    }

    /// <summary>
    /// Không lộ client có tồn tại
    /// </summary>
    /// <returns></returns>
    private static Result<ServiceTokenResponse> InvalidClient()
       => Error.Unauthorized(UserAuthErrors.InvalidClient, "Xác thực client thất bại.");

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="cancellationToken"></param>
    /// <returns></returns>
    public async Task<Result<ServiceTokenResponse>> Handle(IssueServiceTokenCommand request, CancellationToken cancellationToken)
    {
        if (request.GrantType != "client_credentials")
            return Error.Validation(
                    UserAuthErrors.UnsupportedGrantType,
                    "Chỉ hỗ trợ grant_type = client_credentials."
                );

        var clientId = request.ClientId.Trim();

        //simulator được đăng ký trong db 
        var auth = await _simulatorClientService.AuthenticateAsync(clientId, request.ClientSecret, cancellationToken);
        if (auth.Status != ClientAuthStatus.NotFound)
        {
            if (auth.Status != ClientAuthStatus.Ok) return InvalidClient();

            var allowed = SimulatorScopes.Parse(auth.Client!.AllowedScopes);
            var requested = SimulatorScopes.Parse(request.Scope);
            var granted = requested.Count == 0 ? allowed : requested;

            if (granted.Any(s => !allowed.Contains(s, StringComparer.Ordinal)))
                return Error.Validation(UserAuthErrors.InvalidScope, "Scope yêu cầu vượt quá quyền được cấp cho client.");

            var clientToken = _jwt.CreateClientToken(auth.Client.ClientId, granted);
            return new ServiceTokenResponse
            {
                AccessToken = clientToken.Token,
                ExpiresIn = clientToken.ExpiresInSeconds,
                Scope = string.Join(' ', granted)
            };
        }

        //service nội bộ 
        if (!_clients.Validate(clientId, request.ClientSecret))
            return InvalidClient();

        var token = _jwt.CreateServiceToken(clientId);
        return new ServiceTokenResponse { AccessToken = token.Token, ExpiresIn = token.ExpiresInSeconds };
    }
}
