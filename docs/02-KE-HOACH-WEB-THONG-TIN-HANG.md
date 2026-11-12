# Kế hoạch web thông tin hàng — Luma

Ngày cập nhật: 12/11/2026. Trạng thái: đã triển khai FE React + TypeScript trong `FE/web-thong-tin/`; BE chỉ làm khi cần dữ liệu động.

Tài liệu liên quan: [Kế hoạch web quản lý](01-KE-HOACH-WEB-QUAN-LY.md).

## 1. Mục tiêu và tổ chức dự án

### 1.1. Phạm vi hiện tại

Website công khai giúp khách xem thông tin nội thất: **Trang chủ → Danh sách sản phẩm → Chi tiết → Quay lại danh sách / Xem món liên quan**.

Ưu tiên danh sách, chi tiết, tìm theo tên, lọc danh mục, sắp xếp đơn giản và các thành phần web cơ bản. Giữ phần giới thiệu/liên hệ gọn trong trang chủ và footer.

Chưa triển khai giỏ hàng, đặt hàng online, checkout (kể cả mô phỏng), thanh toán, tài khoản khách hoặc yêu thích. CTA dùng “Xem sản phẩm”, “Xem chi tiết”, “Quay lại danh sách”. Web quản lý được đặc tả riêng và vẫn tạm hoãn.

### 1.2. Công nghệ

- **FE: React + TypeScript**; **Node.js** cho môi trường phát triển, dependency và build.
- **BE khi cần: Java + Spring Boot**. Chưa cần BE để chạy bản FE dùng dữ liệu mẫu.
- Node.js không đảm nhiệm một backend riêng. Chưa chọn cơ sở dữ liệu hoặc chốt phiên bản công nghệ; kiểm tra tương thích khi khởi tạo ứng dụng.

### 1.3. Quy hoạch thư mục

Đã có ứng dụng `FE/web-thong-tin/` và hướng dẫn chạy ở `README.md`. Web quản lý chưa khởi tạo; `BE/` hiện có placeholder và README, chưa có Spring Boot. Cấu trúc dưới đây thể hiện cả phần hiện có và phần dự kiến:

```text
Noi-that/
├── FE/
│   ├── web-thong-tin/                React TS công khai — đã triển khai
│   └── web-quan-ly/                  React TS quản lý — dự kiến, chưa tạo
├── BE/                              Một backend Spring Boot khi cần
│   └── src/                         API công khai/quản lý tách quyền — dự kiến, chưa tạo
├── Art/                             Ảnh và prototype gốc, giữ làm tham chiếu
└── docs/
    ├── 01-KE-HOACH-WEB-QUAN-LY.md
    └── 02-KE-HOACH-WEB-THONG-TIN-HANG.md
```

FE có thể tách hai ứng dụng để phát triển/phát hành độc lập. BE dự kiến dùng chung dữ liệu sản phẩm/nội dung, tách API công khai và API quản lý; không dựng hai kho dữ liệu sản phẩm độc lập. Phần quản lý không phải điều kiện để hoàn thành bản FE thông tin ban đầu.

## 2. FE — React + TypeScript

### 2.1. Cấu trúc và cách triển khai

Mã FE đã được triển khai trong `FE/web-thong-tin/` theo cấu trúc sau:

```text
FE/web-thong-tin/
├── package.json
├── tsconfig.json
├── public/ hoặc src/assets/          Bản ảnh dùng trong ứng dụng
└── src/
    ├── app/                         Khởi tạo app, route, bố cục chung
    ├── pages/                       Trang chủ, catalog, chi tiết, 404
    ├── components/                  Header, footer, card, gallery, bộ lọc
    ├── styles/                      Token màu, typography, responsive
    ├── types/                       Kiểu dữ liệu TypeScript
    ├── data/                        Dữ liệu mẫu tập trung
    ├── services/                    Đọc dữ liệu mẫu, sau thay bằng API
    └── utils/                       Định dạng tiền, tìm kiếm, xử lý query
```

