IF DB_ID('user_auth_db')    IS NULL CREATE DATABASE user_auth_db;
IF DB_ID('farm_device_db')  IS NULL CREATE DATABASE farm_device_db;
IF DB_ID('mission_db')      IS NULL CREATE DATABASE mission_db;
IF DB_ID('sensor_data_db')  IS NULL CREATE DATABASE sensor_data_db;
IF DB_ID('alert_db')        IS NULL CREATE DATABASE alert_db;
IF DB_ID('notification_db') IS NULL CREATE DATABASE notification_db;