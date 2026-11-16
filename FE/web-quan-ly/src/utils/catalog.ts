export const money = (value: number) => `${new Intl.NumberFormat('vi-VN').format(value)} đ`;
export const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim().replace(/\s+/g, ' ');
export const slugify = (value: string) => normalize(value).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
export const statusLabels = { published: 'Đang hiển thị', draft: 'Bản nháp', archived: 'Đã lưu trữ' };

export function exportCsv(rows: (string | number)[][]) {
  const csv = '\ufeff' + rows.map(row => row.map(value => {
    const cell = String(value);
    return `"${(/^[=+@\-\t\r]/.test(cell) ? "'" + cell : cell).replace(/"/g, '""')}"`;
  }).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'luma-san-pham.csv';
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
