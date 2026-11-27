USE alert_db;
GO

-- Hibernate creates the table with a nullable node ID on a fresh database.
-- Upgrade databases created before alerts could refer directly to a mission.
IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.dbo_AL_Alerts')
      AND name = N'nodeId' AND is_nullable = 0
)
    ALTER TABLE dbo.dbo_AL_Alerts ALTER COLUMN nodeId UNIQUEIDENTIFIER NULL;
GO

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.dbo_al_alerts')
      AND name = N'node_id' AND is_nullable = 0
)
    ALTER TABLE dbo.dbo_al_alerts ALTER COLUMN node_id UNIQUEIDENTIFIER NULL;
GO
