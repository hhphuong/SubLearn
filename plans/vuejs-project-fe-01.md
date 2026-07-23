# Plan: Khởi tạo dự án FE Vue.js (`_specs/vuejs-project-fe-01.md`)

## Bối cảnh

SubLearn hiện chỉ có backend (`BE/SubLearn`, Spring Boot). Thư mục `FE/` đã tồn tại nhưng hoàn toàn trống — chưa có dự án frontend nào được khởi tạo ở đó. Spec `_specs/vuejs-project-fe-01.md` yêu cầu tạo scaffold frontend đầu tiên: một dự án Vue.js hoạt động bên trong `FE/`, có thể chạy dev server, build cho production, và sau này có thể giao tiếp với backend Spring Boot qua HTTP (chưa cần tích hợp API thật ở bước này). Các câu hỏi mở trong spec đã được người dùng trả lời: **Vite** làm build tool, bật **TypeScript**, dùng **Bootstrap** cho styling, và tích hợp **Vue Router** ngay từ đầu. Quản lý state (Pinia) không được yêu cầu nên sẽ bỏ qua để tránh mở rộng phạm vi không cần thiết.

Kết quả khảo sát đã xác nhận: `FE/` không có file nào (kể cả `.gitkeep`), không có `.gitignore`/`package.json`/cấu hình CI ở cấp root, và `BE/SubLearn/pom.xml` dùng `artifactId=SubLearn` — vì vậy `package.json` mới phải dùng `name` khác để tránh nhầm lẫn. `BE/SubLearn` đã có `.gitignore` riêng ở cấp subproject (không có `.gitignore` ở root) — đây là pattern cần áp dụng tương tự cho `FE/`.

## Phương án

Dùng **`create-vue`** (`npm create vue@latest`, công cụ scaffold chính thức của Vue.js) thay vì dùng template Vite thuần — công cụ này có sẵn các flag cho TypeScript, Vue Router, và Vitest, tạo ra cấu trúc router/view theo đúng convention chuẩn cùng cấu hình test hoạt động sẵn. Bootstrap không được `create-vue` hỗ trợ sẵn nên sẽ được thêm thủ công dưới dạng npm dependency sau đó.

### 1. Khởi tạo dự án

Chạy từ repo root, chỉ định `FE` làm thư mục output (an toàn vì đang trống):

```
npx --yes create-vue@latest FE -- --typescript --router --vitest
cd FE
npm install
```

- `--typescript`, `--router`, `--vitest` theo đúng các quyết định đã chốt. Cố tình bỏ qua `--pinia`, `--eslint`, `--cypress`, `--playwright`, v.v. — không được yêu cầu.
- `npx --yes` để tránh prompt tương tác "OK to proceed?" của npm.
- Điều kiện tiên quyết: kiểm tra Node.js/npm đã được cài (`node -v`, `npm -v`) trước khi chạy — cần xác minh tại thời điểm thực thi.

### 2. Xử lý xung đột tên package

Sửa `FE/package.json`: đặt `"name": "sublearn-fe"` (kebab-case, khác với `artifactId=SubLearn` của Maven bên backend).

### 3. Thêm Bootstrap

```
cd FE
npm install bootstrap
```

Trong `FE/src/main.ts`, thêm `import 'bootstrap/dist/css/bootstrap.min.css'` trước khi tạo app. Bỏ qua Bootstrap JS bundle/Popper ở bước này — trang xác nhận tối giản không cần các component tương tác (modal/dropdown); có thể thêm sau nếu cần.

### 4. Trang mặc định tối giản

`create-vue --router` đã tự sinh `FE/src/router/index.ts`, `FE/src/views/HomeView.vue`, `FE/src/views/AboutView.vue`, và `FE/src/App.vue` đã gắn sẵn `<RouterView />`. Thay nội dung `HomeView.vue` bằng một trang xác nhận tối giản, có style Bootstrap, chứa một chuỗi text ổn định, dễ nhận diện (ví dụ "SubLearn" / "Frontend đã chạy thành công.") để có thể assert trong test. Giữ nguyên cấu trúc router/App như đã sinh ra — đây là nền tảng hợp lý cho các lời gọi HTTP tới `BE/SubLearn` sau này.

### 5. `.gitignore`

**Không** tạo `.gitignore` ở cấp root (theo đúng pattern hiện có: chỉ `BE/SubLearn/.gitignore` tồn tại, không có bản ở root). `create-vue` tự sinh `FE/.gitignore` với `node_modules`, `dist`, `dist-ssr`, `*.local`, v.v. — cần xác minh file này tồn tại và bao phủ `node_modules/` cùng file build; bổ sung nếu thiếu.

