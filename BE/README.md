# Luma API — Spring Boot + Neon PostgreSQL

Backend sơ bộ cho website thông tin nội thất: sản phẩm, danh mục, nội dung trang chủ, kiểm tra DB và **lưu ảnh trên Cloudinary**. Dùng **Java 17+, Maven 3.6.3+, Spring Boot 3.5.16, Spring JDBC, PostgreSQL và Flyway 11.20.3** (hỗ trợ PostgreSQL 18 trên Neon). API quản lý ảnh dùng token riêng; chưa có đăng nhập quản trị, CRUD sản phẩm, giỏ hàng hoặc đặt hàng.

## Chạy local

Chạy các lệnh trong thư mục `BE/`:

```bash
cd BE
cp .env.example .env  # Chỉ cần khi chưa có .env đã cấu hình
# Điền DB_URL, DB_USERNAME và DB_PASSWORD trong .env
mvn spring-boot:run
```

Spring Boot tự đọc `.env` từ thư mục làm việc hiện tại; cũng có thể truyền các biến môi trường tương ứng. File này theo cú pháp Java properties: không thêm `export` và không bao giá trị bằng dấu nháy. `.env` bị loại khỏi Git; `.env.example` chỉ chứa placeholder. **Không đặt thông tin DB trong FE hoặc biến `VITE_*`.**

Ứng dụng chạy tại `http://localhost:8080`. Cấu hình `PORT` khi deploy hoặc `SERVER_PORT` khi chạy local để đổi cổng; nếu dùng cả hai, đặt cùng giá trị. Cấu hình `CORS_ALLOWED_ORIGINS` để đổi danh sách origin FE, phân cách bằng dấu phẩy. Mặc định chấp nhận `http://127.0.0.1:5173` và `http://localhost:5173` cho GET/HEAD/OPTIONS trên API công khai; API quản lý ảnh hỗ trợ thêm POST và header Authorization.

Neon cấp URL dạng `postgresql://user:password@host/database?...`. Với JDBC, tách thành:

```properties
DB_URL=jdbc:postgresql://HOST/DATABASE?sslmode=require&channelBinding=require&prepareThreshold=0
DB_USERNAME=YOUR_USER
DB_PASSWORD=YOUR_PASSWORD
```

JDBC dùng `channelBinding` (camelCase) thay cho `channel_binding`. `prepareThreshold=0` tắt server-side prepared statements để tương thích endpoint pooler; truy vấn vẫn dùng tham số bind. HikariCP giới hạn 5 kết nối, tối thiểu 0 để giảm kết nối idle khi Neon nghỉ. Chuỗi kết nối và tài khoản không xuất hiện trong response API.

## Dữ liệu và migration

Lần khởi động đầu tiên, Flyway tạo **schema riêng `luma`**, bảng lịch sử và chạy:

- `V1__create_catalog.sql`: bảng `categories`, `products`, `home_content`, khóa ngoại, ràng buộc giá, chỉ mục, hàm tìm kiếm không dấu và trigger `updated_at`.
- `V2__seed_demo_catalog.sql`: 3 danh mục, 4 sản phẩm và nội dung trang chủ lấy từ FE hiện tại. Đây vẫn là **nội dung/giá mẫu**.
- `V3__uuid_ids_and_product_attributes.sql`: chuyển ID/khóa ngoại của ba bảng nghiệp vụ sang UUID, chuyển các ID sản phẩm trong JSON trang chủ và thêm thuộc tính sản phẩm. Giữ nguyên các dòng, slug và liên kết danh mục.

Migration chỉ chạy một lần; khởi động lại không seed đè nội dung. Không sửa migration đã áp dụng; thêm `V4__...sql` khi đổi schema/dữ liệu. Không bật baseline hoặc clean tự động. Flyway cần tài khoản có quyền tạo schema/bảng.

Các bảng nằm ngoài `luma` không bị chỉnh sửa. File ảnh mẫu trong `FE/web-thong-tin/public/images/` được giữ lại; lệnh đồng bộ nguồn bên dưới upload ảnh lên Cloudinary và lưu URL vào DB. DB chỉ lưu URL ảnh, thumbnail, alt, kích thước pixel và `publicId` của ảnh Cloudinary. `images`, `specifications` và nội dung trang chủ dùng JSONB; danh mục và sản phẩm có quan hệ khóa ngoại. Phần upload không cần thêm bảng/migration.

