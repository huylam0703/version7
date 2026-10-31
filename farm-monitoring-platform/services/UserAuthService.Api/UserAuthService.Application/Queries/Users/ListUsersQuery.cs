using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;

using FluentValidation;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Queries
{
    /// <summary>
    /// 
    /// </summary>
    public class ListUsersQuery : IQuery<PagedResult<GetUserDto>>
    {
        /// <summary>
        /// 
        /// </summary>
        public string? SearchString { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public string? Role { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public string? Status { get; set; }
        /// <summary>
        /// 
        /// </summary>
        public int Page { get; set; } = 1;
        /// <summary>
        /// 
        /// </summary>
        public int PageSize { get; set; } = 20;
    }

    /// <summary>
    /// 
    /// </summary>
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

    /// <summary>
    /// 
    /// </summary>
    public class ListUsersQueryHandler : IQueryHandler<ListUsersQuery, PagedResult<GetUserDto>>
    {
        /// <summary>
        /// 
        /// </summary>
        private readonly IUserService _userService;

        /// <summary>
        /// 
        /// </summary>
        /// <param name="users"></param>
        public ListUsersQueryHandler(IUserService users)
        {
            _userService = users;
        }

        /// <summary>
        /// 
        /// </summary>
        /// <param name="request"></param>
        /// <param name="cancellationToken"></param>
        /// <returns></returns>
        public async Task<Result<PagedResult<GetUserDto>>> Handle(ListUsersQuery request, CancellationToken cancellationToken)
        {
            var filter = new UserListFilter(request.SearchString, request.Role, request.Status, request.Page, request.PageSize);
            return await _userService.ListUsersAsync(filter);
        }
    }
}
