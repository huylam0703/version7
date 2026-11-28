-- Thêm thông tin kết nối cho FD_SensorNodes. Cột NULL nên node cũ giữ nguyên dữ liệu và vẫn hoạt động.
-- Có điều kiện IF để chạy lại an toàn và để DB mới (chưa có bảng, Hibernate sẽ tự tạo) không bị lỗi.
IF OBJECT_ID(N'dbo.FD_SensorNodes', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH(N'dbo.FD_SensorNodes', N'ConnectionType') IS NULL
        ALTER TABLE dbo.FD_SensorNodes ADD ConnectionType varchar(10) NULL;

    IF COL_LENGTH(N'dbo.FD_SensorNodes', N'EndpointUrl') IS NULL
        ALTER TABLE dbo.FD_SensorNodes ADD EndpointUrl varchar(255) NULL;

    IF COL_LENGTH(N'dbo.FD_SensorNodes', N'LastSeenAt') IS NULL
        ALTER TABLE dbo.FD_SensorNodes ADD LastSeenAt datetime2(0) NULL;
END
