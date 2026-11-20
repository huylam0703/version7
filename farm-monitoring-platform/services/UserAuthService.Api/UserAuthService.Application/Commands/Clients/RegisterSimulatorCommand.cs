using System;
using System.Collections.Generic;
using System.Text;

using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Commands.Clients;


/// <summary>
/// 
/// </summary>
public class RegisterSimulatorCommand : ICommand<SimulatorClientSecretDto>
{
    /// <summary>
    /// 
    /// </summary>
    public string ClientId { get; set; } = "";
    /// <summary>
    /// 
    /// </summary>
    public string? DisplayName { get; set; }
    /// <summary>
    /// 
    /// </summary>
    public List<string> Scopes { get; set; } = [];
}

/// <summary>
/// 
/// </summary>
public class RegisterSimulatorClientValidator : AbstractValidator<RegisterSimulatorCommand>
{
    /// <summary>
    /// 
    /// </summary>
    public RegisterSimulatorClientValidator()
    {
        RuleFor(x => x.ClientId).NotEmpty().MaximumLength(100)
            .Matches("^[a-zA-Z0-9._-]+$").WithMessage("client_id chỉ gồm chữ, số, dấu chấm, gạch ngang, gạch dưới.");
        RuleFor(x => x.DisplayName).MaximumLength(150);
        RuleFor(x => x.Scopes).NotEmpty().WithMessage("Phải có ít nhất một scope.");

        RuleForEach(x => x.Scopes)
            .Must(SimulatorScopes.IsKnown)
            .WithMessage(
                "Scope không hợp lệ (chỉ mission:read, mission:ack, " +
                "mission:progress, mission:heartbeat, mission:results)."
            );

    }
}

/// <summary>
/// 
/// </summary>
public class RegisterSimulatorClientCommandHandler: ICommandHandler<RegisterSimulatorCommand, SimulatorClientSecretDto>
{
    /// <summary>
    /// 
    /// </summary>
    private readonly ISimulatorClientService _simulatorClientService;

    /// <summary>
    /// 
    /// </summary>
    /// <param name="simulatorClientService"></param>
    public RegisterSimulatorClientCommandHandler(ISimulatorClientService simulatorClientService) {
        _simulatorClientService = simulatorClientService;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="request"></param>
    /// <param name="ct"></param>
    /// <returns></returns>
    public async Task<Result<SimulatorClientSecretDto>> Handle(RegisterSimulatorCommand request, CancellationToken ct)
    {
        var scopes = request.Scopes.Distinct(StringComparer.Ordinal).ToList();
        var created = await _simulatorClientService.RegisterAsync(request.ClientId, request.DisplayName, scopes, ct);
        if (created is null)
            return Error.Conflict(UserAuthErrors.ClientExists, "client_id đã tồn tại.");

        return new SimulatorClientSecretDto
        {
            ClientId = created.ClientId!,
            ClientSecret = created.Secret!,
            Scopes = created.Scopes!,
            Note = "Hãy lưu secret ngay. Hệ thống không hiển thị lại; nếu mất phải rotate secret."
        };
    }
}