`categories.id`, `products.id`, `products.category_id` và `home_content.id` có kiểu PostgreSQL **uuid**. Khóa chính mặc định được DB sinh bằng `gen_random_uuid()` (UUID v4); khóa ngoại phải tham chiếu danh mục tồn tại. Java dùng `UUID`, JSON API biểu diễn bằng chuỗi UUID. `slug` tiếp tục dùng cho URL. `home_content.code` là mã duy nhất, ví dụ `home`, dùng để chọn nội dung trang chủ; ID của dòng này cũng là UUID. Bảng kỹ thuật `flyway_schema_history` do Flyway quản lý, không có cột `id`, giữ nguyên `installed_rank`.

Các cột sản phẩm bổ sung:

| Cột DB | Trường API | Kiểu | Ý nghĩa |
| --- | --- | --- | --- |
| `length_cm` | `lengthCm` | `numeric(10,2)` | Chiều dài, đơn vị cm |
| `width_cm` | `widthCm` | `numeric(10,2)` | Chiều rộng, đơn vị cm |
| `height_cm` | `heightCm` | `numeric(10,2)` | Chiều cao, đơn vị cm |
| `material` | `material` | `varchar(255)` | Chất liệu |
| `color` | `color` | `varchar(100)` | Màu sắc |

Năm cột này cho phép `NULL` khi chưa có dữ liệu xác nhận. Kích thước khi nhập phải là số hữu hạn lớn hơn 0, lưu tối đa hai chữ số thập phân. Không tự điền số đo, chất liệu hoặc màu theo ảnh. `width`/`height` trong JSON ảnh vẫn là kích thước ảnh theo pixel, độc lập với kích thước vật lý sản phẩm.

API chỉ trả sản phẩm và danh mục có `is_published = true`; sản phẩm trong danh mục ẩn cũng bị ẩn. ID nổi bật trên trang chủ được tính từ sản phẩm công khai. Các trường nội bộ như trạng thái công khai, tên tìm kiếm và timestamps không được trả về.

## API công khai

| Method | Endpoint | Nội dung |
| --- | --- | --- |
| GET | `/api/products` | `{ items, total, page, size }` |
| GET | `/api/products/{slug}` | Chi tiết một sản phẩm; không có/chưa công khai trả 404 |
| GET | `/api/products/{slug}/summary` | ID, slug, tên, danh mục, giá, giới thiệu ngắn và `primaryImage` (ảnh đầu hoặc null) |
| GET | `/api/products/{slug}/images` | Mảng metadata bộ ảnh; có thể rỗng |
| GET | `/api/products/{slug}/details` | Mô tả, thông số, chiều dài/rộng/cao, chất liệu và màu sắc |
| GET | `/api/products/{slug}/related` | Tối đa 4 thẻ sản phẩm tương tự; `size` từ 1 đến 4, mặc định 4 |
| GET | `/api/categories` | Mảng `{ id, slug, name }` |
| GET | `/api/home` | Hero, ảnh không gian, ID sản phẩm nổi bật; API tổng hợp được giữ tương thích |
| GET | `/api/home/hero` | `{ heroTitle, heroDescription, heroImage }` — chỉ nội dung banner |
| GET | `/api/home/featured-products` | Mảng sản phẩm nổi bật đang công khai; `size` mặc định 4, từ 1 đến 12 |
| GET | `/api/home/spaces` | Mảng `{ title, description, image, href }` — các không gian sống đang công khai |
| GET | `/actuator/health` | Trạng thái ứng dụng và kết nối DB; không lộ chi tiết DB |

FE trang chủ gọi ba API thành phần song song, mỗi khối hiển thị và thử lại độc lập. Mỗi API chỉ chạy một truy vấn; danh sách nổi bật lọc `is_featured` và trạng thái công khai của cả sản phẩm/danh mục, sắp theo `display_order, id` rồi giới hạn ngay trong SQL, không đọc toàn bộ catalog hoặc chạy truy vấn đếm. Banner và không gian trả 404 khi nội dung trang chủ chưa công khai; danh sách nổi bật không có sản phẩm trả mảng rỗng. Khi phát hành thay đổi này, deploy BE trước FE để các endpoint mới sẵn sàng. Không cần migration DB mới.

