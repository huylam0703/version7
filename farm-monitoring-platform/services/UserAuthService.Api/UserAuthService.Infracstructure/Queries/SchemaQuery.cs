using System;
using System.Collections.Generic;
using System.Text;

namespace UserAuthService.Infracstructure.Queries;

public static class SchemaQuery
{
    /// <summary>
    /// Tạo mới 3 bảng nếu chưa được tạo
    /// </summary>
    public const string CreateTables = @"
        IF OBJECT_ID(N'UA_Roles', N'U') IS NULL
        BEGIN
            CREATE TABLE UA_Roles (
              RoleId       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
              Name         VARCHAR(30)      NOT NULL,
              Description  NVARCHAR(255)    NULL,
              CONSTRAINT PK_UA_Roles PRIMARY KEY (RoleId),
              CONSTRAINT UQ_UA_Roles_Name UNIQUE (Name)
            );

        END;

        IF OBJECT_ID(N'UA_Users', N'U') IS NULL
        BEGIN
            CREATE TABLE UA_Users (
                UserId        UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
                RoleId        UNIQUEIDENTIFIER NOT NULL,
                FullName      NVARCHAR(100)    NOT NULL,
                Email         VARCHAR(150)     NOT NULL,
                HashSalt      VARCHAR(255)     NOT NULL,
                PasswordHash  VARCHAR(255)     NOT NULL,
                Phone         VARCHAR(20)      NULL,
                Status        VARCHAR(10)      NOT NULL DEFAULT 'ACTIVE',
                LastLoginAt   DATETIME2(0)     NULL,
                CreatedAt     DATETIME2(0)     NOT NULL DEFAULT SYSUTCDATETIME(),
                UpdatedAt     DATETIME2(0)     NOT NULL DEFAULT SYSUTCDATETIME(),
                CONSTRAINT PK_UA_Users PRIMARY KEY (UserId),
                CONSTRAINT UQ_UA_Users_Email UNIQUE (Email),
                CONSTRAINT FK_UA_Users_Roles FOREIGN KEY (RoleId) REFERENCES UA_Roles(RoleId),
                CONSTRAINT CK_UA_Users_Status CHECK (Status IN ('ACTIVE','LOCKED'))
            );

        END;

        IF OBJECT_ID(N'UA_RefreshTokens', N'U') IS NULL
        BEGIN
            CREATE TABLE UA_RefreshTokens (
                TokenId     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
                UserId      UNIQUEIDENTIFIER NOT NULL,
                TokenHash   VARCHAR(64)      NOT NULL,  
                ExpiresAt   DATETIME2(0)     NOT NULL,
                CreatedAt   DATETIME2(0)     NOT NULL DEFAULT SYSUTCDATETIME(),
                RevokedAt   DATETIME2(0)     NULL,      
                CONSTRAINT PK_UA_RefreshTokens PRIMARY KEY (TokenId),
                CONSTRAINT UQ_UA_RefreshTokens_TokenHash UNIQUE (TokenHash),
                CONSTRAINT FK_UA_RefreshTokens_Users FOREIGN KEY (UserId) REFERENCES UA_Users(UserId)
            );
            CREATE INDEX IX_UA_RefreshTokens_UserId ON UA_RefreshTokens (UserId, RevokedAt)

        END;
    ";

    /// <summary>
    /// Thêm role
    /// </summary>
    public const string SeedRole = @"
        IF NOT EXISTS (SELECT 1 FROM UA_Roles WHERE Name = @pName)
            INSERT INTO UA_Roles (RoleId, Name, Description) VALUES (@pRoleId, @pName, @pDescription);";
}
