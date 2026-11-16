import test from 'node:test';
import assert from 'node:assert/strict';
import { createAdminApi } from '../src/services/admin.ts';

test('session requests stay on the admin origin and include the session cookie', async t => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    return Response.json({ authenticated: false, user: null, setupRequired: false, setupAuthorized: false });
  });
  const api = createAdminApi(() => assert.fail('A public session lookup must not expire the session'));
  const result = await api.session();
  assert.equal(result.authenticated, false);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/api/auth/session');
  assert.equal(calls[0].options.credentials, 'include');
  assert.equal(calls[0].options.cache, 'no-store');
});

test('login and logout send cookies and fresh CSRF tokens through the same-origin proxy', async t => {
  const calls = [];
  let token = 0;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({ url, options });
    if (url === '/api/auth/csrf') return Response.json({ token: `token-${++token}`, headerName: 'X-CSRF-TOKEN' });
    if (url === '/api/auth/login') return Response.json({ authenticated: true, user: { username: 'admin' } });
    if (url === '/api/auth/logout') return new Response(null, { status: 204 });
    assert.fail(`Unexpected request: ${url}`);
  });
  const api = createAdminApi(() => assert.fail('Login/logout must not expire an unrelated session'));
  await api.login(' Admin ', 'test-only-password');
  await api.logout();
  assert.deepEqual(calls.map(call => call.url), [
    '/api/auth/csrf', '/api/auth/login', '/api/auth/csrf', '/api/auth/logout',
  ]);
  for (const { options } of calls) assert.equal(options.credentials, 'include');
  const login = calls[1].options;
  assert.equal(login.method, 'POST');
  assert.equal(login.headers['X-CSRF-TOKEN'], 'token-1');
  assert.ok(login.body instanceof URLSearchParams);
  assert.equal(login.body.get('username'), 'admin');
  assert.equal(calls[3].options.headers['X-CSRF-TOKEN'], 'token-2');
});
