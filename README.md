# Luma — Website thông tin nội thất

Web giới thiệu nội thất/gia dụng bằng **React + TypeScript**, dùng Node.js và Vite để phát triển/build. Đã có trang chủ, danh sách phân trang, chi tiết sản phẩm, tìm kiếm không dấu, lọc danh mục, sắp xếp và giao diện responsive; FE đọc dữ liệu trực tiếp từ API. Backend **Java 17 + Spring Boot + Neon PostgreSQL** nằm trong `BE/`.

## Chạy dự án

Yêu cầu Node.js từ 22.12 trở lên và npm.

```bash
cd FE/web-thong-tin
npm ci
npm run dev
```

Khởi động BE theo [hướng dẫn BE](BE/README.md) trước khi sử dụng FE. Vite chuyển tiếp `/api` tới `http://127.0.0.1:8080`; có thể thay bằng `API_PROXY_TARGET` trong `FE/web-thong-tin/.env.local` (xem `.env.example`). Mở địa chỉ được Vite in ra, mặc định `http://127.0.0.1:5173/`.

```bash
npm test
npm run build
npm run preview
```

`build` bao gồm kiểm tra kiểu TypeScript và tạo tệp tĩnh trong `FE/web-thong-tin/dist/`. Khi phát hành SPA lên hosting, cấu hình các đường dẫn ứng dụng như `/san-pham/ghe-an-nordic` quay về `index.html`; Vite dev/preview đã hỗ trợ việc mở URL trực tiếp. Trang 404 hiện là giao diện phía trình duyệt, không phải HTTP 404 từ backend.

## Tổ chức

```text
FE/web-thong-tin/       Website thông tin sản phẩm
FE/web-quan-ly/         FE quản lý nội dung — nối API quản trị, cổng 5174
BE/                    Spring Boot API công khai + Neon PostgreSQL + Flyway
Art/                   Ảnh gốc, mockup và prototype, giữ nguyên
docs/                  Hai kế hoạch riêng cho web quản lý và web thông tin
```

- [Hướng dẫn FE](FE/web-thong-tin/README.md): cách sửa nội dung, ảnh và cấu trúc ứng dụng.
- [Hướng dẫn BE](BE/README.md): cấu hình `.env`, chạy API, migration, kiểm thử DB, upload Cloudinary và đồng bộ catalog từ source.
- [Kế hoạch web thông tin](docs/02-KE-HOACH-WEB-THONG-TIN-HANG.md).
- [FE quản lý](FE/web-quan-ly/README.md): sản phẩm, danh mục, ảnh, nội dung website; đã nối API CRUD quản trị, dùng tài khoản/phân quyền và version; xem hướng dẫn chạy.
- [Kế hoạch web quản lý](docs/01-KE-HOACH-WEB-QUAN-LY.md): phạm vi và phần BE cần triển khai tiếp.

Dữ liệu hiện là mẫu từ bộ thiết kế; giá chỉ để tham khảo. Các thông số chưa được cung cấp ghi “Thông tin đang cập nhật”. BE cung cấp `/api/products`, `/api/products/{slug}`, `/api/categories`, `/api/home` và `/actuator/health`; Flyway tạo schema riêng `luma` cùng dữ liệu mẫu khớp FE. API quản lý ảnh `/api/admin/media/images` hỗ trợ upload Cloudinary và gắn ảnh vào sản phẩm, yêu cầu phiên đăng nhập và CSRF. FE đã gọi API cho trang chủ, danh mục, danh sách, chi tiết và sản phẩm tương tự; hiển thị ảnh Cloudinary từ DB, có tải/lỗi/thử lại và phân biệt lỗi kết nối với 404. `src/data/catalog.ts` chỉ còn là fixture/nguồn cho script đồng bộ catalog, không được dùng khi khách xem web. FE quản lý tại `FE/web-quan-ly/` đã nối CRUD sản phẩm/danh mục/media và nội dung trang chủ, bảo vệ bằng tài khoản ADMIN/EDITOR, cookie phiên và CSRF; hướng dẫn bảo quản có trường riêng. Có quản lý tài khoản và phân quyền; chưa có giỏ hàng, đặt hàng hoặc thanh toán. Bản mẫu có `noindex, nofollow` để tránh giới thiệu dữ liệu thử như thông tin kinh doanh chính thức; chỉ đổi sau khi xác nhận nội dung và cấu hình phát hành.
