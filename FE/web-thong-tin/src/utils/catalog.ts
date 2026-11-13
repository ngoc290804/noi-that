import type { CatalogQuery, SortOrder } from '../types/catalog';

export function normalizeSearch(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd').toLowerCase().trim().replace(/\s+/g, ' ');
}

const validSorts = new Set<SortOrder>(['featured', 'price-asc', 'price-desc']);

export function parseCatalogQuery(params: URLSearchParams): CatalogQuery {
  const sort = params.get('sort') as SortOrder;
  const category = params.get('category') ?? '';
  const page = Number(params.get('page'));
  return {
    q: (params.get('q') ?? '').trim().slice(0, 120),
    category: /^[a-zA-Z0-9-]{1,100}$/.test(category) ? category : '',
    sort: validSorts.has(sort) ? sort : 'featured',
    ...(Number.isInteger(page) && page > 0 && page <= 2147483647 ? { page } : {}),
  };
}

export function createCatalogParams(query: CatalogQuery): URLSearchParams {
  const params = new URLSearchParams();
  if (query.q) params.set('q', query.q);
  if (query.category) params.set('category', query.category);
  if (query.sort !== 'featured') params.set('sort', query.sort);
  if (query.page) params.set('page', String(query.page));
  return params;
}

const priceFormatter = new Intl.NumberFormat('vi-VN');
export const formatPrice = (value: number) => `${priceFormatter.format(value)}đ`;

// Typed database attributes take precedence over legacy placeholder specifications.
export function getSpecifications(product: import('../types/catalog').ProductDetails) {
  const specifications = product.specifications.map((item) => ({ ...item }));
  function set(label: string, value: string | null | undefined, unit?: string) {
    if (value == null || value === '') return;
    const index = specifications.findIndex((item) => item.label === label);
    const entry = { label, value, unit };
    if (index < 0) specifications.push(entry);
    else specifications[index] = entry;
  }
  set('Chất liệu', product.material);
  set('Màu sắc', product.color);
  const dimensions = [product.lengthCm, product.widthCm, product.heightCm];
  const labels = ['Dài', 'Rộng', 'Cao'];
  const dimensionText = dimensions.flatMap((value, index) => value == null ? [] : [`${labels[index]} ${new Intl.NumberFormat('vi-VN').format(value)} cm`]);
  set('Kích thước', dimensionText.length ? dimensionText.join(' × ') : null);
  return specifications;
}
