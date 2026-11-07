IF DB_ID(N'user_auth_db') IS NULL
    CREATE DATABASE [user_auth_db];
GO

IF DB_ID(N'farm_device_db') IS NULL
    CREATE DATABASE [farm_device_db];
GO

IF DB_ID(N'mission_db') IS NULL
    CREATE DATABASE [mission_db];
GO

IF DB_ID(N'sensor_data_db') IS NULL
    CREATE DATABASE [sensor_data_db];
GO

IF DB_ID(N'alert_db') IS NULL
    CREATE DATABASE [alert_db];
GO

IF DB_ID(N'notification_db') IS NULL
    CREATE DATABASE [notification_db];
GO