export function CatalogStatus({ error, retry }: { error?: unknown; retry: () => void }) {
  return <div className="container data-status" role={error ? 'alert' : 'status'} aria-live="polite">
    {error ? <><h2>Chưa thể tải dữ liệu</h2><p>Kết nối tạm thời gián đoạn. Vui lòng thử lại.</p><button className="button button-primary" onClick={retry}>Thử lại</button></> : <p>Đang tải dữ liệu…</p>}
  </div>;
}
