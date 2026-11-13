import { useCallback, useEffect } from 'react';
import { useCatalogResource } from '../app/useCatalogResource';
import { CatalogStatus } from '../components/CatalogStatus';
import type { FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { usePageMeta } from '../app/usePageMeta';
import { Icon } from '../components/Icon';
import { ProductGrid } from '../components/ProductCard';
import { catalogApi } from '../services/catalog';
import type { CatalogQuery } from '../types/catalog';
import { createCatalogParams, parseCatalogQuery } from '../utils/catalog';

export function CatalogPage() {
  usePageMeta('Sản phẩm — Luma', 'Khám phá danh sách nội thất và gia dụng Luma. Tìm kiếm ghế, đèn, bình và cốc gốm cho không gian của bạn.');
  const [params, setParams] = useSearchParams();
  const query = parseCatalogQuery(params);
  const canonical = createCatalogParams(query).toString();
  const current = params.toString();
  const load = useCallback(async (signal: AbortSignal) => {
    const request = parseCatalogQuery(new URLSearchParams(canonical));
    const [result, categories] = await Promise.all([
      catalogApi.listProducts(request, signal), catalogApi.listCategories(signal),
    ]);
    return { result, categories };
  }, [canonical]);
  const { data, previousData, error, retry } = useCatalogResource(canonical, load);
  const { items = [], total = 0, page = 0, size = 24 } = data?.result ?? {};
  const categories = data?.categories ?? previousData?.categories ?? [];
  const selectedCategory = categories.find((item) => item.id === query.category || item.slug === query.category);
  const pageCount = Math.ceil(total / size);

  useEffect(() => {
    if (canonical !== current) setParams(canonical, { replace: true });
  }, [canonical, current, setParams]);

  function updateQuery(patch: Partial<CatalogQuery>) {
    setParams(createCatalogParams({ ...query, page: 0, ...patch }));
  }
  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = String(new FormData(event.currentTarget).get('q') ?? '').trim();
    updateQuery({ q: value });
  }
  function reset() {
    setParams({});
  }

  return (
    <>
      <section className="catalog-intro"><div className="container"><h1>Nội thất & Gia dụng</h1><p>Những thiết kế tinh giản, mang lại sự bình yên cho ngôi nhà bạn.</p></div></section>
      <section className="container catalog-section" aria-label="Danh sách sản phẩm">
        <div className="catalog-toolbar">
          <div className="category-pills" role="group" aria-label="Lọc theo danh mục">
            <button className={`category-pill ${!query.category ? 'selected' : ''}`} aria-pressed={!query.category} onClick={() => updateQuery({ category: '' })}>Tất cả</button>
            {categories.map((category) => <button key={category.id} className={`category-pill ${selectedCategory?.id === category.id ? 'selected' : ''}`} aria-pressed={selectedCategory?.id === category.id} onClick={() => updateQuery({ category: category.slug })}>{category.name}</button>)}
          </div>
          <label className="sort-control"><span>Sắp xếp:</span><select aria-label="Sắp xếp sản phẩm" value={query.sort} onChange={(event) => updateQuery({ sort: event.target.value as CatalogQuery['sort'] })}><option value="featured">Nổi bật</option><option value="price-asc">Giá tăng dần</option><option value="price-desc">Giá giảm dần</option></select><Icon name="chevron-down" /></label>
        </div>
        <div className="catalog-search-row">
          <p className="result-count" role="status">{data ? <>{total} sản phẩm{query.q ? <> cho “{query.q}”</> : null}</> : error ? 'Chưa thể tải kết quả' : 'Đang tải sản phẩm…'}</p>
          <form key={canonical} className="catalog-search search-form" role="search" onSubmit={submitSearch}>
            <label className="sr-only" htmlFor="catalog-search">Tìm theo tên sản phẩm</label>
            <input id="catalog-search" name="q" type="search" defaultValue={query.q} maxLength={120} placeholder="Tìm theo tên sản phẩm…" onChange={(event) => {
              if (event.currentTarget.value === '' && (query.q || query.category || query.sort !== 'featured')) reset();
            }} />
            <button type="submit" className="icon-button" aria-label="Tìm sản phẩm"><Icon name="search" /></button>
          </form>
        </div>
        {!data ? <CatalogStatus error={error} retry={retry} /> : items.length ? <ProductGrid products={items} priority /> : <div className="empty-state"><div className="empty-icon"><Icon name="search" width={28} height={28} /></div><h2>{total ? 'Trang này không có sản phẩm' : 'Chưa tìm thấy món đồ phù hợp'}</h2><p>Thử một tên khác hoặc bỏ bộ lọc để khám phá thêm nhé.</p><button className="button button-primary" onClick={reset}>Xem tất cả sản phẩm <Icon name="arrow-right" /></button></div>}
        {data && pageCount > 1 ? <nav className="catalog-pagination" aria-label="Phân trang sản phẩm">
          <button className="button" disabled={page === 0} onClick={() => updateQuery({ page: page - 1 })}>Trang trước</button>
          <span role="status">Trang {page + 1} / {pageCount}</span>
          <button className="button" disabled={page + 1 >= pageCount} onClick={() => updateQuery({ page: page + 1 })}>Trang sau</button>
        </nav> : null}
        {data && total ? <div className="catalog-end"><span />{query.q || query.category ? <button className="text-link" onClick={reset}>Xóa tìm kiếm & bộ lọc <Icon name="close" width={16} height={16} /></button> : <p>Những món đồ nhỏ, cho những điều bình dị.</p>}<span /></div> : null}
        <p className="price-note">Giá hiển thị mang tính tham khảo. Website hiện giới thiệu thông tin sản phẩm.</p>
      </section>
    </>
  );
}
