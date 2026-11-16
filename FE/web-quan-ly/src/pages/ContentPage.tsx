import { useEffect, useState } from 'react';
import type { AdminActions, AdminData, ImageAsset } from '../types/admin';
import { Icon } from '../components/Icon';
import { PageHeading } from '../components/PageHeading';
import { MediaPicker } from '../components/ProductEditor';
import { errorMessage } from '../app/useAdminStore';

export function ContentPage({ data, actions, onDirtyChange }: { data: AdminData; actions: AdminActions; onDirtyChange: (dirty: boolean) => void }) {
  const [draft, setDraft] = useState(() => structuredClone(data.content));
  const [dirty, setDirty] = useState(false);
  const [tab, setTab] = useState('hero');
  const [picker, setPicker] = useState<'hero' | number | null>(null);
  const [error, setError] = useState('');
  const [pending,setPending]=useState(false);
  const [baseline,setBaseline]=useState(data.contentVersion);
  useEffect(()=>{if(!dirty&&data.contentVersion>=baseline){setDraft(structuredClone(data.content));setBaseline(data.contentVersion);}},[data.content,data.contentVersion,dirty,baseline]);
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false); }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);
  function update(next: typeof draft) { setDraft(next); setDirty(true); setError(''); }
  function choose(asset: ImageAsset) { if (picker === 'hero') update({ ...draft, heroImage: asset }); else if (typeof picker === 'number') update({ ...draft, spaces: draft.spaces.map((space, i) => i === picker ? { ...space, image: asset } : space) }); setPicker(null); }
  return <><form noValidate onSubmit={async e => {
    e.preventDefault();
    if (!draft.heroTitle.trim()) { setError('Vui lòng nhập tiêu đề banner.'); setTab('hero'); return; }
    if (draft.spaces.some(s => !s.title.trim() || !/^\/san-pham(?:\?[^\s]*)?$/.test(s.href))) { setError('Mỗi không gian cần có tên và liên kết bắt đầu bằng /san-pham.'); setTab('spaces'); return; }
    setPending(true);setError('');
    try { const version=await actions.saveContent(draft,baseline); setBaseline(version); setDirty(false); }
    catch(error) { setError(errorMessage(error)); }
    finally { setPending(false); }
  }}>
    <PageHeading title="Nội dung website" description="Kể câu chuyện Luma qua từng không gian và chi tiết."><button className="button primary" type="submit" disabled={!dirty||pending}><Icon name="check" />{pending?'Đang lưu…':'Lưu & cập nhật website'}</button></PageHeading>
    {error && <p className="form-error" role="alert">{error}</p>}
    <fieldset disabled={pending} className="content-layout"><section className="table-panel content-editor"><div className="tabs" role="tablist" aria-label="Phần nội dung">{[['hero', 'Banner trang chủ'], ['featured', 'Sản phẩm nổi bật'], ['spaces', 'Không gian']].map(([id, label]) => <button type="button" role="tab" aria-selected={tab === id} className={tab === id ? 'active' : ''} key={id} onClick={() => setTab(id)}>{label}</button>)}</div>
      <div className="content-body" hidden={tab !== 'hero'}><div className="form-stack"><h2>Banner trang chủ</h2><p className="muted">Ấn tượng đầu tiên cho một không gian sống mới.</p><label>Tiêu đề <span className="required">*</span><textarea required rows={2} maxLength={180} value={draft.heroTitle} onChange={e => update({ ...draft, heroTitle: e.target.value })} /></label><label>Mô tả<textarea rows={4} maxLength={1000} value={draft.heroDescription} onChange={e => update({ ...draft, heroDescription: e.target.value })} /></label><label>Ảnh banner</label><img className="content-image" src={draft.heroImage.thumbnail} alt={draft.heroImage.alt} /><button className="button fit" type="button" onClick={() => setPicker('hero')}><Icon name="image" />Thay ảnh banner</button></div></div>
      {tab === 'featured' && <div className="content-body"><h2>Sản phẩm nổi bật</h2><p className="muted">Chọn những món đồ bạn muốn giới thiệu trên trang chủ.</p><div className="featured-list">{data.products.filter(p => p.status !== 'archived').map(product => <label key={product.id} className="featured-option">{product.images[0]?<img src={product.images[0].thumbnail} alt="" />:<span className="thumb-placeholder"><Icon name="image" /></span>}<span><strong>{product.name}</strong><small>{product.status === 'published' ? 'Đang hiển thị' : 'Bản nháp · chưa hiển thị công khai'}</small></span><input type="checkbox" checked={draft.featuredProductIds.includes(product.id)} onChange={e => update({ ...draft, featuredProductIds: e.target.checked ? [...draft.featuredProductIds, product.id] : draft.featuredProductIds.filter(id => id !== product.id) })} /></label>)}</div></div>}
      {tab === 'spaces' && <div className="content-body"><h2>Cảm hứng không gian</h2><p className="muted">Gợi mở cách kết nối những món đồ trong một tổ ấm.</p>{draft.spaces.map((space, i) => <section className="space-editor form-stack" key={i}><div className="section-title"><h3>Không gian {i + 1}</h3><button className="text-button" type="button" onClick={() => { if (window.confirm('Gỡ không gian này khỏi bản nháp?')) update({ ...draft, spaces: draft.spaces.filter((_, index) => index !== i) }); }}>Gỡ</button></div><img className="content-image" src={space.image.thumbnail} alt={space.image.alt} /><button className="button fit" type="button" onClick={() => setPicker(i)}><Icon name="image" />Chọn ảnh</button>{(['title', 'description', 'href'] as const).map((field, index) => <label key={field}>{['Tên không gian', 'Mô tả', 'Liên kết danh sách sản phẩm'][index]}<input maxLength={field==='title'?200:field==='href'?500:1000} required={field !== 'description'} pattern={field === 'href' ? '/san-pham([?][^ ]*)?' : undefined} value={space[field]} onChange={e => update({ ...draft, spaces: draft.spaces.map((s, index) => index === i ? { ...s, [field]: e.target.value } : s) })} /></label>)}</section>)}<button className="button" type="button" disabled={draft.spaces.length>=20} onClick={() => update({ ...draft, spaces: [...draft.spaces, { title: '', description: '', href: '/san-pham', image: draft.heroImage }] })}><Icon name="plus" />Thêm không gian</button></div>}
    </section><aside className="content-preview"><div className="section-title"><h3>Xem trước banner</h3><Icon name="eye" /></div><div className="mini-site"><div className="mini-brand">L U M A</div><img src={draft.heroImage.src} alt={draft.heroImage.alt} /><div className="mini-copy"><h2>{draft.heroTitle}</h2><p>{draft.heroDescription}</p><span>Khám phá sản phẩm <Icon name="right" /></span></div></div><div className="info-note"><Icon name="info" /><span>{dirty ? 'Bạn có thay đổi chưa lưu.' : 'Nội dung đã tải từ BE.'} Khi lưu, thay đổi được áp dụng lên website.</span></div></aside></fieldset>
    </form>{picker !== null && <MediaPicker onUpload={actions.uploadImage} media={data.media} onClose={() => setPicker(null)} onChoose={choose} />}</>;
}
