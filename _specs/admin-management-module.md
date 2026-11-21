# Spec cho `admin-management-module`

- **branch:** `claude/feature/admin-management-module`
- **figma_component (nếu có):** không có

---

## Tóm tắt
- Xây dựng phân hệ quản trị (**Admin Portal**) chuyên biệt cho **SubLearn** nhằm kiểm soát phân quyền người dùng, giám sát vận hành pipeline xử lý video/phụ đề (`yt-dlp`, RabbitMQ, Google Gemini), quản trị chất lượng dữ liệu dùng chung (video, phụ đề, bản dịch, cache tra từ AI) và cấu hình các tích hợp nhạy cảm mà không cần thao tác trực tiếp vào database hay SSH vào server.
- Hiện tại `AppUser.java` và `SecurityConfig.java` chưa có phân vai trò (`role`) và trạng thái khóa tài khoản — module Admin sẽ bổ sung nền tảng RBAC trước, sau đó cung cấp giao diện quản trị tách biệt dưới route `/admin/*` (FE) và nhóm API `/api/admin/*` (BE).

---

## Kiến trúc các Module Admin cần quản lý

```text
Admin Portal (/admin)
├── 1. Phân quyền & Bảo mật (RBAC Foundation)
├── 2. Dashboard Tổng quan & Sức khỏe hệ thống (Overview & Health)
├── 3. Quản lý Người dùng (Users & Sessions)
├── 4. Quản lý Video, Phụ đề & Bản dịch (Videos & Subtitles)
├── 5. Quản lý Job & Hàng đợi RabbitMQ (Processing Jobs & DLQ)
├── 6. Quản lý Cache Nghĩa từ AI & Từ điển (Word Cache & Dictionary Insights)
├── 7. Cấu hình Hệ thống & Tích hợp (Gemini, yt-dlp Cookies, Quota, Ngôn ngữ)
└── 8. Nhật ký Kiểm toán & Phân tích Lỗi (Audit Logs & Failure Analytics)
```

---

## Yêu cầu chức năng

### Module 1: Phân quyền & Bảo mật Admin (RBAC Foundation)
- Mô hình **2 vai trò**: `USER` và `ADMIN`, kèm trạng thái tài khoản (`ACTIVE`, `LOCKED`) trên `AppUser.java`.
- **JWT Stateless**: nhúng claim `role` vào access token tại `JwtService.java` để `JwtAuthenticationFilter.java` phân quyền trực tiếp mà không truy vấn DB ở mỗi request (chấp nhận độ trễ tối đa 15 phút theo Access Token TTL). Khi tài khoản bị khóa (`LOCKED`) hoặc thay đổi vai trò, hệ thống thu hồi toàn bộ `RefreshToken` và chặn `login` / `refresh` tại `AuthService.java`.
- Chặn toàn bộ endpoint `/api/admin/**` ở backend cho phép duy nhất vai trò `ADMIN` (trả về `401 Unauthorized` nếu chưa đăng nhập, `403 Forbidden` nếu là `USER`).
- **Admin Bootstrap qua `.env`**: cấu hình `ADMIN_BOOTSTRAP_EMAIL` và `ADMIN_BOOTSTRAP_PASSWORD`. Khi ứng dụng khởi động, nếu chưa có tài khoản với email đó thì tự động tạo mới với vai trò `ADMIN`; nếu email đã tồn tại thì nâng quyền lên `ADMIN`. Tuyệt đối không cho phép đăng ký vai trò `ADMIN` qua `/api/auth/register`.
- Trên frontend (`FE/src/router/index.ts`, `FE/src/components/layout/AppNavbar.vue`): bổ sung route guard `requiresAdmin`, layout riêng có sidebar cho `/admin/*`, và chỉ hiển thị lối vào *Quản trị* khi user có quyền `ADMIN`.

