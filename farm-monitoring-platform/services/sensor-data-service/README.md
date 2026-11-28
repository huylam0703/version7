# Sensor Data Service

Service Java 21 chạy mặc định trên cổng `8084`, dùng SQL Server và database `sensor_data_db`. Swagger UI nằm ở `/swagger-ui/index.html` và OpenAPI ở `/v3/api-docs`.

## Chuẩn bị SQL Server

Áp dụng [migration 03](../../deploy/sql/03_sensor_reading_metadata.sql) sau khi tạo bảng. Compose chạy các file trong `deploy/sql` khi job `db-init` chạy. Tiếp theo, chạy [migration 04](sql/04_sensor_reading_queries.sql) bằng SQL client có quyền sửa schema trước khi khởi động phiên bản SCRUM-80. Migration 04 nằm trong thư mục của Sensor Data Service và cần chạy riêng, trên một kết nối SQL, kể cả với database cài qua Compose. Có thể chạy lại các migration. Bản ghi cũ giữ giá trị và nhận `null` ở các cột mới.

```powershell
mvn -f services/sensor-data-service/pom.xml verify
mvn -f services/sensor-data-service/pom.xml spring-boot:run
```

## Xác thực và phân quyền

Các API đọc và ghi trên cả hai phiên bản yêu cầu header `Authorization: Bearer <token>` từ User Auth. Thiếu token, token sai hoặc hết hạn trả HTTP `401`. Token hợp lệ nhưng thiếu quyền trả HTTP `403`.

| Danh tính | Đọc bản đo và thống kê | Ghi đơn và ghi batch |
| --- | --- | --- |
| `ADMIN` | Có | Có |
| `OPERATOR` | Có | — |
| Simulator hoặc tài khoản `SERVICE` có mã client trong danh sách cho phép | — | Có |

Sensor Data kiểm tra chữ ký bằng JWKS, issuer, audience và hạn JWT. Cấu hình mặc định là issuer `farm-auth`, audience `farm-api` và JWKS tại `http://localhost:8081/.well-known/jwks.json`. Đặt `AUTH_JWKS_URI` theo địa chỉ User Auth mà tiến trình Sensor Data truy cập được. Trong mạng Docker Compose của dự án, địa chỉ nội bộ là `http://user-auth-service:8080/.well-known/jwks.json`. Dùng `AUTH_ISSUER` và `AUTH_AUDIENCE` nếu môi trường triển khai có giá trị riêng.

`SENSOR_WRITER_CLIENT_IDS` nhận danh sách mã client phân cách bằng dấu phẩy, mặc định là `uav-simulator-01`. Token Simulator phải có `client_id` khớp với `sub`. Token `SERVICE` được đối chiếu theo `sub`. Các bộ lọc `farmId` và `zoneId` dùng để lọc kết quả. Quyền đọc hiện xét theo vai trò.

Trong Swagger UI, chọn **Authorize** và nhập access token. Luồng gửi batch trực tiếp từ Simulator yêu cầu kèm token của client có quyền ghi.

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

## REST API v1 (SCRUM-80)

| Method | Route | Kết quả |
| --- | --- | --- |
| POST | `/api/v1/readings` | Ghi một bản đo, cùng quy tắc trạng thái như API ESP32. |
| POST | `/api/v1/readings/batch`, `/api/v1/sync-batches` | Ghi 1–500 bản đo. Trả HTTP 200 với `total`, `validCount`, `invalidCount`, `duplicateCount` và `items` theo thứ tự gửi, `index` bắt đầu từ 0. |
| GET | `/api/v1/readings` | Danh sách phân trang. |
| GET | `/api/v1/readings/{id}` | Chi tiết theo ID số của bản đo, trả 404 nếu không tìm thấy. |
| GET | `/api/v1/readings/nodes/{nodeId}` | Lịch sử của một node, có phân trang. |
| GET | `/api/v1/readings/latest` | Một bản ghi cho mỗi cặp node và loại cảm biến sau khi áp dụng filter. Bản ghi mới nhất được chọn theo `measuredAt`, rồi theo `id`. |
| GET | `/api/v1/readings/statistics` | `min`, `max`, `avg`, `count` theo loại, đơn vị, cách biểu diễn và độ phân giải ADC. |
| GET | `/api/v1/readings/time-series` | Số liệu thống kê theo bucket UTC có dữ liệu và theo các nhóm như trên. |
| GET | `/api/v1/readings/invalid` | Danh sách bản đo `INVALID`, có phân trang. |
| GET | `/api/v1/readings/quality` | Tổng số, số hợp lệ, số không hợp lệ, số thiếu đơn vị/cách biểu diễn và tỷ lệ không hợp lệ theo %. |

