USE sensor_data_db;
GO

SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.sensor_readings', N'unit') IS NULL
    ALTER TABLE dbo.sensor_readings ADD unit NVARCHAR(20) NULL;

IF COL_LENGTH(N'dbo.sensor_readings', N'value_kind') IS NULL
    ALTER TABLE dbo.sensor_readings ADD value_kind VARCHAR(20) NULL;

IF COL_LENGTH(N'dbo.sensor_readings', N'adc_resolution_bits') IS NULL
    ALTER TABLE dbo.sensor_readings ADD adc_resolution_bits INT NULL;

COMMIT TRANSACTION;
GO
