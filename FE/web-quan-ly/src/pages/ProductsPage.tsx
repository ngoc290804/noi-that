import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { AdminActions, AdminData, Product, Status } from '../types/admin';
import { exportCsv, money, normalize, statusLabels } from '../utils/catalog';
import { Icon } from '../components/Icon';
import { PageHeading } from '../components/PageHeading';
import { ProductEditor } from '../components/ProductEditor';
import { ConfirmAction } from '../components/ConfirmAction';
import { errorMessage } from '../app/useAdminStore';

type Tab = 'all' | Status;
const tabs: { id: Tab; label: string }[] = [{ id: 'all', label: 'Tất cả' }, { id: 'published', label: 'Đang hiển thị' }, { id: 'draft', label: 'Bản nháp' }, { id: 'archived', label: 'Đã lưu trữ' }];
export function ProductsPage({ data, actions }: { data: AdminData; actions: AdminActions }) {
  const [params, setParams] = useSearchParams();
  const latestParams = useRef(params);
  useEffect(() => { latestParams.current = params; }, [params]);
  const q = params.get('q') || '';
  const category = params.get('category') || '';
  const tab = tabs.some(t => t.id === params.get('status')) ? params.get('status') as Tab : 'all';
  const sort = params.get('sort') || 'order';
  const [selection, setSelection] = useState<string[]>([]);
  const [editor, setEditor] = useState<Product | 'new' | null>(null);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [deleting,setDeleting]=useState<Product|null>(null);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const totalPagesSize = 6;
  const counts = { all: data.products.length, published: data.products.filter(p => p.status === 'published').length, draft: data.products.filter(p => p.status === 'draft').length, archived: data.products.filter(p => p.status === 'archived').length };
  const filtered = data.products.filter(p => (tab === 'all' || p.status === tab) && (!category || p.categoryId === category) && normalize(`${p.name} ${p.slug} ${p.id}`).includes(normalize(q))).sort((a, b) => sort === 'price-asc' ? a.priceVnd - b.priceVnd : sort === 'price-desc' ? b.priceVnd - a.priceVnd : sort === 'updated' ? b.updatedAt.localeCompare(a.updatedAt) : a.displayOrder - b.displayOrder);
  const totalPages = Math.max(1, Math.ceil(filtered.length / totalPagesSize));
  const page = Math.min(totalPages, Math.max(1, Math.trunc(Number(params.get('page'))) || 1));
  const visible = filtered.slice((page - 1) * totalPagesSize, page * totalPagesSize);
  const allSelected = visible.length > 0 && visible.every(p => selection.includes(p.id));
  function filter(key: string, value: string) {
    // React Router's search-param callback does not queue updates. Keep rapid
    // search, category and sort changes together until navigation commits.
    const next = new URLSearchParams(latestParams.current);
    if (value) next.set(key, value); else next.delete(key);
    if (key !== 'page') next.delete('page');
    latestParams.current = next;
    setParams(next, { replace: key === 'q' });
    setSelection([]);
  }
  async function feature(product: Product) {
    setBusy(true); setError('');
    try { await actions.saveProduct({ ...product, isFeatured: !product.isFeatured }, false); }
    catch(error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }
  function exportList() { exportCsv([['Tên sản phẩm', 'Slug', 'Danh mục', 'Giá VND', 'Trạng thái', 'Nổi bật'], ...filtered.map(p => [p.name, p.slug, data.categories.find(c => c.id === p.categoryId)?.name || '', p.priceVnd, statusLabels[p.status], p.isFeatured ? 'Có' : 'Không'])]); }
  return <>
    <PageHeading title="Sản phẩm" description="Chăm chút từng sản phẩm, hoàn thiện không gian sống."><button className="button" onClick={exportList}><Icon name="download" />Xuất danh sách</button><button className="button primary" onClick={() => setEditor('new')}><Icon name="plus" />Thêm sản phẩm</button></PageHeading>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="summary-strip">{[['Tổng sản phẩm', counts.all], ['Đang hiển thị', counts.published], ['Bản nháp', counts.draft], ['Nổi bật', data.products.filter(p => p.isFeatured).length]].map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>
    <section className="table-panel" aria-label="Danh sách sản phẩm">
      <div className="tabs" role="tablist" aria-label="Trạng thái sản phẩm">{tabs.map(item => <button key={item.id} role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'active' : ''} onClick={() => filter('status', item.id === 'all' ? '' : item.id)}>{item.label}<span>{counts[item.id]}</span></button>)}</div>
      <div className="table-toolbar"><div className="search-field"><Icon name="search" /><input aria-label="Tìm sản phẩm" placeholder="Tìm theo tên, mã hoặc slug…" value={q} onChange={e => filter('q', e.target.value)} />{q && <button className="icon-button" aria-label="Xóa tìm kiếm" onClick={() => filter('q', '')}><Icon name="close" /></button>}</div><select aria-label="Lọc danh mục" value={category} onChange={e => filter('category', e.target.value)}><option value="">Tất cả danh mục</option>{data.categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select><select aria-label="Sắp xếp sản phẩm" value={sort} onChange={e => filter('sort', e.target.value)}><option value="order">Thứ tự hiển thị</option><option value="updated">Cập nhật gần nhất</option><option value="price-asc">Giá tăng dần</option><option value="price-desc">Giá giảm dần</option></select></div>
      {selection.length > 0 && <div className="bulk-toolbar"><span>Đã chọn {selection.length} sản phẩm</span><button className="text-button" onClick={() => setConfirmArchive(true)}><Icon name="archive" />Lưu trữ đã chọn</button><button className="text-button" onClick={() => setSelection([])}>Bỏ chọn</button></div>}
      <div className="table-scroll"><table className="product-table"><thead><tr><th className="checkbox-cell"><input type="checkbox" aria-label="Chọn tất cả sản phẩm trên trang" checked={allSelected} onChange={e => setSelection(e.target.checked ? [...new Set([...selection, ...visible.map(p => p.id)])] : selection.filter(id => !visible.some(p => p.id === id)))} /></th><th>Sản phẩm</th><th>Danh mục</th><th>Giá tham khảo</th><th>Trạng thái</th><th className="center">Nổi bật</th><th><span className="sr-only">Thao tác</span></th></tr></thead><tbody>{visible.map(product => <tr key={product.id} className={selection.includes(product.id) ? 'row-selected' : ''}><td className="checkbox-cell"><input type="checkbox" aria-label={`Chọn ${product.name}`} checked={selection.includes(product.id)} onChange={e => setSelection(e.target.checked ? [...selection, product.id] : selection.filter(id => id !== product.id))} /></td><td><button className="product-identity" onClick={() => setEditor(product)}>{product.images[0] ? <img src={product.images[0].thumbnail} alt="" width="62" height="54" /> : <span className="thumb-placeholder"><Icon name="image" /></span>}<span><strong>{product.name}</strong><small>{product.slug}</small></span></button></td><td className="category-cell">{data.categories.find(c => c.id === product.categoryId)?.name || 'Chưa phân loại'}</td><td className="price-cell">{money(product.priceVnd)}</td><td><span className={`status status-${product.status}`}>{statusLabels[product.status]}</span></td><td className="center"><button className={`star-button ${product.isFeatured ? 'is-featured' : ''}`} aria-label={`${product.isFeatured ? 'Bỏ' : 'Đặt'} nổi bật: ${product.name}`} aria-pressed={product.isFeatured} disabled={busy} onClick={() => void feature(product)}><Icon name="star" fill={product.isFeatured ? 'currentColor' : 'none'} /></button></td><td><button className="icon-button bordered" aria-label={`Sửa ${product.name}`} onClick={() => setEditor(product)}><Icon name="edit" /></button><button className="icon-button delete-button" aria-label={`Xóa ${product.name}`} onClick={() => setDeleting(product)}><Icon name="trash" /></button></td></tr>)}</tbody></table></div>
      {!visible.length && <div className="empty"><Icon name="search" width="32" height="32" /><h3>Không tìm thấy sản phẩm</h3><p>Thử từ khóa hoặc bộ lọc khác để tìm món đồ bạn cần.</p><button className="button" onClick={() => { setParams({}); setSelection([]); }}>Xóa bộ lọc</button></div>}
      <footer className="table-footer"><span>Hiển thị {filtered.length ? (page - 1) * totalPagesSize + 1 : 0}–{Math.min(page * totalPagesSize, filtered.length)} trên {filtered.length} sản phẩm</span><nav className="pagination" aria-label="Phân trang"><button className="icon-button bordered" aria-label="Trang trước" disabled={page <= 1} onClick={() => filter('page', String(page - 1))}><Icon name="left" /></button>{Array.from({ length: totalPages }, (_, i) => i + 1).filter(n => n === 1 || n === totalPages || Math.abs(n - page) < 2).map(n => <button className={page === n ? 'active' : ''} key={n} aria-label={`Trang ${n}`} aria-current={page === n ? 'page' : undefined} onClick={() => filter('page', String(n))}>{n}</button>)}<button className="icon-button bordered" aria-label="Trang sau" disabled={page >= totalPages} onClick={() => filter('page', String(page + 1))}><Icon name="right" /></button></nav></footer>
    </section>
    {editor && <ProductEditor key={editor === 'new' ? 'new' : editor.id} product={editor === 'new' ? null : editor} data={data} onUpload={actions.uploadImage} onSave={product => actions.saveProduct(product, editor === 'new')} onClose={() => setEditor(null)} />}
    {confirmArchive && <ConfirmAction title="Lưu trữ sản phẩm đã chọn?" description={`${selection.length} sản phẩm sẽ ngừng hiển thị trên website. Có thể khôi phục bằng cách sửa trạng thái sản phẩm.`} label="Lưu trữ sản phẩm" action={async()=>{await actions.archiveProducts(data.products.filter(p=>selection.includes(p.id)));setSelection([]);}} onClose={()=>setConfirmArchive(false)} />}
    {deleting && <ConfirmAction title={`Xóa “${deleting.name}”?`} description="Sản phẩm sẽ bị gỡ khỏi danh sách quản lý và website. Các ảnh trong thư viện được giữ lại." label="Xóa sản phẩm" danger action={()=>actions.deleteProduct(deleting)} onClose={()=>setDeleting(null)} />}

  </>;
}
