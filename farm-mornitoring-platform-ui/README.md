# AgriUAV — Farm Monitoring UI

Frontend cho nền tảng IoT nông nghiệp + UAV (chỉ quản lý / giám sát, **không điều khiển bay**).

Code viết đơn giản cho fresher: mỗi trang tự load data bằng `useEffect`, gọi API trong `src/api.ts`.

## Chạy UI

```bash
npm install
npm run dev
```

Mở http://localhost:5173

Vite sẽ proxy `/api` sang backend `http://localhost:5255`.

## Login

UI gọi `POST /api/auth/login`:

```json
{ "email": "admin@farm.com", "password": "..." }
```

Backend nên trả:

```json
{
  "token": "jwt-token",
  "user": {
    "id": "1",
    "email": "admin@farm.com",
    "fullName": "Farm Admin",
    "role": "FarmAdministrator"
  }
}
```

`role` chỉ có 2 giá trị:

- `FarmAdministrator`
- `UavOperator`

Nếu tên role backend khác, sửa trong `src/types.ts`.

## File quan trọng

- `src/api.ts` — tất cả URL API (sửa ở đây nếu backend khác)
- `src/types.ts` — kiểu dữ liệu
- `src/AuthContext.tsx` — login / logout / lưu token
- `src/App.tsx` — routing + phân quyền
- `src/pages/...` — từng màn hình

## API UI đang gọi

| Method | URL |
| --- | --- |
| POST | `/api/auth/login` |
| GET | `/api/auth/me` |
| GET/POST/PUT | `/api/farms` |
| PATCH | `/api/farms/{id}/status` |
| GET/POST/PUT | `/api/zones` |
| PATCH | `/api/zones/{id}/status` |
| GET/POST/PUT | `/api/sensor-nodes` |
| GET | `/api/sensor-readings` |
| GET | `/api/alerts` |
| PATCH | `/api/alerts/{id}/status` |
| GET/POST | `/api/alert-thresholds` |
| GET | `/api/notifications` |
| PATCH | `/api/notifications/{id}/read` |
| GET | `/api/reports/summary` |
| GET/POST/PUT | `/api/users` |
| PUT | `/api/account` |
| GET/POST/PUT | `/api/uavs` |
| GET/POST/PUT | `/api/gateways` |
| GET/POST/PUT | `/api/missions` |
| PATCH | `/api/missions/{id}/status` |
| GET | `/api/missions/{id}/results` |
| GET | `/api/dashboard/admin` |
| GET | `/api/dashboard/operator` |

Backend cần bật CORS nếu không dùng proxy Vite.

Bật CORS ASP.NET ví dụ:

```csharp
builder.Services.AddCors(o => o.AddDefaultPolicy(p =>
    p.WithOrigins("http://localhost:5173").AllowAnyHeader().AllowAnyMethod()));
app.UseCors();
```
