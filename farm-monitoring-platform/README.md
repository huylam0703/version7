# IoT Platform – UAV giám sát nông trại

Monorepo gồm 7 service (.NET và Java), thư viện dùng chung cho từng ngôn ngữ, hợp đồng chung và hạ tầng chạy local.

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `contract/` | Hợp đồng chung: `CONTRACT.md`, schema JSON, file OpenAPI của từng service. **Sửa ở đây trước, code sau.** |
| `libs/dotnet/` | `Iot.Common` (.NET) |
| `libs/java/` | `iot-common` (Java) |
| `services/` | 7 service, mỗi thư mục là một project độc lập |
| `db/` | Script tạo 6 database cho môi trường local |
| `docs/` | Thiết kế CSDL, ERD |
| `scripts/` | Script tiện ích (sinh khóa JWT dev...) |

## Chạy lần đầu

```bash
cp .env.example .env            # đổi SA_PASSWORD nếu muốn
./scripts/gen-jwt-keys.sh       # sinh cặp khóa RSA dev vào ./keys (không commit)
docker compose up -d sqlserver db-init
```

Sau đó kiểm tra: `docker compose logs db-init` phải báo tạo đủ 6 database.

## Phân công (điền vào)

| Service | Ngôn ngữ | Port | Người phụ trách chính |
|---|---|---|---|
| api-gateway | .NET | 8080 | |
| user-auth | .NET | 8081 | |
| farm-device | .NET | 8082 | |
| mission | Java | 8083 | |
| sensor-data | Java | 8084 | |
| alert | Java | 8085 | |
| notification | .NET | 8086 | |

## Quy tắc làm việc

1. Không push thẳng vào `main`. Mỗi tính năng một nhánh ngắn (`feature/<service>-<mo-ta>`), merge bằng Pull Request.
2. Đổi API: sửa `contract/` trước (cần người của cả hai ngôn ngữ duyệt), rồi mới sửa code.
3. Không commit `.env`, thư mục `keys/`, hay mật khẩu thật.
4. Mỗi service có database riêng, không truy cập database của service khác.
