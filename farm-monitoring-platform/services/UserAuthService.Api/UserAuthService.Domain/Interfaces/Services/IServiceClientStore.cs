using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Interfaces.Services;

public interface IServiceClientStore
{
    /// <summary>
    /// 
    /// </summary>
    /// <param name="clientId"></param>
    /// <param name="clientSecret"></param>
    /// <returns></returns>
    bool Validate(string clientId, string clientSecret);
}
