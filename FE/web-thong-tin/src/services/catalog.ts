import type { CatalogQuery, Category, HomeContent, HomeHero, HomeSpace, Product, ProductPage } from '../types/catalog.ts';

type CategoryStorage = Pick<Storage, 'getItem' | 'setItem'>;
const pageStartedAt = globalThis.performance.timeOrigin;

function getSessionStorage(): CategoryStorage | undefined {
  try { return globalThis.sessionStorage; } catch { return undefined; }
}

function waitForCategories(request: Promise<Category[]>, signal?: AbortSignal): Promise<Category[]> {
  if (!signal) return request;
  return new Promise((resolve, reject) => {
    const onAbort = () => { signal.removeEventListener('abort', onAbort); reject(signal.reason); };
    signal.addEventListener('abort', onAbort, { once: true });
    request.then(
      (categories) => { signal.removeEventListener('abort', onAbort); resolve(categories); },
      (error: unknown) => { signal.removeEventListener('abort', onAbort); reject(error); },
    );
    if (signal.aborted) onAbort();
  });
}

export class CatalogApiError extends Error {
  status: number;
  constructor(status: number) {
    super('Không thể tải dữ liệu. Vui lòng thử lại.');
    this.name = 'CatalogApiError';
    this.status = status;
  }
}

// The factory lets tests verify the HTTP contract without replacing production data.
export function createCatalogApi(baseUrl = '/api', fetcher: typeof fetch = globalThis.fetch, storage = getSessionStorage()) {
  const base = baseUrl.replace(/\/+$/, '');
  const categoriesKey = `luma:categories:v1:${base}`;
  let categories: Category[] | undefined;
  let categoriesRequest: Promise<Category[]> | undefined;

  try {
    const stored = JSON.parse(storage?.getItem(categoriesKey) ?? 'null');
    // Refresh once per full page load, then reuse across SPA navigation.
    if (stored?.pageStartedAt === pageStartedAt && Array.isArray(stored.items)
      && stored.items.every((item: unknown) => item !== null && typeof item === 'object'
        && 'id' in item && typeof item.id === 'string'
        && 'slug' in item && typeof item.slug === 'string'
        && 'name' in item && typeof item.name === 'string')) categories = stored.items;
  } catch { /* Unavailable or invalid storage falls back to the API. */ }

  async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
    const timeout = AbortSignal.timeout(35000);
    const response = await fetcher(`${base}${path}`, {
      headers: { Accept: 'application/json' },
      signal: signal ? AbortSignal.any([signal, timeout]) : timeout,
    });
    if (!response.ok) throw new CatalogApiError(response.status);
    // A misconfigured SPA proxy can return index.html with status 200.
    if (!response.headers.get('content-type')?.includes('application/json')) throw new CatalogApiError(502);
    return response.json() as Promise<T>;
  }
  function listProducts(query: CatalogQuery, signal?: AbortSignal, size = 24) {
    const params = new URLSearchParams({ q: query.q, category: query.category, sort: query.sort, page: String(query.page ?? 0), size: String(size) });
    return get<ProductPage>(`/products?${params}`, signal);
  }
  async function listCategories(signal?: AbortSignal): Promise<Category[]> {
    signal?.throwIfAborted();
    if (categories) return categories;
    if (!categoriesRequest) {
      // This request belongs to the page; one component leaving must not cancel it for others.
      categoriesRequest = get<Category[]>('/categories').then((items) => {
        categories = items;
        try { storage?.setItem(categoriesKey, JSON.stringify({ pageStartedAt, items })); } catch { /* Keep the memory cache. */ }
        return items;
      }).finally(() => { categoriesRequest = undefined; });
    }
    return waitForCategories(categoriesRequest, signal);
  }
  const getHomeContent = (signal?: AbortSignal) => get<HomeContent>('/home', signal);
  const getHomeHero = (signal?: AbortSignal) => get<HomeHero>('/home/hero', signal);
  const getHomeFeaturedProducts = (signal?: AbortSignal) => get<Product[]>('/home/featured-products', signal);
  const getHomeSpaces = (signal?: AbortSignal) => get<HomeSpace[]>('/home/spaces', signal);
  const getProduct = (slug: string, signal?: AbortSignal) => get<Product>(`/products/${encodeURIComponent(slug)}`, signal);

  async function getFeaturedProducts(ids: string[], signal?: AbortSignal) {
    if (!ids.length) return [];
    const wanted = new Set(ids);
    const found = new Map<string, Product>();
    let page = 0;
    while (true) {
      const result = await listProducts({ q: '', category: '', sort: 'featured', page }, signal, 100);
      for (const product of result.items) if (wanted.has(product.id)) found.set(product.id, product);
      if (found.size === wanted.size || !result.items.length || (page + 1) * result.size >= result.total) break;
      page++;
    }
    return ids.flatMap((id) => found.has(id) ? [found.get(id)!] : []);
  }

  async function getRelatedProducts(product: Product, signal?: AbortSignal) {
    const query: CatalogQuery = { q: '', category: '', sort: 'featured' };
    const [sameCategory, others] = await Promise.all([
      listProducts({ ...query, category: product.categoryId }, signal, 9),
      listProducts(query, signal, 9),
    ]);
    const unique = new Map<string, Product>();
    for (const item of [...sameCategory.items, ...others.items]) {
      if (item.id !== product.id && !unique.has(item.id)) unique.set(item.id, item);
    }
    return [...unique.values()].slice(0, 8);
  }
  return { listProducts, listCategories, getHomeContent, getHomeHero, getHomeFeaturedProducts, getHomeSpaces, getProduct, getFeaturedProducts, getRelatedProducts };
}

export const catalogApi = createCatalogApi(import.meta.env?.VITE_API_BASE_URL || '/api');
