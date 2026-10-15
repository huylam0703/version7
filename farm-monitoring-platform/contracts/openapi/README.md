# OpenAPI

Mỗi service một file: `user-auth.yaml`, `farm-device.yaml`, `mission.yaml`, `sensor-data.yaml`, `alert.yaml`, `notification.yaml`.

- Người phụ trách service viết/cập nhật file này **trước khi** code endpoint (hoặc xuất từ Swagger rồi commit).
- Nhóm nào cần gọi service khác thì sinh client từ file này (openapi-generator cho Java, NSwag cho .NET), không viết tay lời gọi HTTP.
