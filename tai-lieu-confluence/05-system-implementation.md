# 05 System Implementation — Triển khai hệ thống

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phát triển, cá nhân chưa xác minh.

Chương này trình bày phần triển khai có thể truy vết trong Git và các phần cần kiểm chứng khi chạy. **ĐÃ TRIỂN KHAI** trong bảng 5.13 xác nhận có đường xử lý trong mã. Kết quả chạy, log và kiểm thử nghiệm thu thuộc chương 06.

## 5.1 Môi trường phát triển

Dùng Java 21 và Maven Wrapper cho các dịch vụ Java, .NET SDK 10 cho các dự án ASP.NET Core, Node/npm cho frontend, Git và Docker Compose cho hạ tầng. Kiểm tra yêu cầu phiên bản Node theo Vite và lockfile tại checkout. Môi trường chạy từng tiến trình trên host dùng địa chỉ khác với cấu hình trong container. Mốc đối chiếu là nhánh cục bộ `dev_chien`, commit `612e00b`. Các thay đổi local ngoài commit được loại khỏi mô tả triển khai.

## 5.2 Cấu trúc mã nguồn

```text
version7/
├── farm-monitoring-platform/
│   ├── contracts/               Hợp đồng, schema, OpenAPI
│   ├── deploy/                  Compose, Dockerfile, SQL khởi tạo
│   ├── platform/java/           Starter và mã Java dùng chung
│   └── services/                Sáu miền nghiệp vụ, Gateway và simulator
└── farm-mornitoring-platform-ui/
    └── src/                     UI, route và tích hợp API
```

