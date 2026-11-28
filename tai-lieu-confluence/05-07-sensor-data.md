# 5.7 Sensor Data

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phụ trách service, cá nhân chưa xác minh.

Trang con của [05 System Implementation](05-system-implementation.md). Phạm vi theo đề cương gồm F06, F07, F19 và F20. [Mã nguồn service tại 612e00b](https://github.com/huylam0703/version7/tree/612e00b/farm-monitoring-platform/services/sensor-data-service).

## Trách nhiệm nghiệp vụ

Tiếp nhận và truy vấn dữ liệu đo. Khi gateway gửi lại, việc duy trì định danh phù hợp giữa các lần thử giúp kiểm tra và xử lý dữ liệu trùng theo hợp đồng.

## API và tích hợp

ReadingV1Controller hỗ trợ ghi đơn, batch hoặc sync-batches, truy vấn theo node, latest, thống kê, time-series, invalid và quality. SensorReadingController còn có đường tương thích riêng. Đọc lại phản hồi từng bản ghi khi batch có lỗi từng phần. Để xác định URL sử dụng từ trình duyệt, đối chiếu controller đích với route và transform của API Gateway. Tra cứu hợp đồng ở mục 3.7.

## Kho dữ liệu

sensor_data_db. Script tạo dbo.sensor_readings và dbo.sync_batches. Schema metadata và truy vấn nằm ở deploy/sql. ORM cấu hình validate nên cần chuẩn bị schema đúng trước khi chạy. Chi tiết ERD và kiểu dữ liệu thuộc mục 3.6. Khi kiểm tra lỗi ghi, xác nhận connection đang trỏ đúng database trước khi so sánh schema.

## Cấu hình

DB_PASSWORD và các Spring datasource override nếu dùng Compose. Tra cấu hình của chính service để tìm JDBC đang được áp dụng. Xem thêm [8.4](08-technical-documentation.md#84-danh-sách-biến-môi-trường).

## Kiểm thử và giới hạn

Gửi một batch hợp lệ, gửi lại với cùng định danh rồi so sánh số bản ghi. Thử một mẫu sai loại hoặc sai tham chiếu để kiểm tra lỗi từng mục. Xác minh producer readings.synced qua log/broker trước khi nghiệm thu cảnh báo tự động. Kết quả chạy của các kịch bản này cần ghi vào chương 06.
