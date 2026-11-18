using System;
using System.Collections.Generic;
using System.IdentityModel.Tokens.Jwt;
using System.Text;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.UserDto;

namespace UserAuthService.Domain.Interfaces.Services;

public interface IJwtService
{
    /// <summary>
    /// JWT cho người dùng: claim sub, email, role. Thời hạn lấy từ CoreAppSetting:Jwt:ExpireTime (phút).
    /// </summary>
    /// <param name="user"></param>
    /// <returns></returns>
    AccessTokenDto CreateAccessToken(UserInfoDto user);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="scopes"></param>
    /// <returns></returns>
    AccessTokenDto CreateClientToken(string clientId, IReadOnlyCollection<string> scopes);

    /// <summary>
    /// JWT cho service gọi service: role SERVICE.
    /// </summary>
    /// <param name="clientId"></param>
    /// <returns></returns>
    AccessTokenDto CreateServiceToken(string clientId);

    /// <summary>
    /// Khóa công khai để công bố cho gateway và các service Java kiểm tra chữ ký.
    /// </summary>
    /// <returns></returns>
    JwkDto GetPublicKey();
}
