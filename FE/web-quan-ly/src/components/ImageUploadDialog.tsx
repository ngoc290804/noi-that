import { useEffect, useState } from 'react';
import { Modal } from './Modal';
import { errorMessage } from '../app/useAdminStore';
export function ImageUploadDialog({upload,onClose}:{upload:(file:File,alt:string)=>Promise<void>;onClose:()=>void}) {
  const [file,setFile]=useState<File|null>(null);
  const [alt,setAlt]=useState('');
  const [preview,setPreview]=useState('');
  const [pending,setPending]=useState(false);
  const [error,setError]=useState('');
  useEffect(()=>{if(!file){setPreview('');return;}const url=URL.createObjectURL(file);setPreview(url);return()=>URL.revokeObjectURL(url);},[file]);
  function close(){if(!pending&&(!file||window.confirm('Bỏ ảnh chưa tải lên?')))onClose();}
  return <Modal title="Tải ảnh lên thư viện" subtitle="Ảnh được lưu trên Cloudinary; mô tả sẽ hỗ trợ người dùng đọc màn hình." onClose={close}><form onSubmit={async e=>{e.preventDefault();if(!file)return;setError('');if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>10*1024*1024){setError('Chọn ảnh JPG, PNG hoặc WebP tối đa 10 MB.');return;}if(!alt.trim()){setError('Nhập mô tả ảnh trước khi tải lên.');return;}setPending(true);try{await upload(file,alt.trim());onClose();}catch(error){setError(errorMessage(error));}finally{setPending(false);}}}><fieldset className="modal-body form-stack" disabled={pending}><label>Tệp ảnh *<input required type="file" accept="image/jpeg,image/png,image/webp" onChange={e=>{const f=e.target.files?.[0]||null;setFile(f);setError('');if(f&&!alt)setAlt(f.name.replace(/\.[^.]+$/,'').replace(/[-_]/g,' '));}} /></label>{preview&&<img className="media-detail" src={preview} alt="Xem trước ảnh được chọn" />}<label>Mô tả ảnh (alt) *<textarea required rows={3} maxLength={300} value={alt} onChange={e=>setAlt(e.target.value)} /></label><p className="field-hint">JPG, PNG, WebP · Tối đa 10 MB. Mô tả đúng nội dung ảnh, không chỉ ghi tên tệp.</p>{error&&<p className="form-error" role="alert">{error}</p>}</fieldset><div className="modal-footer"><button className="button" type="button" disabled={pending} onClick={close}>Hủy</button><button className="button primary" disabled={pending||!file}>{pending?'Đang tải lên…':'Tải ảnh lên'}</button></div></form></Modal>;
}
