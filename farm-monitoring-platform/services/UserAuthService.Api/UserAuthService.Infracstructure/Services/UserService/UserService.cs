using System;
using System.Collections.Generic;
using System.Data;
using System.Text;

using Dapper;

using Farm.BuildingBlocks.Abstractions;

using Microsoft.Extensions.Options;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.Mapper;
using UserAuthService.Domain.Dtos.UserDto;
using UserAuthService.Domain.Entity;
using UserAuthService.Domain.Interfaces.Services;
using UserAuthService.Domain.Interfaces.Utilities;
using UserAuthService.Domain.Settings;
using UserAuthService.Infracstructure.Database;
using UserAuthService.Infracstructure.Queries;
using UserAuthService.Infracstructure.Ultilities;

namespace UserAuthService.Infracstructure.Services.UserService;

public class UserService : IUserService
{
    /// <summary>
    /// 
    /// </summary>
    private readonly IOptions<CoreAppSetting> _options;
    /// <summary>
    /// 
    /// </summary>
    private readonly IQuery _query;
    /// <summary>
    /// 
    /// </summary>
    private readonly IHashUtilities _hash;

    /// <summary>
    /// Constructor
    /// </summary>
    /// <param name="option"></param>
    /// <param name="query"></param>
    /// <param name="hash"></param>
    public UserService(IOptions<CoreAppSetting> option, IQuery query, IHashUtilities hash)
    {
        _options = option;
        _query = query;
        _hash = hash;
    }

    /// <summary>
    /// 
    /// </summary>
    private string ConnectionString => _options!.Value!.ConnectionStringSetting!.UserAuthService!;

    ///--------------------------------------
    #region query
    /// <summary>
    /// 
    /// </summary>
    /// <param name="email"></param>
    /// <returns></returns>
    public async Task<UserInfoDto?> GetByEmailAsync(string email)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pEmail", email, DbType.AnsiString, ParameterDirection.Input, 150);

