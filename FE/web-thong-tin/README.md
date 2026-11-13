# Luma — FE web thông tin hàng

Ứng dụng React + TypeScript với Vite và React Router. Node.js dùng để phát triển/build; dữ liệu hiển thị được tải từ API Spring Boot trong `BE/`, ảnh dùng URL/thumbnail Cloudinary do API trả về.

## Lệnh

```bash
npm ci
npm run dev          # http://127.0.0.1:5173
npm run typecheck
npm test             # Hợp đồng HTTP, query URL, thông số và lỗi
npm run build        # typecheck + production build
npm run preview      # http://127.0.0.1:4173
```

Dùng Node.js >=22.12. Test sử dụng tính năng loại bỏ kiểu TypeScript của Node; Node 22 có thể hiển thị cảnh báo experimental. Không cần cài thêm test framework.

## Tương thích trình duyệt

Bản build nhắm tới Safari/iOS 15.4+, Chrome/Edge 105+ và Firefox 121+. Không cần cài thêm polyfill: gọi API dùng `AbortController` và timer 35 giây, không phụ thuộc `AbortSignal.any()`, `AbortSignal.timeout()` hoặc `throwIfAborted()`. Timer và listener được dọn khi request kết thúc, kể cả lỗi; timeout bao gồm thời gian đọc JSON. Hủy yêu cầu vẫn hoạt động khi đổi trang/bộ lọc, và không hủy request danh mục dùng chung của các trang khác.

Tìm kiếm dùng chuỗi query thay cho `URLSearchParams.size`; menu có chiều cao `100vh` dự phòng cho trình duyệt thiếu `dvh`. Ảnh tiếp tục dùng `srcset`, lazy loading và ảnh nhỏ cho mobile. Không đảm bảo trình duyệt cũ hơn các mốc trên, vì giao diện còn dùng CSS layers và dialog gốc.

Kiểm chứng bản sửa: production build và 32 test FE đạt, gồm 6 test tương thích/timeout/dọn tài nguyên. Playwright trên Chromium với `AbortSignal.any`, `timeout`, `throwIfAborted`, `reason` và `URLSearchParams.size` bị vô hiệu hóa đã kiểm tra tải trang chủ, tìm kiếm, xóa tìm kiếm, lọc/sort, chi tiết, menu, anchor và thử lại sau HTTP 503. Dùng API BE thật tại viewport 320/390/1440 px; ca HTTP 503 dùng fixture. Chưa kiểm tra trực tiếp trên thiết bị iOS cũ hoặc Safari thật.

## Kết nối BE

Chạy BE trước theo [hướng dẫn](../../BE/README.md), sau đó chạy `npm run dev` tại FE. Mặc định trình duyệt gọi `/api`; Vite dev/preview chuyển tiếp tới `http://127.0.0.1:8080`. Không cần cấu hình bí mật trong FE.

Có thể sao chép `.env.example` sang `.env.local` để đổi `API_PROXY_TARGET` (chỉ dùng bởi Vite) hoặc `VITE_API_BASE_URL` (URL API công khai, có sẵn trong bundle). Sau khi đổi cấu hình cần khởi động lại Vite; đổi biến `VITE_*` khi phát hành cần build lại. Không đặt token quản lý ảnh, thông tin DB hoặc Cloudinary secret vào `VITE_*`.

Để chạy FE local với BE đã deploy trên Vercel, đặt trong `.env.local`:

```properties
VITE_API_BASE_URL=/api
API_PROXY_TARGET=https://noi-that-be-swart.vercel.app
```

Chạy `npm run dev`, mở `http://127.0.0.1:5173`. Vite chuyển tiếp `/api` tới BE Vercel; trình duyệt gọi cùng origin local nên không cần thêm localhost vào CORS của BE. File `.env.local` được Git bỏ qua. Để quay lại BE local, đổi `API_PROXY_TARGET=http://127.0.0.1:8080` rồi khởi động lại Vite.

Production cần reverse proxy `/api` tới BE, hoặc đặt `VITE_API_BASE_URL=https://api.example.com/api` khi build và thêm origin FE vào `CORS_ALLOWED_ORIGINS` của BE. Cấu hình proxy của Vite chỉ phục vụ dev/preview; static hosting cần proxy riêng và SPA fallback.

