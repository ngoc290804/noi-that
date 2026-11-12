# Luma — Website thông tin nội thất

Web giới thiệu nội thất/gia dụng bằng **React + TypeScript**, dùng Node.js và Vite để phát triển/build. Đã có trang chủ, danh sách, chi tiết bốn sản phẩm, tìm kiếm không dấu, lọc danh mục, sắp xếp và giao diện responsive.

## Chạy dự án

Yêu cầu Node.js từ 22.12 trở lên và npm.

```bash
cd FE/web-thong-tin
npm ci
npm run dev
```

Mở địa chỉ được Vite in ra, mặc định `http://127.0.0.1:5173/`.

```bash
npm test
npm run build
npm run preview
```

`build` bao gồm kiểm tra kiểu TypeScript và tạo tệp tĩnh trong `FE/web-thong-tin/dist/`. Khi phát hành SPA lên hosting, cấu hình các đường dẫn ứng dụng như `/san-pham/ghe-an-nordic` quay về `index.html`; Vite dev/preview đã hỗ trợ việc mở URL trực tiếp. Trang 404 hiện là giao diện phía trình duyệt, không phải HTTP 404 từ backend.

## Tổ chức

```text
FE/web-thong-tin/       Ứng dụng đang triển khai
BE/                    Để sẵn cho Spring Boot khi cần, chưa khởi tạo
Art/                   Ảnh gốc, mockup và prototype, giữ nguyên
docs/                  Hai kế hoạch riêng cho web quản lý và web thông tin
```

- [Hướng dẫn FE](FE/web-thong-tin/README.md): cách sửa nội dung, ảnh và cấu trúc ứng dụng.
- [Kế hoạch web thông tin](docs/02-KE-HOACH-WEB-THONG-TIN-HANG.md).
- [Kế hoạch web quản lý](docs/01-KE-HOACH-WEB-QUAN-LY.md): vẫn hoãn triển khai.

Dữ liệu hiện là mẫu từ bộ thiết kế; giá chỉ để tham khảo. Các thông số chưa được cung cấp ghi “Thông tin đang cập nhật”. Chưa có quản trị, API, cơ sở dữ liệu, giỏ hàng, đặt hàng hoặc thanh toán. Bản mẫu có `noindex, nofollow` để tránh giới thiệu dữ liệu thử như thông tin kinh doanh chính thức; chỉ đổi sau khi xác nhận nội dung và cấu hình phát hành.