        var rows = await _query.QueryAsync<UserInfoDto>(ConnectionString, UserQuery.GetByEmail, parameters);
        return rows.FirstOrDefault();
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <returns></returns>
    public async Task<UserInfoDto?> GetByIdAsync(Guid userId)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);

        var rows = await _query.QueryAsync<UserInfoDto>(ConnectionString, UserQuery.GetById, parameters);
        return rows.FirstOrDefault();
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="email"></param>
    /// <returns></returns>
    public async Task<bool> EmailExistsAsync(string email)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pEmail", email, DbType.AnsiString, ParameterDirection.Input, 150);

        var rows = await _query.QueryAsync<int>(ConnectionString, UserQuery.EmailExists, parameters);
        return rows.FirstOrDefault() == 1;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="email"></param>
    /// <returns></returns>
    public async Task<bool> PhoneExistsAsync(string phone)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pPhone", phone, DbType.AnsiString, ParameterDirection.Input, 150);

        var rows = await _query.QueryAsync<int>(ConnectionString, UserQuery.PhoneExists, parameters);
        return rows.FirstOrDefault() == 1;
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="roleName"></param>
    /// <returns></returns>
    public async Task<Role?> GetRoleByNameAsync(string roleName)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pName", roleName, DbType.AnsiString, ParameterDirection.Input, 30);

        var rows = await _query.QueryAsync<Role>(ConnectionString, UserQuery.GetRoleByName, parameters);
        return rows.FirstOrDefault();
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="filter"></param>
    /// <returns></returns>
    public async Task<PagedResult<GetUserDto>> ListUsersAsync(UserListFilter filter)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pKeyword", string.IsNullOrWhiteSpace(filter.Keyword) ? null : filter.Keyword.Trim(), DbType.String, ParameterDirection.Input, 100);
        parameters.Add("@pRole", string.IsNullOrWhiteSpace(filter.Role) ? null : filter.Role.Trim().ToUpperInvariant(), DbType.AnsiString, ParameterDirection.Input, 30);
        parameters.Add("@pStatus", string.IsNullOrWhiteSpace(filter.Status) ? null : filter.Status.Trim().ToUpperInvariant(), DbType.AnsiString, ParameterDirection.Input, 10);
        parameters.Add("@pOffset", (filter.Page - 1) * filter.PageSize, DbType.Int32, ParameterDirection.Input);
        parameters.Add("@pPageSize", filter.PageSize, DbType.Int32, ParameterDirection.Input);

        var (counts, rows) = await _query.QueryMultipleAsync<long, UserInfoDto>(ConnectionString, UserQuery.ListUsers, parameters);

        var items = rows.Select(r => UserMapper.ToDto(r)).ToList();
        return new PagedResult<GetUserDto>(items, filter.Page, filter.PageSize, counts.FirstOrDefault());
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="password"></param>
    /// <param name="saltBase64"></param>
    /// <returns></returns>
    public async Task<bool> PasswordCorrect(string password, Guid userId)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);
        var saltBase64 = (await _query.QueryAsync<string>(ConnectionString, UserQuery.GetHashSalt, parameters)).FirstOrDefault();

        var hashSalt = Convert.FromBase64String(saltBase64!);
        var passwordHash = _hash.HashPassword(password, hashSalt);

        parameters.Add("@pPasswordHash", passwordHash, DbType.String, ParameterDirection.Input);
        var userInfo = await _query.QueryAsync<int>(ConnectionString, UserQuery.PasswordCheck, parameters);

        return userInfo.FirstOrDefault() == 1;
    }
    #endregion
    ///--------------------------------------

    ///--------------------------------------
    #region Command
    /// <summary>
    /// 
    /// </summary>
    /// <param name="user"></param>
    /// <returns></returns>
    public async Task CreateUserAsync(User user)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", user.UserId, DbType.Guid, ParameterDirection.Input);
        parameters.Add("@pRoleId", user.RoleId, DbType.Guid, ParameterDirection.Input);
        parameters.Add("@pFullName", user.FullName, DbType.String, ParameterDirection.Input, 100);
        parameters.Add("@pEmail", user.Email, DbType.AnsiString, ParameterDirection.Input, 150);
        parameters.Add("@pPasswordHash", user.PasswordHash, DbType.AnsiString, ParameterDirection.Input, 255);
        parameters.Add("@pHashSalt", user.HaslSalt, DbType.AnsiString, ParameterDirection.Input, 255);
        parameters.Add("@pPhone", user.Phone, DbType.AnsiString, ParameterDirection.Input, 20);
        parameters.Add("@pStatus", user.Status, DbType.AnsiString, ParameterDirection.Input, 10);
        parameters.Add("@pCreatedAt", user.CreatedAt, DbType.DateTime2, ParameterDirection.Input);
        parameters.Add("@pUpdatedAt", user.UpdatedAt, DbType.DateTime2, ParameterDirection.Input);

        await _query.ExecuteAsync(ConnectionString, UserCommand.InsertUser, parameters);
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <param name="fullName"></param>
    /// <param name="phone"></param>
    /// <param name="roleId"></param>
    /// <returns></returns>
    public async Task UpdateAsync(Guid userId, string fullName, string? phone, Guid roleId)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);
        parameters.Add("@pFullName", fullName, DbType.String, ParameterDirection.Input, 100);
        parameters.Add("@pPhone", string.IsNullOrWhiteSpace(phone) ? null : phone.Trim(), DbType.AnsiString, ParameterDirection.Input, 20);
        parameters.Add("@pRoleId", roleId, DbType.Guid, ParameterDirection.Input);

        await _query.ExecuteAsync(ConnectionString, UserCommand.UpdateUser, parameters);
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <param name="status"></param>
    /// <returns></returns>
    public async Task SetStatusAsync(Guid userId, string status)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);
        parameters.Add("@pStatus", status, DbType.AnsiString, ParameterDirection.Input, 10);

        await _query.ExecuteAsync(ConnectionString, UserCommand.SetStatus, parameters);
    }

    /// <summary>
    /// 
    /// </summary>
    /// <param name="userId"></param>
    /// <returns></returns>
    public async Task RecordLoginAsync(Guid userId)
    {
        var parameters = new DynamicParameters();
        parameters.Add("@pUserId", userId, DbType.Guid, ParameterDirection.Input);

        await _query.ExecuteAsync(ConnectionString, UserCommand.RecordLogin, parameters);
    }

    #endregion
    ///--------------------------------------
}
