IF OBJECT_ID(N'dbo.reading_alert_outbox', N'U') IS NULL
    CREATE TABLE dbo.reading_alert_outbox (
        reading_id BIGINT NOT NULL PRIMARY KEY,
        event_json NVARCHAR(MAX) NOT NULL
    );