Dùng Node.js để chạy/build FE. Dữ liệu ban đầu nằm trong ứng dụng, không cần bật Spring Boot. Trang chủ, catalog và chi tiết đọc cùng một nguồn qua `services`, không hard-code giá/tên riêng trong component.

### 2.2. Phân tích tài nguyên UI hiện có

Đã phân tích 6 ảnh UI, 6 ảnh sản phẩm/không gian và prototype HTML/CSS. Hướng thị giác giữ nguyên: nền trắng/kem, xanh rừng cho điểm nhấn, ảnh ánh sáng tự nhiên, font Be Vietnam Pro, icon nét mảnh và nhiều khoảng thở.

#### 2.2.1. Ảnh UI và cách áp dụng theo phạm vi mới

| Tệp tham chiếu | Nội dung | Cách sử dụng hiện tại |
| --- | --- | --- |
| [homepage-ui-mockup.jpg](../Art/DesignUI/homepage-ui-mockup.jpg) | Header, hero, bốn món nổi bật | Giữ bố cục chính; bỏ icon giỏ/yêu thích; CTA dẫn catalog |
| [catalog-collection-ui-mockup.jpg](../Art/DesignUI/catalog-collection-ui-mockup.jpg) | Heading, pill danh mục, sort, grid bốn cột | Tham chiếu chính cho danh sách sản phẩm; sửa sai tên/giá và nhịp card |
| [product-detail-ui-mockup.jpg](../Art/DesignUI/product-detail-ui-mockup.jpg) | Breadcrumb, gallery, tên/giá, biến thể, CTA mua | Giữ gallery/thông tin; bỏ số lượng mua và thêm giỏ; dùng CTA quay lại danh sách |
| [mobile-responsive-ui-mockup.jpg](../Art/DesignUI/mobile-responsive-ui-mockup.jpg) | Mobile trang chủ, chi tiết và giỏ | Chỉ dùng bố cục trang chủ/chi tiết; màn giỏ để sau |
| [design-system-ui-guide.jpg](../Art/DesignUI/design-system-ui-guide.jpg) | Màu, font, button, pill, search, card | Chuẩn hóa hệ thống thiết kế và component |
| [cart-drawer-ui-mockup.jpg](../Art/DesignUI/cart-drawer-ui-mockup.jpg) | Drawer giỏ, mã giảm, tạm tính | Lưu làm tham chiếu tương lai; không triển khai hiện tại |

Ảnh UI có kích thước 1376 × 768 px. Ảnh mobile là ba điện thoại trong một ảnh trình bày; không đưa khung máy hoặc nền trình bày vào website.

#### 2.2.2. Các điểm cần sửa từ mockup

- **Catalog sai dữ liệu:** card ghế đầu thiếu tên/giá ngay dưới ảnh; một ảnh cận sofa mang tên Ghế ăn Nordic; ảnh tủ/bình mang tên Bộ cốc Gốm Mộc và giá `4500.000đ`, khác `450.000đ` ở trang chủ. Dùng dữ liệu chung để không sao chép các lỗi này.
- **Card chưa đồng đều:** chọn grid đều với cùng tỷ lệ ảnh, vị trí tên và giá; không dựng bố cục masonry từ các chỗ lệch của mockup.
- **Hero chưa có đúng ảnh nguồn:** mockup là tủ/bình/đèn, còn `phong-khach-nordic.jpg` có sofa làm trọng tâm. Dùng ảnh phòng khách tạm và ghi nhận khác biệt; muốn khớp cần ảnh hero riêng, không dùng cả mockup chứa chữ làm banner.
- **Gallery thiếu ảnh:** thư mục chỉ có một ảnh ghế. Bản đầu hiển thị một ảnh chính; chỉ dùng thumbnail khi có ảnh bổ sung, không giả nhiều góc chụp bằng cùng một ảnh.
- **Mô tả và nhãn:** sửa câu tiếng Việt lỗi; Việt hóa `Wood finish`, `Oak`, `Walnut`. Không đồng nhất `Walnut` với “Sồi hun khói”. Chỉ hiển thị chất liệu/màu đã có dữ liệu xác nhận.
- **Mobile:** giữ grid hai cột ở chiều rộng điện thoại thông thường; tăng vùng chạm, cho tên dài xuống dòng và giữ giá rõ.
- **Guide/CSS khác nhau:** chuẩn hóa typography/radius theo mục 2.4; card phẳng, ảnh bo nhẹ, không trộn nhiều kiểu bóng và bo góc trong một grid.
- **Cam kết thương mại:** chưa có chính sách thật thì không ghi miễn phí vận chuyển toàn quốc, thời gian bảo hành hoặc nguồn gốc/vật liệu như dữ kiện đã xác nhận.

