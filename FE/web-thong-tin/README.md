# Luma — FE web thông tin hàng

Ứng dụng React + TypeScript với Vite và React Router. Node.js dùng để phát triển/build; toàn bộ dữ liệu mẫu chạy trên trình duyệt, không phụ thuộc BE.

## Lệnh

```bash
npm ci
npm run dev          # http://127.0.0.1:5173
npm run typecheck
npm test             # 7 kịch bản dữ liệu với Node test runner
npm run build        # typecheck + production build
npm run preview      # http://127.0.0.1:4173
```

Dùng Node.js >=22.12. Test sử dụng tính năng loại bỏ kiểu TypeScript của Node; Node 22 có thể hiển thị cảnh báo experimental. Không cần cài thêm test framework.

## Các trang và chức năng

| URL | Chức năng |
| --- | --- |
| `/` | Hero, sản phẩm nổi bật, cảm hứng không gian, phần giới thiệu |
| `/san-pham` | Bốn sản phẩm, tìm theo tên, một danh mục, sort giá/nổi bật |
| `/san-pham?q=ghe+an` | Ví dụ tìm kiếm không dấu |
| `/san-pham?category=gom-su&sort=price-asc` | Ví dụ bộ lọc có thể chia sẻ/tải lại |
| `/san-pham/:slug` | Chi tiết, thông số, accordion, danh sách sản phẩm tương tự tối đa hai hàng |
| Đường dẫn sai / slug không có | Giao diện không tìm thấy và link quay về |

Tìm kiếm được thực hiện khi nhấn Enter hoặc nút tìm. Bộ lọc và sort cập nhật ngay; query được giữ trong URL. Card mở chi tiết kèm đường dẫn catalog trước đó, dùng cho CTA “Quay lại danh sách”. Tìm kiếm trên header là ô nhập có viền, mở trực tiếp catalog theo query; logo nằm bên trái, ô tìm ở bên phải. Mobile bố trí ô tìm trên hàng riêng để dễ nhập. Menu mobile dùng dialog với bàn phím/Escape và focus mặc định của trình duyệt.

Khi đang ở trang sản phẩm, xóa hết nội dung trong ô tìm kiếm (bằng dấu × hoặc bàn phím) tự đưa danh sách về tất cả sản phẩm, bỏ từ khóa và các bộ lọc mà không cần nhấn Enter. Áp dụng cho ô tìm trong danh sách và ô trên header.

Cuối trang chi tiết hiển thị “Sản phẩm tương tự” thẳng lề với nội dung chung, theo cùng lưới card: desktop bốn cột/tối đa tám món, tablet ba cột/tối đa sáu món, mobile hai cột/tối đa bốn món; mỗi kích thước tối đa hai hàng. Ưu tiên cùng danh mục rồi bổ sung món khác theo thứ tự nổi bật, loại trừ món đang xem và không nhân bản để lấp đầy danh sách. Nút “Xem sản phẩm khác” bên dưới mở `/san-pham`.

Gallery chi tiết dùng `ProductGallery`: khi mảng `images` có từ hai ảnh, hiển thị mũi tên ở hai bên ảnh lớn và dải thumbnail ngay bên dưới. Chọn thumbnail cập nhật ảnh lớn; mũi tên chuyển vòng đầu/cuối và đồng bộ viền ảnh đang chọn. Khi focus vào gallery, dùng trái/phải để chuyển ảnh, Home/End để về ảnh đầu/cuối. Một ảnh thì ẩn điều khiển; không có ảnh thì hiện dự phòng.

Để bổ sung ảnh cho một sản phẩm, thêm các đối tượng ảnh thật vào mảng `images` trong `src/data/catalog.ts`, mỗi ảnh gồm `src`, `thumbnail`, `alt`, `width`, `height`. Ghế ăn Nordic hiện có hai ảnh (toàn cảnh, cận cảnh); bình gốm, bộ cốc và đèn mỗi món có một ảnh. Không dùng ảnh của món khác để làm gallery cho sản phẩm.

## Vị trí chỉnh sửa

