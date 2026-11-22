using Farm.BuildingBlocks.Abstractions;
using Farm.BuildingBlocks.Application;
using FluentValidation;
using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;

namespace UserAuthService.Application.Queries;
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
