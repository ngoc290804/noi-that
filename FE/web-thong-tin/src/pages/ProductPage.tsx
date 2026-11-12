import { Link, useLocation, useParams } from 'react-router-dom';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';
import { ProductGallery } from '../components/ProductGallery';
import { ProductGrid } from '../components/ProductCard';
import { getProduct, getRelatedProducts, listCategories } from '../services/catalog';
import type { Product } from '../types/catalog';
import { formatPrice } from '../utils/catalog';
import { NotFoundPage } from './NotFoundPage';

export function ProductPage() {
  const { slug = '' } = useParams();
  const product = getProduct(slug);
  return product ? <ProductDetails key={product.id} product={product} /> : <NotFoundPage product />;
}

function ProductDetails({ product }: { product: Product }) {
  usePageMeta(`${product.name} — Luma`, product.shortDescription);
  const { state } = useLocation();
  const candidate: unknown = state?.catalogUrl;
  const backTo = typeof candidate === 'string' && (candidate === '/san-pham' || candidate.startsWith('/san-pham?')) ? candidate : '/san-pham';
  const related = getRelatedProducts(product.id);
  const category = listCategories().find((item) => item.id === product.categoryId);

  return (
    <div className="container product-page">
      <nav className="breadcrumb" aria-label="Đường dẫn"><Link to="/">Trang chủ</Link><span>/</span><Link to={backTo}>Sản phẩm</Link><span>/</span><span aria-current="page">{product.name}</span></nav>
      <div className="product-detail">
        <ProductGallery images={product.images} name={product.name} />
        <div className="product-info">
          <Link className="product-category" to={`/san-pham?category=${product.categoryId}`}>{category?.name}</Link>
          <h1>{product.name}</h1>
          <div className="detail-price"><span>{formatPrice(product.priceVnd)}</span><span className="reference-label">Giá tham khảo</span></div>
          <p className="detail-intro">{product.shortDescription}</p>
          <p className="detail-description">{product.description}</p>
          <Link className="button button-primary detail-back" to={backTo}><Icon name="arrow-left" />Quay lại danh sách</Link>
          <div className="product-accordions">
            <details open><summary>Thông tin sản phẩm <Icon name="chevron-down" /></summary><dl>{product.specifications.map((specification) => <div key={specification.label}><dt>{specification.label}</dt><dd>{specification.value ? `${specification.value}${specification.unit ? ` ${specification.unit}` : ''}` : 'Thông tin đang cập nhật'}</dd></div>)}</dl></details>
            <details><summary>Hướng dẫn bảo quản <Icon name="chevron-down" /></summary><p>Thông tin hướng dẫn bảo quản của sản phẩm đang được cập nhật.</p></details>
          </div>
        </div>
      </div>
      {related.length ? (
        <section className="section related-products" aria-labelledby="related-title">
          <div className="section-header"><h2 id="related-title">Sản phẩm tương tự</h2></div>
          <ProductGrid products={related} />
          <div className="related-products-action"><Link to="/san-pham" className="button button-primary">Xem sản phẩm khác <Icon name="arrow-right" /></Link></div>
        </section>
      ) : null}
    </div>
  );
}