#### 2.2.3. Trạng thái HTML/CSS hiện tại

`Art/DesignUI/index.html` và `styles.css` mới là prototype trang chủ thuần HTML/CSS/JavaScript. Đã có header sticky, hero, bốn card, hai ảnh bộ sưu tập và footer; chưa có ứng dụng React/TypeScript hay route nhiều trang.

Điều hướng hiện dùng anchor; search chưa có chức năng; nút thêm giỏ chỉ tăng bộ đếm; tim chỉ toggle. CSS dưới 640 px đang chuyển product grid về một cột và ẩn nav mà chưa có menu mobile thay thế.

Khi dựng FE, giữ prototype và ảnh gốc trong `Art/` để đối chiếu. Chuyển ngôn ngữ thiết kế sang component React, sửa responsive/menu và loại thao tác giỏ/yêu thích khỏi UI mới trong giai đoạn này. Không cần hoàn thiện chức năng mua hàng còn dang dở của prototype.

### 2.3. Ảnh và dữ liệu mẫu ban đầu

#### 2.3.1. Bốn sản phẩm

| ID đề xuất | Tên | Giá mẫu VND | Danh mục | Ảnh trong `Art/Prod/` |
| --- | --- | --- | --- | --- |
| `p-binh-gom-luma` | Bình gốm Luma | 620000 | Gốm sứ | `binh-gom-luma.jpg` |
| `p-ghe-an-nordic` | Ghế ăn Nordic | 2900000 | Ghế & Bàn | `ghe-an-nordic.jpg` |
| `p-den-ban-horizon` | Đèn bàn Horizon | 1250000 | Đèn | `den-ban-horizon.jpg` |
| `p-bo-coc-gom-moc` | Bộ cốc Gốm Mộc | 450000 | Gốm sứ | `bo-coc-gom-moc.jpg` |

Giá là dữ liệu mẫu từ ảnh trang chủ/HTML, chưa phải bảng giá kinh doanh đã duyệt. Số tiền lưu dạng số nguyên VND và định dạng thống nhất như `2.900.000đ` khi hiển thị.

Hai ảnh `phong-khach-nordic.jpg` và `phong-an-japandi.jpg` dùng cho phần cảm hứng không gian trên trang chủ. Không đưa sofa/bàn ăn thành sản phẩm có thể xem riêng chỉ vì xuất hiện trong ảnh khi chưa có dữ liệu và ảnh sản phẩm phù hợp.

Sáu ảnh gốc đều 1200 × 896 px, gần tỷ lệ 4:3. Khi dựng FE, tạo bản ảnh phù hợp vị trí hiển thị; giữ nguyên nguồn gốc để có thể crop lại.

#### 2.3.2. Mô hình dữ liệu tối thiểu

| Thực thể | Trường dự kiến |
| --- | --- |
| Product | id, slug, name, categoryId, priceVnd, shortDescription, description, images, specifications, displayOrder, isFeatured |
| ProductImage | src, alt, width, height; điểm crop nếu cần |
| Specification | label, value, unit nếu có |
| Category | id, slug, name, displayOrder |
| HomeContent | heroTitle, heroDescription, heroImage, featuredProductIds, các khối ảnh không gian |
| StoreInfo | tên thương hiệu và thông tin liên hệ đã được cung cấp |

