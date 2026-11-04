IF DB_ID(N'sensor_data_db') IS NULL
BEGIN
    CREATE DATABASE sensor_data_db;
END;
GO