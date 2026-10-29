using System;
using System.Collections.Generic;
using System.IdentityModel.Tokens.Jwt;
using System.Text;

using UserAuthService.Domain.Dtos;
using UserAuthService.Domain.Dtos.UserDto;

namespace UserAuthService.Domain.Interfaces.Services;

public interface IJwtService
{
    /// JWT cho người dùng: claim sub, email, role. Thời hạn lấy từ CoreAppSetting:Jwt:ExpireTime (phút).
    AccessTokenDto CreateAccessToken(UserInfoDto user);

    /// JWT cho service gọi service: role SERVICE.
    AccessTokenDto CreateServiceToken(string clientId);

    /// Khóa công khai để công bố cho gateway và các service Java kiểm tra chữ ký.
    JwkDto GetPublicKey();
}
