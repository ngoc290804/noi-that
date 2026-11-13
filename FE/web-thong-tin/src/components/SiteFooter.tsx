import { Link } from 'react-router-dom';
import { BrandLogo } from './BrandLogo';

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="container footer-top">
        <div><Link to="/" className="brand" aria-label="Luma — Trang chủ"><BrandLogo /></Link><p>Những món đồ nhỏ.<br />Một tổ ấm của riêng bạn.</p></div>
        <nav aria-label="Khám phá Luma"><span className="footer-heading">Khám phá</span><Link to="/san-pham">Tất cả sản phẩm</Link><Link to="/san-pham?category=ban-ghe">Ghế & Bàn</Link><Link to="/san-pham?category=den">Đèn</Link><Link to="/san-pham?category=gom-su">Gốm sứ</Link><Link to="/san-pham?category=tu-ke">Tủ & Kệ</Link></nav>
        <div className="footer-about"><span className="footer-heading">Về Luma</span><p>Nội thất & gia dụng tinh giản,<br />cho những không gian sống đầy cảm hứng.</p><Link className="text-link" to="/#ve-luma">Câu chuyện của chúng tôi</Link></div>
      </div>
      <div className="container footer-bottom"><span>© {new Date().getFullYear()} Luma</span><span>Website giới thiệu sản phẩm · Giá hiển thị mang tính tham khảo.</span></div>
    </footer>
  );
}
