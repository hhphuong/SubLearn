# SubLearn — Học ngoại ngữ qua phụ đề video YouTube

**SubLearn** là ứng dụng web giúp người học ngoại ngữ (**Tiếng Anh**, **Tiếng Nhật**, **Tiếng Việt**) học từ vựng và luyện nghe trực tiếp qua video YouTube với phụ đề song ngữ đồng bộ theo thời gian thực và tra từ theo ngữ cảnh bằng AI (Google Gemini).

---

## Tính năng chính

- **Tạo bài học từ link YouTube**: Tự động tải phụ đề (thủ công hoặc tự động) qua `yt-dlp`, phân đoạn câu, tách từ (tokenize) và dịch sang ngôn ngữ đích bằng Google Gemini.
- **Trình phát đồng bộ phụ đề**:
  - Tự động cuộn và làm nổi bật câu phụ đề đang phát.
  - Bấm vào bất kỳ câu nào để tua nhanh đến thời điểm đó.
  - Nhấn giữ câu phụ đề để bật chế độ **lặp lại câu (loop)** giúp luyện nghe sâu.
  - Bật/tắt hiển thị bản dịch song ngữ và tải phụ đề về máy dưới dạng file `.srt` (bản gốc, bản dịch hoặc song ngữ).
- **Tra từ theo ngữ cảnh & Lưu từ điển cá nhân**:
  - Bấm vào từng từ trên phụ đề để xem nghĩa trong ngữ cảnh câu hiện tại, nghĩa từ điển chung và câu ví dụ (có cache vào DB để tiết kiệm quota).
  - Lưu từ vựng kèm câu ngữ cảnh gốc vào từ điển cá nhân, phân loại theo ngôn ngữ và thư mục (folder).
- **Thư viện & Lịch sử học**:
  - Tự động lưu vị trí đang xem dở (`lastPositionMs`) để tiếp tục học bất cứ lúc nào.
  - Quản lý danh sách video đã thêm vào thư viện và lịch sử xem gần đây.
- **Xác thực người dùng**: Đăng ký, đăng nhập và tự động làm mới phiên bằng JWT (`accessToken` + `refreshToken`).

---

## Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| **Backend** | Java 17, Spring Boot 4.1, Spring Security (JWT), Spring Data JPA, Flyway |
| **NLP & Phụ đề** | `yt-dlp`, Apache OpenNLP (English), Kuromoji IPADIC (Japanese) |
| **AI Dịch & Tra từ** | Google Gemini API (`generateContent` với JSON output) |
| **Database** | MySQL 8.4 (`utf8mb4`) |
| **Frontend** | Vue 3 (Composition API), TypeScript, Vite, Pinia, Vue Router, Axios, Bootstrap 5 |
| **Triển khai** | Docker, Docker Compose, Nginx (phục vụ SPA & reverse proxy `/api/*` sang Backend) |

---

## Cấu trúc thư mục

```text
SubLearn/
├── BE/SubLearn/                 # Spring Boot Backend (Java 17, Maven)
│   ├── src/main/java/           # Source code (auth, video, ingestion, gemini, nlp, dictionary...)
│   ├── src/main/resources/
│   │   ├── db/migration/        # Flyway SQL migrations (V1 -> V15)
│   │   └── application.properties
│   ├── Dockerfile
│   └── pom.xml
├── FE/                          # Vue 3 + TypeScript Frontend
│   ├── src/                     # Views, components, Pinia stores, API clients, composables
│   ├── nginx.conf               # Cấu hình Nginx reverse proxy /api/ -> backend:8080
│   ├── Dockerfile
│   └── package.json
├── tests/                       # Frontend unit tests (Vitest)
├── secrets/                     # Chứa youtube-cookies.txt (tùy chọn, đã gitignore)
├── .env.example                 # File mẫu cấu hình biến môi trường
└── docker-compose.yml           # Cấu hình chạy toàn bộ hệ thống (MySQL + BE + FE)
```

---

## Hướng dẫn cài đặt và chạy bằng Docker Compose (Khuyên dùng)

