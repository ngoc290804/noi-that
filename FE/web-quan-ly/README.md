# Luma — FE quản lý nội dung

React + TypeScript + Vite. Ứng dụng đọc và ghi API Spring Boot trong `BE/`, dùng chung catalog với web thông tin. Không còn lưu catalog trong localStorage hoặc dùng fixture làm dữ liệu khi API lỗi.

## Chạy

Node.js >=22.12. Khởi động BE theo `BE/README.md`, sau đó:

```bash
cd FE/web-quan-ly
npm ci
npm run dev
```

Mở **http://127.0.0.1:5174/** và đăng nhập bằng tên tài khoản/mật khẩu. Phiên dùng cookie HttpOnly, lưu tại PostgreSQL, hết hạn sau 30 phút không hoạt động; tải lại trang vẫn giữ phiên. Không lưu mật khẩu hoặc token đăng nhập vào localStorage/sessionStorage.

Lần đầu khi DB chưa có tài khoản, xác nhận `MEDIA_UPLOAD_TOKEN` riêng của BE tại ô **Mã khởi tạo**, sau đó chủ cửa hàng tự nhập mật khẩu cho username `admin`. Không có mật khẩu mặc định. Mã cũ chỉ cấp quyền khởi tạo trong 10 phút và không dùng để truy cập API quản trị sau này. Mật khẩu từ 6 ký tự, tối đa 72 byte UTF-8; BE lưu BCrypt. Không gửi mật khẩu qua chat hoặc đưa vào source.

Vite chuyển tiếp `/api` tới `http://127.0.0.1:8080`. Có thể đặt `API_PROXY_TARGET` trong `.env.local`; xem `.env.example`. `VITE_API_BASE_URL` là URL API công khai khi phát hành (mặc định `/api`); `VITE_STOREFRONT_URL` là URL web thông tin. Không đặt bí mật/token trong `VITE_*`.

`npm run build` kiểm tra TypeScript và build production; `npm run preview` dùng cổng 4174. Hosting cần SPA fallback về `index.html`, proxy `/api` hoặc API base + CORS BE phù hợp. CORS mặc định gồm localhost/127.0.0.1 cổng 5173 và 5174; nếu BE đã đặt `CORS_ALLOWED_ORIGINS`, thêm origin thực tế vào biến đó.

## Màn hình và thao tác

| Màn hình | Chức năng |
| --- | --- |
| `/san-pham` | Tìm không dấu theo tên/mã/slug, lọc trạng thái/danh mục, sort, phân trang phía FE, xuất CSV theo bộ lọc |
| Biểu mẫu sản phẩm | Bốn tab: Thông tin, Hình ảnh, Thông số, Bảo quản. Chọn/tải ảnh và sắp xếp gallery; xem trước; chọn trạng thái rồi bấm Lưu sản phẩm |
| Danh sách sản phẩm | Bật/tắt nổi bật; lưu trữ hàng loạt có xác nhận; xóa có xác nhận. Lưu trữ khôi phục bằng cách đổi trạng thái sang Đang hiển thị |
| `/danh-muc` | Thêm/sửa/xóa, slug, thứ tự và trạng thái. Không xóa khi còn sản phẩm, kể cả bản nháp/lưu trữ |
| `/thu-vien-anh` | Tải JPG/PNG/WebP tối đa 10 MB lên Cloudinary; nhập alt; sửa alt đồng bộ các nơi sử dụng; xóa khỏi thư viện khi không còn tham chiếu |
| `/tai-khoan` | Chỉ ADMIN: thêm/sửa, phân quyền ADMIN/EDITOR, khóa/mở, đặt lại mật khẩu tạm, xóa tài khoản. Mật khẩu tạm bắt đổi trước khi truy cập catalog |
| Menu tài khoản | Đổi mật khẩu của bản thân và đăng xuất |
| `/noi-dung` | Sửa banner, chọn sản phẩm nổi bật, thêm/sửa/gỡ tối đa 20 không gian; lưu để cập nhật website |

Ẩn danh mục sẽ ẩn các sản phẩm thuộc danh mục đó trên web thông tin. Sản phẩm nháp/lưu trữ không hiện công khai. Trạng thái là một bản ghi hiện hành; chưa có phiên bản nháp song song với bản công khai. Đổi slug làm đổi URL, chưa tự chuyển hướng URL cũ.

## Hướng dẫn bảo quản

Nhập tại tab **Bảo quản**, trường `careInstructions`. Đây là trường riêng, không nhập lại trong mô tả hoặc thông số bổ sung. Có thể xuống dòng; để trống khi chưa có hướng dẫn được nhà cung cấp xác nhận. Web thông tin hiển thị “đang được cập nhật” khi trống.

