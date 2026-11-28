# 5.9 Notification

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phụ trách service, cá nhân chưa xác minh.

Trang con của [05 System Implementation](05-system-implementation.md). Phạm vi theo đề cương gồm F10. [Mã nguồn service tại 612e00b](https://github.com/huylam0703/version7/tree/612e00b/farm-monitoring-platform/services/notification-service).

## Trách nhiệm nghiệp vụ

Xử lý sự kiện để tạo hộp thư theo người nhận, cập nhật trạng thái đã đọc và quản lý dữ liệu giao nhận. Đường gửi email cần kiểm thử với cấu hình và người nhận demo.

## API và tích hợp

NotificationsController có me, unread-count, đọc một hoặc tất cả và xóa theo quyền. NotificationEventConsumer bind alert.created và mission.status_changed. NotificationEventProcessor xác định loại và người nhận. Để xác định URL sử dụng từ trình duyệt, đối chiếu controller đích với route và transform của API Gateway. Tra cứu hợp đồng ở mục 3.7.

## Kho dữ liệu

notification_db. EF Core migrations quản lý Notifications cùng các cấu trúc xử lý sự kiện, nguồn event và delivery. Khi kiểm tra chống trùng, xem SourceEventId và logic xử lý của consumer. Chi tiết ERD và kiểu dữ liệu thuộc mục 3.6. Khi kiểm tra lỗi ghi, xác nhận connection đang trỏ đúng database trước khi so sánh schema.

## Cấu hình

Kiểm tra section kết nối SQL, RabbitMQ, xác thực và gửi mail trong appsettings và Compose. Biến .NET dạng phân cấp dùng hai dấu gạch dưới khi đặt qua môi trường. Xem thêm [8.4](08-technical-documentation.md#84-danh-sách-biến-môi-trường).

## Kiểm thử và giới hạn

Thử hai tài khoản có hộp thư riêng, đánh dấu đã đọc và đối chiếu unread-count. Gửi lại cùng sự kiện demo để kiểm tra chống trùng. Email thực gửi và số lần retry cần bằng chứng runtime. Kết quả chạy của các kịch bản này cần ghi vào chương 06.
