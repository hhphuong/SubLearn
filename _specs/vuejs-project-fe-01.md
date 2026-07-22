# Spec for Vue.js Project For FE

branch: claude/feature/vuejs-project-fe-01
figma_component (if used): N/A

## Summary
- Khởi tạo một dự án Vue.js mới bên trong thư mục `FE/` hiện đang trống.
- Đây là nền tảng cấu trúc frontend đầu tiên cho SubLearn, làm cơ sở để phát triển các tính năng phía client sau này.

## Functional Requirements
- Phải khởi tạo được một dự án Vue.js hoạt động bên trong `FE/`, có thể chạy cục bộ bằng lệnh dev server tiêu chuẩn.
- Dự án phải hỗ trợ build cho môi trường production (lệnh build/output tiêu chuẩn).
- Dự án nên có một trang/view mặc định tối giản để xác nhận ứng dụng chạy thành công.
- Công cụ dự án (trình quản lý package, các file cấu hình) nên tuân theo quy ước tiêu chuẩn hiện tại của Vue.js.
- Dự án frontend nên được cấu trúc sao cho sau này có thể giao tiếp với backend Spring Boot hiện có trong `BE/SubLearn/` (ví dụ: qua các lời gọi HTTP), mà không yêu cầu phải xây dựng phần tích hợp đó ngay bây giờ.

## Possible Edge Cases
- Thư mục `FE/` có thể đã chứa các file ẩn/hệ thống (ví dụ `.gitkeep`) gây xung đột với các công cụ khởi tạo dự án vốn kỳ vọng thư mục trống.
- Không tương thích giữa phiên bản Node.js/trình quản lý package trên máy của lập trình viên và phiên bản mà công cụ khởi tạo yêu cầu.
- Xung đột tên giữa trường `name` trong `package.json` của dự án mới và tên artifact `SubLearn` của backend hiện có.
- Cần đảm bảo `.gitignore` được cấu hình đúng để không commit `node_modules/` và các file build.

## Acceptance Criteria
- Chạy lệnh dev server cục bộ của dự án khởi động ứng dụng không lỗi và hiển thị trang mặc định trên trình duyệt.
- Chạy lệnh build của dự án tạo ra bản build production không lỗi.
- Thư mục `FE/` chứa một dự án Vue.js hoàn chỉnh, độc lập (dependencies, file cấu hình, mã nguồn).
- Có file `.gitignore` (hoặc đã được cập nhật) để loại trừ `node_modules/` và các file build.
- Không có thay đổi nào đối với dự án backend hiện có trong `BE/SubLearn/`.

## Open Questions
- Nên dùng Vue CLI, Vite (công cụ chuẩn hiện tại cho Vue.js), hay công cụ khởi tạo khác? dùng Vite
- Có nên bật TypeScript cho dự án Vue.js này không? có 
- Có thư viện UI/component hoặc phương pháp styling nào được ưu tiên (ví dụ Tailwind CSS) để đưa vào ngay từ đầu không? dùng bootstrap
- Có nên đưa Vue Router và quản lý state (Pinia) vào bản khởi tạo ban đầu, hay để bổ sung sau khi cần? dùng router

## Testing Guidelines
Create a test file(s) in the ./tests folder for the new feature, and create meaningful tests for the following cases, without going too heavy:
- Kiểm tra trang/view mặc định render thành công, không phát sinh lỗi.
- Kiểm tra lệnh build production chạy thành công và tạo ra các file output như mong đợi.