Trang chi tiết FE gọi `summary`, `images` và `details` song song; `summary` nhỏ gọn đủ để hiển thị tên, giá, giới thiệu và ảnh chính ngay khi có dữ liệu. Mỗi phần có lỗi/thử lại độc lập. `related` chỉ gọi khi cuộn gần phần sản phẩm tương tự (cách viewport tối đa 200 px), qua một request thay cho hai request danh sách có đếm tổng. Mỗi endpoint mới chạy một truy vấn, chỉ chọn các cột cần thiết; ảnh đầu được trích ngay trong SQL cho summary và related, không đọc toàn bộ gallery để tạo thẻ.

`related` trả tối đa 4 món, ưu tiên từ riêng/dòng tên chung, mức trùng từ khóa trong tên, cùng loại sản phẩm (ghế/bàn/đèn…), rồi cùng danh mục. Tên được chuẩn hóa không dấu/hoa thường; so khớp theo từ đầy đủ, không cộng điểm lặp từ. Các từ loại sản phẩm và vật liệu phổ biến không được coi là tên dòng. Ví dụ `Ghế ăn Nordic` ưu tiên `Bàn ăn Nordic`, rồi các ghế Nordic/ghế khác. Nếu chưa đủ 4, bổ sung từ danh mục khác theo `display_order, id`; catalog ít hơn thì trả số món hiện có. Sản phẩm đang xem, sản phẩm ẩn và danh mục ẩn luôn bị loại. Xếp hạng, giới hạn và lấy ảnh đầu đều chạy trong một câu SQL. Mỗi thẻ chỉ có `{ id, slug, name, priceVnd, images }`, `images` chứa tối đa một ảnh. Không có gợi ý trả `[]`; slug không tồn tại hoặc sản phẩm/danh mục bị ẩn trả 404 cho cả bốn endpoint. API chi tiết tổng hợp cũ giữ nguyên. Deploy BE trước FE; không cần migration DB mới.

Query của `/api/products`:

| Query | Mặc định | Quy tắc |
| --- | --- | --- |
| `q` | Chuỗi rỗng | Tìm theo tên, bỏ khoảng trắng hai đầu và gộp khoảng trắng/tab/xuống dòng liên tiếp như FE; không phân biệt hoa/thường/dấu; tối đa 120 ký tự |
| `category` | Tất cả | UUID danh mục hoặc slug `ban-ghe`, `den`, `gom-su`, `tu-ke`; giá trị không tồn tại cho kết quả rỗng |
| `sort` | `featured` | `featured` theo thứ tự hiển thị, `price-asc`, `price-desc`; giá trị khác dùng thứ tự mặc định |
| `page` | `0` | Chỉ số trang bắt đầu từ 0, không âm |
| `size` | `24` | Từ 1 đến 100 |

`total` là tổng sau lọc trước phân trang. Kết quả có thứ tự ổn định. Tìm kiếm xem `%` và `_` như ký tự thường; dữ liệu đầu vào không được nối vào SQL. Tham số vượt giới hạn/sai kiểu trả 400; DB lỗi trả 503 với `code: DATABASE_UNAVAILABLE`. Lỗi dùng `application/problem+json`, không trả SQL/password/stacktrace. Các API công khai chỉ đọc; thao tác upload/gắn ảnh nằm trong API quản lý có token riêng.

```bash
curl http://localhost:8080/actuator/health
curl 'http://localhost:8080/api/products?q=ghe%20an&category=ban-ghe'
curl 'http://localhost:8080/api/products?sort=price-asc&page=0&size=2'
curl http://localhost:8080/api/products/ghe-an-nordic
curl http://localhost:8080/api/categories
curl http://localhost:8080/api/home
```

DTO giữ tên trường catalog và bổ sung `lengthCm`, `widthCm`, `heightCm`, `material`, `color`. ID/`categoryId`/`featuredProductIds` trong API là chuỗi UUID. **FE đã đọc API**, dùng UUID từ response để ghép quan hệ/featured/related và dùng slug trong URL công khai; bộ lọc cũng chấp nhận URL UUID. Có thể gọi thử từ origin FE đã cấu hình:

```javascript
const response = await fetch('http://localhost:8080/api/products?sort=price-asc');
if (!response.ok) throw new Error('Không tải được sản phẩm');
const { items, total } = await response.json();
```