Trang chủ chia thành ba khối độc lập, gọi song song `/api/home/hero`, `/api/home/featured-products` và `/api/home/spaces`. Banner hiển thị ngay khi API riêng trả về; sản phẩm nổi bật lấy trực tiếp tối đa 4 món, không đợi nội dung trang chủ để dò ID hoặc duyệt catalog. Mỗi khối có skeleton giữ chỗ, lỗi và nút thử lại riêng; phần Về Luma là nội dung tĩnh luôn có sẵn. Ảnh banner ưu tiên tải, ảnh không gian dùng lazy loading. Deploy BE trước FE vì cần các endpoint trang chủ mới; API `/api/home` cũ vẫn được giữ tương thích.

Catalog gọi `/api/products?q=&category=&sort=&page=&size=24`, phân trang từ 0, và `/api/categories`. Bộ lọc dùng slug trong URL; URL UUID từ BE vẫn hoạt động.

Trang chi tiết gọi song song `/api/products/{slug}/summary`, `/images` và `/details`. Summary chỉ có thông tin chính và một ảnh đầu tiên; trang hiển thị tên, giá, giới thiệu và ảnh này ngay khi summary trả về. Bộ ảnh đầy đủ, mô tả/thông số có trạng thái tải và nút thử lại riêng; lỗi từng phần không che sản phẩm chính. `/api/products/{slug}/related` chỉ được gọi khi cuộn gần phần sản phẩm tương tự (trong 200 px); trình duyệt không hỗ trợ IntersectionObserver sẽ tải ngay. Endpoint này trả tối đa 4 thẻ gồm ID, slug, tên, giá và một ảnh, ưu tiên dòng tên/từ khóa liên quan, cùng loại rồi cùng danh mục, không chứa món đang xem. FE truyền `size=4` và giới hạn thêm 4 phần tử nếu máy chủ cũ trả dư; giữ nguyên thứ tự backend. FE không còn gọi hai API danh sách để ghép gợi ý. Khi chuyển sản phẩm, các request cũ bị hủy và dữ liệu cũ được ẩn ngay. Deploy BE trước FE; API chi tiết tổng hợp cũ vẫn giữ tương thích.

Danh mục chỉ tải một lần khi được dùng lần đầu trong mỗi lượt tải trang, rồi dùng lại khi chuyển route, lọc hoặc sắp xếp. Dữ liệu được giữ trong bộ nhớ và `sessionStorage` với khóa có phiên bản, tách theo API base URL. Mỗi lần reload sẽ tải mới một lần; cache của lượt tải trước không được dùng lại. Các lời gọi đồng thời dùng chung request; rời một trang chỉ hủy việc chờ của trang đó, không hủy request danh mục dùng chung. Request lỗi không được cache để nút thử lại có thể tải lại. Nếu trình duyệt chặn storage, cache trong bộ nhớ vẫn hoạt động.

Mỗi trang hoặc khối dữ liệu có trạng thái tải, lỗi và nút thử lại. HTTP 404 chi tiết hiển thị không tìm thấy; lỗi kết nối/5xx không bị coi là sản phẩm đã mất. Yêu cầu có timeout 35 giây, bị hủy khi chuyển trang/đổi query, không dùng dữ liệu mẫu để che lỗi BE. Giá, ảnh, mô tả, chiều dài/rộng/cao, chất liệu và màu sắc lấy từ API; trường chưa có giữ thông báo đang cập nhật. Card/gallery không ảnh có dự phòng.

## Các trang và chức năng

| URL | Chức năng |
| --- | --- |
| `/` | Hero, sản phẩm nổi bật, cảm hứng không gian, phần giới thiệu |
| `/san-pham` | Sản phẩm từ BE, tìm theo tên, một danh mục, sort giá/nổi bật, phân trang 24 món |
| `/san-pham?q=ghe+an` | Ví dụ tìm kiếm không dấu |
| `/san-pham?category=gom-su&sort=price-asc` | Ví dụ bộ lọc có thể chia sẻ/tải lại |
| `/san-pham/:slug` | Chi tiết, thông số, accordion, danh sách sản phẩm tương tự tối đa hai hàng |
| Đường dẫn sai / slug không có | Giao diện không tìm thấy và link quay về |

Tìm kiếm được thực hiện khi nhấn Enter hoặc nút tìm. Bộ lọc và sort cập nhật ngay; query và trang được giữ trong URL; đổi từ khóa/danh mục/sort đặt lại trang đầu. Card mở chi tiết kèm đường dẫn catalog trước đó, dùng cho CTA “Quay lại danh sách”. Tìm kiếm trên header là ô nhập có viền, mở trực tiếp catalog theo query; logo nằm bên trái, ô tìm ở bên phải. Mobile bố trí ô tìm trên hàng riêng để dễ nhập. Menu mobile dùng dialog với bàn phím/Escape và focus mặc định của trình duyệt.