### 6. Test trong thư mục root `./tests` (theo đúng yêu cầu của spec — không phải `FE/tests`)

Test runner: Vitest + `@vue/test-utils` + `jsdom` (đã được cài sẵn bởi `--vitest`, không cần cài thêm). Chỉ các file test nằm ngoài `FE/`, tại `./tests` ở repo root; toàn bộ tooling vẫn nằm trong `FE/node_modules`.

**Vấn đề cần lưu ý:** `./tests` là thư mục anh em (sibling) của `FE/`, không phải thư mục con, nên các import kiểu bare specifier như `@vue/test-utils` sẽ không resolve được qua cơ chế tìm `node_modules` ở các thư mục cha thông thường từ một file test tại `./tests/*.spec.ts`. Khắc phục bằng cách cập nhật `FE/vitest.config.ts`:
- Giữ `root` trỏ vào `FE/` (cần thiết để resolve `FE/src`).
- Đặt `test.include` thành `['../tests/**/*.{test,spec}.ts']`.
- Thêm `resolve.alias` (hoặc `test.alias`) map `@vue/test-utils` tới `./node_modules/@vue/test-utils` bên trong `FE/`.
- Bật `test.globals: true` để `describe`/`it`/`expect` không cần import tường minh (tránh luôn vấn đề resolve tương tự với package `vitest`).
- Xoá file test mặc định do generator sinh ra `FE/src/components/__tests__/HelloWorld.spec.ts` (không còn khớp `include`, không cần thiết).

Tạo hai file test tại repo root:

- **`./tests/HomeView.spec.ts`** — mount `../FE/src/views/HomeView.vue` bằng `mount` của `@vue/test-utils`, assert rằng component render không lỗi và text của wrapper chứa chuỗi xác nhận. Đáp ứng tiêu chí "trang mặc định render thành công, không phát sinh lỗi".
- **`./tests/build.spec.ts`** — chạy `npm run build` qua `child_process.execSync({ cwd: '../FE' })` (lệnh thật, sát nhất với "lệnh build production chạy thành công"), sau đó assert `FE/dist/index.html` và `FE/dist/assets/` tồn tại. Dùng timeout rộng rãi (~60s) vì đây là một lần build thật.

Đảm bảo `FE/package.json` có script test không chạy ở chế độ watch, ví dụ `"test": "vitest run"` (script `test:unit` mặc định do generator sinh ra sẽ chạy ở chế độ watch nếu gọi không kèm tham số — dùng `vitest run` để chạy một lần).

### 7. Kiểm thử/xác minh (chạy từ `FE/` trừ khi có ghi chú khác)

```
node -v; npm -v            # xác nhận toolchain đã sẵn sàng
npm install                 # cài đặt sạch, không lỗi
npm run dev                 # khởi động dev server, mở URL localhost được in ra, xác nhận HomeView render với style Bootstrap, sau đó dừng lại
npm run build                # xác nhận exit code 0, kiểm tra dist/index.html + dist/assets/*
npm run test                 # (vitest run) xác nhận cả 2 test đều pass
```

Cuối cùng, từ repo root: `git status` / `git diff` để xác nhận thay đổi chỉ giới hạn trong `FE/**` và `tests/**`, **không** có thay đổi nào trong `BE/SubLearn/`.

### Các file quan trọng
- `FE/package.json` — sửa tên, thêm test script
- `FE/vitest.config.ts` — include/alias/globals cho test nằm khác thư mục
- `FE/src/main.ts` — import CSS Bootstrap
- `FE/src/views/HomeView.vue` — trang xác nhận tối giản
- `FE/.gitignore` — xác nhận đã loại trừ node_modules/dist
- `tests/HomeView.spec.ts`, `tests/build.spec.ts` — file test mới tại repo root

### Rủi ro / lưu ý
- Máy thực thi phải có Node/npm đã cài — cần kiểm tra trước, đây là điều kiện bắt buộc.
- PowerShell có thể xử lý sai dấu `--` trong lệnh khởi tạo; nếu vậy, chạy qua Bash tool (Git Bash) thay thế.
- Alias `@vue/test-utils` trong `vitest.config.ts` là bắt buộc, không phải tuỳ chọn — nếu bỏ qua rất có thể sẽ gây lỗi "Cannot find module" trong `HomeView.spec.ts`.
- `npm run build` bên trong `build.spec.ts` sẽ spawn một child process thật; cần timeout đủ rộng để tránh fail giả trên máy chậm.
- Cần để ý nếu `create-vue` còn sót prompt tương tác dù đã truyền đủ flag — nếu chạy không tương tác, một prompt bất ngờ có thể khiến tiến trình bị treo.
