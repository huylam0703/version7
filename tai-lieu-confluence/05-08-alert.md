# 5.8 Alert

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phụ trách service, cá nhân chưa xác minh.

Trang con của [05 System Implementation](05-system-implementation.md). Phạm vi theo đề cương gồm F08 và F09. [Mã nguồn service tại 612e00b](https://github.com/huylam0703/version7/tree/612e00b/farm-monitoring-platform/services/alert-service).

## Trách nhiệm nghiệp vụ

Tiếp nhận dữ liệu đo qua listener và đánh giá quy tắc phát hiện. Quản trị viên tra cứu, xác nhận đã tiếp nhận và đóng cảnh báo sau xử lý.

## API và tích hợp

AlertController có tạo, liệt kê, đọc theo ID, lọc theo node/trạng thái, acknowledge và resolve. SensorReadingListener nghe readings.synced. AlertEventPublisher gửi alert.created sau commit. Consumer node.state_updated và tuyến CRUD thresholds cần xác minh. Để xác định URL sử dụng từ trình duyệt, đối chiếu controller đích với route và transform của API Gateway. Tra cứu hợp đồng ở mục 3.7.

## Kho dữ liệu

alert_db. Alert entity ánh xạ dbo_AL_Alerts. Kiểm tra metadata của cảnh báo, trạng thái và quan hệ với node trước khi bổ sung bộ lọc. Chi tiết ERD và kiểu dữ liệu thuộc mục 3.6. Khi kiểm tra lỗi ghi, xác nhận connection đang trỏ đúng database trước khi so sánh schema.

## Cấu hình

MSSQL_SA_PASSWORD, RABBITMQ_HOST, RABBITMQ_PORT, RABBITMQ_USERNAME và RABBITMQ_PASSWORD. Quy tắc ngưỡng đối chiếu SensorAlertProperties. Xem thêm [8.4](08-technical-documentation.md#84-danh-sách-biến-môi-trường).

## Kiểm thử và giới hạn

Thử dữ liệu trong và ngoài ngưỡng, thao tác acknowledge, resolve và xác minh một thông báo được tạo. Kiểm tra riêng giao hàng của producer rồi đến consumer. Lệnh gọi publisher sau commit có thể cần xử lý vận hành nếu broker gián đoạn. Kết quả chạy của các kịch bản này cần ghi vào chương 06.
