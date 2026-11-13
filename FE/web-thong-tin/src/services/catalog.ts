import type { CatalogQuery, Category, HomeContent, HomeHero, HomeSpace, Product, ProductDetails, ProductImage, ProductPage, ProductPreview, ProductSummary } from '../types/catalog.ts';
import { abortReason, throwIfAborted } from '../utils/abort.ts';

type CategoryStorage = Pick<Storage, 'getItem' | 'setItem'>;
const pageStartedAt = globalThis.performance.timeOrigin;

function getSessionStorage(): CategoryStorage | undefined {
  try { return globalThis.sessionStorage; } catch { return undefined; }
}

function waitForCategories(request: Promise<Category[]>, signal?: AbortSignal): Promise<Category[]> {
  if (!signal) return request;
  return new Promise((resolve, reject) => {
    const onAbort = () => { signal.removeEventListener('abort', onAbort); reject(abortReason(signal)); };
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
    throwIfAborted(signal);
    // AbortSignal.any()/timeout() are unavailable on older iPhones.
    const controller = new AbortController();
    const onAbort = () => controller.abort(signal && abortReason(signal));
    signal?.addEventListener('abort', onAbort, { once: true });
    const timeout = globalThis.setTimeout(() => controller.abort(new DOMException('The request timed out.', 'TimeoutError')), 35000);
    try {
      const response = await fetcher(`${base}${path}`, {
        headers: { Accept: 'application/json' },
        signal: controller.signal,
      });
      if (!response.ok) throw new CatalogApiError(response.status);
      // A misconfigured SPA proxy can return index.html with status 200.
      if (!response.headers.get('content-type')?.includes('application/json')) throw new CatalogApiError(502);
      // Keep the timeout active until the response body has finished loading.
      return await response.json() as T;
    } finally {
      globalThis.clearTimeout(timeout);
      signal?.removeEventListener('abort', onAbort);
    }
  }
  function listProducts(query: CatalogQuery, signal?: AbortSignal, size = 24) {
    const params = new URLSearchParams({ q: query.q, category: query.category, sort: query.sort, page: String(query.page ?? 0), size: String(size) });
    return get<ProductPage>(`/products?${params}`, signal);
  }
  async function listCategories(signal?: AbortSignal): Promise<Category[]> {
    throwIfAborted(signal);
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
  const getProductSummary = (slug: string, signal?: AbortSignal) => get<ProductSummary>(`/products/${encodeURIComponent(slug)}/summary`, signal);
  const getProductImages = (slug: string, signal?: AbortSignal) => get<ProductImage[]>(`/products/${encodeURIComponent(slug)}/images`, signal);
  const getProductDetails = (slug: string, signal?: AbortSignal) => get<ProductDetails>(`/products/${encodeURIComponent(slug)}/details`, signal);
  const getRelatedProducts = async (slug: string, signal?: AbortSignal) =>
    (await get<ProductPreview[]>(`/products/${encodeURIComponent(slug)}/related?size=4`, signal)).slice(0, 4);

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

  return { listProducts, listCategories, getHomeContent, getHomeHero, getHomeFeaturedProducts, getHomeSpaces, getProduct, getProductSummary, getProductImages, getProductDetails, getFeaturedProducts, getRelatedProducts };
}

export const catalogApi = createCatalogApi(import.meta.env?.VITE_API_BASE_URL || '/api');
