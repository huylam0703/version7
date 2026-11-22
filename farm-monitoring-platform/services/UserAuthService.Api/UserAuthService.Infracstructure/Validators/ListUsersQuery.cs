using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;
using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Queries;
    public class ListUsersValidator : AbstractValidator<ListUsersQuery>
    {
        /// <summary>
        ///
        /// </summary>
        public ListUsersValidator()
        {
            RuleFor(x => x.Page).GreaterThanOrEqualTo(1).WithMessage("Page phải từ 1 trở lên.");
            RuleFor(x => x.PageSize).InclusiveBetween(1, 100).WithMessage("PageSize phải từ 1 đến 100.");

            RuleFor(x => x.Role)
                .Must(FarmRole.IsAssignable).WithMessage("Vai trò phải là ADMIN hoặc OPERATOR.")
                .When(x => !string.IsNullOrWhiteSpace(x.Role));

            RuleFor(x => x.Status)
                .Must(UserStatus.IsValid).WithMessage("Trạng thái phải là ACTIVE hoặc LOCKED.")
                .When(x => !string.IsNullOrWhiteSpace(x.Status));
        }
    }
