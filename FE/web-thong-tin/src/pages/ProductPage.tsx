import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { useCatalogResource } from '../app/useCatalogResource';
import { CatalogStatus } from '../components/CatalogStatus';
import { Link, useLocation, useParams } from 'react-router-dom';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';
import { ProductGallery } from '../components/ProductGallery';
import { ProductGrid } from '../components/ProductCard';
import { catalogApi, CatalogApiError } from '../services/catalog';
import type { ProductDetails as ProductDetailsData, ProductImage, ProductSummary } from '../types/catalog';
import { formatPrice, getSpecifications } from '../utils/catalog';
import { NotFoundPage } from './NotFoundPage';

export function ProductPage() {
  const { slug = '' } = useParams();
  usePageMeta('Chi tiết sản phẩm — Luma', 'Thông tin sản phẩm nội thất và gia dụng Luma.');
  // Start independent sections together; the summary alone can reveal the page.
  const load = useCallback((signal: AbortSignal) => catalogApi.getProductSummary(slug, signal), [slug]);
  const loadImages = useCallback((signal: AbortSignal) => catalogApi.getProductImages(slug, signal), [slug]);
  const loadDetails = useCallback((signal: AbortSignal) => catalogApi.getProductDetails(slug, signal), [slug]);
  const { data, error, retry } = useCatalogResource(slug, load);
  const images = useCatalogResource(slug, loadImages);
  const details = useCatalogResource(slug, loadDetails);
  if (error instanceof CatalogApiError && error.status === 404) return <NotFoundPage product />;
  if (!data) return <CatalogStatus error={error} retry={retry} />;
  return <ProductDetails key={data.id} product={data} images={images} details={details} />;
}

type SectionResource<T> = Pick<ReturnType<typeof useCatalogResource<T>>, 'data' | 'error' | 'retry'>;

function SectionError({ message, retry }: { message: string; retry: () => void }) {
  return <div role="alert"><p>{message}</p><button className="text-link" onClick={retry}>Thử lại</button></div>;
}

function ProductDetails({ product, images, details }: {
  product: ProductSummary;
  images: SectionResource<ProductImage[]>;
  details: SectionResource<ProductDetailsData>;
}) {
  usePageMeta(`${product.name} — Luma`, product.shortDescription);
  const descriptionId = useId();
  const [descriptionExpanded, setDescriptionExpanded] = useState(false);
  const { state } = useLocation();
  const candidate: unknown = state?.catalogUrl;
  const backTo = typeof candidate === 'string' && (candidate === '/san-pham' || candidate.startsWith('/san-pham?')) ? candidate : '/san-pham';
  const careLabel = 'Hướng dẫn bảo quản';
  const referenceLabel = 'Thông tin tham khảo';
  const careInstructions = details.data?.specifications.find((item) => item.label === careLabel)?.value;
  const referenceNote = details.data?.specifications.find((item) => item.label === referenceLabel)?.value;
  const specifications = details.data ? getSpecifications(details.data).filter((item) => item.label !== careLabel && item.label !== referenceLabel) : [];
  const description = details.data?.description ?? '';
  const canExpandDescription = description.length > 220;

  return (
    <div className="container product-page">
      <nav className="breadcrumb" aria-label="Đường dẫn"><Link to="/">Trang chủ</Link><span>/</span><Link to={backTo}>Sản phẩm</Link><span>/</span><span aria-current="page">{product.name}</span></nav>
      <div className="product-detail">
        <div className="product-gallery" aria-busy={!images.data && !images.error}>
          <ProductGallery images={images.data ?? (product.primaryImage ? [product.primaryImage] : [])} name={product.name} />
          {images.error ? <SectionError message="Chưa thể tải bộ ảnh sản phẩm." retry={images.retry} /> : !images.data ? <p role="status">Đang tải bộ ảnh sản phẩm…</p> : null}
        </div>
        <div className="product-info">
          <h1>{product.name}</h1>
          <div className="detail-price"><span>{formatPrice(product.priceVnd)}</span><span className="reference-label">Giá tham khảo</span></div>
          <p className="detail-intro">{product.shortDescription}</p>
          {details.data ? <>
            <p id={descriptionId} className={`detail-description${canExpandDescription && !descriptionExpanded ? ' detail-description-collapsed' : ''}`}>{description}</p>
            {canExpandDescription ? <button type="button" className="text-link detail-description-toggle" aria-expanded={descriptionExpanded} aria-controls={descriptionId} onClick={() => setDescriptionExpanded((expanded) => !expanded)}>{descriptionExpanded ? 'Thu gọn' : 'Xem thêm'}</button> : null}
          </> : null}
          <div className="product-accordions">
            <details>
              <summary>Thông tin sản phẩm <Icon name="chevron-down" /></summary>
              <div aria-busy={!details.data && !details.error}>
                {details.data ? <>
                  <dl>{specifications.map((specification) => (
                    <div key={specification.label}>
                      <dt>{specification.label}</dt>
                      <dd>{specification.value ? `${specification.value}${specification.unit ? ` ${specification.unit}` : ''}` : 'Thông tin đang cập nhật'}</dd>
                    </div>
                  ))}</dl>
                  {referenceNote ? <p className="product-reference-note">{referenceLabel}: {referenceNote}</p> : null}
                </> : details.error ? <SectionError message="Chưa thể tải thông tin sản phẩm." retry={details.retry} /> : <p role="status">Đang tải thông tin sản phẩm…</p>}
              </div>
            </details>
            <details>
              <summary>{careLabel} <Icon name="chevron-down" /></summary>
              <div aria-busy={!details.data && !details.error}>
                {details.data ? <p>{careInstructions || 'Thông tin hướng dẫn bảo quản của sản phẩm đang được cập nhật.'}</p> : details.error ? <SectionError message="Chưa thể tải hướng dẫn bảo quản." retry={details.retry} /> : <p role="status">Đang tải hướng dẫn bảo quản…</p>}
              </div>
            </details>
          </div>
        </div>
      </div>
      <DeferredRelatedProducts slug={product.slug} />
    </div>
  );
}

function DeferredRelatedProducts({ slug }: { slug: string }) {
  const section = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    if (!('IntersectionObserver' in window)) {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setVisible(true);
        observer.disconnect();
      }
    }, { rootMargin: '200px' });
    if (section.current) observer.observe(section.current);
    return () => observer.disconnect();
  }, []);
  return <div ref={section}>{visible ? <RelatedProducts slug={slug} /> : <section className="section related-products" aria-label="Sản phẩm tương tự"><div className="section-header"><h2>Sản phẩm tương tự</h2></div></section>}</div>;
}

function RelatedProducts({ slug }: { slug: string }) {
  const load = useCallback((signal: AbortSignal) => catalogApi.getRelatedProducts(slug, signal), [slug]);
  const { data, error, retry } = useCatalogResource(slug, load);
  if (data && !data.length) return null;

  return (
    <section className="section related-products" aria-labelledby="related-title" aria-busy={!data && !error}>
      <div className="section-header"><h2 id="related-title">Sản phẩm tương tự</h2></div>
      {data ? <ProductGrid products={data} /> : error ? <SectionError message="Chưa thể tải sản phẩm tương tự." retry={retry} /> : <p role="status">Đang tải sản phẩm tương tự…</p>}
      <div className="related-products-action"><Link to="/san-pham" className="button button-primary">Xem sản phẩm khác <Icon name="arrow-right" /></Link></div>
    </section>
  );
}
