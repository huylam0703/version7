USE sensor_data_db;
GO

-- Danh sách bản ghi dữ liệu cảm biến
IF OBJECT_ID(N'dbo.sensor_readings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.sensor_readings (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        node_id NVARCHAR(100) NOT NULL,
        sensor_type NVARCHAR(50) NOT NULL,
        sensor_value FLOAT NOT NULL,
        measured_at DATETIME2(3) NOT NULL,
        collected_at DATETIME2(3) NOT NULL,
        latitude FLOAT NOT NULL,
        longitude FLOAT NOT NULL,
        received_at DATETIME2(3) NOT NULL
            DEFAULT SYSUTCDATETIME(),
        validation_status NVARCHAR(20) NOT NULL
            DEFAULT 'VALID',

        CONSTRAINT uq_sensor_reading
            UNIQUE (node_id, sensor_type, measured_at)
    );
END;
GO

-- Lưu thông tin các đợt đồng bộ
IF OBJECT_ID(N'dbo.sync_batches', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.sync_batches (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        gateway_id NVARCHAR(100) NOT NULL,
        batch_id NVARCHAR(100) NOT NULL,
        received_at DATETIME2(3) NOT NULL
            DEFAULT SYSUTCDATETIME(),
        total_records INT NOT NULL DEFAULT 0,
        accepted_records INT NOT NULL DEFAULT 0,
        rejected_records INT NOT NULL DEFAULT 0,

        CONSTRAINT uq_sync_batch
            UNIQUE (gateway_id, batch_id)
    );
END;
GO
