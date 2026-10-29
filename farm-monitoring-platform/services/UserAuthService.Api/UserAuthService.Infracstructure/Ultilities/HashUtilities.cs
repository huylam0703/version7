using System;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Text;

using Konscious.Security.Cryptography;

using UserAuthService.Domain.Interfaces.Utilities;

namespace UserAuthService.Infracstructure.Ultilities;

public class HashUtilities : IHashUtilities
{
    /// <summary>
    /// Mã hóa password
    /// </summary>
    /// <param name="password"></param>
    /// <param name="salt"></param>
    /// <returns></returns>
    public string HashPassword(string password, byte[] salt)
    {
        var argon2 = new Argon2id(Encoding.UTF8.GetBytes(password))
        {
            DegreeOfParallelism = 4, // số luồng CPU
            Iterations = 4,          // số vòng lặp
            MemorySize = 1 << 16     // 65.536 KB = 64 MB RAM
        };

        argon2.Salt = salt;

        byte[] hash = argon2.GetBytes(32); // lấy hash 32 byte
        return Convert.ToBase64String(hash);
    }

    /// <summary>
    /// Kiểm tra password
    /// </summary>
    /// <param name="password"></param>
    /// <param name="salt"></param>
    /// <param name="hashToCompare"></param>
    /// <returns></returns>
    public bool VerifyPassword(string password, byte[] salt, string hashToCompare)
    {
        string hashedInput = HashPassword(password, salt);
        return CryptographicOperations.FixedTimeEquals(
            Convert.FromBase64String(hashedInput),
            Convert.FromBase64String(hashToCompare)
        );
    }

    /// <summary>
    /// Sinh mã Salt ngẫu nhiên
    /// </summary>
    /// <param name="length"></param>
    /// <returns></returns>
    public byte[] GenerateSalt(int length = 16)
    {
        using var rng = RandomNumberGenerator.Create();
        byte[] salt = new byte[length];
        rng.GetBytes(salt);
        return salt;
    }

}
