import assert from 'node:assert/strict';
import test from 'node:test';
import { getProduct, getRelatedProducts, listProducts } from '../src/services/catalog.ts';
import { createCatalogParams, formatPrice, parseCatalogQuery } from '../src/utils/catalog.ts';

const all = { q: '', category: '', sort: 'featured' };

test('Vietnamese names are searchable without accents and regardless of case', () => {
  assert.deepEqual(listProducts({ ...all, q: '  GHE AN  ' }).items.map((p) => p.slug), ['ghe-an-nordic']);
  assert.deepEqual(listProducts({ ...all, q: 'DEN BAN' }).items.map((p) => p.slug), ['den-ban-horizon']);
});

test('search and category filters combine, including no matches', () => {
  assert.equal(listProducts({ ...all, category: 'gom-su' }).total, 2);
  assert.equal(listProducts({ ...all, category: 'gom-su', q: 'ghe' }).total, 0);
  assert.equal(listProducts({ ...all, q: 'khong co mon nay' }).total, 0);
});

test('price sorts are numeric and do not mutate featured order', () => {
  const prices = (sort) => listProducts({ ...all, sort }).items.map((p) => p.priceVnd);
  assert.deepEqual(prices('price-asc'), [450000, 620000, 1250000, 2900000]);
  assert.deepEqual(prices('price-desc'), [2900000, 1250000, 620000, 450000]);
  assert.deepEqual(prices('featured'), [620000, 2900000, 1250000, 450000]);
});

test('invalid URLs normalize safely and valid filters survive a URL roundtrip', () => {
  assert.deepEqual(parseCatalogQuery(new URLSearchParams('category=invalid&sort=invalid&q=%20ghế%20')), { ...all, q: 'ghế' });
  const query = { q: 'gốm mộc', category: 'gom-su', sort: 'price-asc' };
  assert.deepEqual(parseCatalogQuery(createCatalogParams(query)), query);
  assert.equal(createCatalogParams(all).toString(), '');
  assert.equal(parseCatalogQuery(new URLSearchParams({ q: 'a'.repeat(200) })).q.length, 120);
});

test('product detail resolves known slugs and missing slugs are absent', () => {
  assert.equal(getProduct('ghe-an-nordic')?.priceVnd, 2900000);
  assert.equal(getProduct('san-pham-khong-co'), undefined);
});

test('related products prioritize the same category, fill with other items and exclude self', () => {
  const related = getRelatedProducts('p-binh-gom-luma');
  assert.deepEqual(related.map((p) => p.slug), ['bo-coc-gom-moc', 'ghe-an-nordic', 'den-ban-horizon']);
  assert.equal(new Set(related.map((p) => p.id)).size, related.length);
  assert.ok(related.every((p) => p.id !== 'p-binh-gom-luma'));
  assert.deepEqual(getRelatedProducts('p-ghe-an-nordic').map((p) => p.slug), ['binh-gom-luma', 'den-ban-horizon', 'bo-coc-gom-moc']);
  assert.deepEqual(getRelatedProducts('missing-product'), []);
});

test('reference prices use consistent Vietnamese formatting', () => {
  assert.equal(formatPrice(2900000), '2.900.000đ');
  assert.equal(formatPrice(450000), '450.000đ');
});