Khi đang ở trang sản phẩm, xóa hết nội dung trong ô tìm kiếm (bằng dấu × hoặc bàn phím) tự đưa danh sách về tất cả sản phẩm, bỏ từ khóa và các bộ lọc mà không cần nhấn Enter. Áp dụng cho ô tìm trong danh sách và ô trên header.

Cuối trang chi tiết hiển thị “Sản phẩm tương tự” thẳng lề với nội dung chung, tối đa 4 món ở mọi kích thước màn hình: desktop một hàng bốn cột, tablet/mobile hai hàng hai cột. Backend ưu tiên tên/dòng sản phẩm liên quan (ví dụ Ghế ăn Nordic → Bàn ăn Nordic), rồi cùng loại ghế/bàn/đèn và cùng danh mục. Thiếu thì bổ sung từ danh mục khác cho đủ 4 nếu catalog cho phép; luôn loại món đang xem và không lặp sản phẩm. Nút “Xem sản phẩm khác” bên dưới mở `/san-pham`.

Gallery chi tiết dùng `ProductGallery`: khi mảng `images` có từ hai ảnh, hiển thị mũi tên ở hai bên ảnh lớn và dải thumbnail ngay bên dưới. Chọn thumbnail cập nhật ảnh lớn; mũi tên chuyển vòng đầu/cuối và đồng bộ viền ảnh đang chọn. Khi focus vào gallery, dùng trái/phải để chuyển ảnh, Home/End để về ảnh đầu/cuối. Một ảnh thì ẩn điều khiển; không có ảnh thì hiện dự phòng.

Để bổ sung ảnh hiển thị thực tế, cập nhật gallery trong DB qua API media được bảo vệ của BE (xem README BE). Fixture `src/data/catalog.ts` và ảnh local chỉ được dùng cho test/script đồng bộ nguồn; sửa fixture không trực tiếp đổi nội dung web. Script đồng bộ từ source lên BE sẽ thay gallery DB bằng gallery nguồn, cần dùng có chủ đích. Ghế ăn Nordic hiện có ba ảnh (toàn cảnh, cận cảnh, phối cùng bàn ăn); các sản phẩm còn lại mỗi món có một ảnh. Không dùng ảnh của món khác để làm gallery cho sản phẩm.

Catalog nguồn hiện có 4 danh mục và 9 sản phẩm, gồm Tủ gỗ sồi Komorebi (`/san-pham/tu-go-soi-komorebi`), Bàn trà Sol, Đèn thả Dome và Bàn ăn Nordic (`/san-pham/ban-an-nordic`, gallery 5 ảnh). Danh mục Tủ & Kệ dùng slug `tu-ke`, có bộ lọc và liên kết ở footer. Cả 9 sản phẩm dùng bộ thông tin mẫu đầy đủ; các giá trị kích thước, chất liệu, màu sắc và thông số điện được ghi rõ là tham khảo, chưa phải thông tin nhà cung cấp xác nhận. Mô tả, gợi ý bố trí, thông số và bảo quản lấy từ DB. Desktop đặt ảnh bên trái, tên/giá/giới thiệu và thông tin bên phải; mobile xếp thành một cột. Mô tả dài mặc định gọn trong ba dòng, có nút `Xem thêm`/`Thu gọn`. Thông tin sản phẩm và bảo quản mặc định đóng, mở độc lập khi người dùng cần; có thể mở cả hai phần cùng lúc. Bảng thông số chỉ có một cột nhãn–giá trị, không dùng đường kẻ giữa từng dòng. Accordion bảo quản đọc phần tử `specifications` có nhãn `Hướng dẫn bảo quản`; phần tử này không lặp lại trong bảng thông tin. Khi API đang tải hoặc lỗi, phần bảo quản có trạng thái tải/thử lại tương ứng; chỉ hiện thông báo đang cập nhật khi dữ liệu trả về chưa có hướng dẫn. Các giá trị giá/thông số mẫu đang có trong source được ghi rõ bằng ghi chú `Thông tin tham khảo` bên dưới bảng trên từng sản phẩm.

## Vị trí chỉnh sửa