### 1. Yêu cầu
- Đã cài đặt và khởi động **Docker Desktop** (Engine ở trạng thái *Running*).
- Có **Gemini API Key** (lấy miễn phí tại [Google AI Studio](https://aistudio.google.com/apikey)).

### 2. Tạo file cấu hình `.env`
Sao chép file `.env.example` thành `.env` ở thư mục gốc dự án:

```powershell
Copy-Item .env.example .env
```

Mở file `.env` và cập nhật các biến quan trọng:

```dotenv
# Bắt buộc - chuỗi ngẫu nhiên tối thiểu 32 ký tự (dùng để ký JWT HS256)
JWT_SECRET=change-me-to-a-long-random-string-at-least-32-chars

# Bắt buộc để dịch phụ đề khi tạo video và tra nghĩa từ vựng
GEMINI_API_KEY=AIzaSy...
```

> **Tùy chọn (YouTube Cookies):** Nếu một số video YouTube yêu cầu xác thực hoặc chặn bot, bạn có thể xuất cookies trình duyệt ra định dạng Netscape và đặt tại `./secrets/youtube-cookies.txt`. Nếu không có file này, hệ thống vẫn tự động chạy `yt-dlp` ở chế độ không cookies.

### 3. Khởi chạy hệ thống

```powershell
docker compose up -d --build
```

Kiểm tra trạng thái và theo dõi log khởi động của Backend:

```powershell
docker compose ps
docker compose logs -f backend
```

Đợi đến khi thấy dòng `Started SubLearnApplication` là hệ thống đã sẵn sàng:
- **Frontend (Web UI):** http://localhost:5173
- **Backend API (qua Nginx proxy):** http://localhost:5173/api
- **Backend API trực tiếp:** http://localhost:8080

---

## Kết nối Database từ công cụ quản trị (DBeaver, DataGrip, MySQL Workbench...)

Container MySQL được mở cổng ra máy host với thông tin mặc định (có thể tùy chỉnh trong `.env`):

| Thông số | Giá trị mặc định |
|---|---|
| **Host** | `localhost` (hoặc `127.0.0.1`) |
| **Port** | `3306` (`MYSQL_PORT` trong `.env`) |
| **Database** | `sublearn` (`DB_NAME`) |
| **Username** | `sublearn` (`DB_USERNAME`) |
| **Password** | `sublearn` (`DB_PASSWORD`) |
| **Root Password** | `sublearn` (`MYSQL_ROOT_PASSWORD`) |

- **JDBC URL:** `jdbc:mysql://localhost:3306/sublearn?allowPublicKeyRetrieval=true&useSSL=false`
- Nếu dùng **DBeaver / DataGrip** gặp lỗi *"Public Key Retrieval is not allowed"*, hãy vào tab **Driver properties** và đặt `allowPublicKeyRetrieval = true`, `useSSL = false`.

---

## Chạy môi trường Development (Local không qua Docker)

### Backend (`BE/SubLearn`)
Yêu cầu: **JDK 17**, **MySQL 8.x** đang chạy, và **`yt-dlp`** đã có trong `PATH`.

```powershell
cd BE\SubLearn
$env:JWT_SECRET="your-secret-key-with-at-least-32-characters"
$env:GEMINI_API_KEY="your-gemini-api-key"
.\mvnw.cmd spring-boot:run
```

Chạy bộ unit test Backend:
```powershell
cd BE\SubLearn
.\mvnw.cmd test
```

### Frontend (`FE`)
Yêu cầu: **Node.js** `^22.18.0` hoặc `>=24.12.0`.

```powershell
cd FE
npm install
npm run dev
```

Chạy unit test và kiểm tra kiểu TypeScript:
```powershell
cd FE
npm test
npm run type-check
```

---

## Xử lý các lỗi thường gặp (Troubleshooting)

1. **`bind: Only one usage of each socket address...` (Trùng cổng 8080, 5173 hoặc 3306)**
   - Một chương trình khác trên máy đang chiếm cổng. Bạn có thể tắt chương trình đó hoặc đổi cổng trong `.env`:
     ```dotenv
     FRONTEND_PORT=5173
     BACKEND_PORT=8081
     MYSQL_PORT=3307
     ```
   - Sau đó chạy lại `docker compose up -d`. (Trình duyệt gọi API qua Nginx ở cổng `FRONTEND_PORT` nên việc đổi `BACKEND_PORT` không ảnh hưởng tới Frontend).

2. **`FlywayValidateException: Migration checksum mismatch`**
   - Xảy ra khi volume MySQL lưu schema từ phiên bản cũ không khớp với các file migration hiện tại. Để xóa volume DB cũ và khởi tạo lại:
     ```powershell
     docker compose down -v
     docker compose up -d --build
     ```

3. **Thông báo `"có lỗi khi tạo video! vui lòng thử lại."` khi tạo video**
   - Kiểm tra `GEMINI_API_KEY` trong `.env` đã được điền đúng và còn hiệu lực/quota hay chưa.
   - Kiểm tra link YouTube có hợp lệ, công khai và có phụ đề (cc/auto-caption) hay không.
   - Xem chi tiết nguyên nhân trong log Backend: `docker compose logs backend --tail 100`.
