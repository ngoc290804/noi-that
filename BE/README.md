# BE — dành cho Spring Boot khi cần

Chưa khởi tạo ứng dụng backend. FE web thông tin chạy độc lập bằng dữ liệu mẫu trong `FE/web-thong-tin/src/data/catalog.ts`.

Khi cần dữ liệu động, triển khai một backend Java + Spring Boot dùng chung nguồn sản phẩm/nội dung; tách API chỉ đọc công khai và API quản lý có quyền riêng theo hai kế hoạch:

- [Web thông tin hàng — phần BE](../docs/02-KE-HOACH-WEB-THONG-TIN-HANG.md#3-be--spring-boot-khi-cần).
- [Web quản lý — phần BE](../docs/01-KE-HOACH-WEB-QUAN-LY.md#2-be--spring-boot-cho-web-quản-lý).

Quản trị và đặt hàng online tiếp tục để sau; không cần chạy Java hoặc cơ sở dữ liệu để xem bản FE hiện tại.
