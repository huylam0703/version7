# 5.6 Mission

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phụ trách service, cá nhân chưa xác minh.

Trang con của [05 System Implementation](05-system-implementation.md). Phạm vi theo đề cương gồm F14 đến F17. [Mã nguồn service tại 612e00b](https://github.com/huylam0703/version7/tree/612e00b/farm-monitoring-platform/services/mission-service).

## Trách nhiệm nghiệp vụ

Quản lý kế hoạch và vòng đời nhiệm vụ. Lưu waypoint, node được giao, tiến độ và trạng thái thực thi để người vận hành có thể theo dõi và tiếp tục xử lý khi gián đoạn.

## API và tích hợp

MissionController triển khai tạo, đọc, sửa, plan, start, cancel, kết quả và status logs. Các controller liên quan xử lý ACK, heartbeat, progress và recovery. Outbox lưu sự kiện chờ phát. Scheduler xử lý ACK và heartbeat theo cấu hình. Để xác định URL sử dụng từ trình duyệt, đối chiếu controller đích với route và transform của API Gateway. Tra cứu hợp đồng ở mục 3.7.

## Kho dữ liệu

mission_db. Entity có dbo_M_Missions, dbo_M_Waypoints, dbo_M_MissionNodes, dbo_M_MissionStatusLogs và OutboxEvent. Tham chiếu Farm Device được kiểm tra qua client dịch vụ. Chi tiết ERD và kiểu dữ liệu thuộc mục 3.6. Khi kiểm tra lỗi ghi, xác nhận connection đang trỏ đúng database trước khi so sánh schema.

## Cấu hình

FARM_DEVICE_BASE_URL, MISSION_REFERENCE_VALIDATION_MODE, AUTH_JWKS_URI và UAV_CLIENT_ID. Dùng cấu hình môi trường để xác định URL, thông tin xác thực và timeout. Xem thêm [8.4](08-technical-documentation.md#84-danh-sách-biến-môi-trường).

## Kiểm thử và giới hạn

Kiểm tra tạo nhiệm vụ với tập node hợp lệ, chuyển trạng thái, phát message, nhận ACK và ghi kết quả. Khi kẹt tiến độ, đối chiếu missionId, executionId, log outbox, heartbeat và quyền recovery. Có mã test cho outbox và các luồng nhiệm vụ. Kết quả chạy của các kịch bản này cần ghi vào chương 06.