Chưa cần mô hình giỏ, đơn hàng, thanh toán, tồn kho hoặc tài khoản. Chưa buộc mỗi sản phẩm có SKU/biến thể bán hàng. Chất liệu, kích thước và màu dùng như thông tin mô tả khi có; phần chưa có ghi “Thông tin đang cập nhật”, không tự đoán từ hình.

Danh sách, trang chủ và chi tiết phải đọc chung một nguồn; không ghi tên/giá riêng trong từng component.

### 2.4. Hệ thống thiết kế

#### 2.4.1. Token nền tảng

| Nhóm | Giá trị đề xuất | Ứng dụng |
| --- | --- | --- |
| Nền chính | `#FFFFFF` | Trang và bề mặt nội dung |
| Nền kem | `#F3EFE9` | Hero và vùng phụ |
| Nền ảnh chờ | `#F7F5F1` | Placeholder ảnh |
| Chữ chính | `#1F1E1C` | Heading, tên và nội dung |
| Chữ phụ | `#68635B` | Mô tả, chú thích |
| Xanh chính / hover | `#1D3B2D` / `#142A20` | CTA, trạng thái chọn, focus |
| Viền | `#E7E2DA` | Đường phân cách và input |
| Font | Be Vietnam Pro, fallback hệ thống | Toàn bộ UI |
| Khoảng cách | 4, 8, 12, 16, 24, 32, 48, 64 px | Nhịp bố cục |
| Bo góc | 4–8 px ảnh/card; 8 px button; 12 px panel | Thống nhất giữa các trang |

Typography đề xuất: H1 hero desktop 48 px/mobile 30–34 px; H1 catalog/chi tiết desktop 40–44 px/mobile 28–32 px; H2 28 px/mobile 22–24 px; nội dung 16 px; tên card/giá 14–15 px. Không ép số dòng làm mất chữ tiếng Việt.

Container tối đa khoảng 1280 px; gutter desktop 32–48 px, mobile 16 px. Header khoảng 72 px desktop/60 px mobile. Nút và vùng chạm icon tối thiểu 44 px. Đây là đặc tả đề xuất để triển khai, không phải số đo tuyệt đối từ ảnh.

#### 2.4.2. Component React cần có

- `SiteHeader`, `MobileMenu`, `SiteFooter`, `PageContainer`.
- `Button`, `IconButton`, `SearchInput`, `CategoryPill`, `SortSelect`.
- `ProductCard`, `ProductGrid`, `ProductGallery`, `ProductSpecifications`.
- `Breadcrumb`, `Accordion`, `EmptyState`, `ErrorState`, `ImageFallback`.

Component chỉ tách khi dùng lại hoặc có trách nhiệm rõ. Dữ liệu có kiểu TypeScript; trạng thái lọc/tìm kiếm đặt ở trang hoặc URL phù hợp. Không tạo global store cho giỏ/yêu thích vì các chức năng này chưa nằm trong phạm vi.

### 2.5. Sitemap và đặc tả từng trang

| Route | Vai trò | Mức ưu tiên |
| --- | --- | --- |
| `/` | Trang chủ giới thiệu ngắn và sản phẩm nổi bật | P0 |
| `/san-pham` | Danh sách sản phẩm, tìm kiếm/lọc/sắp xếp đơn giản | P0 chính |
| `/san-pham/:slug` | Xem chi tiết sản phẩm | P0 chính |
| Route không tồn tại | Trang 404 có đường quay lại | P0 |

Thông tin giới thiệu/liên hệ ban đầu có thể là section trong trang chủ và footer. Chưa cần route bộ sưu tập, yêu thích, giỏ, checkout, tài khoản hoặc quản trị.

#### 2.5.1. Khung website và trang chủ

- Header: logo Luma về trang chủ; menu Trang chủ, Sản phẩm, Giới thiệu. Có tìm kiếm đơn giản đưa về catalog với query.
- Mobile: nút menu trái, logo giữa, nút tìm kiếm phải; không hiện icon giỏ/yêu thích.
- Trang chủ: hero gọn → bốn sản phẩm nổi bật → hai ảnh cảm hứng không gian → giới thiệu ngắn/footer.
- Hero giữ headline và CTA “Xem sản phẩm” dẫn `/san-pham`; điều chỉnh crop theo ảnh thực tế.
- “Xem tất cả” dẫn catalog. Ảnh không gian có thể dẫn catalog với danh mục phù hợp, không cần trang bộ sưu tập riêng.
- Footer chỉ có liên kết hoạt động và thông tin đã cung cấp; không tự tạo số điện thoại, địa chỉ hoặc chính sách.

