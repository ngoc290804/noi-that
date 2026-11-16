import { useEffect, useState } from 'react';
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Icon } from '../components/Icon';
import { Modal } from '../components/Modal';
import { LoginView, PasswordForm } from '../components/AuthForms';
import type { IconName } from '../components/Icon';
import { ProductsPage } from '../pages/ProductsPage';
import { CategoriesPage } from '../pages/CategoriesPage';
import { MediaPage } from '../pages/MediaPage';
import { ContentPage } from '../pages/ContentPage';
import { AccountsPage } from '../pages/AccountsPage';
import { errorMessage, useAdminStore } from './useAdminStore';
const navigation: {path:string;label:string;icon:IconName;admin?:boolean}[]=[
  {path:'/san-pham',label:'Sản phẩm',icon:'box'}, {path:'/danh-muc',label:'Danh mục',icon:'tag'},
  {path:'/thu-vien-anh',label:'Thư viện ảnh',icon:'image'}, {path:'/noi-dung',label:'Nội dung website',icon:'file'},
  {path:'/tai-khoan',label:'Tài khoản',icon:'users',admin:true},
];
export function App() {
  const store=useAdminStore();const [menu,setMenu]=useState(false);const [userMenu,setUserMenu]=useState(false);
  const [unsaved,setUnsaved]=useState(false);const [contentKey,setContentKey]=useState(0);const [passwordModal,setPasswordModal]=useState(false);
  const {pathname}=useLocation();const title=navigation.find(item=>item.path===pathname)?.label||'Sản phẩm';
  const user=store.session.user;
  useEffect(()=>{document.title=`${user?title:'Đăng nhập'} — Luma Studio`;setMenu(false);setUserMenu(false);},[pathname,title,user?.id]);
  useEffect(()=>{if(!store.notice)return;const timer=setTimeout(()=>store.setNotice(''),6500);return()=>clearTimeout(timer);},[store.notice,store.setNotice]);
  async function logout(){if(unsaved&&!window.confirm('Bạn có nội dung chưa lưu. Đăng xuất và bỏ thay đổi?'))return;try{await store.logout();setUserMenu(false);setPasswordModal(false);setUnsaved(false);}catch(error){store.setNotice(errorMessage(error));}}
  const toast=store.notice&&<div className="toast" role="status"><Icon name="info" /><span>{store.notice}</span><button className="icon-button" aria-label="Đóng thông báo" onClick={()=>store.setNotice('')}><Icon name="close" /></button></div>;
  if(store.initializing)return <div className="auth-loading" role="status"><img src="/logo-L-sculptural.svg" alt="Luma" width="52" height="52" /><p>Đang kiểm tra phiên đăng nhập…</p></div>;
  if(!store.session.authenticated||!user)return <><LoginView key={String(store.session.setupRequired)+String(store.session.setupAuthorized)} session={store.session} error={store.error} login={store.login} setup={store.setup} authorizeSetup={store.authorizeSetup} />{toast}</>;
  if(user.mustChangePassword)return <div className="force-password"><div className="connection-form"><h2>Đổi mật khẩu lần đầu</h2><p className="field-hint">Tài khoản: {user.username}</p><PasswordForm required change={store.changePassword} /><button className="text-button" onClick={()=>void logout()}>Đăng xuất</button></div>{toast}</div>;
  return <div className="app-shell"><a className="skip-link" href="#main-content">Đi đến nội dung</a>
    {menu&&<button className="sidebar-backdrop" aria-label="Đóng menu" onClick={()=>setMenu(false)} />}
    <aside className={`sidebar ${menu?'open':''}`}><div className="brand"><img src="/logo-L-sculptural.svg" width="45" height="52" alt="" /><div><span className="wordmark">LUMA</span><span className="brand-subtitle">Không gian quản lý</span></div><button className="icon-button mobile-close" aria-label="Đóng điều hướng" onClick={()=>setMenu(false)}><Icon name="close" /></button></div>
      <div className="nav-label">Quản lý nội dung</div><nav aria-label="Điều hướng chính">{navigation.filter(item=>!item.admin||user.role==='ADMIN').map(item=><NavLink key={item.path} to={item.path} className={({isActive})=>`nav-item ${isActive?'active':''}`} onClick={e=>{if(unsaved&&pathname!==item.path&&!window.confirm('Nội dung còn thay đổi chưa lưu. Bỏ thay đổi và chuyển trang?'))e.preventDefault();}}><Icon name={item.icon} /><span>{item.label}</span></NavLink>)}</nav>
      <div className="sidebar-bottom"><div className="brand-note"><p>Một chút chăm chút.<br />Một tổ ấm đẹp hơn.</p><span>Luma · Catalog Studio</span></div></div>
    </aside><div className="workspace"><header className="topbar"><div className="breadcrumb"><button className="icon-button mobile-menu" aria-label="Mở menu" aria-expanded={menu} onClick={()=>setMenu(!menu)}><Icon name="menu" /></button><span>Quản lý nội dung</span><span className="breadcrumb-divider">/</span><strong>{title}</strong></div><div className="topbar-right"><a href={import.meta.env.VITE_STOREFRONT_URL||'http://127.0.0.1:5173'} target="_blank" rel="noreferrer" className="storefront-link"><Icon name="external" /><span>Xem website</span></a><div className="user-menu-wrap"><button className="user-menu-trigger" aria-label="Menu tài khoản" aria-expanded={userMenu} onClick={()=>setUserMenu(!userMenu)}><span className="avatar">{user.displayName.slice(0,1).toUpperCase()}</span><span className="user-menu-name">{user.displayName}</span><Icon name="down" /></button>{userMenu&&<div className="user-dropdown"><strong>{user.username}</strong><small>{user.role==='ADMIN'?'Quản trị viên':'Nhân viên nội dung'}</small><button onClick={()=>{setPasswordModal(true);setUserMenu(false);}}><Icon name="key" />Đổi mật khẩu</button><button onClick={()=>void logout()}><Icon name="logout" />Đăng xuất</button></div>}</div></div></header>
      <main id="main-content">{store.error&&<div className="api-error" role="alert"><div><strong>Chưa tải lại được dữ liệu</strong><p>{store.error}</p></div><button className="button" disabled={store.loading||store.mutating} onClick={()=>void store.reload().catch(()=>{})}>{store.loading?'Đang tải…':'Tải lại'}</button></div>}
        {store.data?<><Routes>
          <Route path="/" element={<Navigate replace to="/san-pham" />} />
          <Route path="/dang-nhap" element={<Navigate replace to="/san-pham" />} />
          <Route path="/san-pham" element={<ProductsPage data={store.data} actions={store.actions} />} />
          <Route path="/danh-muc" element={<CategoriesPage data={store.data} actions={store.actions} />} />
          <Route path="/thu-vien-anh" element={<MediaPage data={store.data} actions={store.actions} />} />
          <Route path="/noi-dung" element={<ContentPage key={contentKey} data={store.data} actions={store.actions} onDirtyChange={setUnsaved} />} />
          <Route path="/tai-khoan" element={user.role==='ADMIN'?<AccountsPage key={contentKey} api={store.api} current={user} onProfileChange={store.refreshSession} />:<div className="empty"><h1>Bạn không có quyền quản lý tài khoản</h1><NavLink className="button" to="/san-pham">Về sản phẩm</NavLink></div>} />
          <Route path="*" element={<div className="empty"><h1>Không tìm thấy trang</h1><NavLink className="button primary" to="/san-pham">Về danh sách sản phẩm</NavLink></div>} />
        </Routes><div className="workspace-footer"><span>Dữ liệu từ BE · Đăng nhập với tài khoản {user.username}.</span><button disabled={store.loading||store.mutating} onClick={()=>{if(!unsaved||window.confirm('Tải lại nội dung sẽ bỏ các thay đổi chưa lưu. Tiếp tục?'))void store.reload().then(()=>setContentKey(k=>k+1)).catch(()=>{});}}>{store.loading?'Đang tải…':'Làm mới dữ liệu'}</button></div></>:!store.error&&<p className="empty" role="status">Đang tải dữ liệu cửa hàng…</p>}
      </main>
    </div>{toast}
    {passwordModal&&<Modal title="Đổi mật khẩu của bạn" onClose={()=>setPasswordModal(false)}><div className="modal-body"><PasswordForm change={async(current,next)=>{await store.changePassword(current,next);setPasswordModal(false);}} /></div></Modal>}
  </div>;
}