Ghi v1 nhận các trường của sensor reading như trên, với timestamp ISO 8601 UTC (ví dụ `2026-11-24T02:30:00Z`). `farmId`, `zoneId`, `missionId` và `executionId` là UUID tùy chọn trên từng bản đo. Chúng biểu thị ngữ cảnh lúc thu thập. Service lưu và lọc chính xác theo UUID đã nhận. Dữ liệu lịch sử và bản đo thiếu ngữ cảnh chỉ xuất hiện khi truy vấn phù hợp với các trường hiện có. Quy trình xác nhận liên kết với service sở hữu và contract triển khai của Simulator cần được thống nhất với nhóm.

Batch nhận object `{"readings": [...]}`, trong đó mỗi phần tử có cấu trúc giống POST v1. Backend kiểm tra cấu trúc toàn bộ request trước khi ghi. Các bản đo được ghi theo từng giao dịch để phân biệt `VALID`, `INVALID`, `DUPLICATE`. Nếu request gián đoạn, gửi lại cùng `nodeId`, `sensorType` và `measuredAt` để các dòng đã ghi được nhận diện là trùng. Bản đo trùng giữ dữ liệu và UUID của lần ghi đầu.

Danh sách, Latest và Invalid trả `{ "items": [], "page": 1, "pageSize": 20, "total": 0 }`. Tham số gồm `page` bắt đầu từ 1, `pageSize` từ 1 đến 100, `sortBy` mặc định `measuredAt`, và `sortDirection` là `asc` hoặc `desc`. Các cột sắp xếp được hỗ trợ là `id`, `nodeId`, `sensorType`, `sensorValue`, `measuredAt`, `collectedAt` và `receivedAt`.

Filter dùng chung gồm `nodeId`, `sensorType`, `farmId`, `zoneId`, `missionId`, `executionId`, `validationStatus`, `valueKind`, `unit`, và khoảng thời gian `[from, to)` tính theo `measuredAt`. Tên loại và alias cũ được chuẩn hóa khi đọc v1. Statistics và Time-Series mặc định dùng bản đo `VALID`. Time-Series yêu cầu cả `from` lẫn `to`, nhận `interval=MINUTE|HOUR|DAY` và giới hạn 1.000 bucket, 10.000 điểm. Dashboard có thể lọc `valueKind` để chọn dữ liệu vật lý, đã chuyển đổi hoặc ADC cho từng biểu đồ.

Lỗi v1 trả `application/problem+json` với HTTP status, `code`, `traceId` và thông tin lỗi theo trường khi có. Swagger UI và `/v3/api-docs` mô tả cả hai phiên bản.

### Kiểm thử Postman

Import [collection SCRUM-80](postman/scrum-80.postman_collection.json) và đặt `baseUrl` trỏ tới Sensor Data Service trên database kiểm thử. Điền access token của `ADMIN` vào biến `sensorAdminToken` để chạy cả request đọc và ghi. Collection tự tạo node và UUID theo lần chạy, có cả request đọc và ghi dữ liệu. Có thể chạy từ thư mục dự án bằng Newman.

```powershell
npx.cmd --yes --package=newman@6.2.1 newman run services/sensor-data-service/postman/scrum-80.postman_collection.json --env-var baseUrl=http://localhost:8084 --env-var "sensorAdminToken=$env:SENSOR_ADMIN_TOKEN"
```
