# 5.5 Farm Device

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phụ trách service, cá nhân chưa xác minh.

Trang con của [05 System Implementation](05-system-implementation.md). Phạm vi theo đề cương gồm F03, F05, F12, F13 và F18. [Mã nguồn service tại 612e00b](https://github.com/huylam0703/version7/tree/612e00b/farm-monitoring-platform/services/farm-device-service).

## Trách nhiệm nghiệp vụ

Quản lý nông trại, vùng, node, kênh đo, UAV và gateway. Cấp metadata thiết bị và kiểm tra tham chiếu dùng trong lập nhiệm vụ.

## API và tích hợp

FarmController và ZoneController quản lý tài nguyên theo controller hiện có. Node, UAV và Gateway có tuyến đọc, đăng ký, cập nhật. InternalController tra node/gateway theo UID và mission-check. MissionStatusListener cập nhật trạng thái thiết bị theo event. Để xác định URL sử dụng từ trình duyệt, đối chiếu controller đích với route và transform của API Gateway. Tra cứu hợp đồng ở mục 3.7.

## Kho dữ liệu

farm_device_db. Entity dùng tên FD_Farms, FD_Zones, FD_SensorNodes, FD_NodeChannels, FD_Uavs, FD_Gateways và FD_SensorTypes. SensorTypeSeeder nạp danh mục cảm biến. Chi tiết ERD và kiểu dữ liệu thuộc mục 3.6. Khi kiểm tra lỗi ghi, xác nhận connection đang trỏ đúng database trước khi so sánh schema.

## Cấu hình

DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, SERVER_PORT và SPRING_PROFILES_ACTIVE. Xem thêm cấu hình JWT và RabbitMQ ở profile đang chạy. Xem thêm [8.4](08-technical-documentation.md#84-danh-sách-biến-môi-trường).

## Kiểm thử và giới hạn

Kiểm tra node thuộc vùng, UID tra được và tham chiếu nhiệm vụ đúng. Khi thiết bị không khả dụng, kiểm tra trạng thái hiện tại, thao tác gần nhất và event mission.status_changed. Khả năng tra từ cache cần xác minh bằng cấu hình runtime. Kết quả chạy của các kịch bản này cần ghi vào chương 06.
