import type { Account, AccountInput, AdminData, Category, CreateAccountInput, HomeContent, ImageAsset, Product, SessionInfo } from '../types/admin';
export class ApiError extends Error {
  constructor(message: string, public status: number, public code?: string) { super(message); this.name = 'ApiError'; }
}
const apiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
export function createAdminApi(onExpired: () => void) {
  let csrf: Promise<{ token: string; headerName: string }> | undefined;
  async function request<T>(path: string, method='GET', body?: unknown, signal?: AbortSignal, extraHeaders?: Record<string,string>): Promise<T> {
    const controller=new AbortController();const abort=()=>controller.abort();
    signal?.addEventListener('abort',abort,{once:true});if(signal?.aborted)controller.abort();
    const timer=setTimeout(abort,45000);
    try {
      let protection: {token:string;headerName:string}|undefined;
      if(!['GET','HEAD'].includes(method)) {
        csrf ??= request('/auth/csrf');
        try { protection=await csrf; } catch(error) {csrf=undefined;throw error;}
      }
      const form=body instanceof FormData||body instanceof URLSearchParams;
      const response=await fetch(`${apiBase}${path}`,{method,credentials:'include',signal:controller.signal,cache:'no-store',
        headers:{...extraHeaders,...(protection?{[protection.headerName]:protection.token}:{}),...(body!==undefined&&!form?{'Content-Type':'application/json'}:{})},
        body:body===undefined?undefined:form?body:JSON.stringify(body)});
      if(!response.ok) {
        const problem=await response.json().catch(()=>({}));
        if(problem.code==='CSRF_INVALID')csrf=undefined;
        if(response.status===401&&path!=='/auth/login'&&path!=='/auth/setup-session') {csrf=undefined;onExpired();}
        const fields=problem.errors?Object.values(problem.errors).join(' · '):'';
        throw new ApiError(fields||problem.detail||`Yêu cầu thất bại (${response.status}).`,response.status,problem.code);
      }
      return response.status===204?undefined as T:await response.json();
    } catch(error) {
      if(error instanceof ApiError||signal?.aborted)throw error;
      if(controller.signal.aborted)throw new ApiError(method==='GET'?'Máy chủ phản hồi quá lâu. Vui lòng thử lại.':'Chưa xác định kết quả do quá thời gian. Tải lại dữ liệu để kiểm tra trước khi thử lại.',0);
      throw new ApiError(method==='GET'?'Không kết nối được BE. Vui lòng thử lại.':'Mất kết nối khi ghi dữ liệu. Tải lại để xác minh trước khi thử lại.',0);
    } finally {clearTimeout(timer);signal?.removeEventListener('abort',abort);}
  }
  return {
    session:(signal?:AbortSignal)=>request<SessionInfo>('/auth/session','GET',undefined,signal),
    async login(username:string,password:string) {
      const form=new URLSearchParams({username:username.trim().toLowerCase(),password});
      const result=await request<SessionInfo>('/auth/login','POST',form);csrf=undefined;return result;
    },
    async logout(){await request('/auth/logout','POST');csrf=undefined;},
    async authorizeSetup(token:string){
      await request<void>('/auth/setup-session','POST',undefined,undefined,{Authorization:`Bearer ${token}`});
      return request<SessionInfo>('/auth/session');
    },
    setup:(displayName:string,password:string)=>request<Account>('/auth/setup','POST',{displayName,password}),
    async changePassword(currentPassword:string,newPassword:string){await request('/auth/password','POST',{currentPassword,newPassword});csrf=undefined;},
    async catalog(signal?:AbortSignal):Promise<AdminData> {
      const data=await request<AdminData>('/admin/catalog','GET',undefined,signal);
      const lookup=new Map(data.media.map(asset=>[asset.src,asset]));const resolve=(asset:ImageAsset)=>lookup.get(asset.src)||asset;
      return {...data,content:{...data.content,heroImage:resolve(data.content.heroImage),spaces:data.content.spaces.map(s=>({...s,image:resolve(s.image)}))}};
    },
    saveProduct:(p:Product,create:boolean)=>request<Product>(create?'/admin/products':`/admin/products/${p.id}`,create?'POST':'PUT',p),
    deleteProduct:(p:Product)=>request<void>(`/admin/products/${p.id}?version=${p.version}`,'DELETE'),
    archiveProducts:(products:Product[])=>request('/admin/products/archive','POST',{products:products.map(({id,version})=>({id,version}))}),
    saveCategory:(c:Category,create:boolean)=>request<Category>(create?'/admin/categories':`/admin/categories/${c.id}`,create?'POST':'PUT',c),
    deleteCategory:(c:Category)=>request<void>(`/admin/categories/${c.id}?version=${c.version}`,'DELETE'),
    saveContent:(content:HomeContent,version:number)=>request<{content:HomeContent;version:number}>('/admin/content','PUT',{...content,version}),
    uploadImage:(file:File,alt:string)=>{const body=new FormData();body.set('file',file);body.set('alt',alt);return request('/admin/media/images','POST',body);},
    saveImage:(a:ImageAsset)=>request(`/admin/media/${a.id}`,'PUT',{alt:a.alt,version:a.version}),
    deleteImage:(a:ImageAsset)=>request<void>(`/admin/media/${a.id}?version=${a.version}`,'DELETE'),
    accounts:(signal?:AbortSignal)=>request<Account[]>('/admin/accounts','GET',undefined,signal),
    createAccount:(input:CreateAccountInput)=>request<Account>('/admin/accounts','POST',input),
    updateAccount:(id:string,input:AccountInput)=>request<Account>(`/admin/accounts/${id}`,'PUT',input),
    resetPassword:(id:string,password:string,version:number)=>request<void>(`/admin/accounts/${id}/password`,'PUT',{password,version}),
    deleteAccount:(a:Account)=>request<void>(`/admin/accounts/${a.id}?version=${a.version}`,'DELETE'),
  };
}
export type AdminApi = ReturnType<typeof createAdminApi>;
