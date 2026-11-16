import { useState } from 'react';
import type { AdminActions, AdminData, ImageAsset } from '../types/admin';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { ConfirmAction } from '../components/ConfirmAction';
import { ImageUploadDialog } from '../components/ImageUploadDialog';
import { PageHeading } from '../components/PageHeading';
import { errorMessage } from '../app/useAdminStore';
import { normalize } from '../utils/catalog';
export function MediaPage({data,actions}:{data:AdminData;actions:AdminActions}) {
  const [query,setQuery]=useState('');
  const [editing,setEditing]=useState<ImageAsset|null>(null);
  const [deleting,setDeleting]=useState<ImageAsset|null>(null);
  const [uploading,setUploading]=useState(false);
  const [pending,setPending]=useState(false);
  const [error,setError]=useState('');
  const filtered=data.media.filter(asset=>normalize(asset.alt).includes(normalize(query)));
  function close(){if(!pending&&(!editing||data.media.find(a=>a.id===editing.id)?.alt===editing.alt||window.confirm('Bỏ mô tả ảnh chưa lưu?')))setEditing(null);}
  return <><PageHeading title="Thư viện ảnh" description="Một nơi lưu ảnh, dùng thống nhất trên sản phẩm và website."><button className="button primary" onClick={()=>setUploading(true)}><Icon name="upload" />Thêm ảnh</button></PageHeading><div className="media-toolbar"><div className="search-field"><Icon name="search" /><input aria-label="Tìm trong thư viện ảnh" placeholder="Tìm ảnh theo mô tả…" value={query} onChange={e=>setQuery(e.target.value)} /></div><span className="muted">{filtered.length} ảnh · JPG, PNG, WebP · Tối đa 10 MB/ảnh</span></div>
    <div className="media-grid">{filtered.map(asset=><button key={asset.id} className="media-card" onClick={()=>{setError('');setEditing({...asset});}}><div className="media-photo"><img src={asset.thumbnail} alt={asset.alt} loading="lazy" /><span><Icon name="edit" />Chỉnh sửa mô tả</span></div><div className="media-caption"><strong>{asset.alt||'Chưa có mô tả'}</strong><small>{asset.width} × {asset.height} px · {asset.usedCount?'Đang sử dụng':'Chưa sử dụng'}</small></div></button>)}</div>{!filtered.length&&<div className="empty"><Icon name="image" width="32" height="32" /><h3>Chưa có ảnh phù hợp</h3><p>Thử từ khóa khác hoặc tải ảnh mới vào thư viện.</p></div>}
    {uploading&&<ImageUploadDialog upload={actions.uploadImage} onClose={()=>setUploading(false)} />}
    {editing&&<Modal title="Thông tin ảnh" onClose={close}><form onSubmit={async e=>{e.preventDefault();if(!editing.alt.trim()){setError('Nhập mô tả ảnh.');return;}setPending(true);setError('');try{await actions.saveImage({...editing,alt:editing.alt.trim()});setEditing(null);}catch(error){setError(errorMessage(error));}finally{setPending(false);}}}><fieldset disabled={pending} className="modal-body form-stack"><img className="media-detail" src={editing.src} alt={editing.alt} /><p className="field-hint">{editing.width} × {editing.height} px · {editing.usedCount} bản ghi sử dụng</p><label>Mô tả ảnh (alt) *<textarea required maxLength={300} rows={3} value={editing.alt} onChange={e=>setEditing({...editing,alt:e.target.value})} /></label><p className="field-hint">Khi lưu, mô tả được cập nhật ở tất cả sản phẩm và nội dung đang dùng ảnh này.</p>{error&&<p className="form-error" role="alert">{error}</p>}</fieldset><div className="modal-footer"><button className="text-button delete-button" type="button" disabled={pending||editing.usedCount>0} title={editing.usedCount?'Gỡ ảnh khỏi các nơi sử dụng trước khi xóa':undefined} onClick={()=>setDeleting(editing)}><Icon name="trash" />Xóa khỏi thư viện</button><button className="button primary" disabled={pending}>{pending?'Đang lưu…':'Lưu mô tả'}</button></div></form></Modal>}
    {deleting&&<ConfirmAction title="Xóa ảnh khỏi thư viện?" description="Chỉ ảnh không còn được sử dụng mới có thể xóa. Tệp trên Cloudinary được giữ để tránh làm hỏng liên kết đã chia sẻ." label="Xóa ảnh" danger action={async()=>{await actions.deleteImage(deleting);setEditing(null);}} onClose={()=>setDeleting(null)} />}
  </>;
}
