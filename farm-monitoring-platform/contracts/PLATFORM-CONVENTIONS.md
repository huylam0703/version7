# Hợp đồng chung giữa các service

Áp dụng cho mọi service, bất kể ngôn ngữ. Muốn đổi: sửa file này (và schema/OpenAPI liên quan) trong một Pull Request
có người duyệt từ cả nhóm .NET lẫn nhóm Java, rồi mới sửa code.

## 1. Quy ước chung

- Giao tiếp: HTTP + JSON, UTF-8.
- Tên field JSON: **camelCase**.
- Id: **chuỗi UUID chuẩn, chữ thường** (ví dụ `3fa85f64-5717-4562-b3fc-2c963f66afa6`).
- Thời gian: **ISO 8601, UTC, có `Z`**, ví dụ `2026-10-13T08:30:00Z`.
- Số thực (tọa độ, giá trị đo): số JSON, không dùng chuỗi.
- Enum: chuỗi **VIẾT HOA** (`"ACTIVE"`).
- URL công khai: `/api/v1/<tài-nguyên-số-nhiều>` (ví dụ `/api/v1/farms`, `/api/v1/missions/{id}`).
- URL nội bộ giữa service: `/internal/v1/...`. Gateway chặn, không bao giờ route ra ngoài.
- Mọi service có `GET /health/live` (còn sống) và `GET /health/ready` (sẵn sàng nhận request).
- Ở môi trường dev, mọi service có `/swagger` hoặc `/openapi.json`.

## 2. Xác thực (JWT)

- Thuật toán: **RS256**. Chỉ `user-auth` giữ khóa bí mật để ký.
- `user-auth` công khai khóa tại `/.well-known/jwks.json`. Gateway và mọi service tự xác thực qua JWKS, không chia sẻ secret.
- `iss` = `farm-auth`, `aud` = `farm-api`.
- Access token sống ngắn (mặc định 30 phút, cấu hình `JWT_ACCESS_MINUTES` của `user-auth`).
- Claims bắt buộc:

| Claim | Ý nghĩa |
|---|---|
| `sub` | `UserId` (UUID) |
| `role` | `ADMIN` hoặc `OPERATOR` (khớp `dbo_UA_Roles.Name`); token hệ thống dùng `SERVICE` |
| `email` | Email người dùng (token hệ thống có thể không có) |
| `jti` | Id của token |
| `iat`, `exp` | Thời điểm cấp và hết hạn |

- Header gửi token: `Authorization: Bearer <token>`.
- Gateway kiểm tra token, **nhưng mỗi service vẫn phải tự kiểm tra lại** (phòng khi bị gọi thẳng).

## 3. Gọi giữa các service

- Gọi REST vào đường `/internal/v1/...`, kèm **service token** (JWT có `role` = `SERVICE`) do `user-auth` cấp qua client-credentials.
- Biến môi trường để lấy token: `AUTH_TOKEN_URI`, `SERVICE_CLIENT_ID`, `SERVICE_CLIENT_SECRET`.
- **Chưa chốt:** đường dẫn và định dạng response của endpoint cấp token (đề xuất: `POST /internal/v1/auth/token`, nhận
  `grant_type=client_credentials`, `client_id`, `client_secret`, trả `access_token` và `expires_in` theo OAuth2).
  Chốt trước khi làm `user-auth`.

## 4. Dạng response

- Thành công: **trả thẳng resource**, không bọc envelope.
- Danh sách có phân trang: `{ "items": [], "page": 1, "pageSize": 20, "total": 135 }`.
  Query: `?page=1&pageSize=20` (page bắt đầu từ 1, pageSize tối đa 100).
  Cần sửa `schemas/page.schema.json` cho khớp (`total`, bỏ `totalItems` và `totalPages`).
- Lỗi: **RFC 7807 ProblemDetails**, `Content-Type: application/problem+json`. HTTP status phản ánh đúng loại lỗi
  (400, 401, 403, 404, 409, 422, 500).

```json
{
  "type": "https://farm.vn/errors/farm_not_found",
  "title": "Not Found",
  "status": 404,
  "detail": "Không tìm thấy nông trại",
  "code": "FARM_NOT_FOUND",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [ { "field": "farmId", "message": "..." } ]
}
```

- `code`: chữ in hoa, gạch dưới, ổn định (không đổi theo ngôn ngữ hiển thị).
- `errors`: danh sách lỗi theo field khi validate, mỗi phần tử `{ "field", "message" }`; không có thì bỏ qua field này.

## 5. Truy vết (trace)

- W3C `traceparent` qua OpenTelemetry; cả hai ngôn ngữ tự lan truyền khi gọi HTTP.
- `traceId` xuất hiện trong response lỗi, trong log, và trong envelope của event.