## Cloudinary và upload ảnh

Điền các biến trong `BE/.env` hoặc môi trường máy chủ:

```properties
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_API_KEY=your-api-key
CLOUDINARY_API_SECRET=your-api-secret
MEDIA_UPLOAD_TOKEN=at-least-32-random-characters
```

API secret và token chỉ dùng ở BE/công cụ quản lý, không đưa vào FE công khai hoặc biến `VITE_*`. Token quản lý đã được sinh ngẫu nhiên trong `.env` local; dùng giá trị này cho header `Authorization: Bearer ...`. Nếu không có token đủ 32 ký tự hoặc thiếu cấu hình Cloudinary, API ảnh trả 503; API catalog vẫn chạy bình thường.

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| GET | `/api/admin/media/status` | Gọi ping có xác thực tới Cloudinary, trả provider/cloudName/status |
| POST | `/api/admin/media/images` | Upload multipart `file`, `alt`; tùy chọn `productId` UUID để thêm ảnh vào gallery sản phẩm |

```bash
# Thay YOUR_MEDIA_UPLOAD_TOKEN bằng token trong .env
curl http://localhost:8080/api/admin/media/status \
  -H 'Authorization: Bearer YOUR_MEDIA_UPLOAD_TOKEN'

# Upload độc lập, nhận URL để sử dụng sau
curl http://localhost:8080/api/admin/media/images \
  -H 'Authorization: Bearer YOUR_MEDIA_UPLOAD_TOKEN' \
  -F 'file=@/absolute/path/to/photo.jpg' \
  -F 'alt=Ảnh sản phẩm'

# Upload và thêm trực tiếp vào gallery của sản phẩm có UUID tương ứng
curl http://localhost:8080/api/admin/media/images \
  -H 'Authorization: Bearer YOUR_MEDIA_UPLOAD_TOKEN' \
  -F 'file=@/absolute/path/to/photo.jpg' \
  -F 'alt=Ảnh chi tiết sản phẩm' \
  -F 'productId=UUID_FROM_PRODUCTS_API'
```

Upload thành công trả HTTP 201 với `publicId`, `src` (URL HTTPS), `thumbnail`, `alt`, `width`, `height`, `bytes`, `format` và `productId` (null nếu upload độc lập). Thumbnail rộng tối đa 600 px, giữ tỷ lệ, dùng chất lượng/định dạng tự động. Public ID do BE sinh, nằm dưới `luma/media/` hoặc `luma/products/{productId}/`; không ghi đè ảnh đã có. Mỗi upload truyền rõ `asset_folder`, nên ảnh thực sự nằm trong thư mục tương ứng của Media Library.

Cấu trúc thư mục Cloudinary của dự án:

```text
luma/
  catalog/                 17 ảnh đồng bộ từ source, tên hiển thị theo tên file
  media/                   Ảnh upload độc lập
  products/<product UUID>/ Ảnh upload gắn vào sản phẩm
```