| Tệp / thư mục | Trách nhiệm |
| --- | --- |
| `src/data/catalog.ts` | Fixture và nguồn cho script sync-catalog; không được import vào runtime FE |
| `src/types/catalog.ts` | Kiểu dữ liệu sản phẩm, ảnh, thông số và query |
| `src/services/catalog.ts` | HTTP adapter cho API Spring Boot, timeout/hủy request, featured và related |
| `src/utils/catalog.ts` | Query URL/phân trang, định dạng tiền và kết hợp thông số từ BE |
| `src/components/` | Header, footer, icon, ảnh dự phòng, ProductGallery và card dùng chung |
| `src/pages/` | Nội dung các trang |
| `src/styles/styles.css` | Token, typography, layout, responsive và reduced motion |
| `public/images/` | Ảnh WebP giữ kích thước gốc và bản nhỏ 600 px; cấu trúc danh mục/từng món khớp với `Art/Prod/` |
| `tests/catalog.test.mjs` | Hợp đồng HTTP, lỗi/abort/retry, featured nhiều trang, related, URL UUID/slug, thông số và tiền |

Đường dẫn ảnh theo danh mục và từng món: ví dụ nguồn `Art/Prod/Ghe/Ghe-an-Nordic/ghe-an-nordic-gan.png` tương ứng `/images/Ghe/Ghe-an-Nordic/ghe-an-nordic-gan.webp` và `/images/Ghe/Ghe-an-Nordic/ghe-an-nordic-gan-600.webp`. Khi di chuyển ảnh nguồn, đồng bộ thư mục trong `public/images/` và đường dẫn ở `src/data/catalog.ts`; không tham chiếu trực tiếp thư mục `Art/` từ FE.

Khi thêm sản phẩm trên BE: tạo UUID/slug duy nhất, gắn danh mục, nhập giá số nguyên VND, thêm mô tả và ảnh/alt, chỉ nhập thông số có nguồn. FE tải danh mục động từ API, không cần thêm slug vào mã FE. API chỉ công khai sản phẩm/danh mục đã xuất bản.

Font Be Vietnam Pro được lưu trong dependency và phục vụ nội bộ, không tải font từ Google ở thời điểm khách mở trang. Ảnh có `srcset`, kích thước, lazy loading bên dưới và phương án dự phòng nếu lỗi.

## Áp dụng thiết kế đã thống nhất

Tham chiếu gốc ở `../../Art/DesignUI/`: homepage, catalog, product detail, mobile và design system.

- Giữ bảng màu trắng/kem/xanh rừng, Be Vietnam Pro, khoảng thở và card phẳng.
- Giữ catalog bốn cột desktop, hai cột mobile; chuẩn hóa tên/giá từ một nguồn, bỏ các card sai dữ liệu trong mockup.
- Chi tiết giữ ảnh trái/thông tin phải ở desktop và một cột mobile; thông số thiếu ghi rõ thay vì tạo dữ liệu giả.
- Bỏ tim, giỏ, số lượng mua và CTA mua theo phạm vi mới; dùng CTA xem/điều hướng. Nav dùng Trang chủ, Sản phẩm, Về Luma.
- Hero dùng ảnh phòng khách sẵn có vì chưa có ảnh hero trùng mockup. Mobile đặt chữ trên nền kem dưới ảnh để dễ đọc.
- Gallery dùng ảnh đã có: Ghế ăn Nordic gồm ảnh toàn cảnh, ảnh cận và ảnh phối cùng bàn ăn từ `Art/Prod/Ghe/Ghe-an-Nordic/`; các món khác hiện có một ảnh. Không tạo thêm góc chụp hoặc ảnh màu/vật liệu giả. Giá và mô tả đang là nội dung mẫu; các tuyên bố “bền vững”/giao hàng/bảo hành chưa xác nhận không được đưa vào UI.

## Kiểm chứng tích hợp

Đã qua typecheck/production build, 15 test FE và 52 test BE (gồm DB Neon). Có 22 kiểm tra Playwright tại `http://127.0.0.1:5173`, dùng BE thật và ảnh Cloudinary; kiểm tra bố cục ở 1440/768/390/320 px, không có lỗi console/runtime ở các luồng thật. Các ca phân trang 25 sản phẩm, danh mục mới, thông số có dữ liệu, ảnh rỗng, lỗi/thử lại và request trả chậm dùng HTTP fixture, không sửa catalog trên DB. Chưa kiểm tra Firefox/Safari hoặc hosting production.

## Giới hạn hiện tại

Đã kết nối API công khai; chưa có giao diện CMS, đăng nhập, quản trị, giỏ hoặc giao dịch online. API upload ảnh được bảo vệ của BE không đưa vào web công khai. Chưa phát hành hosting; nếu phục vụ bằng static host phải có SPA fallback. Bản build đánh dấu không lập chỉ mục tìm kiếm cho đến khi thông tin kinh doanh được xác nhận. SEO metadata có theo trang ở phía client; HTML theo sản phẩm và mã HTTP chính xác cần giải pháp phục vụ/pre-render ở giai đoạn phát hành nếu được yêu cầu.
