# Notification service

Service .NET 10 lưu thông báo web và gửi email cho admin từ sự kiện RabbitMQ.
Database riêng là `notification_db`. Migration chạy khi khởi động service.
OpenAPI trong Development tại `/openapi/v1.json`.

## API web

Các endpoint yêu cầu JWT của User Auth. Các thao tác trong hộp thông báo cá nhân
yêu cầu role `ADMIN` hoặc `OPERATOR` và chỉ áp dụng cho thông báo của người đang
đăng nhập. Endpoint tạo yêu cầu role `ADMIN` và chỉ tạo thông báo web.

| Method | Endpoint trực tiếp | Chức năng |
|---|---|---|
| GET | `/api/notifications/me` | Danh sách thông báo của người đăng nhập |
| GET | `/api/notifications/unread-count` | Đếm thông báo chưa đọc |
| POST | `/api/notifications` | Admin tạo thông báo web |
| PATCH | `/api/notifications/{id}/read` | Đánh dấu đã đọc |
| PATCH | `/api/notifications/read-all` | Đánh dấu tất cả đã đọc |
| DELETE | `/api/notifications/{id}` | Xóa thông báo web |

Gateway công khai đường dẫn `/api/v1/notifications/...` và chuyển tiếp sang
`/api/notifications/...` của service này.

## Nhận sự kiện và chống trùng

Exchange `farm.events` loại topic. Queue mặc định `notification.events`, durable.
Hỗ trợ envelope version 1 trong `contracts/events`, gồm `eventId`, `eventType`,
`version` và `payload`. `eventId` phải là UUID khác rỗng và được giữ nguyên khi gửi lại.

- `alert.created` tạo thông báo từ loại, mức độ và nội dung cảnh báo.
- `mission.status_changed` tạo thông báo cho trạng thái `FAILED` và `PARTIAL`.
- `Notifications:MissionSource=Direct` là mặc định để tương thích luồng đã có.
  Noti nhận lỗi Mission trực tiếp và bỏ qua `MISSION_FAILED` hoặc `COLLECTION_FAILED`
  có `missionId` từ Alert, tránh thông báo hai lần cho cùng sự cố.
- Khi Alert đã chuyển lỗi Mission thành `alert.created`, đặt
  `Notifications:MissionSource=Alert`. Noti xử lý các cảnh báo đó và bỏ qua sự kiện
  Mission trực tiếp. Các instance phải dùng cùng cấu hình nguồn.

Một giao dịch lưu lịch sử `ProcessedNotificationEvents`, toàn bộ thông báo web và
công việc gửi email `NotificationEmails`. Khóa duy nhất trên event ID xử lý cạnh tranh
giữa nhiều consumer. Xóa thông báo web giữ lại lịch sử sự kiện và công việc email.
Danh sách người nhận được chốt tại lần xử lý thành công đầu tiên.

Migration bổ sung lịch sử từ các `SourceEventId` còn tồn tại trong thông báo cũ.
Thông báo đã bị xóa trước migration không có đủ dữ liệu để phục hồi lịch sử chống trùng.
Migration không tạo email cho thông báo lịch sử.

## Lỗi và thử lại

- Sự kiện sai định dạng hoặc version vào `<queue>.dlq` ngay.
- Lỗi Auth, database hoặc xử lý tạm thời được chuyển sang `<queue>.retry`, đợi
  10 giây rồi về queue chính. Tối đa 3 lần xử lý, sau đó chuyển vào DLQ.
- Bản sao retry/DLQ được publish persistent và chờ broker xác nhận trước khi ACK
  bản gốc. Nếu publish thất bại, channel đóng để RabbitMQ giao lại bản chưa ACK.
- Consumer kết nối lại sau 5 giây khi mất kết nối hoặc bị hủy đăng ký.
- Queue retry sử dụng TTL và dead-letter exchange của RabbitMQ. Cần giám sát độ bền
  của broker khi vận hành thực tế, nhất là lúc broker bị sự cố trong quá trình dead-letter.

Sau khi sửa nguyên nhân, có thể publish lại payload trong DLQ vào `farm.events`
với routing key bằng `eventType`, giữ `eventId`, bỏ header `x-notification-attempt`.
Chỉ xóa bản DLQ sau khi xác nhận publish thành công. Theo dõi log `moved to ...dlq`;
payload lỗi được giữ trong queue, log chỉ ghi loại lỗi để hạn chế lộ dữ liệu.

## Cấu hình người nhận

Hai chế độ được chọn tường minh bằng `Notifications:RecipientSource`.

### Configuration

Chế độ mặc định dùng danh sách admin do người vận hành cấu hình.

```json
{
  "Notifications": {
    "RecipientSource": "Configuration",
    "MissionSource": "Direct",
    "Recipients": [
      { "UserId": "<UUID của admin>", "Email": "admin@example.com" }
    ]
  }
}
```

Danh sách `Notifications:AdminUserIds` cũ vẫn hoạt động khi `Recipients` rỗng và
email chưa bật. Khi bật email, mọi người nhận phải có email hợp lệ. Không có người
nhận hợp lệ sẽ gây thử lại rồi vào DLQ, tránh ACK một sự kiện chưa được giao.

### UserAuth

Đặt `Notifications:RecipientSource=UserAuth`, `UserAuth:BaseUrl` có dấu `/` cuối,
`UserAuth:ClientId=notification-service` và `UserAuth:ClientSecret` qua biến môi trường.
Noti dùng `POST /connect/token` với `grant_type=client_credentials`, sau đó gửi
Bearer service token tới `UserAuth:RecipientsPath`.