Migration V4 chuyển nội dung từ nhãn cũ “Hướng dẫn bảo quản”/“Bảo quản”, giữ nguyên đoạn văn. BE duy trì một bản sao nhãn tương thích tự động trong JSON cho BE/FE cũ; người quản trị chỉ sửa trường riêng. Không hiển thị bảo quản lặp trong bảng thông số. Chất liệu, màu và kích thước cũng nhập tại trường riêng; thông số bổ sung dùng cho các mục như xuất xứ, công suất, dung tích hoặc tải trọng. Đơn vị có ô riêng.

## Lưu, lỗi và xóa

- Chỉ báo thành công sau khi BE xác nhận; trường đang nhập được giữ khi lưu lỗi. Không tự thử lại thao tác ghi.
- Mỗi bản ghi có `version`; BE trả 409 khi dữ liệu đã đổi ở phiên khác. Đối chiếu bản mới trước khi tiếp tục, không âm thầm ghi đè.
- Nếu timeout/mất mạng khi ghi, kết quả có thể đã được lưu; FE yêu cầu tải lại để xác minh trước thao tác tiếp theo.
- Nếu ghi thành công nhưng tải lại thất bại, thông báo phân biệt rõ hai bước và chặn ghi tiếp đến khi tải lại được.
- Có trạng thái đang lưu, xác nhận xóa/lưu trữ và cảnh báo đóng form còn thay đổi. Cảnh báo nội dung chưa lưu áp dụng cho menu và tải/đóng trang, chưa chặn Back/Forward.
- DELETE đánh dấu xóa tại BE; bản ghi biến mất khỏi danh sách/API công khai. Ảnh còn dùng bị chặn xóa. Xóa ảnh khỏi thư viện không hủy tệp Cloudinary để tránh hỏng các liên kết đã chia sẻ. Chưa có UI thùng rác/khôi phục bản đã xóa; dùng Lưu trữ nếu muốn dễ khôi phục.
- Danh sách quản trị hiện tải toàn catalog rồi lọc/phân trang tại FE, phù hợp catalog nhỏ; API công khai vẫn phân trang tại BE.

## Kiểm chứng

TypeScript/production build cho cả hai FE, kiểm thử API công khai và 87 test BE trên PostgreSQL riêng. Kiểm tra HTTP thật: đăng nhập/session/CSRF/phân quyền/thu hồi phiên, CRUD, bảo quản qua API chi tiết, xung đột phiên bản, chặn xóa tham chiếu, rollback lưu trữ hàng loạt. IAB kiểm tra luồng FE → BE → DB, biểu mẫu bảo quản, sản phẩm nháp/xóa và hiển thị xuống dòng trên web thông tin; responsive desktop/mobile. Chưa thử Safari/thiết bị thật. Upload Cloudinary được kiểm tra bằng unit test với provider giả lập; không upload ảnh thử vào tài khoản thật trong lần này.

Cấu hình local hiện tại: quản lý ở cổng 5174 gọi BE local cổng 8080; BE này đọc `BE/.env` và dùng cùng Neon với API công khai trên Vercel. Nút Xem website mở web thông tin cổng 5173. Không trỏ ứng dụng đang sử dụng sang BE/DB QA (8181/5185); khi kiểm thử cần tách URL và không bàn giao cấu hình QA như cấu hình cửa hàng.

Migration và HTTP/UI QA dùng DB tạm riêng. Sau khi người dùng xác nhận, V4 đã được áp dụng trên Neon có sao lưu; BE quản trị local hiện đọc cùng DB cửa hàng. Chưa triển khai mã BE mới lên Vercel. Không dùng `baseline-on-migrate` cho DB đang có Flyway history; chạy BE mới để Flyway áp dụng migration còn thiếu. V5 đã áp dụng trên Neon; chỉ bổ sung tài khoản và phiên, snapshot 9 sản phẩm trước/sau giống hoàn toàn. Backup tại `BE/.local/accounts-auth/20261113T110330Z/`.

## Quyền truy cập và phát hành

EDITOR quản lý sản phẩm/danh mục/ảnh/nội dung; ADMIN có thêm quyền tài khoản. BE kiểm tra quyền độc lập với menu FE. Không thể tự khóa, hạ quyền, xóa hay reset mật khẩu của mình; dùng Đổi mật khẩu. Hệ thống luôn giữ ít nhất một ADMIN hoạt động. Khóa, đổi quyền, reset hoặc xóa sẽ thu hồi phiên liên quan.

Mọi thao tác ghi yêu cầu CSRF lấy từ `/api/auth/csrf`, gửi `X-CSRF-TOKEN` cùng cookie. Đăng nhập bị giới hạn sau 10 lần thử trong 15 phút theo tên đăng nhập và IP. FE không tự thử lại thao tác ghi.

Nên phát hành FE và proxy API cùng origin. HTTPS đặt `SESSION_COOKIE_SECURE=true`; chỉ dùng `SESSION_COOKIE_SAME_SITE=none` khi thật sự cần cross-site và đã bật Secure. CORS phải cho phép chính xác origin FE cùng credentials. BE mới phải được triển khai trước khi dùng FE mới trên hosting; Vercel hiện chưa cập nhật mã xác thực này.
