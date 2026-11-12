import { categories, homeContent, products } from '../data/catalog.ts';
import type { CatalogQuery } from '../types/catalog';
import { normalizeSearch } from '../utils/catalog.ts';

// Local read adapter. Replace its implementation with the public API when BE is needed.
export function listProducts(query: CatalogQuery) {
  const needle = normalizeSearch(query.q);
  const items = products.filter((product) =>
    (!query.category || product.categoryId === query.category)
    && (!needle || normalizeSearch(product.name).includes(needle)),
  ).sort((a, b) => {
    if (query.sort === 'price-asc') return a.priceVnd - b.priceVnd;
    if (query.sort === 'price-desc') return b.priceVnd - a.priceVnd;
    return a.displayOrder - b.displayOrder;
  });
  return { items, total: items.length };
}

export const getProduct = (slug: string) => products.find((product) => product.slug === slug);
export const listCategories = () => categories;
export const getHomeContent = () => homeContent;
export const getFeaturedProducts = () => products.filter((product) => homeContent.featuredProductIds.includes(product.id));
export const getRelatedProducts = (id: string) => {
  const product = products.find((item) => item.id === id);
  return products.filter((item) => item.id !== id && item.categoryId === product?.categoryId);
};
