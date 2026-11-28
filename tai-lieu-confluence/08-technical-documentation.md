# 08 Technical Documentation — Tài liệu kỹ thuật

**Trạng thái:** Bản bàn giao đối chiếu mã · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm kỹ thuật, cá nhân chưa xác minh.

Chương này hướng dẫn lập trình viên xác định service sở hữu một chức năng, tìm cấu hình thực tế, kiểm tra hợp đồng và sửa lỗi từ UI đến database hoặc broker. Thiết kế API và ERD đầy đủ thuộc mục 3.7 và 3.6. Môi trường khởi chạy thuộc chương 07.

## 8.1 Repository và quản lý mã nguồn

Monorepo chứa backend, frontend và hạ tầng local. Để tiếp nhận một thay đổi, đọc từ hợp đồng đến phần triển khai theo thứ tự `contracts` → project trong `services` → cấu hình `deploy` → client/UI liên quan. Nếu sửa logic dùng chung cho Java, xem `platform/java` và xác định danh sách service sử dụng starter.

| Phạm vi quyền đề xuất theo loại công việc | Trách nhiệm |
| --- | --- |
| Quản trị repository | Quản lý quyền và quy tắc nhánh, xử lý thay đổi tích hợp |
| Phụ trách service | Duyệt logic, migration và hành vi khi retry |
| Phụ trách frontend | Xác nhận hợp đồng API và xử lý trạng thái giao diện |
| Phụ trách tích hợp | Đối chiếu sự kiện, cấu hình Compose và kết quả kiểm thử |

