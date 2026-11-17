using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Infracstructure.Queries;

public class SimulatorClientQuery
{
    /// <summary>
    /// Lấy thông tin simulator client bằng id
    /// </summary>
    public const string FindByClientId = @"
        SELECT  SC.ClientRowId, 
                SC.ClientId, 
                SC.DisplayName, 
                SC.SecretHash, 
                SC.AllowedScopes, 
                SC.IsActive,
                SC.SecretRotatedAt, 
                SC.RevokedAt, 
                SC.CreatedAt, 
                SC.UpdatedAt
          FROM  UA_SimulatorClients AS SC
         WHERE  SC.ClientId = @pClientId
    ";

    /// <summary>
    /// Lấy tất cả simulator client
    /// </summary>
    public const string ListAll = @"
        SELECT  SC.ClientRowId, 
                SC.ClientId, 
                SC.DisplayName, 
                SC.SecretHash, 
                SC.AllowedScopes, 
                SC.IsActive,
                SC.SecretRotatedAt, 
                SC.RevokedAt, 
                SC.CreatedAt, 
                SC.UpdatedAt
          FROM  UA_SimulatorClients AS SC
         ORDER  BY SC.CreatedAt DESC
    ";

    /// <summary>
    /// Số dòng ảnh hưởng = 0 nghĩa là ClientId đã tồn tại
    /// </summary>
    public const string Insert = @"
        INSERT INTO UA_SimulatorClients (ClientId, DisplayName, SecretHash, AllowedScopes)
        SELECT @pClientId, @pDisplayName, @pSecretHash, @pScopes
         WHERE NOT EXISTS (SELECT 1 FROM UA_SimulatorClients WHERE ClientId = @pClientId)
    ";

    /// <summary>
    /// 
    /// </summary>
    public const string RotateSecret = @"
        UPDATE UA_SimulatorClients
           SET SecretHash = @pSecretHash,
               SecretRotatedAt = SYSUTCDATETIME(),
               UpdatedAt = SYSUTCDATETIME()
         WHERE ClientId = @pClientId
    ";

    /// <summary>
    /// 
    /// </summary>
    public const string SetActive = @"
        UPDATE UA_SimulatorClients
           SET IsActive = @pActive,
               RevokedAt = CASE WHEN @pActive = 1 THEN NULL ELSE SYSUTCDATETIME() END,
               UpdatedAt = SYSUTCDATETIME()
         WHERE ClientId = @pClientId
    ";
}
