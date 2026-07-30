# Spec cho youtube-subtitle-vocab-learning

branch: claude/feature/youtube-subtitle-vocab-learning
figma_component (nếu có): không có

## Tóm tắt
- Tính năng web app giúp học ngoại ngữ bằng cách xem video YouTube với phụ đề song ngữ, trong đó mỗi từ/cụm từ trong phụ đề đều có thể click để xem nghĩa theo ngữ cảnh, nghĩa thông thường và ví dụ sử dụng. Người dùng cũng có thể xây dựng từ điển cá nhân từ những từ đã click và lưu lại.

## Yêu cầu chức năng
- Người dùng chọn/xác định ngôn ngữ đích cần dịch phụ đề sang.
- Người dùng dán link video YouTube vào ứng dụng.
- Backend lấy phụ đề/transcript của video vừa được gắn link thông qua thư viện hoặc API.
- Backend sử dụng NLP để tách văn bản phụ đề lấy được thành các từ/cụm từ có nghĩa (tokenization/chunking).
- Các câu phụ đề đã tách cùng với từ/cụm từ của chúng được lưu vào database, gắn với video gốc và thứ tự/thời điểm (timestamp) tương ứng.
- Backend trả về cho frontend các câu phụ đề kèm theo đánh dấu thứ tự/vị trí của từng từ/cụm từ vừa tách, tương ứng với thời điểm trong video.
- Frontend phát video YouTube và hiển thị phụ đề song song đồng thời — ngôn ngữ gốc và ngôn ngữ đích (đã dịch) — chạy song song, đồng bộ với quá trình phát video.
- Người dùng có thể click vào bất kỳ từ/cụm từ nào trong phụ đề để mở popup hiển thị:
  - Nghĩa dựa trên bối cảnh của video/câu mà từ đó xuất hiện.
  - Nghĩa thông thường/nghĩa từ điển của từ/cụm từ.
  - Các câu ví dụ minh họa cách sử dụng.
- Người dùng có thể chọn để lưu bất kỳ từ/cụm từ nào đã click vào từ điển cá nhân để học sau này.
- Người dùng có thể xem và quản lý từ điển cá nhân chứa các từ/cụm từ đã lưu.

## Tham chiếu thiết kế Figma (chỉ khi có sử dụng)
- Không áp dụng — tính năng này không tham chiếu đến thiết kế Figma nào.

## Các trường hợp đặc biệt có thể xảy ra
- Video YouTube không có phụ đề/caption khả dụng (cả tự động lẫn thủ công) ở ngôn ngữ gốc.
- Link video không hợp lệ, ở chế độ riêng tư, bị giới hạn khu vực, giới hạn độ tuổi, hoặc đã bị gỡ bỏ.
- Không thể xác định chính xác ngôn ngữ gốc của phụ đề.
- Ngôn ngữ đích được chọn để dịch trùng với ngôn ngữ gốc.
- Việc tách từ bằng NLP tạo ra ranh giới từ/cụm từ mơ hồ, chồng lấn, hoặc không chính xác.
- Video quá dài tạo ra tập dữ liệu phụ đề/từ vựng lớn, gây vấn đề về hiệu năng hoặc phân trang.
- Cùng một từ/cụm từ được lưu nhiều lần từ các video hoặc bối cảnh khác nhau, tạo ra các mục từ điển trùng lặp.
- Dịch vụ dịch thuật hoặc tra cứu nghĩa theo ngữ cảnh gặp lỗi, timeout, hoặc bị giới hạn tần suất gọi (rate limit).
- Người dùng click vào một từ không có bản dịch hoặc không có trong từ điển.
- Người dùng tương tác với phụ đề (click vào từ) trong khi video đang phát so với khi đang tạm dừng.
- Các từ chỉ có nghĩa khi là một phần của cụm từ nhiều từ (thành ngữ, cụm động từ, cụm từ cố định) lại bị tách riêng lẻ.