### Module 2: Dashboard Tổng quan & Sức khỏe hệ thống (Overview & System Health)
- **Chỉ số KPI tổng hợp** (lọc theo 24h / 7 ngày / 30 ngày / toàn thời gian):
  - Tổng số người dùng, số người dùng đăng ký mới, số người dùng hoạt động gần đây.
  - Tổng số video phân theo trạng thái (`READY`, `PROCESSING`, `PENDING`, `FAILED`, và số lượng đã xóa mềm).
  - Tổng số `ProcessingJob` theo loại (`INGEST_VIDEO`, `TRANSLATE_SUBTITLES`, `RETRANSLATE_SUBTITLES`) và tỉ lệ thành công / thất bại.
  - Tổng số bản ghi `WordMeaningCache` (`READY` vs `FAILED`) và tổng số từ vựng đã lưu (`DictionaryEntry`).
- **Biểu đồ xu hướng**: số video mới tạo, số job lỗi theo ngày và phân bố nguyên nhân lỗi theo `FailureReason` (`FailureReason.java`).
- **Giám sát sức khỏe phụ thuộc (System Health)**:
  - Trạng thái kết nối **MySQL** và **RabbitMQ** (chỉ hiển thị số lượng message đang tồn trong 3 queue tại `RabbitMqConfig.java`: `sublearn.video.jobs`, `sublearn.video.jobs.retry`, `sublearn.video.jobs.dlq`).
  - Trạng thái **`yt-dlp`** (đã cài đặt, phiên bản, có đang nạp `youtube-cookies.txt` hay không).
  - Trạng thái **Google Gemini API** (đã cấu hình API key, model hiện tại, tỉ lệ gặp rate limit `429` gần đây).

### Module 3: Quản lý Người dùng (User Management)
- Xem danh sách người dùng có phân trang, tìm kiếm theo email / tên hiển thị, lọc theo vai trò (`USER` / `ADMIN`) và trạng thái (`ACTIVE` / `LOCKED`).
- Xem trang chi tiết một người dùng:
  - Thông tin tài khoản, vai trò, trạng thái, thời gian tạo/cập nhật, số phiên đăng nhập (`RefreshToken`) còn hiệu lực.
  - Thống kê học tập: số video đã thêm vào thư viện (`UserVideo`), số video do người dùng submit lần đầu, số từ vựng (`DictionaryEntry`) và thư mục (`DictionaryFolder`) đã tạo, lịch sử xem gần đây (`VideoWatchHistory`).