README yêu cầu làm việc trên nhánh riêng và merge qua Pull Request. Danh sách sáu thành viên, quyền từng người và trạng thái bảo vệ `main` trên GitHub **CHƯA XÁC MINH**. Hồ sơ dự án có thể điền theo mục 9.1. Xem [mốc Git bàn giao](https://github.com/huylam0703/version7/tree/612e00b).

## 8.2 Quy ước đặt tên

| Thành phần | Quy ước và ví dụ | Khi chỉnh sửa |
| --- | --- | --- |
| Service và container | Chữ thường, gạch nối như `sensor-data-service` | Giữ thống nhất với Compose và route target |
| Định danh và JSON | UUID ở dạng chuỗi, tên trường camelCase, enum chuỗi viết hoa theo hợp đồng | Kiểm tra cách ánh xạ DTO và tương thích với frontend |
| Thời gian | ISO 8601 UTC có hậu tố Z trong trao đổi | Ghi rõ thời điểm đo, thời điểm tạo và thời điểm nhận |
| CSDL | Tên thực tế lấy từ entity và script hoặc migration | Kiểm tra schema và bảng trước khi dùng tên trong ERD |
| Endpoint | Hợp đồng ưu tiên `/api/v1/<resources>`, nội bộ ưu tiên `/internal/v1/...` | So sánh controller và rewrite của Gateway, vì các route hiện có khác nhau |
| Event | Tên miền nghiệp vụ và hành động, như `mission.status_changed` | Tra version schema và routing key |

Ví dụ cùng một database có tên bảng được ghi theo dạng schema + tên bảng như `dbo.sensor_readings`, trong khi entity Mission có tên bảng `dbo_M_Missions` chứa tiền tố trong chính tên. Tránh suy ra định danh SQL từ tiền tố tài liệu. Xem [quy ước nền tảng](https://github.com/huylam0703/version7/blob/612e00b/farm-monitoring-platform/contracts/PLATFORM-CONVENTIONS.md).

## 8.3 Cấu hình môi trường

Môi trường host dùng cổng publish trong Compose. Môi trường container dùng DNS của mạng Docker và cổng nội bộ. Đối với Java, tìm profile hiện hành trong `application.yml` hoặc `application.yaml`, file theo profile như `application-dev.yml`, sau đó lần đến biến môi trường. Đối với .NET, tra appsettings, tên môi trường thực tế và override dạng `Section__Key` trong Compose. Giá trị từ `.env` để nội suy Compose đi vào container khi Compose chuyển tiếp chúng.

Khi xác nhận một cấu hình, lần theo bốn điểm gồm giá trị đặt ở host, giá trị Compose truyền, tên property ở ứng dụng và hành vi runtime. Điều này giúp phân biệt lỗi đặt sai biến với lỗi cấu hình đúng tên nhưng sai địa chỉ. Dùng cấu hình mẫu trong `deploy/.env.example` và quản lý giá trị nhạy cảm theo môi trường.

## 8.4 Danh sách biến môi trường

Các giá trị đều là ví dụ hoặc ký hiệu thay thế. Kiểm tra đúng namespace và ứng dụng nhận biến trước khi dùng. Dấu `<...>` chỉ nơi điền qua cơ chế bí mật của nhóm.

| Biến | Ý nghĩa | Giá trị minh họa |
| --- | --- | --- |
| `MSSQL_SA_PASSWORD` | SQL Server và job khởi tạo trong Compose | `<sql-dev-password>` |
| `DB_HOST`, `DB_PORT` | Địa chỉ SQL theo tiến trình | `sqlserver`, `1433` |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Database và thông tin xác thực phía dịch vụ | `farm_device_db`, `app_demo`, `<db-password>` |
| `SPRING_DATASOURCE_URL` | Override datasource Spring theo môi trường | `jdbc:sqlserver://sqlserver:1433;databaseName=mission_db` |
| `SERVER_PORT`, `ASPNETCORE_URLS` | Cổng nghe Java và .NET | `8080`, `http://+:8080` |
| `SPRING_PROFILES_ACTIVE`, `ASPNETCORE_ENVIRONMENT` | Profile hoặc môi trường | `dev`, `Development` |
| `AUTH_JWKS_URI` | URL JWKS cho xác minh JWT | `http://user-auth-service:8080/.well-known/jwks.json` |
| `AUTH_TOKEN_URI`, `AUTH_TOKEN_URL` | URL cấp token. Quy ước starter và simulator dùng tên khác nhau. | `http://user-auth-service:8080/connect/token` |
| `SERVICE_CLIENT_ID`, `SERVICE_CLIENT_SECRET` | Danh tính gọi nội bộ theo hợp đồng chung | `service-demo`, `<service-secret>` |
| `UAV_CLIENT_ID`, `UAV_CLIENT_SECRET` | Danh tính của simulator | `uav-demo`, `<simulator-secret>` |
| `FARM_DEVICE_BASE_URL`, `MISSION_SERVICE_BASE_URL`, `SENSOR_DATA_BASE_URL` | Định tuyến client HTTP nội bộ | `http://farm-device-service:8080`, `http://mission-service:8080`, `http://sensor-data-service:8080` |
| `MISSION_REFERENCE_VALIDATION_MODE` | Chế độ kiểm tra tham chiếu nhiệm vụ | Tra giá trị enum hợp lệ trong mã |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | Địa chỉ broker AMQP | `rabbitmq`, `5672` |
| `RABBITMQ_USER`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | Thông tin xác thực broker, tên user khác tùy ứng dụng | `iot_demo`, `iot_demo`, `<broker-password>` |
| SIMULATOR_PENDING_RESULTS_DIR | Thư mục lưu bền vững payload kết quả nhiệm vụ ở bản workspace. Mỗi instance dùng một thư mục riêng, giữ cùng đường dẫn qua các lần khởi động. | ./data/pending-results |
| REDIS_PASSWORD | Thông tin xác thực Redis nếu cấu hình sử dụng | `<redis-password>` |
| `JWT_ACCESS_MINUTES` | Tuổi access token theo quy ước, cần đối chiếu mapping User Auth | `30` |

## 8.5 Hướng dẫn đọc tài liệu API

Chọn nghiệp vụ ở mục 3.7, rồi kiểm tra version, role, tham số và kiểu dữ liệu cần gửi. Đọc schema trong `contracts/openapi` nếu route đã có tài liệu. Repo theo mốc đối chiếu có OpenAPI lưu sẵn cho Farm Device. Với các service còn lại, tìm controller và cấu hình Swagger/OpenAPI của project. Nếu đường gọi từ frontend và controller khác nhau, mở cấu hình transform của Gateway trước khi kết luận route sai.

Ví dụ chỉ đọc danh sách nông trại qua Gateway local với token demo có quyền. Trong PowerShell có thể dùng `curl.exe` với cú pháp nhiều dòng phù hợp với shell, hoặc nhập lệnh một dòng vào Postman.

```bash
curl -i "http://localhost:5000/api/v1/farms?page=1&pageSize=10" -H "Authorization: Bearer DEMO_ACCESS_TOKEN"
```

Để xác nhận tích hợp, ghi mã HTTP, dạng response và dấu vết phân trang. Token minh họa cần được thay bằng phiên hợp lệ của môi trường demo. Khi xem log hoặc chia sẻ ảnh, bỏ header Authorization. Dữ liệu mẫu đầy đủ thuộc mục 10.6.

## 8.6 Hướng dẫn đọc cấu trúc cơ sở dữ liệu

Xác định database sở hữu tài nguyên từ 4.8 và tra ERD tại 3.6. Kiểm tra kiểu và tên cột trong entity, rồi đối chiếu DDL từ migration hoặc script. Với SQL Server, phân biệt schema `dbo` và tiền tố trong tên bảng. Với JPA, xem các annotation `@Table` và `@Column`. Với EF Core, xem mapping trong context và migration snapshot.

Tham chiếu khác service lưu định danh logic và kiểm tra qua API khi nghiệp vụ yêu cầu. Để đọc dữ liệu nông trại gắn với phép đo, lấy metadata qua service sở hữu hoặc API hỗ trợ, tránh quyết định dựa trên phép ghép schema chéo. Với thao tác ngừng sử dụng, đọc quy tắc trạng thái của tài nguyên và xác minh bản ghi tham chiếu lịch sử vẫn tra được. Trình tự khởi tạo xem 5.11, script phụ lục thuộc 10.7.

## 8.7 Logging và xử lý lỗi

Quy ước dự án ưu tiên ProblemDetails với status HTTP, mã lỗi ổn định và `traceId` khi có hỗ trợ. Hành vi thực tế cần kiểm tra cho từng service. Khi ghi phiếu lỗi, giữ timestamp, route, service, status HTTP, business ID và trace ID nếu có. Với message, bổ sung `eventId`, routing key và tên queue đã kiểm tra. Đây là các khóa để ghép các log của cùng một lần xử lý.

| Nhóm phản hồi | Cách đọc |
| --- | --- |
| 400 hoặc 422 | Kiểm tra DTO, field được báo và quy tắc nghiệp vụ |
| 401 hoặc 403 | Kiểm tra token/issuer/audience hoặc quyền trên tài nguyên, theo mã cụ thể |
| 404 | Xác minh host, Gateway transform và ID của dữ liệu |
| 409 | Đọc mã lỗi để phân biệt trạng thái xung đột và gửi trùng |
| 5xx hoặc timeout | Đối chiếu log dịch vụ đích, database/broker và tình trạng kết nối |

Retry cần phân theo hoạt động. Với yêu cầu ghi chưa rõ kết quả, kiểm tra idempotency và trạng thái trên server trước khi gửi lại. Với outbox Mission, xem trạng thái, lần thử và log publisher. Với simulator, đối chiếu `executionId` và payload pending. Với Alert/Notification, theo dõi log ở producer và consumer để biết giao nhận dừng tại đâu. Không suy ra mọi queue đều áp dụng chung một ngưỡng retry hoặc có DLQ từ quy ước nền tảng.

## 8.8 Quy trình Git và Pull Request

1. Cập nhật nhánh làm việc từ nhánh tích hợp và tạo nhánh ngắn cho một thay đổi.
2. Khi đổi hợp đồng, sửa schema hoặc OpenAPI trước, nêu thay đổi tương thích và liệt kê các caller cần cập nhật.
3. Dùng mẫu Conventional Commits như `feat(sensor-data): add reading quality filter` hoặc `fix(mission): handle expired recovery lease`.
4. Mở PR với yêu cầu liên quan, phạm vi, các bước kiểm thử đã chạy và kết quả. Với migration hoặc event mới, bổ sung thứ tự triển khai và cách kiểm tra sau merge.
5. Sau review, cập nhật nhánh nếu cần và kiểm tra lại cả đường gọi qua Gateway và consumer chịu ảnh hưởng. Quy tắc branch protection thực tế cần xác nhận trong GitHub.

Mẫu PR nên có bốn mục **Mục tiêu**, **Thay đổi**, **Kiểm thử**, **Ảnh hưởng triển khai**. Kèm log hoặc ảnh đã lược thông tin nhạy cảm khi cần tái hiện kết quả.

## 8.9 Hướng dẫn thêm chức năng mới

1. Xác định service sở hữu dữ liệu, yêu cầu F liên quan và các vai trò sử dụng.
2. Định nghĩa hợp đồng route, DTO, trạng thái lỗi và kiểu dữ liệu. Với hợp đồng đang có người dùng, xem khả năng thêm trường tùy chọn hoặc cung cấp phiên bản mới.
3. Triển khai validation, xác thực và quyền ở service. Bổ sung Gateway và frontend client nếu endpoint phục vụ người dùng. Kiểm tra điều kiện phân trang hoặc lọc nếu trả danh sách.
4. Khi thêm bảng, viết thay đổi theo cơ chế migration của service đó. Kiểm tra nâng cấp từ dữ liệu cũ, tạo schema mới và chỉ mục phục vụ truy vấn dự kiến.
5. Khi thêm event, ghi producer, consumer, cấu trúc payload, version, khóa chống trùng và hành vi khi retry. Xác định thời điểm phát so với commit và khi nào consumer ghi nhận đã xử lý.
6. Kiểm tra một ca thành công, một ca sai quyền, đầu vào không hợp lệ và ca gửi lại nếu có thay đổi dữ liệu. Cập nhật mục 3.6 hoặc 3.7 và tài liệu triển khai tương ứng.

## 8.10 Lưu ý kỹ thuật

| Hiện tượng | Cách khoanh vùng |
| --- | --- |
| UI gọi API nhận 404 | Mở Network, ghi URL/method, kiểm tra Gateway host 5000 và controller đích |
| Token hợp lệ ở User Auth nhưng service trả 401 | Đối chiếu issuer, audience, JWKS URI và thời gian máy |
| Lỗi SQL lúc startup | Xem db-init đã hoàn thành, database đúng tên và schema đủ cho chế độ ORM đang dùng |
| Có phép đo nhưng chưa có cảnh báo | Kiểm tra liệu sự kiện đã phát, binding đúng routing key, payload hợp lệ và listener nhận |
| Có cảnh báo nhưng hộp thư rỗng | Kiểm tra consumer Notification, xác định người nhận và thử đúng tài khoản |
| Simulator khởi động lại khi đang có kết quả chờ | Kiểm tra recovery trên server, quyền ghi của thư mục pending và volume được giữ qua lần tạo lại container |
| Build Linux báo thiếu Dockerfile | So sánh path trong Compose với tên thư mục được Git ghi nhận, gồm hoa và thường |

Khi chưa khoanh vùng được, tái hiện với một farm, một node và một nhiệm vụ demo. Giữ ID tạo ra tại từng chặng để so sánh phép đo, kết quả nhiệm vụ và thông báo. Hướng dẫn thao tác dành cho người dùng thuộc mục 7.11.
