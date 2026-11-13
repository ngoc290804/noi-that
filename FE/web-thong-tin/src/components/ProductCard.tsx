import { Link, useLocation } from 'react-router-dom';
import type { ProductPreview } from '../types/catalog';
import { formatPrice } from '../utils/catalog';
import { Icon } from './Icon';
import { Photo } from './Photo';

export function ProductCard({ product, priority = false }: { product: ProductPreview; priority?: boolean }) {
  const location = useLocation();
  const catalogUrl = location.pathname === '/san-pham' ? `${location.pathname}${location.search}` : '/san-pham';
  return (
    <article className="product-card" data-product-id={product.id}>
      <Link className="product-card-link" to={`/san-pham/${product.slug}`} state={{ catalogUrl }}>
        <div className="product-card-image">{product.images[0] ? <Photo key={product.images[0].src} image={product.images[0]} priority={priority} /> : <div className="image-fallback" role="img" aria-label={`Ảnh ${product.name} đang cập nhật`}>Ảnh đang được cập nhật</div>}<span className="card-discover">Xem chi tiết <Icon name="arrow-right" /></span></div>
        <div className="product-card-meta">
          <div><h3>{product.name}</h3><p>{formatPrice(product.priceVnd)}</p></div>
        </div>
      </Link>
    </article>
  );
}

export function ProductGrid({ products, priority = false }: { products: readonly ProductPreview[]; priority?: boolean }) {
  return <div className="product-grid">{products.map((product, index) => <ProductCard key={product.id} product={product} priority={priority && index < 4} />)}</div>;
}
