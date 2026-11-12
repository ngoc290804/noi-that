import { Link } from 'react-router-dom';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';
import { Photo } from '../components/Photo';
import { ProductGrid } from '../components/ProductCard';
import { getFeaturedProducts, getHomeContent } from '../services/catalog';

export function HomePage() {
  usePageMeta('Luma — Nội thất & Gia dụng tinh giản', 'Khám phá những món nội thất và gia dụng tinh giản, mang cảm hứng đến không gian sống của bạn.');
  const content = getHomeContent();
  return (
    <>
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy"><div className="hero-copy-inner">
          <p className="eyebrow"><span />Nhà là nơi bắt đầu</p>
          <h1 id="hero-title">{content.heroTitle}</h1>
          <p className="hero-description">{content.heroDescription}</p>
          <Link className="button button-primary" to="/san-pham">Xem sản phẩm <Icon name="arrow-right" /></Link>
        </div></div>
        <div className="hero-image"><Photo image={content.heroImage} priority sizes="(min-width: 768px) 58vw, 100vw" /></div>
      </section>

      <section className="container section featured" aria-labelledby="featured-title">
        <div className="section-header"><h2 id="featured-title">Sản phẩm nổi bật</h2><Link className="text-link" to="/san-pham">Xem tất cả <Icon name="arrow-right" /></Link></div>
        <ProductGrid products={getFeaturedProducts()} />
      </section>

      <section className="container section spaces" aria-labelledby="spaces-title">
        <div className="section-header"><div><h2 id="spaces-title">Cảm hứng cho không gian sống</h2><p className="section-description">Tìm một chút cảm hứng, tạo nên một góc nhà của riêng bạn.</p></div></div>
        <div className="spaces-grid">{content.spaces.map((space) => <Link className="space-card" to={space.href} key={space.title}>
          <Photo image={space.image} sizes="(min-width: 640px) 50vw, 100vw" />
          <div className="space-caption"><div><h3>{space.title}</h3><p>{space.description}</p></div><span className="space-arrow"><Icon name="arrow-right" /></span></div>
        </Link>)}</div>
      </section>

      <section className="about-section" id="ve-luma" tabIndex={-1} aria-labelledby="about-title"><div className="container about-inner">
        <p className="about-label">Về Luma</p><div><h2 id="about-title">Một ngôi nhà đẹp,<br />bắt đầu từ những điều giản dị.</h2><p>Chúng tôi tin rằng một góc nhà dễ chịu không cần quá nhiều. Một chiếc ghế vừa ý, ánh đèn dịu nhẹ, hay chiếc cốc thân quen — những món đồ nhỏ để bạn thấy mình thật sự ở nhà.</p><Link className="text-link" to="/san-pham">Tìm món đồ dành cho bạn <Icon name="arrow-right" /></Link></div>
      </div></section>
    </>
  );
}