Endpoint dự kiến mặc định là `GET /internal/v1/users/notification-recipients`.
**Endpoint này chưa có trong User Auth của repository.** Chủ service Auth cần cung
cấp endpoint cho service token, chỉ trả admin đang hoạt động, theo dạng sau.

```json
{
  "result": [
    { "userId": "<UUID của admin>", "email": "admin@example.com" }
  ]
}
```

Có thể đổi `UserAuth:RecipientsPath` nếu nhóm thống nhất đường dẫn khác. Chế độ
UserAuth không tự chuyển sang danh sách cấu hình khi Auth lỗi.
Hợp đồng sự kiện hiện dự kiến gửi cho các admin toàn hệ thống. Phân quyền theo
nông trại cần thống nhất riêng với Auth và các service nguồn.

## Email

`Email:Enabled=false` theo mặc định. Khi bật, email được đưa vào outbox cùng giao
dịch tạo thông báo từ sự kiện. Worker gửi email văn bản thuần qua SMTP có cấu hình.
Đặt các giá trị `Email:Host`, `Email:Port`, `Email:From`, `Email:EnableSsl`,
`Email:Username`, `Email:Password`. SSL mặc định bật, port mặc định 587 dùng STARTTLS.
Chỉ tắt SSL khi chạy với SMTP thử nghiệm cục bộ.

Worker dùng lease 2 phút trong database để hai instance không cùng gửi một công
việc đang xử lý. Mỗi lần gửi có timeout 30 giây. Mặc định tối đa 5 lần gửi, chờ
10, 20, 40, 80 giây giữa các lần. `Email:MaxAttempts` cho phép 1–10 và
`Email:RetrySeconds` cho phép 1–300; thời gian chờ tối đa 300 giây.

`SentAt` ghi nhận thành công, `FailedAt` ghi nhận hết số lần thử, `Attempts` và
`LastError` phục vụ kiểm tra. SMTP lỗi không xóa thông báo web. Công việc chưa gửi
tiếp tục sau khi worker khởi động lại. Tắt email không tạo công việc mới và dừng
worker; bật lại sẽ xử lý công việc đang chờ.

SMTP có thể đã nhận thư ngay trước khi ứng dụng dừng mà chưa kịp lưu `SentAt`.
Trường hợp này có thể gửi lại email sau khi lease hết hạn. SMTP không cung cấp
giao dịch chung với database, nên không bảo đảm email đúng một lần khi tiến trình
dừng ở thời điểm đó. Thông báo web và công việc email vẫn chống trùng theo event ID.

## Chạy local

Cấu hình tối thiểu qua biến môi trường trước khi chạy từ thư mục này.

```powershell
$env:ASPNETCORE_ENVIRONMENT = 'Development'
$env:ConnectionStrings__Default = '<connection string notification_db>'
$env:Jwt__MetadataAddress = 'http://localhost:15081/.well-known/openid-configuration'
$env:RabbitMq__Enabled = 'true'
$env:RabbitMq__Host = 'localhost'
$env:RabbitMq__User = '<rabbit user>'
$env:RabbitMq__Password = '<rabbit password>'
$env:Notifications__Recipients__0__UserId = '<admin UUID>'
$env:Notifications__Recipients__0__Email = 'admin@example.com'
dotnet run --project NotificationService.Api --no-launch-profile --urls http://localhost:15086
```

`RabbitMq:Port` mặc định 5672, `VirtualHost` mặc định `/`, `Queue` mặc định
`notification.events`. Bật email sau khi cung cấp SMTP và email người nhận.
Các bí mật đặt qua biến môi trường hoặc secret store của môi trường triển khai.

## Kiểm thử tích hợp

`tests/integration.mjs` dùng Node.js 22+, Docker CLI, SQL Server và RabbitMQ có
management API. Test tự tạo database và virtual host tên ngẫu nhiên, xóa chúng
sau khi chạy. Auth và SMTP là server giả lập bind loopback, không gửi email ra ngoài.
Test kiểm tra cả nâng cấp schema cũ và database mới.

```powershell
dotnet build NotificationService.Api
$env:NOTI_TEST_SQL_CONTAINER = 'noti-local-sql'
$env:NOTI_TEST_SQL_ADDRESS = 'localhost,14331'
$env:NOTI_TEST_SQL_PASSWORD = '<mật khẩu SQL thử nghiệm>'
$env:NOTI_TEST_RABBIT_URL = 'http://localhost:15672'
$env:NOTI_TEST_RABBIT_USER = '<rabbit user>'
$env:NOTI_TEST_RABBIT_PASSWORD = '<rabbit password>'
node tests/integration.mjs
```

Test bao gồm service token, nhiều người nhận, hai consumer/worker, nhận lại sự kiện
sau khi xóa thông báo, dữ liệu sai, Auth lỗi rồi phục hồi, hết số lần thử, SMTP lỗi,
khởi động lại worker, hai chế độ nguồn Mission và kết nối lại RabbitMQ.
Test Auth giả lập xác nhận client của noti theo hợp đồng ở trên; tích hợp với API
Auth thật cần được kiểm tra khi nhóm Auth cung cấp endpoint.

## Phần phối hợp với các service khác

User Auth cung cấp endpoint admin nhận thông báo. Alert phát `alert.created` sau
khi lưu cảnh báo và bảo đảm gửi lại khi broker lỗi. Mission đã có mã phát
`mission.status_changed`; cần kiểm thử sự kiện phát từ thao tác nghiệp vụ thật.
Các task này thuộc service nguồn. Các thay đổi trong lượt triển khai này nằm ở noti.
