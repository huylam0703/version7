-- Mỗi service có DB riêng nên mỗi DB sẽ có bảng này. Consumer là tên queue, để hai handler trong cùng service không chặn nhau.
IF OBJECT_ID('dbo.ProcessedEvents', 'U') IS NULL
CREATE TABLE dbo.ProcessedEvents (
     EventId     UNIQUEIDENTIFIER NOT NULL,
     Consumer    NVARCHAR(200)    NOT NULL,
     ProcessedAt DATETIME2(3)     NOT NULL CONSTRAINT DF_ProcessedEvents_At DEFAULT SYSUTCDATETIME(),
     CONSTRAINT PK_ProcessedEvents PRIMARY KEY (EventId, Consumer)
);