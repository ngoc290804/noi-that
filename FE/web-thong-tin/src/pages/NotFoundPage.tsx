import { Link } from 'react-router-dom';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';

export function NotFoundPage({ product = false }: { product?: boolean }) {
  usePageMeta('Không tìm thấy trang — Luma', 'Trang bạn đang tìm không có ở đây. Khám phá những món đồ nội thất và gia dụng tại Luma.');
  return <section className="container not-found"><p className="not-found-number">404</p><h1>{product ? 'Chưa tìm thấy sản phẩm này' : 'Có lẽ bạn đã đi lạc một chút'}</h1><p>{product ? 'Sản phẩm có thể đã được cập nhật hoặc đường dẫn chưa đúng.' : 'Trang bạn tìm không có ở đây. Cùng quay về và tìm một góc nhỏ thân quen nhé.'}</p><div className="not-found-actions"><Link className="button button-primary" to="/san-pham">Khám phá sản phẩm <Icon name="arrow-right" /></Link><Link className="text-link" to="/">Về trang chủ</Link></div></section>;
}
