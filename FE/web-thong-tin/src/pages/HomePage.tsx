import { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useCatalogResource } from '../app/useCatalogResource';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';
import { Photo } from '../components/Photo';
import { ProductGrid } from '../components/ProductCard';
import { catalogApi } from '../services/catalog';

export function HomePage() {
  usePageMeta('Luma — Nội thất & Gia dụng tinh giản', 'Khám phá những món nội thất và gia dụng tinh giản, mang cảm hứng đến không gian sống của bạn.');
  const { hash } = useLocation();
  useEffect(() => {
    if (hash === '#ve-luma') {
      const section = document.getElementById('ve-luma');
      section?.scrollIntoView({ behavior: 'instant' });
      section?.focus({ preventScroll: true });
    }
  }, [hash]);
  return (
    <>
      <HomeHero />
      <HomeFeaturedProducts />
      <HomeSpaces />

      <section className="about-section" id="ve-luma" tabIndex={-1} aria-labelledby="about-title"><div className="container about-inner">
        <p className="about-label">Về Luma</p><div><h2 id="about-title">Một ngôi nhà đẹp,<br />bắt đầu từ những điều giản dị.</h2><p>Chúng tôi tin rằng một góc nhà dễ chịu không cần quá nhiều. Một chiếc ghế vừa ý, ánh đèn dịu nhẹ, hay chiếc cốc thân quen — những món đồ nhỏ để bạn thấy mình thật sự ở nhà.</p><Link className="text-link" to="/san-pham">Tìm món đồ dành cho bạn <Icon name="arrow-right" /></Link></div>
      </div></section>
    </>
  );
}

function HomeSectionError({ message, retry }: { message: string; retry: () => void }) {
  return <div className="home-section-state" role="alert"><p>{message}</p><button className="text-link" onClick={retry}>Thử lại</button></div>;
}

function HomeHero() {
  const { data, error, retry } = useCatalogResource('home-hero', catalogApi.getHomeHero);
  return (
    <section className="hero" aria-labelledby={data ? 'hero-title' : undefined} aria-label={data ? undefined : 'Giới thiệu Luma'} aria-busy={!data && !error}>
      <div className="hero-copy"><div className="hero-copy-inner">
        <p className="eyebrow"><span />Nhà là nơi bắt đầu</p>
        {data ? <>
          <h1 id="hero-title">{data.heroTitle}</h1>
          <p className="hero-description">{data.heroDescription}</p>
        </> : error ? <HomeSectionError message="Chưa thể tải nội dung giới thiệu." retry={retry} /> : (
          <div role="status"><span className="sr-only">Đang tải nội dung giới thiệu…</span><div className="home-skeleton hero-title-placeholder" aria-hidden="true" /><div className="home-skeleton hero-description-placeholder" aria-hidden="true" /></div>
        )}
        <Link className="button button-primary" to="/san-pham">Xem sản phẩm <Icon name="arrow-right" /></Link>
      </div></div>
      <div className="hero-image">{data ? <Photo image={data.heroImage} priority sizes="(min-width: 768px) 58vw, 100vw" /> : <div className="home-skeleton" aria-hidden="true" />}</div>
    </section>
  );
}

function HomeFeaturedProducts() {
  const { data, error, retry } = useCatalogResource('home-featured', catalogApi.getHomeFeaturedProducts);
  return (
    <section className="container section featured" aria-labelledby="featured-title" aria-busy={!data && !error}>
      <div className="section-header"><h2 id="featured-title">Sản phẩm nổi bật</h2><Link className="text-link" to="/san-pham">Xem tất cả <Icon name="arrow-right" /></Link></div>
      {data ? data.length ? <ProductGrid products={data} /> : <p className="home-section-state">Chưa có sản phẩm nổi bật.</p>
        : error ? <HomeSectionError message="Chưa thể tải sản phẩm nổi bật." retry={retry} /> : (
          <div role="status"><span className="sr-only">Đang tải sản phẩm nổi bật…</span><div className="product-grid" aria-hidden="true">{Array.from({ length: 4 }, (_, index) => (
            <div key={index}><div className="product-card-image home-skeleton" /><div className="product-card-meta"><div className="home-placeholder-meta"><div className="home-skeleton home-placeholder-title" /><div className="home-skeleton home-placeholder-description" /></div></div></div>
          ))}</div></div>
        )}
    </section>
  );
}

function HomeSpaces() {
  const { data, error, retry } = useCatalogResource('home-spaces', catalogApi.getHomeSpaces);
  return (
    <section className="container section spaces" aria-labelledby="spaces-title" aria-busy={!data && !error}>
      <div className="section-header"><div><h2 id="spaces-title">Cảm hứng cho không gian sống</h2><p className="section-description">Tìm một chút cảm hứng, tạo nên một góc nhà của riêng bạn.</p></div></div>
      {data ? data.length ? <div className="spaces-grid">{data.map((space) => <Link className="space-card" to={space.href} key={space.title}>
        <Photo image={space.image} sizes="(min-width: 640px) 50vw, 100vw" />
        <div className="space-caption"><div><h3>{space.title}</h3><p>{space.description}</p></div><span className="space-arrow"><Icon name="arrow-right" /></span></div>
      </Link>)}</div> : <p className="home-section-state">Không gian sống đang được cập nhật.</p>
        : error ? <HomeSectionError message="Chưa thể tải không gian sống." retry={retry} /> : (
          <div role="status"><span className="sr-only">Đang tải không gian sống…</span><div className="spaces-grid" aria-hidden="true">{Array.from({ length: 2 }, (_, index) => (
            <div key={index}><div className="home-skeleton home-space-placeholder" /><div className="space-caption"><div className="home-placeholder-meta"><div className="home-skeleton home-placeholder-title" /><div className="home-skeleton home-placeholder-description" /></div></div></div>
          ))}</div></div>
        )}
    </section>
  );
}