#### 2.5.2. Danh sách sản phẩm — ưu tiên triển khai đầu tiên sau khung chung

- Heading, mô tả ngắn, ô tìm kiếm, pill danh mục, sort, số kết quả, grid.
- Card có ảnh, tên, giá tham khảo; ảnh/tên hoặc CTA “Xem chi tiết” dẫn đến đúng slug. Không có nút “Thêm vào giỏ”.
- Tìm theo tên, không phân biệt hoa/thường và hỗ trợ chuỗi không dấu như `ghe an`.
- Lọc một danh mục tại một thời điểm; “Tất cả” bỏ lọc. Danh mục mặc định: Ghế & Bàn, Đèn, Gốm sứ. Chưa hiển thị Trang trí nếu chưa có món phù hợp.
- Sort: Nổi bật, Giá tăng dần, Giá giảm dần; không thêm nhiều bộ lọc khi mới có bốn sản phẩm.
- Đồng bộ `q`, `category`, `sort` vào URL; refresh/back giữ trạng thái. Giá trị query không hợp lệ trở về lựa chọn mặc định.
- Dữ liệu bốn món không cần phân trang hoặc nút tải thêm. Để khả năng mở rộng trong lớp dữ liệu, chưa triển khai sớm.
- Không có kết quả: giải thích và nút xóa tìm kiếm/bộ lọc. Dữ liệu lỗi: thông báo và thử lại khi nguồn hỗ trợ.

**Nghiệm thu:** lọc Ghế & Bàn ra Ghế ăn Nordic; sort giá tăng dần ra Cốc → Bình → Đèn → Ghế; tìm món không có ra empty state; mỗi card mở đúng trang chi tiết.

#### 2.5.3. Chi tiết sản phẩm — ưu tiên chính

- Breadcrumb: Trang chủ / Sản phẩm / Tên món.
- Desktop: ảnh/gallery bên trái, tên/giá/mô tả/thông số bên phải. Mobile: ảnh trước, thông tin sau.
- Ảnh chính có tỷ lệ hợp lý, không crop mất vật thể; có alt. Gallery chỉ xuất hiện khi thật sự có nhiều ảnh.
- Đã bổ sung gallery: có từ hai ảnh thì hiện mũi tên trái/phải chồng ở hai bên ảnh lớn và thumbnail ngay dưới để chọn, có viền đánh dấu ảnh hiện tại. Chuyển vòng đầu/cuối, hỗ trợ bàn phím; một ảnh thì ẩn các điều khiển này. Dữ liệu hiện vẫn một ảnh mỗi món, chờ bổ sung ảnh thật.
- Hiển thị tên, giá tham khảo, mô tả ngắn, mô tả chi tiết, chất liệu/kích thước nếu có.
- Thuộc tính màu/chất liệu hiện dưới dạng thông tin đọc; chưa dựng chọn phiên bản mua hàng, số lượng, tồn kho hoặc thanh mua cố định.
- Dùng accordion cho nội dung dài như thông số/hướng dẫn bảo quản khi có dữ liệu. Không tạo các cam kết giao hàng/bảo hành chưa xác nhận.
- CTA “Quay lại danh sách” đưa về catalog, ưu tiên giữ query trước đó; nếu truy cập trực tiếp thì về catalog mặc định.
- Sản phẩm liên quan dùng lại card, loại trừ món hiện tại; có thể chọn theo cùng danh mục, không cố đủ bốn card.
- Slug sai hiển thị trang không tìm thấy, có link về catalog; mở trực tiếp URL hợp lệ phải xem được đúng món.

#### 2.5.4. Thành phần đơn giản và các trạng thái

