use user_auth_db;

CREATE TABLE UA_Roles (
  RoleId       UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
  Name         VARCHAR(30)      NOT NULL,
  Description  NVARCHAR(255)    NULL,
  CONSTRAINT PK_UA_Roles PRIMARY KEY (RoleId),
  CONSTRAINT UQ_UA_Roles_Name UNIQUE (Name)
);

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

INSERT INTO UA_Roles (Name, Description) VALUES
  ('ADMIN',    N'Quản trị viên nông trại'),
  ('OPERATOR', N'Nhân viên vận hành UAV và thiết bị');

