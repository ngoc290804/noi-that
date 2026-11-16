import { useState } from 'react';
import type { AdminActions, AdminData, Category } from '../types/admin';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { ConfirmAction } from '../components/ConfirmAction';
import { PageHeading } from '../components/PageHeading';
import { slugify } from '../utils/catalog';
import { errorMessage } from '../app/useAdminStore';

export function CategoriesPage({ data, actions }: { data: AdminData; actions: AdminActions }) {
  const [editing,setEditing]=useState<Category|null>(null);
  const [deleting,setDeleting]=useState<Category|null>(null);
  const [error,setError]=useState('');
  const [dirty,setDirty]=useState(false);
  const [pending,setPending]=useState(false);
  const [manualSlug,setManualSlug]=useState(false);
  function open(category:Category){setError('');setDirty(false);setManualSlug(data.categories.some(c=>c.id===category.id));setEditing({...category});}
  function close(){if(!pending&&(!dirty||window.confirm('Bỏ thay đổi danh mục chưa lưu?')))setEditing(null);}
  return <><PageHeading title="Danh mục" description="Phân loại rõ ràng để khách dễ tìm sản phẩm."><button className="button primary" onClick={()=>open({id:crypto.randomUUID(),name:'',slug:'',active:true,displayOrder:data.categories.length+1,version:0})}><Icon name="plus" />Thêm danh mục</button></PageHeading>
    <section className="table-panel category-panel"><div className="panel-title"><h2>Danh mục sản phẩm</h2><span className="muted">{data.categories.length} danh mục</span></div><div className="table-scroll"><table><thead><tr><th>Thứ tự</th><th>Danh mục</th><th>Đường dẫn</th><th>Sản phẩm</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{data.categories.map(category=><tr key={category.id}><td><span className="category-index">{category.displayOrder}</span></td><td><strong>{category.name}</strong></td><td className="muted">{category.slug}</td><td>{data.products.filter(p=>p.categoryId===category.id).length}</td><td><span className={`status status-${category.active?'published':'archived'}`}>{category.active?'Đang hiển thị':'Đang ẩn'}</span></td><td><button className="icon-button bordered" aria-label={`Sửa danh mục ${category.name}`} onClick={()=>open(category)}><Icon name="edit" /></button><button className="icon-button delete-button" aria-label={`Xóa danh mục ${category.name}`} onClick={()=>setDeleting(category)}><Icon name="trash" /></button></td></tr>)}</tbody></table></div>{!data.categories.length&&<div className="empty"><h3>Chưa có danh mục</h3><p>Thêm danh mục đầu tiên để bắt đầu tạo sản phẩm.</p></div>}</section>
    <p className="section-tip"><Icon name="info" />Ẩn danh mục sẽ ẩn các sản phẩm thuộc danh mục trên website. Chỉ xóa khi danh mục không còn sản phẩm.</p>
    {editing&&<Modal title={data.categories.some(c=>c.id===editing.id)?'Chỉnh sửa danh mục':'Thêm danh mục'} onClose={close}><form onSubmit={async e=>{e.preventDefault();setError('');if(!editing.name.trim()){setError('Vui lòng nhập tên danh mục.');return;}setPending(true);try{await actions.saveCategory({...editing,name:editing.name.trim()},!data.categories.some(c=>c.id===editing.id));setEditing(null);}catch(error){setError(errorMessage(error));}finally{setPending(false);}}}><fieldset disabled={pending} className="modal-body form-stack"><label>Tên danh mục *<input required autoFocus maxLength={200} value={editing.name} onChange={e=>{const name=e.target.value;setEditing({...editing,name,...(!manualSlug?{slug:slugify(name)}:{})});setDirty(true);}} /></label><label>Đường dẫn *<input required pattern="[a-z0-9]+(-[a-z0-9]+)*" maxLength={100} value={editing.slug} onChange={e=>{setManualSlug(true);setEditing({...editing,slug:e.target.value});setDirty(true);}} /></label><label>Thứ tự hiển thị<input type="number" required min="0" max="1000000" step="1" value={Number.isNaN(editing.displayOrder)?'':editing.displayOrder} onChange={e=>{setEditing({...editing,displayOrder:e.target.valueAsNumber});setDirty(true);}} /></label><label className="check-label"><input type="checkbox" checked={editing.active} onChange={e=>{setEditing({...editing,active:e.target.checked});setDirty(true);}} />Hiển thị trên website</label>{error&&<p className="form-error" role="alert">{error}</p>}</fieldset><div className="modal-footer"><button type="button" className="button" disabled={pending} onClick={close}>Hủy</button><button className="button primary" type="submit" disabled={pending}>{pending?'Đang lưu…':'Lưu danh mục'}</button></div></form></Modal>}
    {deleting&&<ConfirmAction title={`Xóa danh mục “${deleting.name}”?`} description="BE sẽ kiểm tra danh mục còn sản phẩm hay không. Chuyển các sản phẩm sang danh mục khác trước khi xóa." label="Xóa danh mục" danger action={()=>actions.deleteCategory(deleting)} onClose={()=>setDeleting(null)} />}
  </>;
}