- Menu mobile có nút đóng; đóng khi chọn link hoặc Escape; trả focus về nút mở.
- Ảnh thiếu/hỏng có nền dự phòng và giữ tỷ lệ để không làm lệch grid.
- Dữ liệu thông số chưa đủ dùng nhãn cập nhật; không làm trang chi tiết thành khối trống khó hiểu.
- Trang 404 có lời giải thích, link về trang chủ và catalog.
- Không tạo loading giả khi dữ liệu cục bộ đọc ngay; chỉ cần thiết kế trạng thái tải cho khi nối API.
- Các link/button đang hiển thị đều có hành vi hữu ích; không dùng `#` cho chức năng chưa làm.

### 2.6. Responsive và khả năng sử dụng

| Chiều rộng đề xuất | Danh sách | Chi tiết / điều hướng |
| --- | --- | --- |
| 360–639 px | Hai cột, gap khoảng 12 px; gutter 16 px | Một cột; menu mobile; không có CTA mua cố định |
| 640–1023 px | Hai cột dưới 768 px, ba cột từ 768 px | Một cột khi chưa đủ chỗ; hai cột từ khoảng 900 px nếu đọc tốt |
| Từ 1024 px | Bốn cột, gap khoảng 24 px | Hai cột ảnh/thông tin; menu đầy đủ |

- Kiểm tra thực tế bằng tên tiếng Việt dài; khi phóng chữ có thể giảm số cột để đọc được.
- Không cuộn ngang toàn trang; pill danh mục có thể cuộn trong vùng riêng trên mobile.
- Chữ trên hero cần vùng nền/gradient rõ. Nếu ảnh mobile không có vùng sạch, đặt nội dung dưới ảnh trên nền kem.
- Có trạng thái focus; thao tác được bằng bàn phím; icon có nhãn; vùng chạm đủ rộng.
- Heading có cấu trúc, mỗi trang có tiêu đề phù hợp; màu không phải dấu hiệu duy nhất của danh mục đang chọn.
- Khai báo kích thước/tỷ lệ ảnh, dùng ảnh tối ưu theo vị trí; kiểm tra font tiếng Việt, không để tải ảnh/font làm bố cục nhảy nhiều.

### 2.7. Lộ trình FE theo phạm vi hiện tại

Các bước FE-0 đến FE-5 đã được thực hiện cho bản dữ liệu mẫu. Ước lượng ban đầu dưới đây được giữ làm tham khảo, chưa gồm ảnh/nội dung mới, BE, quản trị hoặc đặt hàng. Hướng dẫn chạy, build và chỉnh dữ liệu nằm trong README của dự án và FE.

| Bước | Công việc | Đầu ra / điều kiện hoàn thành | Ước lượng |
| --- | --- | --- | --- |
| FE-0 | Chốt token, crop ảnh, kiểu dữ liệu và phạm vi | Đặc tả UI thống nhất, dữ liệu bốn món rõ | 1 ngày |
| FE-1 | Khởi tạo React TS bằng môi trường Node.js; route, CSS nền, header/footer/menu | FE chạy độc lập; có khung ba trang chính và 404 | 1 ngày |
| FE-2 | ProductCard, grid, catalog, search/lọc/sort qua URL | Danh sách sản phẩm dùng được trên desktop/mobile | 2–3 ngày |
| FE-3 | Chi tiết, ảnh, mô tả, thông số, breadcrumb, liên quan | Mỗi món có URL riêng, quay lại danh sách đúng | 1–2 ngày |
| FE-4 | Trang chủ gọn, giới thiệu, ảnh không gian | Hoàn thiện phần cơ bản và điều hướng | 1 ngày |
| FE-5 | Kiểm tra trình duyệt, responsive, lỗi/ảnh/font, build và bản xem thử | Đạt checklist dưới đây; ghi nhận phần thiếu | 1–2 ngày |

Tổng sơ bộ **7–10 ngày công**. Đây là ước lượng ban đầu, không phải số ngày công thực tế đã đo. Bản FE mẫu hiện đã được triển khai và kiểm tra; BE/quản lý vẫn chưa triển khai.

