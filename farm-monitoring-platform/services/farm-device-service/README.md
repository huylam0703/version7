# farm-device

- Ngôn ngữ: Java (Spring Boot)
- Port local: 8082
- Database: farm_device_db
- Người phụ trách: Dũng
- OpenAPI: `contracts/openapi/farm-device.v1.yaml` (dev: `/openapi.json`, `/swagger`)

## Chạy local (Windows, PowerShell)

1. SQL Server và các database: `cd deploy; docker compose up -d sqlserver db-init`
2. RabbitMQ: `docker run -d --name rabbitmq --restart unless-stopped -p 5672:5672 -p 15672:15672 rabbitmq:3-management`
3. Biến môi trường (nếu SQL Server Docker không ở cổng 1433, ví dụ đổi sang 1434):
   `$env:DB_USER='sa'; $env:DB_PASSWORD='<SA_PASSWORD trong deploy/.env>'; $env:DB_PORT='1434'`
4. `mvn spring-boot:run` trong thư mục service, rồi thử `GET http://localhost:8082/api/v1/sensor-types`.

Test: `mvn test`. Ghi chú các điểm còn mở: `NOTES.md`.