## Tiêu chí chấp nhận
- Với một link YouTube hợp lệ, khi người dùng gửi link kèm ngôn ngữ đích, hệ thống lấy phụ đề, tách từ và trả về dữ liệu phụ đề đã xử lý cho frontend trong thời gian hợp lý.
- Với phụ đề đã được xử lý cho một video, khi người dùng phát video, cả phụ đề ngôn ngữ gốc và ngôn ngữ đích đều được hiển thị đồng thời, đồng bộ với quá trình phát video.
- Với phụ đề đang hiển thị, khi người dùng click vào một từ/cụm từ, một popup xuất hiện hiển thị nghĩa theo ngữ cảnh, nghĩa thông thường và các câu ví dụ sử dụng cho từ/cụm từ đó.
- Với popup từ/cụm từ đang mở, khi người dùng chọn lưu, từ/cụm từ đó được thêm vào từ điển cá nhân.
- Với người dùng đã lưu từ/cụm từ trước đó, khi họ mở từ điển cá nhân, họ thấy danh sách tất cả các mục đã lưu.
- Với video không có phụ đề khả dụng, khi người dùng gửi link, hệ thống thông báo rõ ràng cho người dùng thay vì lỗi âm thầm hoặc hiển thị kết quả trống.

## Câu hỏi còn bỏ ngỏ
- Nên dùng phương pháp nào để lấy phụ đề/caption YouTube (YouTube Data API chính thức, thư viện bên thứ ba, hay cách khác), và có giới hạn tần suất gọi hay ràng buộc bản quyền nào cần lưu ý không? dùng yt-dlp
- Nên dùng phương pháp/thư viện NLP nào để tách từ/cụm từ, và có cần hỗ trợ nhiều ngôn ngữ gốc không? dùng NLP của Java
- Dịch vụ dịch thuật nào sẽ tạo ra văn bản phụ đề ngôn ngữ đích và nghĩa của từ/cụm từ? sử dụng model của gemini
- "Nghĩa theo ngữ cảnh" nên được tính toán như thế nào — thông qua LLM, khả năng xử lý ngữ cảnh của API dịch thuật, hay tra cứu từ điển kết hợp với văn bản xung quanh? sử dụng model của gemini
- Có cần hệ thống tài khoản/xác thực người dùng để hỗ trợ từ điển cá nhân theo từng người dùng không, hay ban đầu chỉ cần hoạt động ẩn danh/lưu cục bộ? dùng hệ thống tài khoản/xác thực người dùng để hỗ trợ từ điển cá nhân theo từng người dùng
- Cần hỗ trợ những ngôn ngữ gốc và ngôn ngữ đích nào ban đầu? ngôn ngữ gốc của là ngôn ngữ của video, ngôn ngữ đích là tiếng anh, việt, nhật
- Có ràng buộc pháp lý hoặc bản quyền nào liên quan đến việc lấy, lưu trữ và hiển thị văn bản phụ đề YouTube không? không

## Hướng dẫn kiểm thử
Tạo (các) file test trong thư mục ./tests cho tính năng mới, và viết các test có ý nghĩa cho những trường hợp sau, không cần quá nặng:
- Logic lấy phụ đề trả về dữ liệu đã tách từ đúng cho một link video hợp lệ.
- Logic lấy phụ đề xử lý được trường hợp video không có phụ đề mà không bị crash, và hiển thị rõ trạng thái/thông báo lỗi.
- Việc tách từ bằng NLP tách đúng một câu phụ đề mẫu thành các từ/cụm từ mong đợi kèm đánh dấu thứ tự chính xác.
- Dữ liệu phụ đề song ngữ trả về cho frontend đúng với cấu trúc thời điểm/thứ tự mong đợi để đồng bộ khi phát video.
- Việc lưu một từ/cụm từ vào từ điển cá nhân được lưu trữ và có thể truy xuất lại sau đó.
- Việc tra cứu nghĩa từ/cụm từ trả về nghĩa theo ngữ cảnh, nghĩa thông thường và ví dụ sử dụng cho một mục nhất định.