Thứ tự ưu tiên: **khung chung → danh sách → chi tiết → trang chủ gọn → kiểm tra và bàn giao**. Không chen giỏ hàng hoặc checkout vào giữa các bước này.

### 2.8. Checklist nghiệm thu FE

#### 2.8.1. Phạm vi và hành vi

- [x] FE dùng React + TypeScript, chạy/build bằng môi trường Node.js; chạy được mà không cần BE.
- [x] Có trang chủ, danh sách, chi tiết từng món và 404; mở trực tiếp URL hoạt động.
- [x] Card ảnh/tên/giá cùng đọc một nguồn; không còn sai tên/giá như ảnh catalog.
- [x] Tìm kiếm, lọc danh mục và sort đúng; query được giữ khi refresh/back.
- [x] Thứ tự giá tăng: Bộ cốc 450.000đ → Bình 620.000đ → Đèn 1.250.000đ → Ghế 2.900.000đ.
- [x] Trang chi tiết có thông tin đúng món; dữ kiện thiếu được ghi rõ, không suy ra từ ảnh.
- [x] Mọi CTA/link hiển thị hoạt động; không có icon giỏ, thêm giỏ, số lượng mua, checkout hoặc đặt hàng.
- [x] Không có đăng nhập/quản trị hoặc endpoint giao dịch được triển khai ngoài phạm vi.

#### 2.8.2. Trình bày và chất lượng

- [x] Kiểm tra tại 360, 390, 768, 1024, 1376 và 1440 px; không tràn ngang/chữ đè nhau.
- [x] Grid mobile hai cột ở cỡ chữ mặc định; desktop bốn cột; chi tiết co về một cột khi cần.
- [x] Không cắt mất tên/giá hoặc vật thể chính; font hiển thị đủ dấu tiếng Việt.
- [x] Menu mobile, input và link thao tác được bằng bàn phím, focus nhìn thấy rõ.
- [ ] Có empty state, 404, thông số chưa có và dự phòng ảnh lỗi.
- [ ] Không có lỗi console từ app, link hỏng hoặc layout nhảy nhiều khi tải ảnh.
- [x] Build thành công và kiểm tra trên trình duyệt với luồng trang chủ → catalog → chi tiết → quay lại.
- [x] Bàn giao cách chạy/build, vị trí dữ liệu mẫu, phần còn thiếu và trạng thái BE chưa triển khai.

### 2.9. Nội dung còn thiếu

| Nội dung | Cách xử lý hiện tại |
| --- | --- |
| Ảnh hero đúng mockup | Dùng ảnh phòng khách sẵn có và ghi nhận sai khác |
| Gallery nhiều góc và ảnh từng chất liệu/màu | Chỉ dùng ảnh thực có; chưa dựng selector phiên bản mua |
| Kích thước, vật liệu, bảo quản | Chỉ công khai dữ liệu được cung cấp; phần thiếu ghi đang cập nhật |
| Giá chính thức | Dùng giá mẫu để dựng UI, ghi rõ là tham khảo cho đến khi xác nhận |
| Liên hệ, địa chỉ, câu chuyện thương hiệu | Bổ sung khi có nội dung; chưa tạo dữ liệu giả |
| API và lưu trữ | FE dùng dữ liệu mẫu; thư mục BE giữ sẵn cho Spring Boot nếu cần |

## 3. BE — Spring Boot khi cần

### 3.1. Điều kiện và phạm vi triển khai

Chỉ bắt đầu BE khi cần cập nhật sản phẩm độc lập với bản build FE, cần nguồn dữ liệu động hoặc kết nối web quản lý. BE chưa phải hạng mục thực hiện ngay; thư mục `BE/` đang để sẵn.

BE phục vụ website thông tin chỉ cung cấp dữ liệu công khai: sản phẩm, danh mục, ảnh và nội dung trang chủ. Không có API giỏ, tạo đơn, thanh toán hay giữ hàng trong phạm vi hiện tại.

### 3.2. Tổ chức backend dự kiến

