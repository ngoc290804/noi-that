# Kế hoạch web quản lý — Luma

Ngày cập nhật: 12/11/2026. **Trạng thái: quy hoạch cho giai đoạn sau, tạm thời chưa triển khai.**

Tài liệu liên quan: [Kế hoạch web thông tin hàng](02-KE-HOACH-WEB-THONG-TIN-HANG.md).

## 1. Mục tiêu và tổ chức dự án

### 1.1. Vai trò của web quản lý

Web quản lý dành cho nhân sự cửa hàng cập nhật sản phẩm, danh mục, ảnh, thông số và nội dung hiển thị trên web thông tin. Giai đoạn hiện tại ưu tiên FE web thông tin bằng dữ liệu mẫu; chưa xây dựng ứng dụng quản lý, đăng nhập hoặc API quản lý.

Khi bắt đầu giai đoạn quản lý, chỉ tập trung vào **quản lý thông tin hàng và nội dung**, chưa quản lý đơn hàng, thanh toán, tồn kho giao dịch, khuyến mãi hoặc vận chuyển. Đặt hàng online tiếp tục để giai đoạn riêng sau này.

Không có ảnh UI quản lý trong bộ tài nguyên hiện tại. Bố cục và màn hình dưới đây là đề xuất để triển khai sau, không phải giao diện đã được cung cấp hoặc đã xây dựng.

### 1.2. Công nghệ và phân chia trách nhiệm

| Phần | Công nghệ | Trách nhiệm |
| --- | --- | --- |
| FE quản lý | React + TypeScript | Đăng nhập, danh sách, biểu mẫu, chọn ảnh, xem trước và thao tác công khai |
| Môi trường FE | Node.js | Dependency, chạy phát triển và build; không làm backend riêng |
| BE quản lý | Java + Spring Boot | Xác thực, phân quyền, kiểm tra dữ liệu, lưu nội dung và cung cấp API quản lý |
| Lưu trữ | Chốt khi bắt đầu BE | Dữ liệu sản phẩm/nội dung và media; chưa chọn cơ sở dữ liệu hoặc nhà cung cấp |

Phiên bản công nghệ và cấu hình triển khai sẽ được kiểm tra khi khởi tạo ứng dụng. Hiện tại chưa cài dependency hay tạo dự án Spring Boot.

### 1.3. Quy hoạch thư mục và quan hệ với web thông tin

```text
Noi-that/
├── FE/
│   ├── web-thong-tin/                Ứng dụng React TS công khai — làm trước
│   └── web-quan-ly/                  Ứng dụng React TS quản lý — làm sau
├── BE/                              Backend Spring Boot dùng chung khi cần
│   └── src/                         API công khai và API quản lý tách quyền
├── Art/                             Tài nguyên tham chiếu gốc
└── docs/
    ├── 01-KE-HOACH-WEB-QUAN-LY.md
    └── 02-KE-HOACH-WEB-THONG-TIN-HANG.md
```

Đây là cấu trúc dự kiến khi lập trình. Hiện tại `FE/web-thong-tin/` đã có ứng dụng React TypeScript. `FE/web-quan-ly/` chưa tạo; `BE/` chỉ có placeholder và README, chưa khởi tạo backend.

Hai FE có thể phát triển/phát hành độc lập. Một BE dự kiến phục vụ cả API đọc công khai và API quản lý, dùng chung dữ liệu sản phẩm/nội dung; API quản lý có xác thực và quyền riêng. Không tạo hai danh mục sản phẩm độc lập phải đồng bộ thủ công.

## 2. BE — Spring Boot cho web quản lý

### 2.1. Phạm vi nghiệp vụ khi được triển khai

| Miền | Chức năng cần có | Chưa bao gồm |
| --- | --- | --- |
| Sản phẩm | Tạo, sửa, xem trước, công khai, ẩn/lưu trữ; chỉnh thứ tự và nổi bật | Giỏ, đặt hàng, biến thể mua hàng hoặc tồn kho giao dịch |
| Danh mục | Tạo/sửa tên, slug, thứ tự, trạng thái hiển thị | Phân loại nhiều tầng phức tạp khi chưa có nhu cầu |
| Media | Tải ảnh, nhập alt, sắp xếp, gắn vào sản phẩm/nội dung | Tạo ảnh giả hoặc tự suy ra thuộc tính sản phẩm |
| Nội dung | Hero, sản phẩm nổi bật, ảnh không gian, giới thiệu và liên hệ | Hệ thống bài viết/bộ sưu tập chuyên sâu |
| Nhân sự | Đăng nhập, kiểm tra quyền và nhật ký thao tác | Tài khoản khách mua hàng |