## 6. Event

- Exchange kiểu topic `farm.events`; routing key = tên event (ví dụ `readings.synced`).
- Queue: `<service>.<event>`; DLQ: `<queue>.dlq`.
- Envelope: `eventId`, `eventType`, `version`, `occurredAt`, `producer`, `traceId`, `payload`.
- Consumer chống trùng theo `eventId`.
- Schema payload: `contracts/events/<tên-event>.v1.yaml`.

## 7. Cấu hình (biến môi trường)

Cùng tên cho mọi service; mỗi base tự map sang chuẩn của mình:

`SERVICE_NAME`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
`RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `AUTH_JWKS_URI`,
và (khi cần gọi service khác) `AUTH_TOKEN_URI`, `SERVICE_CLIENT_ID`, `SERVICE_CLIENT_SECRET`.

Cổng nghe: Java dùng `SERVER_PORT`, .NET dùng `ASPNETCORE_URLS`.

## 8. Bảng service, port và database

| Service | Route prefix công khai | Port (local) | Database |
|---|---|---|---|
| api-gateway | (cổng vào) | 8080 | không có |
| user-auth | `/api/v1/auth`, `/api/v1/users`, `/api/v1/roles` | 8081 | user_auth_db |
| farm-device | `/api/v1/farms`, `/api/v1/zones`, `/api/v1/nodes`, `/api/v1/uavs`, `/api/v1/gateways`, `/api/v1/sensor-types` | 8082 | farm_device_db |
| mission | `/api/v1/missions` | 8083 | mission_db |
| sensor-data | `/api/v1/readings`, `/api/v1/sync-batches` | 8084 | sensor_data_db |
| alert | `/api/v1/alerts`, `/api/v1/thresholds` | 8085 | alert_db |
| notification | `/api/v1/notifications` | 8086 | notification_db |

Trong mạng Docker, mọi service nghe cổng **8080** bên trong container (port ở bảng là cổng map ra máy host).
Địa chỉ giữa các service trong Docker là `http://<tên-service>:8080/`.

## 9. Quy tắc dữ liệu giữa các service

- Mỗi service chỉ truy cập database của chính nó. Không có FK chéo database.
- Cột tham chiếu sang service khác (`ref` trong ERD) được kiểm tra ở tầng ứng dụng khi ghi.
- Không xóa cứng bản ghi được tham chiếu; dùng cột `Status`.

## 10. Quy tắc entity để khớp tài liệu thiết kế (cho cả Java)

| Trong tài liệu thiết kế | Entity Java |
|---|---|
| Tên bảng `dbo_FD_Farms` | `@Table(name = "dbo_FD_Farms")` |
| Cột PascalCase `FarmId` | `@Column(name = "FarmId")` (tên field Java vẫn camelCase) |
| `NVARCHAR(n)` | `@Nationalized` + `length = n` |
| `VARCHAR(n)` (mã, hash) | không có `@Nationalized`, `length = n` |
| `NVARCHAR(MAX)` | `@Nationalized` + `columnDefinition = "nvarchar(max)"` |
| `UNIQUEIDENTIFIER` | kiểu `UUID` |
| Enum `VARCHAR` + `CHECK` | `@Enumerated(EnumType.STRING)` + `length` |
| `DATETIME2(0)` / `(3)` | `Instant` (+ `columnDefinition = "datetime2(0)"` hoặc `"datetime2(3)"` nếu muốn ghi rõ) |
| `TINYINT` | `Short` + `@JdbcTypeCode(SqlTypes.TINYINT)` |
| `DECIMAL(p,s)` | `BigDecimal` + `precision`, `scale` |
| `UQ(...)` | `@Table(uniqueConstraints = @UniqueConstraint(...))` |
| `IX(...)` | `@Table(indexes = @Index(...))` |
| FK cùng service | `@ManyToOne` |
| `ref` sang service khác | chỉ lưu cột `UUID`, không `@ManyToOne` |

Lưu ý về cách tạo bảng: starter hiện chạy script SQL của tài liệu bằng Flyway (`db/migration`) rồi để Hibernate chỉ
kiểm tra (`ddl-auto=validate`), nên `CHECK`, `DEFAULT SYSUTCDATETIME()` và tên constraint theo mẫu đều có sẵn từ script.
Các ghi chú "Hibernate tự sinh" chỉ đúng nếu nhóm chọn `ddl-auto=update` ở môi trường dev. **Cần chốt một cách cho cả nhóm.**
Kiểu `Instant` ánh xạ sang `datetime2` nhờ cấu hình chung của starter (`hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`).