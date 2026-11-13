import assert from 'node:assert/strict';
import test from 'node:test';
import { createCatalogApi } from '../src/services/catalog.ts';
import { abortReason, throwIfAborted } from '../src/utils/abort.ts';

test('home, categories and products load without newer AbortSignal APIs', async (t) => {
  for (const [target, name] of [[AbortSignal, 'any'], [AbortSignal, 'timeout'], [AbortSignal.prototype, 'throwIfAborted']]) {
    const descriptor = Object.getOwnPropertyDescriptor(target, name);
    Object.defineProperty(target, name, { ...descriptor, value: undefined });
    t.after(() => Object.defineProperty(target, name, descriptor));
  }
  const calls = [];
  const api = createCatalogApi('/api', async (url, { signal }) => {
    calls.push(url);
    assert.equal(signal.aborted, false);
    return Response.json([]);
  });
  const signal = new AbortController().signal;
  await Promise.all([
    api.getHomeHero(signal), api.getHomeFeaturedProducts(signal), api.getHomeSpaces(signal),
    api.listCategories(signal), api.getProductSummary('chair', signal),
    api.getProductImages('chair', signal), api.getProductDetails('chair', signal), api.getRelatedProducts('chair', signal),
  ]);
  assert.equal(calls.length, 8);
});

test('already aborted requests never reach the network', async () => {
  const controller = new AbortController();
  controller.abort();
  const api = createCatalogApi('/api', () => assert.fail('Must not fetch'));
  await assert.rejects(api.getHomeHero(controller.signal), { name: 'AbortError' });
  await assert.rejects(api.listCategories(controller.signal), { name: 'AbortError' });
});

test('cancellation without AbortSignal.reason still has an AbortError', async () => {
  const oldSignal = { aborted: true };
  assert.equal(abortReason(oldSignal).name, 'AbortError');
  assert.throws(() => throwIfAborted(oldSignal), { name: 'AbortError' });

  const controller = new AbortController();
  Object.defineProperty(controller.signal, 'reason', { value: undefined });
  const api = createCatalogApi('/api', async (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true });
  }));
  const request = api.getHomeHero(controller.signal);
  controller.abort();
  await assert.rejects(request, { name: 'AbortError' });
});

test('request times out after 35 seconds without AbortSignal.timeout', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const api = createCatalogApi('/api', async (_url, { signal }) => new Promise((_resolve, reject) => {
    signal.addEventListener('abort', () => reject(signal.reason), { once: true });
  }));
  const request = api.getHomeHero();
  const rejected = assert.rejects(request, { name: 'TimeoutError' });
  t.mock.timers.tick(35000);
  await rejected;
});

test('timeout also covers a response whose JSON body stalls', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let startedBody = false;
  const api = createCatalogApi('/api', async (_url, { signal }) => ({
    ok: true,
    headers: new Headers({ 'Content-Type': 'application/json' }),
    json: () => {
      startedBody = true;
      return new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(signal.reason), { once: true }));
    },
  }));
  const request = api.getHomeHero();
  const rejected = assert.rejects(request, { name: 'TimeoutError' });
  await Promise.resolve();
  assert.equal(startedBody, true);
  t.mock.timers.tick(35000);
  await rejected;
});

test('completed and failed requests release timers and caller listeners', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  for (const result of ['success', 'http', 'network', 'invalid-json']) {
    const controller = new AbortController();
    const remove = t.mock.method(controller.signal, 'removeEventListener');
    let requestSignal;
    const api = createCatalogApi('/api', async (_url, { signal }) => {
      requestSignal = signal;
      if (result === 'network') throw new TypeError('Offline');
      if (result === 'invalid-json') return new Response('{', { headers: { 'Content-Type': 'application/json' } });
      return Response.json([], { status: result === 'http' ? 503 : 200 });
    });
    if (result === 'success') assert.deepEqual(await api.getHomeHero(controller.signal), []);
    else await assert.rejects(api.getHomeHero(controller.signal));
    assert.equal(remove.mock.callCount(), 1);
    t.mock.timers.tick(35000);
    controller.abort();
    assert.equal(requestSignal.aborted, false, result);
  }
});
