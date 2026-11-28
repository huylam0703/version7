# 5.4 User Auth

**Trạng thái:** Đối chiếu tĩnh · **Cập nhật:** 27/11/2026 · **Phụ trách:** Nhóm phụ trách service, cá nhân chưa xác minh.

Trang con của [05 System Implementation](05-system-implementation.md). Phạm vi theo đề cương gồm F01 và F02. [Mã nguồn service tại 612e00b](https://github.com/huylam0703/version7/tree/612e00b/farm-monitoring-platform/services/UserAuthService.Api).

## Trách nhiệm nghiệp vụ

Xử lý phiên đăng nhập và danh tính người dùng, cấp token cho các client dịch vụ và simulator. Phân quyền cần được xác minh ở endpoint đích vì mỗi service có trách nhiệm kiểm tra access token.

## API và tích hợp

AuthController có login, refresh và logout. UsersController có danh sách, hồ sơ cá nhân, tạo và cập nhật, khóa/mở khóa. TokenController công bố route token qua connect hoặc oauth2. PublishKeyController cung cấp JWKS và metadata. SimulatorClientsController quản lý trạng thái client. Để xác định URL sử dụng từ trình duyệt, đối chiếu controller đích với route và transform của API Gateway. Tra cứu hợp đồng ở mục 3.7.

## Kho dữ liệu

user_auth_db. Script 001 tạo UA_Roles, UA_Users và UA_RefreshTokens. Kiểm tra ánh xạ schema trong EF khi bổ sung đối tượng mới. Chi tiết ERD và kiểu dữ liệu thuộc mục 3.6. Khi kiểm tra lỗi ghi, xác nhận connection đang trỏ đúng database trước khi so sánh schema.

## Cấu hình

JWT_ACCESS_MINUTES theo hợp đồng, AUTH_JWKS_URI ở phía consumer. Mở cấu hình .NET để kiểm tra tên profile và connection string thực tế. Xem thêm [8.4](08-technical-documentation.md#84-danh-sách-biến-môi-trường).

## Kiểm thử và giới hạn

Đăng nhập với hai vai trò, refresh, logout, thử tài khoản đã khóa và kiểm tra quyền trên endpoint quản trị. Khi cấu hình issuer, audience hoặc khóa lệch giữa các service, kiểm tra phản hồi xác thực trước logic nghiệp vụ. Kết quả chạy của các kịch bản này cần ghi vào chương 06.
