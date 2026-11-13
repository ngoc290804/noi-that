import { useCallback } from 'react';
import { useCatalogResource } from '../app/useCatalogResource';
import { CatalogStatus } from '../components/CatalogStatus';
import { Link, useLocation, useParams } from 'react-router-dom';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';
import { ProductGallery } from '../components/ProductGallery';
import { ProductGrid } from '../components/ProductCard';
import { catalogApi, CatalogApiError } from '../services/catalog';
import type { Product } from '../types/catalog';
import { formatPrice, getSpecifications } from '../utils/catalog';
import { NotFoundPage } from './NotFoundPage';

export function ProductPage() {
  const { slug = '' } = useParams();
  usePageMeta('Chi tiết sản phẩm — Luma', 'Thông tin sản phẩm nội thất và gia dụng Luma.');
  const load = useCallback((signal: AbortSignal) => catalogApi.getProduct(slug, signal), [slug]);
  const { data, error, retry } = useCatalogResource(slug, load);
  if (error instanceof CatalogApiError && error.status === 404) return <NotFoundPage product />;
  if (!data) return <CatalogStatus error={error} retry={retry} />;
  return <ProductDetails key={data.id} product={data} />;
}

function ProductDetails({ product }: { product: Product }) {
  usePageMeta(`${product.name} — Luma`, product.shortDescription);
  const { data: categories = [] } = useCatalogResource('categories', catalogApi.listCategories);
  const { state } = useLocation();
  const candidate: unknown = state?.catalogUrl;
  const backTo = typeof candidate === 'string' && (candidate === '/san-pham' || candidate.startsWith('/san-pham?')) ? candidate : '/san-pham';
  const category = categories.find((item) => item.id === product.categoryId);

  return (
    <div className="container product-page">
      <nav className="breadcrumb" aria-label="Đường dẫn"><Link to="/">Trang chủ</Link><span>/</span><Link to={backTo}>Sản phẩm</Link><span>/</span><span aria-current="page">{product.name}</span></nav>
      <div className="product-detail">
        <ProductGallery images={product.images} name={product.name} />
        <div className="product-info">
          <Link className="product-category" to={`/san-pham?category=${category?.slug ?? product.categoryId}`}>{category?.name ?? 'Sản phẩm'}</Link>
          <h1>{product.name}</h1>
          <div className="detail-price"><span>{formatPrice(product.priceVnd)}</span><span className="reference-label">Giá tham khảo</span></div>
          <p className="detail-intro">{product.shortDescription}</p>
          <p className="detail-description">{product.description}</p>
          <Link className="button button-primary detail-back" to={backTo}><Icon name="arrow-left" />Quay lại danh sách</Link>
          <div className="product-accordions">
            <details open><summary>Thông tin sản phẩm <Icon name="chevron-down" /></summary><dl>{getSpecifications(product).map((specification) => <div key={specification.label}><dt>{specification.label}</dt><dd>{specification.value ? `${specification.value}${specification.unit ? ` ${specification.unit}` : ''}` : 'Thông tin đang cập nhật'}</dd></div>)}</dl></details>
            <details><summary>Hướng dẫn bảo quản <Icon name="chevron-down" /></summary><p>Thông tin hướng dẫn bảo quản của sản phẩm đang được cập nhật.</p></details>
          </div>
        </div>
      </div>
      <RelatedProducts product={product} />
    </div>
  );
}

function RelatedProducts({ product }: { product: Product }) {
  const load = useCallback((signal: AbortSignal) => catalogApi.getRelatedProducts(product, signal), [product]);
  const { data, error, retry } = useCatalogResource(product.id, load);
  if (data && !data.length) return null;

  return (
    <section className="section related-products" aria-labelledby="related-title" aria-busy={!data && !error}>
      <div className="section-header"><h2 id="related-title">Sản phẩm tương tự</h2></div>
      {data ? <ProductGrid products={data} /> : error ? (
        <div role="alert"><p>Chưa thể tải sản phẩm tương tự.</p><button className="text-link" onClick={retry}>Thử lại</button></div>
      ) : <p role="status">Đang tải sản phẩm tương tự…</p>}
      <div className="related-products-action"><Link to="/san-pham" className="button button-primary">Xem sản phẩm khác <Icon name="arrow-right" /></Link></div>
    </section>
  );
}