- Thao tác quản trị (mọi thao tác nhạy cảm đều xác nhận qua popup modal `FE/src/components/common/AppModal.vue`):
  - **Khóa / Mở khóa tài khoản (`LOCKED` / `ACTIVE`)**: **không hỗ trợ xóa tài khoản người dùng** để bảo toàn dữ liệu lịch sử, video đã submit và nhật ký kiểm toán. Khi khóa tài khoản phải lập tức thu hồi toàn bộ `RefreshToken` và chặn đăng nhập / làm mới token mới.
  - **Thay đổi vai trò** (`USER` ↔ `ADMIN`): khi hạ quyền một `ADMIN`, thu hồi toàn bộ `RefreshToken` của tài khoản đó.
  - **Cưỡng chế đăng xuất toàn bộ thiết bị** (revoke all refresh tokens).
  - **Gửi link đặt lại mật khẩu qua SMTP (triển khai từ Phase 1)**:
    - Tích hợp `spring-boot-starter-mail` cấu hình qua `.env` (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`).
    - Admin kích hoạt gửi email đặt lại mật khẩu cho user (`POST /api/admin/users/{id}/send-password-reset`); token đặt lại mật khẩu lưu dưới dạng hash trong DB, hiệu lực **30 phút** và chỉ **dùng 1 lần**.
    - Cung cấp trang FE công khai `/reset-password?token=...` và endpoint công khai tương ứng để user đặt mật khẩu mới; sau khi đổi mật khẩu thành công phải tự động thu hồi toàn bộ `RefreshToken` cũ của user đó.
    - Chưa làm luồng "Quên mật khẩu" tự phục vụ trên trang đăng nhập (`LoginView`) trong spec này.
    - Nếu môi trường chưa cấu hình SMTP, ứng dụng vẫn khởi động bình thường và trả lỗi rõ ràng hiển thị qua popup `AppModal.vue` khi Admin bấm gửi mail.

### Module 4: Quản lý Video, Phụ đề & Bản dịch (Video & Subtitle Management)
- Do `Video` (`Video.java`) là tài nguyên dùng chung toàn hệ thống theo `youtube_video_id`, Admin cần kiểm soát tập trung:
- Danh sách toàn bộ video trong hệ thống: tìm kiếm theo tiêu đề / `youtubeVideoId` / email người yêu cầu; lọc theo `status`, `sourceLanguage`, `failureReason`, và trạng thái hiển thị (**đang hoạt động** / **đã xóa mềm**); hiển thị danh sách ngôn ngữ đã có bản dịch và số user đang lưu video.
- Xem chi tiết video:
  - Metadata (URL, thời lượng, tổng số dòng phụ đề, trạng thái xử lý, danh sách các `ProcessingJob` liên quan).
  - Kiểm tra nội dung từng câu phụ đề gốc (`SubtitleLine`), token NLP đã tách (`SubtitleToken`), và bản dịch theo từng ngôn ngữ đích (`SubtitleLineTranslation`).
- Thao tác quản trị:
  - **Chỉnh sửa thủ công cả phụ đề gốc (`sourceText`) lẫn bản dịch đích (`targetText`)**: khi sửa `sourceText` của một dòng phụ đề, hệ thống tự động chạy lại NLP tách từ (`TokenizerFactory`) để cập nhật lại danh sách `SubtitleToken` của dòng đó mà vẫn giữ nguyên `SubtitleLine.id`.
  - **Kích hoạt tải lại phụ đề gốc (Re-ingest)** hoặc **Dịch lại theo ngôn ngữ đích (Force Re-translate)**.
  - **Xóa bản dịch của một ngôn ngữ** cụ thể mà không ảnh hưởng phụ đề gốc.
  - **Xóa mềm Video (`deleted_at`) & Khôi phục (Restore)**: không xóa cứng dữ liệu để giữ nguyên phụ đề và ngữ cảnh từ điển (`dictionary_entry_context`) đã lưu của người dùng; video đã xóa mềm bị ẩn khỏi giao diện người học và không thể mở xem. Khi user dán lại link của một `youtube_video_id` đã bị Admin xóa mềm, hệ thống **chặn submit lại và báo lỗi rõ ràng** (ví dụ *"Video hiện không khả dụng"*); chỉ Admin mới có quyền khôi phục (`Restore`) từ màn Admin.
  - *(Ngoài phạm vi spec)* Chưa làm tính năng đánh dấu "Video nổi bật / Đề xuất" trên trang chủ (`HomeView`) trong spec này để giữ gọn phạm vi.

### Module 5: Quản lý Job xử lý & Hàng đợi (Processing Jobs & Queue Management)
- Quản trị theo hướng **DB-driven** trực tiếp trên bảng `processing_job` (`ProcessingJob.java`):
- Danh sách toàn bộ job: lọc theo `jobType`, `status`, `failureReason`, `videoId`, người yêu cầu, khoảng thời gian; hiển thị `progressPercent`, `retryCount/maxRetries`, thời điểm tạo/cập nhật và `failureMessage` chi tiết.
- Thao tác quản trị:
  - **Thử lại thủ công (Retry)** một job `FAILED`: giữ nguyên bản ghi job `FAILED` làm lịch sử, tạo job mới qua `ProcessingJobService.retryJob()` và đẩy vào hàng đợi RabbitMQ.
  - **Bulk Retry**: cho phép chọn/retry nhiều job `FAILED` cùng lúc, nhưng bắt buộc có **giới hạn số lượng tối đa N job mỗi lượt** để tránh bão request tới YouTube và Gemini.
  - **Hủy / Đánh dấu thất bại** các job bị treo lâu ở trạng thái `PENDING` / `PROCESSING` (bổ trợ thao tác tay cho `StaleIngestionRecovery.java`).
  - **Giám sát & Dọn Dead Letter Queue (`sublearn.video.jobs.dlq`)**: hiển thị số lượng message đang tồn trong các queue (`sublearn.video.jobs`, `sublearn.video.jobs.retry`, `sublearn.video.jobs.dlq`) và hỗ trợ **Dọn sạch DLQ (Purge)** có xác nhận qua popup modal — không duyệt hay requeue từng message riêng lẻ trong DLQ từ UI.

### Module 6: Quản lý Cache AI & Phân tích Từ điển (Word Cache & Dictionary Management)
- Quản trị bảng `word_meaning_cache` (`WordMeaningCache.java`) để kiểm soát chất lượng tra từ và tiết kiệm quota Gemini:
- Danh sách bản ghi cache tra từ: tìm theo `normalizedWord`, lọc theo `status` (`READY` / `FAILED`), cặp ngôn ngữ nguồn–đích, xem thời điểm `lastFetchedAt` / `expiresAt`.
- Thao tác quản trị:
  - **Xóa hàng loạt cache bị lỗi (`FAILED`)** chỉ với một nút bấm để người dùng tra lại không bị dính lỗi cũ.
  - **Xóa một bản ghi cache cụ thể** để ép hệ thống gọi lại Gemini ở lần tra tiếp theo.
  - **Chỉnh sửa thủ công nghĩa theo ngữ cảnh / nghĩa từ điển / ví dụ** khi AI giải thích sai hoặc thiếu tự nhiên.
- Xem thống kê tổng hợp từ vựng (Top từ được lưu nhiều nhất, phân bố theo cặp ngôn ngữ) — chỉ xem dữ liệu tổng hợp hoặc dữ liệu kiểm duyệt, tôn trọng tính riêng tư từ điển cá nhân của từng user.

### Module 7: Cấu hình Hệ thống & Tích hợp (System Settings & Integrations)
- Tập trung hóa các thiết lập hiện đang phụ thuộc vào `.env`, `BE/SubLearn/src/main/resources/application.properties` và `secrets/youtube-cookies.txt`. Các thành phần như `GeminiClient.java` và `YtDlpClient.java` cần đọc cấu hình động ở runtime (ưu tiên giá trị cấu hình trong DB, fallback về `.env` nếu chưa thiết lập trong DB):
- **Quản lý YouTube Cookies (`yt-dlp`)**:
  - Xem trạng thái file cookies (đã nạp / trống, thời điểm cập nhật gần nhất).
  - Tải lên hoặc dán nội dung `youtube-cookies.txt` mới qua Admin UI; validate định dạng Netscape, mã hóa lưu DB và đồng bộ ra file runtime quyền `600` để áp dụng ngay cho `YtDlpClient.java` mà không cần restart container; cung cấp nút **Kiểm tra `yt-dlp`** với một URL YouTube mẫu.
  - Tuyệt đối không trả ngược nội dung cookies thô về frontend sau khi đã lưu.
- **Cấu hình AI (Google Gemini)**:
  - Xem trạng thái kết nối, đổi `GEMINI_MODEL` (ví dụ `gemini-flash-lite-latest`, `gemini-flash-latest`), **đổi `GEMINI_API_KEY` trực tiếp qua Admin UI** (mã hóa AES-GCM bằng master key từ `.env` khi lưu DB; không bao giờ trả ngược API key gốc ra API response — chỉ hiển thị trạng thái đã cấu hình / dạng che ký tự), chỉnh timeout và nút **Test kết nối Gemini** (`GeminiClient.java`).
- **Giới hạn sử dụng theo ngày (Daily Quota per User)**:
  - Cấu hình hạn mức theo ngày cho mỗi `USER` thông qua Admin UI (ví dụ: tối đa số video submit mới/ngày, số lần bấm *Dịch lại phụ đề*/ngày, số lượt tra từ gọi Gemini mới/ngày).
  - Các lượt tra từ trúng `word_meaning_cache` ở trạng thái `READY` **không tính vào quota**; tài khoản `ADMIN` được **miễn quota**.
  - Khi vượt hạn mức trả về `429 Too Many Requests` kèm thông báo tiếng Việt rõ ràng.
- **Cấu hình Ngôn ngữ & Chính sách Job**:
  - Bật/tắt các ngôn ngữ đích được phép sử dụng trên FE/BE; điều chỉnh `maxRetries`, `retryDelayMs`, giới hạn Bulk Retry.

### Module 8: Nhật ký Kiểm toán & Giám sát Lỗi (Audit Logs & Error Monitoring)
- **Audit Log (bắt đầu ghi từ Phase 1, giao diện tra cứu ở Phase 3)**:
  - Tạo bảng `admin_audit_log` và tự động ghi nhận ở backend ngay từ Phase 1 đối với mọi thao tác làm thay đổi dữ liệu/cấu hình của Admin (`adminUserId`, loại hành động, đối tượng tác động, tóm tắt thay đổi trước/sau, thời gian, địa chỉ IP). Tuyệt đối không ghi mật khẩu, token, API key hay nội dung cookies vào log.
- **UI Tra cứu Audit Log & Giám sát Lỗi (Phase 3)**:
  - Bộ lọc Audit Log theo Admin thực hiện, loại hành động, đối tượng và khoảng thời gian (chỉ đọc).
  - **Bảng tổng hợp lỗi hệ thống**: gom nhóm lỗi theo `FailureReason` theo khung giờ/ngày để Admin phát hiện ngay khi YouTube chặn bot hàng loạt hoặc Gemini trả `429 Too Many Requests`.

---

## Đề xuất phân giai đoạn triển khai (Roadmap)

| Giai đoạn | Phạm vi ưu tiên | Giá trị mang lại |
|---|---|---|
| **Phase 1 (Core MVP)** | Module 1 (RBAC 2 vai trò, JWT stateless, bootstrap `.env`) + Module 2 (Dashboard cơ bản + số lượng queue RabbitMQ) + Module 3 (Quản lý User: khóa/mở khóa, không xóa user, gửi link reset password qua SMTP + `/reset-password`) + Module 5 (Quản lý `ProcessingJob` DB-driven, Retry & Bulk Retry giới hạn, Purge DLQ) + **Backend Audit Log (`admin_audit_log`)** | Kiểm soát truy cập, khóa user vi phạm, cấp lại mật khẩu qua email, xử lý ngay các video/job bị lỗi và ghi nhận đầy đủ nhật ký kiểm toán |
| **Phase 2 (Chất lượng nội dung)** | Module 4 (Quản lý Video: xóa mềm `deleted_at` + chặn submit lại + Restore, sửa `sourceText` tự re-tokenize & `targetText`, Re-ingest / Re-translate) + Module 6 (Quản lý `WordMeaningCache` & thống kê từ vựng) | Sửa lỗi phụ đề/dịch AI, dọn cache lỗi, ẩn và khôi phục video an toàn mà không mất ngữ cảnh từ điển |
| **Phase 3 (Vận hành chuyên sâu)** | Module 7 (Cấu hình động qua Admin UI: upload `youtube-cookies.txt`, đổi `GEMINI_MODEL`, đổi `GEMINI_API_KEY` mã hóa, quota ngày per-user miễn cache-hit & miễn `ADMIN`) + Module 8 (UI tra cứu Audit Logs & Failure Analytics) | Vận hành production không downtime, chống đốt quota Gemini, truy vết trực quan trên UI |

---

## Tham chiếu thiết kế Figma (chỉ khi có sử dụng)
- Không áp dụng — tái sử dụng hệ thống thiết kế hiện có của SubLearn (`--brand-*` trong `FE/src/assets/base.css`, Bootstrap 5, và hộp thoại xác nhận/cảnh báo dùng chung `FE/src/components/common/AppModal.vue`).

---

## Các trường hợp đặc biệt có thể xảy ra
- **Tự khóa / tự hạ quyền Admin**: Admin cố tự khóa tài khoản của chính mình hoặc tự hạ quyền xuống `USER`, hoặc hạ quyền tài khoản `ADMIN` duy nhất còn lại trong hệ thống -> hệ thống phải từ chối và báo lỗi rõ ràng.
- **Độ trễ hiệu lực JWT khi khóa user / thu hồi quyền Admin (Stateless 15 phút)**: Vì giữ `JwtAuthenticationFilter` hoàn toàn stateless (đọc `role` từ JWT claim), access token đang có hiệu lực vẫn dùng được tối đa đến hết TTL 15 phút; khi khóa/hạ quyền phải thu hồi toàn bộ `RefreshToken` và chặn `login`/`refresh` mới khi `status == LOCKED`.
- **Submit lại video đã bị Admin xóa mềm (`deleted_at`)**: Cột `youtube_video_id` là `UNIQUE`; khi user dán lại link video đã bị xóa mềm, hệ thống không được tạo bản ghi trùng gây lỗi DB và không được tự động khôi phục mà phải trả thông báo rõ ràng rằng video hiện không khả dụng.
- **Chỉnh sửa `sourceText` trên dòng phụ đề có ngữ cảnh từ điển**: Dòng phụ đề được tham chiếu bởi `DictionaryEntryContext` -> thao tác sửa `sourceText` phải giữ nguyên bản ghi `SubtitleLine` và chỉ tái tạo lại `SubtitleToken` thông qua `TokenizerFactory` đúng với `sourceLanguage` của video.
- **Link đặt lại mật khẩu SMTP hết hạn, dùng lại hoặc chưa cấu hình SMTP**: Token đặt lại mật khẩu quá 30 phút hoặc đã sử dụng phải bị từ chối rõ ràng; nếu chưa cấu hình SMTP ở môi trường dev/staging, hệ thống vẫn khởi động bình thường và báo lỗi rõ qua popup `AppModal.vue` khi Admin thao tác gửi email.
- **Xung đột đồng thời khi Admin Retry hoặc Chỉnh sửa**: Admin bấm Retry cho một job đúng lúc worker RabbitMQ đang xử lý nó, hoặc Admin sửa bản dịch thủ công trong khi một job `RETRANSLATE_SUBTITLES` đang chạy ghi đè lại bản dịch đó -> cần khóa trạng thái hợp lệ (chỉ retry job `FAILED`, chặn sửa khi đang có job active).
- **Bulk Retry gây bão quota Gemini / YouTube 429**: Admin retry quá nhiều job lỗi cùng lúc -> giới hạn số lượng tối đa N job mỗi lượt Bulk Retry.
- **Lộ bí mật vận hành (`youtube-cookies.txt`, `GEMINI_API_KEY`, master key mã hóa)**: Giá trị lưu DB phải được mã hóa AES-GCM bằng master key từ `.env`; không trả plaintext về API response; không ghi vào `ApiLoggingFilter.java` hay `admin_audit_log`; file cookies đồng bộ ra runtime cần validate định dạng Netscape và phân quyền `600`.
- **Dữ liệu lớn khi xem chi tiết phụ đề / cache / audit log**: Video dài hàng nghìn dòng phụ đề hoặc bảng `processing_job` / `word_meaning_cache` / `admin_audit_log` tăng lớn -> bắt buộc phân trang server-side và đánh index phù hợp trên các cột lọc.

---

## Tiêu chí chấp nhận
- Người dùng thường (`USER`) gọi bất kỳ API `/api/admin/**` nào đều nhận `403 Forbidden`, và khi truy cập trực tiếp URL `/admin/*` trên trình duyệt sẽ bị chặn/chuyển hướng rõ ràng.
- Tài khoản `ADMIN` được khởi tạo tự động từ `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` khi khởi động nếu chưa tồn tại (hoặc được nâng quyền nếu đã tồn tại); đăng nhập thành công thấy menu quản trị và truy cập được Dashboard cùng các module trong phạm vi từng Phase.
- Khi Admin khóa một tài khoản `USER`, toàn bộ `RefreshToken` của user đó bị thu hồi ngay lập tức, người dùng không thể đăng nhập lại hay làm mới token (và access token cũ hết hiệu lực hoàn toàn sau tối đa 15 phút). Hệ thống không có nút/API xóa tài khoản user.
- Admin không thể tự khóa hoặc hạ quyền tài khoản `ADMIN` cuối cùng của hệ thống.
- Admin có thể gửi link đặt lại mật khẩu qua email SMTP; người dùng mở trang `/reset-password?token=...` đổi mật khẩu thành công trong vòng 30 phút (dùng 1 lần), sau đó toàn bộ `RefreshToken` cũ bị thu hồi.
- Admin có thể lọc danh sách `ProcessingJob` bị `FAILED`, xem được `failureReason`/`failureMessage`, thực hiện Retry đơn lẻ hoặc Bulk Retry (trong giới hạn N job/lượt) thành công và có thể Purge DLQ từ UI.
- Admin có thể xem danh sách `Video`, xóa mềm (`deleted_at`) khiến video bị ẩn và chặn user submit lại, khôi phục (`Restore`), hoặc sửa cả `sourceText` (tự động re-tokenize) lẫn `targetText` và thấy thay đổi phản ánh ngay ở `WatchView`.
- Admin có thể dọn các bản ghi `WordMeaningCache` ở trạng thái `FAILED` để lượt tra từ tiếp theo gọi lại AI bình thường.
- Admin có thể upload `youtube-cookies.txt`, đổi `GEMINI_MODEL` / `GEMINI_API_KEY` mã hóa và cấu hình quota theo ngày cho `USER` (tra từ trúng cache `READY` không trừ quota; `ADMIN` được miễn quota) ngay trên Admin UI mà không cần restart container và không lộ giá trị bí mật ra response.
- Mọi thao tác cảnh báo/xác nhận xóa mềm, khóa, retry hàng loạt, purge DLQ trên giao diện Admin đều hiển thị qua popup modal thống nhất (`FE/src/components/common/AppModal.vue`), không dùng `window.alert`/`window.confirm`.
- Mọi hành động thay đổi dữ liệu của Admin đều được ghi nhận đầy đủ vào bảng `admin_audit_log` ngay từ Phase 1 (không chứa dữ liệu nhạy cảm) và tra cứu được trên UI ở Phase 3.

---

## Câu hỏi đã chốt
1. **Phạm vi triển khai trước mắt**: Triển khai theo 3 Phase đã đề xuất — chốt spec đầy đủ cho cả 3 Phase, ưu tiên thực hiện Phase 1 (RBAC, Dashboard, Users, Jobs + Backend Audit Log) trước rồi mở rộng dần.
2. **Mô hình vai trò (Roles)**: Chỉ dùng 2 cấp `USER` và `ADMIN`.
3. **Cách khởi tạo tài khoản Admin đầu tiên**: Khai báo `ADMIN_BOOTSTRAP_EMAIL` và `ADMIN_BOOTSTRAP_PASSWORD` trong `.env`; khi ứng dụng khởi động sẽ tự tạo tài khoản `ADMIN` nếu chưa có hoặc nâng quyền email đó thành `ADMIN` nếu đã tồn tại.
4. **Hiệu lực khi khóa user / hạ quyền Admin**: Giữ `JwtAuthenticationFilter` hoàn toàn stateless, chấp nhận độ trễ tối đa 15 phút theo Access Token TTL; đồng thời thu hồi ngay toàn bộ `RefreshToken` và chặn `login`/`refresh` mới.
5. **Đặt lại mật khẩu & Xóa tài khoản user**: Tích hợp gửi link đặt lại mật khẩu qua SMTP ngay từ Phase 1 (token hiệu lực 30 phút, dùng 1 lần, trang `/reset-password?token=...`, tự thu hồi `RefreshToken` cũ sau khi đổi); chưa làm luồng "Quên mật khẩu" công khai trên `LoginView`. Không hỗ trợ xóa tài khoản user, chỉ dùng Khóa/Mở khóa (`LOCKED`/`ACTIVE`).
6. **Quản lý Job & RabbitMQ DLQ**: Quản lý & Retry theo hướng DB-driven trên `processing_job` (giữ job `FAILED` làm lịch sử, tạo job mới khi Retry, hỗ trợ Bulk Retry có giới hạn tối đa N job/lượt); Dashboard/màn Job chỉ hiển thị số lượng message tồn trong các queue và hỗ trợ nút Purge DLQ.
7. **Chính sách khi xóa Video & submit trùng**: Dùng xóa mềm (`deleted_at`) để giữ nguyên dữ liệu DB và ngữ cảnh từ điển; khi user dán lại link video đã xóa mềm thì chặn submit và báo lỗi rõ ràng, chỉ Admin mới có quyền khôi phục (`Restore`).
8. **Chỉnh sửa nội dung phụ đề & Video nổi bật**: Cho phép sửa cả phụ đề gốc (`sourceText`, tự chạy lại NLP tách từ cho dòng đó) lẫn bản dịch đích (`targetText`); chưa làm tính năng "Video nổi bật / Đề xuất" trong spec này.
9. **Quản lý `youtube-cookies.txt` & Cấu hình Gemini qua Web UI**: Cho chỉnh tất cả qua Admin UI ở Phase 3 (upload `youtube-cookies.txt`, đổi `GEMINI_MODEL` và đổi `GEMINI_API_KEY`), mã hóa khi lưu DB, không trả ngược giá trị gốc ra API.
10. **Cơ chế Quota người dùng**: Áp hạn mức theo ngày cấu hình được từ Admin (submit video mới, dịch lại phụ đề, tra từ gọi Gemini mới); tra từ trúng cache DB (`READY`) không tính vào quota; `ADMIN` được miễn quota.
11. **Lộ trình Audit Log**: Tạo bảng `admin_audit_log` và ghi log ở Backend ngay từ Phase 1 cho mọi thao tác thay đổi dữ liệu của Admin; giao diện UI tra cứu Audit Log & Failure Analytics triển khai ở Phase 3.

---

## Hướng dẫn kiểm thử
Tạo các file test cho cả Backend (`BE/SubLearn/src/test/...`) và Frontend (`./tests/...`) tập trung vào các kịch bản trọng tâm:
- **Backend — Bảo mật, Phân quyền & SMTP Reset Password**:
  - Khởi động với `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` tạo đúng tài khoản `ADMIN` (và nâng quyền nếu email đã tồn tại).
  - Request chưa đăng nhập vào `/api/admin/**` trả về `401`; tài khoản `USER` trả về `403`; tài khoản `ADMIN` trả về `200`; claim `role` được đọc từ JWT ở `JwtAuthenticationFilter` không cần query DB.
  - Không thể tự đăng ký quyền `ADMIN` qua `/api/auth/register`.
  - Khóa user thu hồi toàn bộ `RefreshToken` và chặn đăng nhập/refresh mới; từ chối thao tác tự khóa chính mình hoặc hạ quyền `ADMIN` cuối cùng.
  - Gửi link đặt lại mật khẩu qua SMTP tạo token hash hiệu lực 30 phút, dùng 1 lần, đổi mật khẩu thành công thu hồi toàn bộ `RefreshToken` cũ, từ chối token hết hạn/đã dùng; xử lý lỗi sạch sẽ khi chưa cấu hình SMTP.
- **Backend — Nghiệp vụ quản trị, Soft Delete, Config động & Quota**:
  - Retry job `FAILED` giữ nguyên job cũ và tạo job mới ở trạng thái `PENDING` đẩy vào RabbitMQ; từ chối retry job `PROCESSING`/`COMPLETED`; Bulk Retry tôn trọng giới hạn tối đa N job/lượt; Purge DLQ dọn sạch queue DLQ.
  - Xóa mềm video (`deleted_at`) ẩn video khỏi người dùng, giữ nguyên `DictionaryEntryContext`, chặn user submit lại cùng `youtube_video_id`, và cho phép Admin khôi phục (`Restore`).
  - Chỉnh sửa `sourceText` chạy lại `TokenizerFactory` cập nhật `SubtitleToken` đúng ngôn ngữ; chỉnh sửa `targetText` lưu đúng vào DB và trả về dữ liệu mới qua `SubtitleController`.
  - Xóa `WordMeaningCache` trạng thái `FAILED` không làm mất các bản ghi `READY`.
  - Lưu `GEMINI_API_KEY` và `youtube-cookies.txt` ở dạng mã hóa, không bao giờ trả plaintext ra response; `GeminiClient`/`YtDlpClient` nhận cấu hình mới ở runtime.
  - Quota ngày cho `USER` đếm đúng lượt submit/retranslate/tra từ mới, không trừ quota khi trúng cache `READY`, và miễn quota cho `ADMIN`.
  - Mọi thao tác mutate của Admin đều sinh bản ghi `admin_audit_log` ngay từ Phase 1 và không chứa dữ liệu nhạy cảm.
- **Frontend — Routing & UI**:
  - Route guard chặn user thường khi điều hướng tới `/admin/*` và ẩn mục menu quản trị trên `FE/src/components/layout/AppNavbar.vue`.
  - Trang công khai `/reset-password?token=...` xử lý đúng luồng đổi mật khẩu và báo lỗi khi token không hợp lệ/hết hạn.
  - Các màn hình bảng dữ liệu (Users, Videos, Jobs, Cache, Settings, Audit Logs) hiển thị đúng bộ lọc, phân trang và mở popup xác nhận `FE/src/components/common/AppModal.vue` trước khi thực hiện hành động nguy hiểm.
