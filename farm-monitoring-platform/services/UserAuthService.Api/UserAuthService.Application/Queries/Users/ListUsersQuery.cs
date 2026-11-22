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


    /// <summary>
    /// 
    /// </summary>

}