Một ứng dụng Java + Spring Boot trong `BE/`, chia theo miền sản phẩm, danh mục, media, nội dung và các lớp controller/service/repository/model/DTO phù hợp. API công khai và quản lý có controller/DTO/quyền riêng, dùng chung service nghiệp vụ và nguồn dữ liệu khi thích hợp.

Chưa chốt Maven/Gradle, hệ quản trị cơ sở dữ liệu hoặc hạ tầng. Lựa chọn khi bắt đầu BE; không thêm các thành phần này chỉ để dựng thư mục.

### 3.3. API chỉ đọc dự kiến

| API | Mục đích | Quy tắc |
| --- | --- | --- |
| `GET /api/products` | Danh sách theo `q`, `category`, `sort` | Chỉ sản phẩm công khai; cùng quy tắc lọc/sort với FE |
| `GET /api/products/{slug}` | Chi tiết sản phẩm | Không có hoặc chưa công khai trả lỗi không tìm thấy |
| `GET /api/categories` | Danh mục hiển thị | Tên/slug/thứ tự thống nhất |
| `GET /api/home` | Nội dung trang chủ nếu cần dữ liệu động | Chỉ bản đã công khai |

Đây là hợp đồng dự kiến, chưa phải endpoint đã tồn tại. Danh sách có `items` và `total`; phân trang bổ sung khi số sản phẩm cần đến. Query không hợp lệ được xử lý nhất quán, lỗi trả mã ổn định để FE hiển thị tiếng Việt.

### 3.4. Dữ liệu và kết nối FE

- Dùng mô hình Product, ProductImage, Specification, Category, HomeContent và StoreInfo ở mục 2.3.2 làm căn cứ trao đổi dữ liệu.
- Giá là số nguyên VND; ID ổn định, slug duy nhất. Không suy ra thuộc tính sản phẩm từ ảnh.
- API công khai chỉ trả dữ liệu được công khai; không trả dữ liệu nháp, thông tin nhân sự hoặc trường nội bộ.
- Trong FE, `listProducts(query)`, `getProduct(slug)`, `listCategories()` và `getHomeContent()` chuyển nguồn từ dữ liệu mẫu sang API; component giữ nguyên trách nhiệm hiển thị.
- Cấu hình địa chỉ API theo môi trường; không đặt bí mật trong mã FE. Chốt chính sách truy cập giữa FE/BE khi triển khai thực tế.

### 3.5. Nghiệm thu BE khi được triển khai

- [ ] Các API chỉ trả dữ liệu công khai, có lỗi không tìm thấy rõ ràng.
- [ ] Tìm/lọc/sort và định dạng dữ liệu thống nhất với bản FE dùng dữ liệu mẫu.
- [ ] Mở URL sản phẩm trực tiếp, tải lỗi và thử lại được kiểm tra sau khi nối API.
- [ ] Thay dữ liệu mẫu bằng BE không phải viết lại card/trang chi tiết.
- [ ] Không phát sinh API đặt hàng hoặc quản lý ngoài phạm vi đã đưa vào thực hiện.

## 4. Quan hệ với web quản lý và thứ tự thực hiện

1. Đã làm FE web thông tin: khung chung → catalog → chi tiết → trang chủ gọn → kiểm tra/bàn giao, theo mục 2.7. Build/kiểm tra kiểu và 6 kịch bản dữ liệu đã qua; kiểm tra tương tác bằng trình duyệt IAB.
2. Khi cần dữ liệu động, triển khai BE công khai rồi nối FE theo mục 3.
3. Web quản lý được triển khai riêng ở giai đoạn sau theo [tài liệu web quản lý](01-KE-HOACH-WEB-QUAN-LY.md); nội dung công khai từ quản lý trở thành nguồn cho API thông tin.
4. Giỏ hàng/đặt hàng online/thanh toán tiếp tục để ngoài cả hai kế hoạch thực hiện hiện tại.

Ước lượng 7–10 ngày công ở mục 2.7 chỉ dành cho FE web thông tin. Chưa tính BE, quản lý, nội dung/ảnh mới hoặc giao dịch online.
