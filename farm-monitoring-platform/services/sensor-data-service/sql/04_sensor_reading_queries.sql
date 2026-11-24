USE sensor_data_db;
GO

-- Filtered indexes require these settings on the session running the migration.
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.sensor_readings', N'farm_id') IS NULL
    ALTER TABLE dbo.sensor_readings ADD farm_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.sensor_readings', N'zone_id') IS NULL
    ALTER TABLE dbo.sensor_readings ADD zone_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.sensor_readings', N'mission_id') IS NULL
    ALTER TABLE dbo.sensor_readings ADD mission_id UNIQUEIDENTIFIER NULL;
IF COL_LENGTH(N'dbo.sensor_readings', N'execution_id') IS NULL
    ALTER TABLE dbo.sensor_readings ADD execution_id UNIQUEIDENTIFIER NULL;

GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.sensor_readings') AND name = N'ix_sensor_readings_time')
    CREATE INDEX ix_sensor_readings_time ON dbo.sensor_readings (measured_at DESC, id DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.sensor_readings') AND name = N'ix_sensor_readings_farm')
    CREATE INDEX ix_sensor_readings_farm ON dbo.sensor_readings (farm_id, measured_at DESC, id DESC) WHERE farm_id IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.sensor_readings') AND name = N'ix_sensor_readings_zone')
    CREATE INDEX ix_sensor_readings_zone ON dbo.sensor_readings (zone_id, measured_at DESC, id DESC) WHERE zone_id IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.sensor_readings') AND name = N'ix_sensor_readings_mission')
    CREATE INDEX ix_sensor_readings_mission ON dbo.sensor_readings (mission_id, measured_at DESC, id DESC) WHERE mission_id IS NOT NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.sensor_readings') AND name = N'ix_sensor_readings_execution')
    CREATE INDEX ix_sensor_readings_execution ON dbo.sensor_readings (execution_id, measured_at DESC, id DESC) WHERE execution_id IS NOT NULL;

COMMIT TRANSACTION;
GO