Giữ nguyên cách viết `farm-mornitoring-platform-ui` khi chạy lệnh hoặc dẫn đường dẫn. Backend chia thành các project theo service. Từ một lỗi UI, lần theo client gọi API rồi tìm transform của Gateway và controller xử lý. Xem [mốc mã nguồn](https://github.com/huylam0703/version7/tree/612e00b).

## 5.3 Công nghệ thực tế sử dụng

.NET 10 triển khai Gateway, User Auth và Notification. Java 21/Spring Boot triển khai Farm Device, Mission, Sensor Data, Alert và simulator. Dữ liệu trung tâm dùng SQL Server. RabbitMQ phục vụ sự kiện, Redis có cấu hình trong Compose hạ tầng. Frontend dùng React, TypeScript, Vite, Axios, React Router, Leaflet và Recharts theo manifest. Đối chiếu công nghệ dự kiến và hệ quả tại [4.11](04-system-architecture.md#411-nhật-ký-quyết-định-kiến-trúc).

## Các trang con 5.4 đến 5.9

| Mục | Trang con | Dùng khi cần |
| --- | --- | --- |
| 5.4 | [User Auth](05-04-user-auth.md) | Xác thực, quyền, danh tính simulator |
| 5.5 | [Farm Device](05-05-farm-device.md) | Nông trại, vùng, danh mục thiết bị |
| 5.6 | [Mission](05-06-mission.md) | Kế hoạch, tiến độ, kết quả, khôi phục |
| 5.7 | [Sensor Data](05-07-sensor-data.md) | Dữ liệu đo, batch, truy vấn và dữ liệu trùng |
| 5.8 | [Alert](05-08-alert.md) | Phát hiện và xử lý cảnh báo |
| 5.9 | [Notification](05-09-notification.md) | Thông báo cá nhân và xử lý giao nhận |

## 5.10 Triển khai frontend

| Cụm màn hình trong mã | Trách nhiệm | Điểm xác minh khi demo |
| --- | --- | --- |
| Login, Settings, Forbidden | Bắt đầu phiên, hồ sơ và phản hồi quyền | Thử bằng tài khoản hợp lệ của từng vai trò |
| Dashboard, Farms, Zones, Users | Tổng quan và quản trị dữ liệu nền | Kiểm tra bộ lọc, dữ liệu rỗng và phản hồi khi sửa |
| Sensor Monitor, History, Compare, Reports | Quan sát giá trị mới, lịch sử, so sánh và báo cáo | Đối chiếu request, đơn vị và múi giờ với dữ liệu trả về |
| Nodes, UAVs, Gateways | Quản lý thiết bị vận hành | Kiểm tra dữ liệu và quyền theo API của từng đối tượng |
| Missions, Create/Edit, Monitor, Results | Lập kế hoạch, theo dõi tiến độ và xem kết quả | Dùng một nhiệm vụ mẫu đi từ lập kế hoạch tới xem kết quả |
| Alerts, Thresholds, Notifications | Cảnh báo, màn hình ngưỡng, hộp thư | Kiểm tra tuyến API và kết quả lưu khi reload |

Các trang có trong `src/pages`. Chất lượng tích hợp được đánh giá trên route, client gọi API và phản hồi thực tế, vì tên màn hình chỉ xác nhận UI tồn tại.

| Kiểm tra local ngày 27/11/2026 | Kết quả |
| --- | --- |
| `npm.cmd run build` | Thành công. TypeScript hoàn tất và Vite 8.3.5 tạo bản build với 2.710 module. Vite cảnh báo có bundle JavaScript lớn hơn 500 kB sau khi minify. |
| `npm.cmd run lint` | Thành công theo mã thoát. Có cảnh báo về import chưa dùng và cách dùng React hooks trong các màn hình hiện có. |
| Khởi động bản preview | Vite báo đang lắng nghe tại `127.0.0.1:4179`. Môi trường thực thi từ chối kết nối HTTP local khi thử mở trang đăng nhập, nên chưa thu được ảnh. |

Khi có trình duyệt truy cập được môi trường demo, dùng dữ liệu giả để chụp Dashboard, Sensors, Alerts và Missions và đối chiếu mục 3.8. Trong mỗi ảnh, thể hiện bộ lọc hoặc trạng thái đang được xác minh và đối chiếu với mã yêu cầu trong bảng 5.13.

## 5.11 Triển khai cơ sở dữ liệu

Compose khởi động SQL Server rồi chạy `db-init`. Nhóm script `deploy/db/init` tạo các database và bảng ban đầu cho Sensor Data. `deploy/sql` có nhóm SQL riêng về database, bảng đọc, metadata và truy vấn. Kiểm tra thư mục volume job thực sự mount trước khi chọn script bổ sung.

Notification có EF Core migrations trong project API. User Auth có script bảng riêng. Farm Device, Mission và Alert có cấu hình Hibernate cập nhật schema, còn Sensor Data đặt `ddl-auto: validate`. Cần đọc cấu hình Flyway của từng ứng dụng trước khi viết thứ tự migration chung. Farm Device có seeder loại cảm biến. Khi nghiệm thu môi trường sạch, lưu log khởi tạo, danh sách database và tình trạng schema của từng service.

## 5.12 Tích hợp UAV, gateway và cảm biến

Simulator có `Esp32Client`, `Esp32ReadingMapper` và `SensorDataClient`. Luồng này phân biệt dữ liệu đo đưa vào Sensor Data và báo cáo kết quả thực thi gửi về Mission. Ở chiều nhiệm vụ, simulator xử lý `mission.ready`, có tiến độ, heartbeat và các thành phần recovery với claim/renew. Việc khôi phục trạng thái trên máy chủ cần kiểm tra riêng với khả năng giữ payload phía simulator sau khi tiến trình dừng.

Tại commit đối chiếu, `MissionResultPayloadStore` giữ payload chờ gửi trong RAM. Bản cập nhật workspace lưu kết quả nhiệm vụ theo `executionId` xuống thư mục cấu hình. Tệp JSON được ghi, đồng bộ xuống đĩa rồi đổi tên nguyên tử trước khi gửi. Khi luồng thực thi hoặc recovery tới bước gửi Results lần nữa, simulator đọc lại payload đã lưu. Luồng cleanup hiện có xóa payload sau khi xác nhận kết quả hoặc hủy nhiệm vụ. Test `MissionResultPayloadStoreTest` đã chạy thành công, xác nhận đọc được payload khi mở lại kho, giữ bản gốc khi ghi trùng và xóa sau khi xử lý. **CHƯA XÁC MINH** firmware node, đo đạc ngoài hiện trường, hoạt động trên UAV và độ bền dữ liệu qua mất điện. Điều kiện nghiệm thu riêng gồm gửi lại trong cùng tiến trình, khởi động lại simulator, khôi phục phiên thực thi và đối chiếu tổng bản ghi ở máy chủ.

Lần kiểm tra ngày 27/11/2026 chưa chạy được tích hợp. Docker CLI không kết nối được Linux Engine. Lệnh khởi động Docker Desktop báo thiếu khóa registry và chạy launcher trực tiếp vẫn chưa mở được đường kết nối tới Engine. Maven Wrapper mặc định dừng vì không tạo được thư mục trong hồ sơ người dùng. Maven 3.9.9 có sẵn chạy được với JDK 23 và đích biên dịch Java 21. Nhóm kiểm thử kho lưu, ánh xạ ESP32, gửi kết quả, retry và recovery gồm 35 ca đạt, 0 lỗi.

| Kịch bản cần ghi bằng chứng | Dữ liệu và tiêu chí chấp nhận |
| --- | --- |
| Thu nhận và đồng bộ theo nhiệm vụ | Ghi `missionId`, `executionId` và các ID mẫu từ một nhiệm vụ demo. Kiểm tra kết quả Mission khớp node được giao, giá trị và đơn vị trong Sensor Data. |
| Gián đoạn kết nối trong cùng tiến trình | Ghi số mẫu trước khi ngắt kết nối, đối chiếu sau khi kết nối lại và kiểm tra mẫu trùng theo hợp đồng. Ghi riêng kết quả retry của Mission và kết quả ghi Sensor Data. |
| Khởi động lại hoặc mất nguồn | Tạo dữ liệu chờ, dừng tiến trình, khởi động lại và kiểm tra số mẫu khôi phục. Muốn xác nhận hành vi mất nguồn ngoài hiện trường, cần thiết bị và log đầu cuối của node lẫn gateway. |
| Thông báo và email | Tạo cảnh báo demo, dùng cùng ID để lần theo event, hộp thư cá nhân và bản ghi giao nhận. Kiểm tra thư đến hộp thư thử nghiệm khi môi trường đã trỏ tới SMTP thử nghiệm. |

## 5.13 Chức năng đã hoàn thành

Tên chức năng dưới đây mô tả theo nhóm trong đề cương và dấu vết triển khai. Đối chiếu tên chính thức và điều kiện đầy đủ ở mục 2.3, đặc biệt F04 và F11.

| Mã | Phạm vi đối chiếu | Trạng thái | Dấu vết |
| --- | --- | --- | --- |
| F01 | Xác thực | ĐÃ TRIỂN KHAI | Login, refresh, logout |
| F02 | Quản lý người dùng | ĐÃ TRIỂN KHAI | Users controller, hồ sơ và khóa/mở khóa |
| F03 | Nông trại và vùng | ĐÃ TRIỂN KHAI | Farm và Zone controllers |
| F04 | Hiển thị tổng quan và vị trí | ĐANG LÀM | Có UI, cần đối chiếu tiêu chí và dữ liệu thực |
| F05 | Thiết bị cảm biến | ĐÃ TRIỂN KHAI | Node controller và metadata |
| F06 | Đọc dữ liệu gần nhất | ĐÃ TRIỂN KHAI | API latest |
| F07 | Lịch sử cảm biến | ĐÃ TRIỂN KHAI | API truy vấn, thống kê, time-series |
| F08 | Phát hiện cảnh báo | ĐANG LÀM | Có listener và xử lý ngưỡng, cần thử end-to-end |
| F09 | Quản lý cảnh báo | ĐÃ TRIỂN KHAI | Xem, acknowledge, resolve |
| F10 | Gửi và xem thông báo | ĐANG LÀM | Có hộp thư và pipeline, cần xác minh gửi email |
| F11 | Báo cáo hoặc thống kê theo hồ sơ gốc | ĐANG LÀM | Có Reports UI và thống kê, tên F cần xác minh |
| F12 | Nhóm quản lý UAV | ĐÃ TRIỂN KHAI | UavController |
| F13 | Nhóm quản lý gateway | ĐÃ TRIỂN KHAI | GatewayController của Farm Device |
| F14 | Tạo nhiệm vụ | ĐÃ TRIỂN KHAI | Controller và DTO tạo nhiệm vụ |
| F15 | Lập kế hoạch và điều phối | ĐÃ TRIỂN KHAI | Plan, start, cancel |
| F16 | Theo dõi thực thi | ĐÃ TRIỂN KHAI | Progress, heartbeat và status logs |
| F17 | Kết quả nhiệm vụ | ĐÃ TRIỂN KHAI | API results, màn hình kết quả |
| F18 | Tham chiếu thiết bị | ĐÃ TRIỂN KHAI | Lookup UID và mission-check |
| F19 | Lưu offline bền vững | ĐANG LÀM | Bản workspace giữ payload kết quả nhiệm vụ qua lần mở lại kho. Bộ đệm bền vững cho từng mẫu và thử nghiệm mất điện vẫn mở. |
| F20 | Đồng bộ lại | ĐANG LÀM | Có batch và retry kết quả, cần xác nhận khôi phục sau mất điện |

## 5.14 Vấn đề và cách xử lý

| Vấn đề nhận diện từ mã và cấu hình | Cách kiểm tra hoặc xử lý |
| --- | --- |
| Tài liệu chung ghi Gateway 8080, Compose publish 5000 | Ghi rõ cổng host và cổng container trong hướng dẫn tích hợp |
| Schema kỳ vọng khác SQL khởi tạo | Đọc log db-init và ORM, so sánh phiên bản SQL với entity trước khi thay đổi |
| Event có schema nhưng chưa thấy đủ producer và consumer | Kiểm tra binding, publish và log bằng một mẫu dữ liệu demo |
| Tiến trình simulator mất kết nối hoặc dừng khi còn kết quả chờ | Dùng thư mục bền vững cho từng instance và kiểm tra việc gửi lại qua luồng recovery tại 5.12 |
| Đường dẫn Dockerfile trong Compose và tên thư mục Git khác hoa/thường | Kiểm tra trên filesystem phân biệt hoa/thường trước khi coi build dùng được |
| UI có màn hình nhưng thiếu bằng chứng dữ liệu thật | Ghi lại route, mã HTTP và kết quả thao tác trên môi trường demo |

Các vấn đề trên có căn cứ từ việc đọc mã và cấu hình. Nhật ký lỗi đã tái hiện cần bổ sung thời điểm, bước tái hiện và bản sửa nếu có.
