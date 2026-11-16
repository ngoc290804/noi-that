import { useState } from 'react';
import type { SessionInfo } from '../types/admin';
import { errorMessage } from '../app/useAdminStore';
import { Icon } from './Icon';
export function passwordIssue(password:string) {
  if(password.length<6||new TextEncoder().encode(password).length>72||!password.trim())return 'Mật khẩu cần ít nhất 6 ký tự và tối đa 72 byte UTF-8.';
  return '';
}
export function LoginView({session,error,login,setup,authorizeSetup}:{session:SessionInfo;error:string;login:(username:string,password:string)=>Promise<void>;setup:(name:string,password:string)=>Promise<void>;authorizeSetup:(token:string)=>Promise<void>}) {
  const [username,setUsername]=useState('');const [password,setPassword]=useState('');const [confirm,setConfirm]=useState('');
  const [name,setName]=useState('Quản trị viên');const [token,setToken]=useState('');const [pending,setPending]=useState(false);const [issue,setIssue]=useState('');const [visible,setVisible]=useState(false);
  const initializing=session.setupRequired;const owner=initializing&&!session.setupAuthorized;
  return <div className="auth-layout"><aside className="auth-intro"><div className="auth-brand"><img src="/logo-L-sculptural.svg" alt="" /><span>LUMA</span></div><div><h1>Chăm chút từng chi tiết.<br />Vun đắp một tổ ấm.</h1><p>Không gian quản lý sản phẩm, hình ảnh và nội dung của Luma.</p></div><small>Luma · Catalog Studio</small></aside><main className="auth-main"><form className="connection-form form-stack" onSubmit={async event=>{
    event.preventDefault();if(pending)return;setIssue('');
    if(initializing&&!owner){const problem=passwordIssue(password);if(problem){setIssue(problem);return;}if(password!==confirm){setIssue('Mật khẩu xác nhận chưa khớp.');return;}}
    setPending(true);try{if(owner){await authorizeSetup(token);setToken('');}else if(initializing)await setup(name.trim(),password);else await login(username,password);}catch(error){setIssue(errorMessage(error));}finally{setPending(false);}
  }}><span className="connection-mark"><Icon name="users" width="25" height="25" /></span><h2>{owner?'Xác nhận khởi tạo':initializing?'Tạo tài khoản admin':'Đăng nhập quản lý'}</h2><p className="muted">{owner?'Xác nhận quyền chủ cửa hàng để tạo quản trị viên đầu tiên.':initializing?'Tự đặt mật khẩu cho tài khoản quản trị đầu tiên của cửa hàng.':'Đăng nhập bằng tài khoản được quản trị viên cấp.'}</p><fieldset disabled={pending}>
    {owner?<label>Mã khởi tạo<input autoFocus type="password" autoComplete="off" required minLength={32} value={token} onChange={e=>setToken(e.target.value)} /><span className="field-hint">Mã chỉ dùng để khởi tạo admin lần đầu.</span></label>:<>
      <label>Tên đăng nhập<input autoFocus={!initializing} required autoComplete="username" maxLength={64} value={initializing?'admin':username} readOnly={initializing} onChange={e=>setUsername(e.target.value)} /></label>
      {initializing&&<label>Tên hiển thị<input autoFocus required maxLength={100} value={name} onChange={e=>setName(e.target.value)} /></label>}
      <label>Mật khẩu<input required type={visible?'text':'password'} autoComplete={initializing?'new-password':'current-password'} minLength={initializing?6:undefined} maxLength={72} value={password} onChange={e=>setPassword(e.target.value)} /></label>
      {initializing&&<label>Xác nhận mật khẩu<input required type={visible?'text':'password'} autoComplete="new-password" minLength={6} maxLength={72} value={confirm} onChange={e=>setConfirm(e.target.value)} /></label>}
      <label className="check-label"><input type="checkbox" checked={visible} onChange={e=>setVisible(e.target.checked)} />Hiện mật khẩu</label>
      {initializing&&<p className="field-hint">Từ 6 ký tự, tối đa 72 byte UTF-8. Nên dùng một cụm từ dài, riêng cho tài khoản này.</p>}
    </>}
    {(issue||error)&&<p className="form-error" role="alert">{issue||error}</p>}<button className="button primary full" disabled={pending}>{pending?'Đang xử lý…':owner?'Xác nhận quyền khởi tạo':initializing?'Tạo admin & đăng nhập':'Đăng nhập'}<Icon name="right" /></button></fieldset><p className="auth-caption">{initializing?'Chỉ khởi tạo một lần. Dữ liệu cửa hàng được giữ nguyên.':'Phiên đăng nhập được bảo vệ và tự hết hạn khi không hoạt động.'}</p></form></main></div>;
}
export function PasswordForm({change,required=false}:{change:(current:string,next:string)=>Promise<void>;required?:boolean}) {
  const [current,setCurrent]=useState('');const [next,setNext]=useState('');const [confirm,setConfirm]=useState('');const [pending,setPending]=useState(false);const [error,setError]=useState('');
  return <form className="form-stack password-form" onSubmit={async e=>{e.preventDefault();if(pending)return;const issue=passwordIssue(next);if(issue){setError(issue);return;}if(next!==confirm){setError('Mật khẩu xác nhận chưa khớp.');return;}setPending(true);setError('');try{await change(current,next);}catch(error){setError(errorMessage(error));}finally{setPending(false);}}}><p className="muted">{required?'Đặt mật khẩu riêng trước khi truy cập dữ liệu cửa hàng.':'Sau khi đổi, các phiên đăng nhập cũ sẽ kết thúc. Bạn cần đăng nhập lại.'}</p><fieldset disabled={pending}><label>Mật khẩu hiện tại<input autoFocus required type="password" autoComplete="current-password" maxLength={72} value={current} onChange={e=>setCurrent(e.target.value)} /></label><label>Mật khẩu mới<input required type="password" autoComplete="new-password" minLength={6} maxLength={72} value={next} onChange={e=>setNext(e.target.value)} /></label><label>Xác nhận mật khẩu mới<input required type="password" autoComplete="new-password" minLength={6} maxLength={72} value={confirm} onChange={e=>setConfirm(e.target.value)} /></label><p className="field-hint">Từ 6 ký tự, tối đa 72 byte UTF-8.</p>{error&&<p className="form-error" role="alert">{error}</p>}<button className="button primary full" disabled={pending}>{pending?'Đang lưu…':'Đổi mật khẩu'}</button></fieldset></form>;
}
