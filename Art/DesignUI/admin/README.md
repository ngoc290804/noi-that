# Luma Catalog Studio — Giao diện quản lý

Mẫu chính: `products-concept.png` (1536×1024), tạo bằng Imagegen. FE tại `FE/web-quan-ly/`.

## Hệ thiết kế

- Nền `#F7F8F5`, sidebar `#F1F2EC`, bảng/dialog trắng `#FFFFFF`.
- Hành động/heading `#1D3B2D`, chữ `#242A26`, chữ phụ `#777E77`, viền `#DFE3DC`, sao `#C8A044`.
- Be Vietnam Pro 400/500/600/700; heading 36 px desktop/28 px mobile, nav/tên sản phẩm 15 px, slug 12 px.
- Sidebar 268 px, topbar 56 px, lề 26 px. Thống kê chia cột bằng đường kẻ. Panel radius 12 px, button 7 px; icon nét 1.5 px. Ảnh không phủ màu.
- Component: shell, breadcrumb, heading, summary strip, tabs, toolbar, table, status, sao, pagination, dialog, form, media picker và preview.
- Dưới 760 px thu menu, bảng cuộn trong panel, form một cột và thư viện ảnh hai cột.

## Nội dung chính

Sidebar: Sản phẩm, Danh mục, Thư viện ảnh, Nội dung website. Breadcrumb: Quản lý nội dung / Sản phẩm. Tác vụ: Xem website, Xuất danh sách, Thêm sản phẩm. Heading: Sản phẩm. Mô tả: Chăm chút từng sản phẩm, hoàn thiện không gian sống. Thống kê: Tổng sản phẩm, Đang hiển thị, Bản nháp, Nổi bật. Tabs: Tất cả, Đang hiển thị, Bản nháp, Đã lưu trữ. Bảng: Sản phẩm, Danh mục, Giá tham khảo, Trạng thái, Nổi bật. Footer: Chế độ demo · Thay đổi được lưu trên trình duyệt này.

## Đối chiếu triển khai

| Điểm | Kết quả |
| --- | --- |
| Bố cục | Giữ sidebar, topbar, heading, thống kê, toolbar, bảng 6 hàng và pagination; chỉnh spacing để vừa 1536×1024 |
| Typography | Font thương hiệu tự host; tăng cỡ chữ desktop sau đối chiếu render đầu tiên |
| Màu | Giữ bảng trắng, nền sáng, xanh rừng, sao vàng; không phủ màu ảnh |
| Icon | Outline đồng bộ, sao bật/tắt, menu chỉ xuất hiện trên mobile |
| Copy | Giữ nhãn/thứ tự chính; thêm “Đặt lại bản demo” ở footer để khôi phục dữ liệu |
| Dữ liệu/ảnh/logo | Dùng giá, số sao, ảnh và logo hiện có trong repo thay cho chi tiết khác nhau do concept sinh ra; đây là khác biệt có chủ đích |
| Mobile | Menu thu gọn và bảng cuộn riêng; sửa nhãn hỗ trợ đọc màn hình gây tràn ngang toàn trang |

Danh mục, media, nội dung và form dùng cùng hệ component. FE đã nối BE CRUD bằng tài khoản ADMIN/EDITOR và phiên đăng nhập, không dùng kho demo. ADMIN có thêm mục Tài khoản để quản lý người dùng. Biểu mẫu sản phẩm chia bốn tab Thông tin/Hình ảnh/Thông số/Bảo quản, có trạng thái và một nút Lưu sản phẩm. Thêm xác nhận xóa và lỗi inline, dùng cùng hệ thiết kế ban đầu.
