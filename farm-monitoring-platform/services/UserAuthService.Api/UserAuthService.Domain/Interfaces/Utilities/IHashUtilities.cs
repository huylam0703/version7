using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Domain.Interfaces.Utilities;

public interface IHashUtilities
{
    /// <summary>
    /// 
    /// </summary>
    /// <param name="password"></param>
    /// <param name="salt"></param>
    /// <returns></returns>
    string HashPassword(string password, byte[] salt);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="password"></param>
    /// <param name="salt"></param>
    /// <param name="hashToCompare"></param>
    /// <returns></returns>
    bool VerifyPassword(string password, byte[] salt, string hashToCompare);

    /// <summary>
    /// 
    /// </summary>
    /// <param name="length"></param>
    /// <returns></returns>
    byte[] GenerateSalt(int length = 16);
}