### 2.2. Dữ liệu dùng chung

Dựa trên mô hình Product, ProductImage, Specification, Category, HomeContent và StoreInfo trong [mục dữ liệu của web thông tin](02-KE-HOACH-WEB-THONG-TIN-HANG.md#232-mô-hình-dữ-liệu-tối-thiểu).

Các trường bổ sung phục vụ quản lý:

- Sản phẩm/danh mục/nội dung: trạng thái công khai, thời gian cập nhật, người cập nhật và phiên bản để nhận biết xung đột chỉnh sửa.
- Sản phẩm: ID ổn định, slug duy nhất, tên, danh mục, giá VND, mô tả, ảnh, thông số, thứ tự và cờ nổi bật.
- Media: định danh, đường dẫn, alt, kích thước, nguồn/quyền sử dụng nếu có và các vị trí đang tham chiếu.
- Nhân sự/quyền/nhật ký: tài khoản quản lý, quyền thao tác và lịch sử thay đổi cần truy vết.

Quy tắc kiểm tra: số tiền nguyên VND không âm, slug không trùng, danh mục tồn tại, thông số có nhãn/giá trị rõ, ảnh hợp lệ. Không buộc có SKU/biến thể/tồn kho khi sản phẩm chỉ phục vụ xem thông tin.

### 2.3. API quản lý dự kiến

| Nhóm API | Thao tác | Kiểm soát chính |
| --- | --- | --- |
| Phiên đăng nhập | Đăng nhập, đọc tài khoản hiện tại, đăng xuất | Xác thực, hết phiên, bảo vệ thông tin đăng nhập |
| `/api/admin/products` | Danh sách/chi tiết, tạo, sửa, công khai, ẩn/lưu trữ | Quyền sản phẩm, dữ liệu hợp lệ, xử lý xung đột |
| `/api/admin/categories` | Danh sách, tạo, sửa, đổi thứ tự/trạng thái | Slug duy nhất, xử lý khi danh mục đang có sản phẩm |
| `/api/admin/media` | Upload, đọc, cập nhật metadata, xóa khi không còn tham chiếu | Loại/kích thước/nội dung tệp, quyền và tham chiếu |
| `/api/admin/content` | Đọc/sửa bản nháp, xem trước, công khai nội dung | Quyền nội dung; bản nháp không lộ qua API công khai |
| Nhật ký | Đọc lịch sử thay đổi theo đối tượng | Chỉ người có quyền xem |

Đây là nhóm endpoint đề xuất, chưa tồn tại. Đường dẫn thao tác cụ thể và cơ chế phiên đăng nhập chốt khi thiết kế BE. Phần API chỉ đọc `/api/products`, `/api/categories`, `/api/home` thuộc [phần BE web thông tin](02-KE-HOACH-WEB-THONG-TIN-HANG.md#3-be--spring-boot-khi-cần); không triển khai lặp ở một backend khác.

### 2.4. Luồng công khai nội dung

1. Tạo hoặc sửa bản nháp.
2. Kiểm tra trường bắt buộc và tính hợp lệ.
3. Xem trước ở chế độ có quyền, không công khai bản nháp cho khách.
4. Công khai hoặc lưu trữ theo quyền được cấp.
5. API công khai trả bản đã công khai; bản đã công khai vẫn giữ nguyên khi đang chỉnh một bản nháp mới.
6. Cập nhật/ẩn sản phẩm được phản ánh trên FE thông tin khi làm mới dữ liệu; xử lý URL đã bị ẩn bằng trạng thái không tìm thấy phù hợp.

Khi đổi slug của sản phẩm đang công khai, cần lưu đường dẫn cũ và xử lý chuyển hướng nếu muốn duy trì liên kết. Không âm thầm làm mất liên kết đã chia sẻ.

### 2.5. Quyền và vận hành

- Vai trò dự kiến: chủ cửa hàng có quyền cấu hình/nhân sự/công khai; nhân sự nội dung được sửa và công khai theo quyền được cấp.
- Kiểm tra quyền phía máy chủ cho mọi thao tác; ẩn nút FE chỉ hỗ trợ giao diện.
- Lưu nhật ký người thao tác, đối tượng, hành động, thời gian và thay đổi cần thiết; không ghi mật khẩu/bí mật vào log.
- Upload kiểm tra tệp ở máy chủ; không xóa ảnh đang được nội dung công khai tham chiếu nếu chưa có phương án thay thế.
- Không ghi đè âm thầm khi hai người sửa cùng bản ghi; thông báo và yêu cầu tải lại/đối chiếu thay đổi.
- Khi dùng dữ liệu thật cần cấu hình môi trường, sao lưu/khôi phục và giám sát lỗi phù hợp.

### 2.6. Nghiệm thu BE khi bắt đầu làm quản lý

- [ ] Đăng nhập/đăng xuất/hết phiên và quyền đọc/ghi được kiểm tra trên backend.
- [ ] CRUD thông tin sản phẩm/danh mục hợp lệ; dữ liệu sai hoặc slug trùng bị từ chối rõ ràng.
- [ ] Bản nháp không xuất hiện trong API công khai; công khai/ẩn phản ánh đúng ở web thông tin.
- [ ] Media có kiểm tra tệp, alt/thứ tự và kiểm soát tham chiếu trước khi xóa.
- [ ] Có xử lý xung đột chỉnh sửa và nhật ký phù hợp.
- [ ] Không phát sinh module đơn hàng/thanh toán trong giai đoạn quản lý thông tin.

## 3. FE — React + TypeScript cho web quản lý

### 3.1. Cấu trúc dự kiến

Khi giai đoạn quản lý được đưa vào thực hiện, khởi tạo trong `FE/web-quan-ly/`:

```text
FE/web-quan-ly/
├── package.json
├── tsconfig.json
└── src/
    ├── app/                         Route, layout quản lý và trạng thái phiên
    ├── pages/                       Đăng nhập, sản phẩm, danh mục, media, nội dung
    ├── components/                  Bảng, bộ lọc, form, uploader, dialog
    ├── types/                       DTO/kiểu dữ liệu TypeScript
    ├── services/                    API quản lý và xử lý lỗi/phiên
    ├── styles/                      Token và bố cục quản lý
    └── utils/                       Định dạng, kiểm tra dữ liệu hỗ trợ form
```

Dùng Node.js cho phát triển/build. FE không quyết định quyền cuối cùng hoặc tự xác nhận rằng dữ liệu đã được lưu khi BE chưa trả thành công.

### 3.2. Màn hình và điều hướng

Route dưới đây tương đối với ứng dụng FE quản lý; cách đặt tên miền/base path chốt khi phát hành.

| Màn / route đề xuất | Nội dung |
| --- | --- |
| `/dang-nhap` | Form đăng nhập, báo sai thông tin và hết phiên |
| `/san-pham` | Bảng sản phẩm: ảnh, tên, danh mục, giá, trạng thái, thời gian sửa |
| `/san-pham/tao-moi`, `/san-pham/:id` | Biểu mẫu sản phẩm và xem trước |
| `/danh-muc` | Danh sách, tên/slug/thứ tự và trạng thái |
| `/thu-vien-anh` | Upload, xem trước, alt và chọn ảnh |
| `/noi-dung` | Hero, sản phẩm nổi bật, ảnh không gian, giới thiệu/liên hệ |
| `/nhan-su` | Quản lý nhân sự/quyền nếu được cấp quyền |

Trang đầu có thể là danh sách sản phẩm. Chưa cần dashboard hoặc biểu đồ khi mục tiêu chỉ là quản lý nội dung.

### 3.3. Bố cục và biểu mẫu

- Giữ nhận diện Luma: Be Vietnam Pro, nền sáng, xanh rừng `#1D3B2D` cho hành động chính.
- Desktop dùng sidebar và vùng nội dung; ưu tiên bảng/form rõ ràng, không dùng hero bán hàng trong quản lý.
- Bảng có tìm kiếm, lọc trạng thái/danh mục và phân trang khi dữ liệu cần; có loading, empty, error và nút thử lại.
- Form chia nhóm nhận diện, phân loại, mô tả, giá tham khảo, ảnh, thông số và hiển thị. Dùng nhãn đơn vị rõ ràng.
- Có lỗi ngay tại trường; dữ liệu người dùng đang nhập được giữ khi lỗi có thể khắc phục; trạng thái lưu không gây gửi trùng.
- Có xem trước, lưu nháp, công khai/ẩn; phân biệt rõ thay đổi đang sửa và bản đã được công khai.
- Khi rời form còn thay đổi chưa lưu có cảnh báo phù hợp. Thao tác lưu trữ/xóa ảnh có giải thích ảnh hưởng và xác nhận.
- Màn nhỏ thu menu và giữ thao tác chính tiếp cận được; bảng có vùng cuộn riêng khi cần, không làm toàn trang vỡ bố cục.

### 3.4. Trạng thái và tích hợp BE

- UI đọc quyền tài khoản để hiển thị thao tác phù hợp; lỗi 401/403 đưa người dùng đến trạng thái đăng nhập/hạn chế quyền rõ ràng.
- Chỉ báo lưu thành công sau khi BE xác nhận. Nếu xung đột phiên bản, giữ dữ liệu đang sửa và hướng dẫn đối chiếu/tải lại.
- Lỗi upload chỉ rõ tệp không hợp lệ; hỗ trợ thử lại mà không mất các trường khác.
- Sau khi công khai nội dung, có link kiểm tra ở web thông tin; không đưa dữ liệu nháp vào đường dẫn công khai.
- Không gắn nút giỏ/đặt hàng, dashboard giao dịch hoặc chức năng chưa có trong BE.

### 3.5. Nghiệm thu FE khi bắt đầu làm quản lý

- [ ] Đăng nhập → mở sản phẩm → sửa/lưu nháp → xem trước → công khai → kiểm tra web thông tin hoạt động hoàn chỉnh.
- [ ] Form có nhãn, lỗi tại trường, focus bàn phím và trạng thái đang lưu rõ ràng.
- [ ] Bảng tìm/lọc đúng, thao tác theo quyền, lỗi/hết phiên được xử lý.
- [ ] Ảnh/tên/giá/thông số hiển thị thống nhất với nguồn BE và web thông tin.
- [ ] Chưa lưu, xung đột chỉnh sửa và thao tác có ảnh hưởng đều có phản hồi phù hợp.
- [ ] Không có lỗi console từ app hoặc chức năng giao dịch ngoài phạm vi.

## 4. Thứ tự thực hiện trong tương lai

1. Hoàn thành FE web thông tin theo [kế hoạch riêng](02-KE-HOACH-WEB-THONG-TIN-HANG.md).
2. Khi cần cập nhật dữ liệu động, chốt mô hình lưu trữ và BE dùng chung, API công khai và hợp đồng dữ liệu.
3. Khi bắt đầu làm quản lý: triển khai BE xác thực/quyền, sản phẩm, danh mục, media và nội dung theo mục 2.
4. Triển khai FE quản lý theo mục 3; có thể dựng khung/form theo hợp đồng API trước, chỉ nghiệm thu lưu/công khai sau khi nối BE thật.
5. Nhập dữ liệu mẫu đã chuẩn hóa, kiểm tra luồng quản lý → công khai → web thông tin; bàn giao cách vận hành.

Chưa đặt thời hạn quản lý vì phần này vẫn hoãn và chưa chốt lưu trữ/xác thực/nội dung thực tế. Ước lượng sau khi đưa vào phạm vi triển khai; không cộng vào 7–10 ngày công của FE web thông tin.

## 5. Trạng thái hiện tại và việc để sau

FE web thông tin đã được triển khai và chạy độc lập bằng dữ liệu mẫu. Web quản lý vẫn ở bước quy hoạch; chưa khởi tạo ứng dụng quản lý, Spring Boot, cơ sở dữ liệu hoặc tài khoản nhân sự. Trong lúc chưa có quản lý, nội dung của web thông tin được sửa ở nguồn dữ liệu mẫu của FE.

Đơn hàng, thanh toán, tồn kho giao dịch, khuyến mãi, tài khoản khách và giao hàng không thuộc kế hoạch quản lý thông tin này; chỉ bổ sung khi có yêu cầu tiếp theo.
