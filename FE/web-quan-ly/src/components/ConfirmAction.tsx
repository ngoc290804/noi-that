import { useState } from 'react';
import { Modal } from './Modal';
import { errorMessage } from '../app/useAdminStore';
export function ConfirmAction({ title, description, label, action, onClose, danger=false }: {
  title: string; description: string; label: string; action: () => Promise<void>; onClose: () => void; danger?: boolean;
}) {
  const [pending,setPending]=useState(false);
  const [error,setError]=useState('');
  return <Modal title={title} onClose={()=>{if(!pending)onClose();}}><div className="modal-body"><p>{description}</p>{error&&<p className="form-error" role="alert">{error}</p>}</div><div className="modal-footer"><button className="button" disabled={pending} onClick={onClose}>Hủy</button><button className={`button ${danger?'danger':'primary'}`} disabled={pending} onClick={async()=>{setPending(true);setError('');try{await action();onClose();}catch(error){setError(errorMessage(error));}finally{setPending(false);}}}>{pending?'Đang xử lý…':label}</button></div></Modal>;
}
