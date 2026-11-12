import type { CatalogQuery, SortOrder } from '../types/catalog';

export function normalizeSearch(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd').toLowerCase().trim().replace(/\s+/g, ' ');
}

const validCategories = new Set(['ban-ghe', 'den', 'gom-su']);
const validSorts = new Set<SortOrder>(['featured', 'price-asc', 'price-desc']);

export function parseCatalogQuery(params: URLSearchParams): CatalogQuery {
  const sort = params.get('sort') as SortOrder;
  const category = params.get('category') ?? '';
  return {
    q: (params.get('q') ?? '').trim().slice(0, 120),
    category: validCategories.has(category) ? category : '',
    sort: validSorts.has(sort) ? sort : 'featured',
  };
}

export function createCatalogParams(query: CatalogQuery): URLSearchParams {
  const params = new URLSearchParams();
  if (query.q) params.set('q', query.q);
  if (query.category) params.set('category', query.category);
  if (query.sort !== 'featured') params.set('sort', query.sort);
  return params;
}

const priceFormatter = new Intl.NumberFormat('vi-VN');
export const formatPrice = (value: number) => `${priceFormatter.format(value)}đ`;