Tài khoản đang dùng dynamic folders; `asset_folder` là vị trí hiển thị trong Media Library. Việc chuyển thư mục giữ nguyên public ID và URL. Tham khảo [Cloudinary folder modes](https://cloudinary.com/documentation/folder_modes).

Chấp nhận **JPEG, PNG, WebP**, tối đa **10 MiB mỗi file**, mô tả `alt` từ 1–300 ký tự. BE kiểm tra chữ ký định dạng trong dữ liệu; Cloudinary tiếp tục kiểm tra ảnh thực và giới hạn định dạng. File rỗng trả 400, sai định dạng trả 415, quá dung lượng trả 413, thiếu/sai token trả 401, sản phẩm không tồn tại trả 404. Lỗi từ Cloudinary trả thông báo an toàn với `CLOUDINARY_UNAVAILABLE` (502), `IMAGE_REJECTED` (400) hoặc `CLOUDINARY_NOT_CONFIGURED` (503), không trả nội dung lỗi/credentials của nhà cung cấp.

Nếu có `productId`, BE kiểm tra sản phẩm trước khi upload, rồi thêm metadata vào mảng `products.images` bằng một câu UPDATE nguyên tử, giữ nguyên gallery cũ. Nếu lưu DB thất bại hoặc sản phẩm bị xóa trong lúc upload, BE cố gắng xóa riêng ảnh vừa tạo khỏi Cloudinary. API này không tự di chuyển ảnh mẫu; dùng lệnh đồng bộ nguồn bên dưới cho tác vụ đó. Upload độc lập chỉ lưu ở Cloudinary và trả metadata, không chỉnh DB. API quản lý ảnh cũng dùng được với sản phẩm nháp; API công khai vẫn chỉ hiển thị nội dung đã công khai.

Tích hợp dùng REST API HTTPS của Cloudinary, Basic Auth trong header, timeout kết nối 10 giây và timeout đọc 30 giây. Tham khảo [Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference) và [Admin API ping](https://cloudinary.com/documentation/admin_api#ping).

## Đồng bộ catalog hiện tại trong source lên Neon và Cloudinary

Từ thư mục gốc dự án, dùng Python 3, Node.js 22.12+, `psql` và `pg_dump`. Thông tin Neon/Cloudinary lấy từ `BE/.env` hoặc biến môi trường, không ghi vào script.

```bash
# Chỉ đối chiếu, không upload hoặc ghi DB
python3 BE/scripts/sync-catalog.py --dry-run

# Sao lưu, upload ảnh trước, sau đó cập nhật catalog
python3 BE/scripts/sync-catalog.py --apply
```

Script đọc trực tiếp `FE/web-thong-tin/src/data/catalog.ts`: danh mục, sản phẩm, gallery, hero và các khối không gian trang chủ. Đọc ảnh đầy đủ từ `FE/web-thong-tin/public/images/`; thumbnail được Cloudinary tạo tự động. Catalog nguồn hiện có 4 danh mục, 9 sản phẩm và 17 ảnh riêng biệt. Danh mục `tu-ke` chứa Tủ gỗ sồi Komorebi; Bàn trà Sol thuộc Ghế & Bàn, Đèn thả Dome thuộc Đèn. Ghế ăn Nordic có ba ảnh; Bàn ăn Nordic có năm ảnh (bàn riêng, bài trí, hai góc phối ghế và chi tiết), thuộc Ghế & Bàn; hero và một khối không gian dùng chung ảnh. Cả 9 sản phẩm đã có mô tả, kích thước, chất liệu, màu sắc và bảo quản; Đèn thả Dome có thêm nguồn điện/công suất, nhiệt độ màu mẫu. Giá và thông số là dữ liệu mẫu có ghi chú rõ; thay bằng dữ liệu chính thức sau khi được nhà cung cấp xác nhận.

Mỗi sản phẩm có mô tả ngắn/chi tiết, gợi ý bố trí và hướng dẫn bảo quản. Hướng dẫn nằm trong `specifications` với nhãn `Hướng dẫn bảo quản`, được FE hiển thị tại accordion tương ứng; không cần migration mới. Giá và thông số vật lý hiện có trong source là **dữ liệu mẫu**, đã được gắn nhãn `Thông tin tham khảo` trên từng sản phẩm, chưa phải thông tin nhà cung cấp xác nhận. Chỉ thay bằng thông số chính thức khi có nguồn xác nhận.

Trước khi ghi, script lưu bản dump schema `luma`, snapshot JSON và dữ liệu nguồn tại `BE/.local/catalog-sync/<UTC timestamp>/`. Thư mục này bị loại khỏi Git; file backup/report có quyền 0600. `images.json` ghi ánh xạ file nguồn → Cloudinary, `apply.sql` ghi câu lệnh áp dụng, `before.json`/`after.json` dùng để đối chiếu và `result.json` chứa kết quả.

Ảnh dùng public ID `luma/catalog/<SHA-256 nội dung file>`, `asset_folder=luma/catalog`, tên hiển thị theo tên file và `overwrite=false`. Chạy lại sẽ dùng ảnh đã có, không upload trùng; ảnh đang nằm sai thư mục được chuyển về `luma/catalog` mà không đổi URL. `cloudinary-before.json` lưu metadata trước khi chuyển; `cloudinary-folders.json` ghi vị trí sau khi sắp xếp. Sau khi tất cả URL ảnh/thumbnail được xác minh, script upsert danh mục/sản phẩm theo slug và trang chủ theo `code=home` trong **một transaction**. UUID hiện có được giữ nguyên; các ID sản phẩm nổi bật trong JSON trang chủ được ánh xạ sang UUID của DB. Bản ghi ngoài catalog nguồn không bị xóa.

Để chỉ đồng bộ một sản phẩm mới hoặc đã có mà giữ nguyên các sản phẩm khác, danh mục và trang chủ:

```bash
python3 BE/scripts/sync-catalog.py --product ban-an-nordic --dry-run
python3 BE/scripts/sync-catalog.py --product ban-an-nordic --apply
```

Danh mục của sản phẩm phải tồn tại trong DB. Chế độ này vẫn sao lưu toàn bộ schema `luma`, xác minh URL ảnh và đối chiếu dữ liệu trước/sau. Bàn ăn Nordic dùng giá mẫu 5.900.000đ, đường kính mẫu 110 cm, cao mẫu 75 cm; chất liệu và màu sắc cũng được ghi rõ là thông số mẫu. Gallery minh họa bàn riêng và cách phối ghế, với ghi chú ghế/phụ kiện không thuộc sản phẩm bàn.

Các trường có trong source (tên, giá, mô tả, gallery, thông số, thứ tự, trạng thái nổi bật/công khai) được cập nhật theo source. Gallery trong DB trở thành danh sách ảnh nguồn đã chuyển sang URL Cloudinary. Chiều dài/rộng/cao, chất liệu và màu sắc chỉ được thay đổi nếu trường tương ứng có trong source; nếu chưa có thì giữ giá trị trên DB. Script kiểm tra lại nội dung, UUID và quan hệ sau khi ghi, đồng thời bỏ qua UPDATE khi dữ liệu không đổi.

File ảnh và dữ liệu mẫu trong FE được giữ nguyên. FE hiện đã đọc API công khai qua `src/services/catalog.ts`; dữ liệu local chỉ còn là fixture/nguồn cho script đồng bộ. Cấu hình proxy/API theo hướng dẫn FE. Khởi tạo DB mới bằng Flyway vẫn seed đường dẫn ảnh local, sau đó chạy lệnh đồng bộ để chuyển ảnh lên Cloudinary.

Catalog hiện có 4 danh mục, 9 sản phẩm, 1 trang chủ và 17 ảnh Cloudinary. Lần bổ sung danh mục Tủ & Kệ đã thêm ba sản phẩm Komorebi/Sol/Dome cùng ảnh phối bàn ăn cho Ghế ăn Nordic: upload 4 ảnh mới, dùng lại 8 ảnh cũ. Lần cập nhật thông tin tiếp theo đã điền đủ các trường còn trống cho ba món này bằng thông số mẫu, dùng lại cả 12 ảnh. Giá, gallery, UUID, danh mục, nội dung trang chủ và năm sản phẩm còn lại được đối chiếu giữ nguyên. Lần bổ sung Bàn ăn Nordic dùng `--product ban-an-nordic --apply`, tạo riêng một sản phẩm và upload năm ảnh mới; tám sản phẩm cũ, bốn danh mục và trang chủ giữ nguyên. Backup và snapshot trước/sau nằm trong thư mục `.local/catalog-sync/` của từng lần chạy.

## Deploy BE bằng Docker trên Vercel

`BE/Dockerfile` build JAR bằng Maven/Java 17, sau đó chạy bằng Java 17 JRE với UID không phải root. Image chỉ chứa JAR; `.dockerignore` giới hạn build context vào `pom.xml` và `src/`, loại `.env`, khóa riêng, log và bản sao lưu. Không cần build JAR trước trên máy cá nhân.

Sau khi đẩy các file lên GitHub, tạo hoặc cập nhật project BE trên Vercel:

1. **Root Directory:** `BE`.
2. **Application Preset:** `Container`. Với project mới, Vercel tự nhận diện Dockerfile. Với project đã tạo bằng `Other`, đổi preset sang `Container` trong Settings.
3. **Build Command / Output Directory / Install Command:** để mặc định của Container, bỏ các override Maven/npm cũ nếu có. Dockerfile tự thực hiện bước build và khởi động.
4. Nhập các biến môi trường bên dưới rồi **Deploy**. Thêm cho từng môi trường Production/Preview mà bạn sử dụng.

| Biến | Giá trị |
| --- | --- |
| `PORT` | `8080` — cần đặt rõ trên Vercel để bộ định tuyến và ứng dụng dùng cùng cổng |
| `DB_URL` | JDBC URL của Neon như ví dụ phía trên |
| `DB_USERNAME` | Tài khoản Neon |
| `DB_PASSWORD` | Mật khẩu Neon |
| `CORS_ALLOWED_ORIGINS` | Origin FE thật, ví dụ `https://your-frontend.vercel.app`, không có dấu `/` cuối; nhiều origin cách nhau bằng dấu phẩy |
| `CLOUDINARY_CLOUD_NAME` | Cloud name, nếu dùng API ảnh |
| `CLOUDINARY_API_KEY` | API key, nếu dùng API ảnh |
| `CLOUDINARY_API_SECRET` | API secret, nếu dùng API ảnh |
| `MEDIA_UPLOAD_TOKEN` | Token riêng tối thiểu 32 ký tự, nếu dùng API ảnh |

Không cần upload `.env`: nhập giá trị trong Vercel Environment Variables, không bao giá trị bằng dấu nháy. Nếu đã có `SERVER_PORT` trong project Vercel, xóa biến đó hoặc đặt cùng `8080`. Flyway chạy migration khi ứng dụng khởi động, vì vậy Neon phải truy cập được và tài khoản DB phải đủ quyền như phần migration phía trên.

Sau deploy, kiểm tra `https://your-backend.vercel.app/actuator/health` trả `{"status":"UP"}` và `/api/products` trả dữ liệu. Để FE gọi BE này, đặt `VITE_API_BASE_URL=https://your-backend.vercel.app/api` trong project FE rồi redeploy FE; thay tên miền ví dụ bằng tên miền thực tế.

Theo [tài liệu Container Runtime của Vercel](https://vercel.com/docs/functions/runtimes/container), tính năng đang ở Beta trên tất cả các gói. Vercel mặc định định tuyến container tới cổng 80 nếu không đặt `PORT`, nên cấu hình `PORT=8080` ở trên là cần thiết với image này.

Có thể thử Docker local từ thư mục gốc repo:

```bash
docker build -t luma-api ./BE
docker run --rm --name luma-api -p 8080:8080 --env-file BE/.env -e PORT=8080 luma-api
```

Hoặc chạy `docker build -t luma-api .` từ trong `BE/`. Lệnh `--env-file` chỉ truyền biến lúc chạy, không thêm `.env` vào image. Dùng file `.env` theo cú pháp không có `export` hoặc dấu nháy như hướng dẫn local.

## Kiểm thử và đóng gói

```bash
# Test controller, validation, CORS và response lỗi; không kết nối DB
mvn test

# Test tích hợp trên DB trong .env: migration được áp dụng nếu cần.
# Các thay đổi fixture trong từng test được rollback; migration không rollback.
LUMA_DB_TESTS=true mvn test

# Tạo JAR; chạy từ BE/ để tự đọc .env
mvn package
java -jar target/luma-api-0.0.1-SNAPSHOT.jar
```

Test tích hợp được bật riêng bằng `LUMA_DB_TESTS=true`, kiểm tra catalog demo: tìm không dấu/đ, sort, phân trang, JSON gallery, sản phẩm/danh mục ẩn, UUID sinh tự động, khóa ngoại/featured IDs, lọc UUID/slug, số đo thập phân và ràng buộc kích thước. Sau khi thay catalog demo bằng nội dung thật, dùng DB/branch Neon riêng cho test để giữ fixture ổn định.

Đã kiểm tra 52 trường hợp tự động, gồm tìm kiếm với nhiều khoảng trắng/tab/NBSP/BOM và thư mục upload Cloudinary. Luồng thực tế BE → Cloudinary → Neon đã được xác minh: ping có xác thực, upload ảnh WebP, tải URL ảnh/thumbnail và lưu gallery vào sản phẩm nháp thử. Sản phẩm và ảnh thử đã được dọn sau khi xác minh; gallery của các sản phẩm hiện có được giữ nguyên.

Khi phát hành, đặt biến môi trường trên máy chủ, cấu hình origin FE thật và dùng HTTPS. Tài khoản DB hiện có thể chạy migration; sau này có thể tách tài khoản migration và tài khoản runtime chỉ đọc khi triển khai quản trị.

Tham khảo: [Spring Boot 3.5 / Java 17](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [kết nối Java với Neon](https://neon.com/docs/guides/java), [tham số PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/use/).
