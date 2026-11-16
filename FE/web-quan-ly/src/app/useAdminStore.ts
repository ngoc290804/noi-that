import { useEffect, useRef, useState } from 'react';
import type { AdminActions, AdminData, SessionInfo } from '../types/admin';
import { ApiError, createAdminApi } from '../services/admin';

export const errorMessage = (error: unknown) => error instanceof Error ? error.message : 'Thao tác chưa hoàn tất. Vui lòng thử lại.';
export function useAdminStore() {
  const [data, setData] = useState<AdminData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [mutating,setMutating]=useState(false);
  const [session,setSession]=useState<SessionInfo>({authenticated:false,user:null,setupRequired:false,setupAuthorized:false});
  const [initializing,setInitializing]=useState(true);
  const stale = useRef(false);
  const writing = useRef(false);
  const mounted = useRef(true);
  const [api]=useState(()=>createAdminApi(()=>{setData(null);setSession(previous=>({...previous,authenticated:false,user:null}));setError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');}));
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(()=>{
    const controller=new AbortController();
    async function initialize(){
      try {const current=await api.session(controller.signal);if(controller.signal.aborted)return;setSession(current);if(current.authenticated&&!current.user?.mustChangePassword){const next=await api.catalog(controller.signal);if(!controller.signal.aborted)setData(next);}}
      catch(error){if(!controller.signal.aborted)setError(errorMessage(error));}
      finally{if(!controller.signal.aborted)setInitializing(false);}
    }
    void initialize();return()=>controller.abort();
  },[api]);
  async function reload() {
    setLoading(true);
    try { const next = await api.catalog(); if (mounted.current) { setData(next); setError(''); stale.current = false; } }
    catch (error) { if (mounted.current) setError(errorMessage(error)); throw error; }
    finally { if (mounted.current) setLoading(false); }
  }
  async function login(username:string,password:string){const current=await api.login(username,password);setSession(current);setError('');if(!current.user?.mustChangePassword)await reload();}
  async function setup(displayName:string,password:string){await api.setup(displayName,password);await login('admin',password);}
  async function authorizeSetup(token:string){setSession(await api.authorizeSetup(token));}
  async function logout(){await api.logout();setData(null);setSession({authenticated:false,user:null,setupRequired:false,setupAuthorized:false});setError('');}
  async function changePassword(current:string,next:string){await api.changePassword(current,next);setData(null);setSession({authenticated:false,user:null,setupRequired:false,setupAuthorized:false});setNotice('Đã đổi mật khẩu. Vui lòng đăng nhập lại.');}
  async function refreshSession(){setSession(await api.session());}
  async function mutate<T>(operation: () => Promise<T>, message: string) {
    if (writing.current) throw new Error('Đang xử lý một thao tác khác. Vui lòng chờ.');
    if (stale.current) throw new Error('Tải lại dữ liệu từ BE trước khi tiếp tục chỉnh sửa.');
    writing.current = true; setMutating(true);
    try {
      const result = await operation();
      setNotice(message);
      try { await reload(); }
      catch { stale.current = true; setError('Thay đổi đã được BE xác nhận, nhưng chưa tải lại được dữ liệu. Bấm Tải lại trước khi tiếp tục chỉnh sửa.'); }
      return result;
    } catch (error) {
      // An interrupted write may have committed. Require reconciliation before retry.
      if (error instanceof ApiError && error.status === 0) stale.current = true;
      if (stale.current) setError('Dữ liệu cần được tải lại để đối chiếu trước thao tác tiếp theo. Biểu mẫu đang nhập vẫn được giữ.');
      throw error;
    } finally { writing.current = false; if(mounted.current)setMutating(false); }
  }
  const actions: AdminActions = {
    saveProduct: (p, create) => mutate(() => api.saveProduct(p, create), 'Đã lưu sản phẩm trên BE.').then(() => undefined),
    deleteProduct: p => mutate(() => api.deleteProduct(p), 'Đã xóa sản phẩm khỏi danh sách và website.').then(() => undefined),
    archiveProducts: p => mutate(() => api.archiveProducts(p), 'Đã lưu trữ các sản phẩm đã chọn.').then(() => undefined),
    saveCategory: (c, create) => mutate(() => api.saveCategory(c, create), 'Đã lưu danh mục trên BE.').then(() => undefined),
    deleteCategory: c => mutate(() => api.deleteCategory(c), 'Đã xóa danh mục.').then(() => undefined),
    saveContent: async (c, version) => (await mutate(() => api.saveContent(c, version), 'Đã cập nhật nội dung website.')).version,
    uploadImage: (file, alt) => mutate(() => api.uploadImage(file, alt), 'Đã tải ảnh lên Cloudinary và lưu thư viện.').then(() => undefined),
    saveImage: a => mutate(() => api.saveImage(a), 'Đã cập nhật mô tả ảnh và các nơi sử dụng.').then(() => undefined),
    deleteImage: a => mutate(() => api.deleteImage(a), 'Đã gỡ ảnh khỏi thư viện.').then(() => undefined),
  };
  return { data, actions, api, session, initializing, loading, mutating, error, notice, setNotice, reload, login, logout, setup, authorizeSetup, changePassword, refreshSession };
}