| Tệp / thư mục | Trách nhiệm |
| --- | --- |
| `src/data/catalog.ts` | Nguồn sản phẩm, danh mục, nội dung hero, ảnh không gian; sửa tên/giá ở đây |
| `src/types/catalog.ts` | Kiểu dữ liệu sản phẩm, ảnh, thông số và query |
| `src/services/catalog.ts` | Lớp đọc dữ liệu; nơi thay nguồn mẫu bằng API Spring Boot khi cần |
| `src/utils/catalog.ts` | Chuẩn hóa tiếng Việt, query URL và định dạng tiền |
| `src/components/` | Header, footer, icon, ảnh dự phòng, ProductGallery và card dùng chung |
| `src/pages/` | Nội dung các trang |
| `src/styles/styles.css` | Token, typography, layout, responsive và reduced motion |
| `public/images/` | Ảnh WebP giữ kích thước gốc và bản nhỏ 600 px; cấu trúc danh mục/từng món khớp với `Art/Prod/` |
| `tests/catalog.test.mjs` | Tìm kiếm, kết hợp bộ lọc, sort không đổi nguồn, URL, slug/liên quan và tiền |

Đường dẫn ảnh theo danh mục và từng món: ví dụ nguồn `Art/Prod/Ghe/Ghe-an-Nordic/ghe-an-nordic-gan.png` tương ứng `/images/Ghe/Ghe-an-Nordic/ghe-an-nordic-gan.webp` và `/images/Ghe/Ghe-an-Nordic/ghe-an-nordic-gan-600.webp`. Khi di chuyển ảnh nguồn, đồng bộ thư mục trong `public/images/` và đường dẫn ở `src/data/catalog.ts`; không tham chiếu trực tiếp thư mục `Art/` từ FE.

Khi thêm sản phẩm: tạo ID/slug duy nhất, gắn danh mục, nhập giá số nguyên VND, thêm mô tả và ảnh/alt, chỉ nhập thông số có nguồn. Cập nhật kiểu/query nếu thêm danh mục hoặc cách sắp xếp. Giữ component đọc qua service; khi nối BE sẽ cần bổ sung trạng thái tải/lỗi thực tế cùng hợp đồng API.

Font Be Vietnam Pro được lưu trong dependency và phục vụ nội bộ, không tải font từ Google ở thời điểm khách mở trang. Ảnh có `srcset`, kích thước, lazy loading bên dưới và phương án dự phòng nếu lỗi.

## Áp dụng thiết kế đã thống nhất

Tham chiếu gốc ở `../../Art/DesignUI/`: homepage, catalog, product detail, mobile và design system.

- Giữ bảng màu trắng/kem/xanh rừng, Be Vietnam Pro, khoảng thở và card phẳng.
- Giữ catalog bốn cột desktop, hai cột mobile; chuẩn hóa tên/giá từ một nguồn, bỏ các card sai dữ liệu trong mockup.
- Chi tiết giữ ảnh trái/thông tin phải ở desktop và một cột mobile; thông số thiếu ghi rõ thay vì tạo dữ liệu giả.
- Bỏ tim, giỏ, số lượng mua và CTA mua theo phạm vi mới; dùng CTA xem/điều hướng. Nav dùng Trang chủ, Sản phẩm, Về Luma.
- Hero dùng ảnh phòng khách sẵn có vì chưa có ảnh hero trùng mockup. Mobile đặt chữ trên nền kem dưới ảnh để dễ đọc.
- Gallery dùng ảnh đã có: Ghế ăn Nordic gồm ảnh toàn cảnh và ảnh cận từ `Art/Prod/Ghe/Ghe-an-Nordic/`; các món khác hiện có một ảnh. Không tạo thêm góc chụp hoặc ảnh màu/vật liệu giả. Giá và mô tả đang là nội dung mẫu; các tuyên bố “bền vững”/giao hàng/bảo hành chưa xác nhận không được đưa vào UI.

## Giới hạn hiện tại

Chưa có API, CMS, đăng nhập, quản trị, giỏ hoặc giao dịch online. Chưa phát hành hosting; nếu phục vụ bằng static host phải có SPA fallback. Bản build đánh dấu không lập chỉ mục tìm kiếm cho đến khi thông tin kinh doanh được xác nhận. SEO metadata có theo trang ở phía client; HTML theo sản phẩm và mã HTTP chính xác cần giải pháp phục vụ/pre-render ở giai đoạn phát hành nếu được yêu cầu.
