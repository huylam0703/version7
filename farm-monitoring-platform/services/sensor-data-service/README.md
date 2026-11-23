# Sensor Data Service

Service Java 21 chạy mặc định trên cổng `8084`, dùng SQL Server và database `sensor_data_db`. Swagger UI nằm ở `/swagger-ui/index.html` và OpenAPI ở `/v3/api-docs`.

## Chuẩn bị SQL Server

Áp dụng [migration 03](../../deploy/sql/03_sensor_reading_metadata.sql) sau khi tạo bảng và trước khi chạy phiên bản service mới. Compose chạy tuần tự các file trong `deploy/sql` khi job `db-init` chạy. Với database đã có sẵn, chạy riêng migration bằng SQL client có quyền sửa schema. Có thể chạy lại migration. Các bản ghi hiện có giữ nguyên giá trị và nhận `null` ở các cột mới.

```powershell
mvn -f services/sensor-data-service/pom.xml verify
mvn -f services/sensor-data-service/pom.xml spring-boot:run
```

## Gửi bản đo

Dùng `POST /api/sensor-readings`. Firmware gửi một bản đo trên mỗi request với `nodeId` cố định theo thiết bị. Giữ nguyên `measuredAt` khi gửi lại. Hai timestamp dùng chuỗi ISO local date-time như trước. Hai thiết bị cần thống nhất dùng giờ UTC trong chuỗi gửi lên. Service làm tròn tới mili giây để khớp SQL Server.

| `sensorType` | Đơn vị vật lý | Dải chấp nhận hiện tại |
| --- | --- | --- |
| `AIR_TEMP` | `°C` | −40 đến 85 |
| `AIR_HUMIDITY` | `%` | 0 đến 100 |
| `LIGHT` | `lux` | 0 đến 100.000 |
| `SOIL_MOISTURE` | `%` | 0 đến 100 |
| `WATER_LEVEL` | `cm` | 0 đến 500 |
| `PH` | `pH` | 0 đến 14 |

Dải nhiệt độ giữ tương thích với `TEMPERATURE`. Dải ánh sáng và mực nước lấy theo giá trị đề xuất trong Farm Device Service. Xác nhận dải và hiệu chuẩn theo phần cứng trước khi đưa vào hiện trường. Khi độ ẩm đất được chuyển thành phần trăm, firmware cần thống nhất ý nghĩa của thang đo. Với mực nước tính theo cm, firmware cần biết mốc đo và đường hiệu chuẩn.

- `PHYSICAL` dùng cho số đo đã có đơn vị vật lý. `CONVERTED` dùng khi firmware đã chuyển đổi sang đơn vị trong bảng. Cả hai gửi `unit` và bỏ `adcResolutionBits`.
- `ADC_RAW` dùng với `unit: "ADC_COUNT"` và `adcResolutionBits` đúng theo độ phân giải đầu ra của firmware. Service chấp nhận số đếm nguyên từ 0 đến `2^n - 1`, với `n` là số bit trong khoảng 1–31. `VALID` ở chế độ này xác nhận số đếm hợp lệ. Service giữ nguyên số đếm ADC.
- Mã `TEMPERATURE`, `HUMIDITY` và `PH` vẫn nhận payload cũ thiếu metadata. Bản ghi `SOIL_MOISTURE` mới thiếu metadata nhận `INVALID`. Các mã đã biết được lưu bằng mã chuẩn trong bảng, còn mã trong lịch sử giữ theo giá trị đã lưu.

Ví dụ từ ESP32 thứ nhất khi gửi nhiệt độ

```json
{
  "nodeId": "esp32-a",
  "sensorType": "AIR_TEMP",
  "sensorValue": 28.4,
  "unit": "°C",
  "valueKind": "PHYSICAL",
  "measuredAt": "2026-11-23T02:30:00.123",
  "collectedAt": "2026-11-23T02:30:01.000",
  "latitude": 10.03,
  "longitude": 105.78
}
```

Ví dụ từ ESP32 thứ hai khi gửi số đếm ADC của cảm biến mực nước

```json
{
  "nodeId": "esp32-b",
  "sensorType": "WATER_LEVEL",
  "sensorValue": 981,
  "unit": "ADC_COUNT",
  "valueKind": "ADC_RAW",
  "adcResolutionBits": 12,
  "measuredAt": "2026-11-23T02:30:00.123",
  "collectedAt": "2026-11-23T02:30:01.000",
  "latitude": 10.03,
  "longitude": 105.78
}
```

## Phản hồi API

- `201 VALID` hoặc `201 INVALID` có `id` khi bản ghi được lưu. Client kiểm tra trường `status` trước khi sử dụng số đo.
- `200 DUPLICATE` khi đã có cùng `nodeId`, mã cảm biến tương đương và `measuredAt` tới mili giây. Gửi lại payload không cập nhật bản ghi trước.
- `400` khi request sai định dạng, thiếu trường bắt buộc, vượt giới hạn cấu trúc hoặc gửi `sensorValue` không hữu hạn.
- `GET /api/sensor-readings?limit=100` trả mảng như trước, gồm cả `INVALID`. Đọc `valueKind` và `unit` trước khi dùng một giá trị trong biểu đồ vật lý. Metadata có thể là `null` với bản ghi cũ hoặc payload tương thích.
