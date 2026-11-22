using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;
using UserAuthService.Application;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

public class RefreshTokenValidator : AbstractValidator<RefreshTokenCommand>
{
    /// <summary>
    ///
    /// </summary>
    public RefreshTokenValidator()
    {
        RuleFor(x => x.RefreshToken).NotEmpty().WithMessage("Refresh token không được để trống.");
    }
}
