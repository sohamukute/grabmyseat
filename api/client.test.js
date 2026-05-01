import assert from 'node:assert/strict';
import test from 'node:test';
import { createGatewayClient } from './client.js';

function storage() {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

function client(fetch) {
  return createGatewayClient({ baseUrl: '/api', fetch, storage: storage() });
}

test('returns JSON responses and sends the stored bearer token', async () => {
  let request;
  const api = client(async (url, options) => {
    request = { url, options };
    return url.endsWith('/auth/login')
      ? Response.json({ accessToken: 'access-token', refreshToken: 'refresh-token', tokenType: 'Bearer', expiresInSeconds: 60 })
      : Response.json([{ id: 1 }]);
  });
  await api.session.login('ada', 'secret');
  const result = await api.events.list();

  assert.deepEqual(result, { ok: true, status: 200, data: [{ id: 1 }] });
  assert.equal(request.url, '/api/inventory/events');
  assert.equal(request.options.headers.Authorization, 'Bearer access-token');
});

test('returns a safe error when a successful response is not JSON', async () => {
  const result = await client(async () => new Response('upstream html', { status: 200 })).events.list();

  assert.deepEqual(result, { ok: false, error: { kind: 'invalid-response', message: 'The service returned an unexpected response.', status: 200 } });
});

test('returns a safe session-expired error for unauthorized responses', async () => {
  const result = await client(async () => Response.json({ detail: 'internal detail' }, { status: 401 })).wallet.balance();

  assert.deepEqual(result, { ok: false, error: { kind: 'unauthorized', message: 'Your session has expired. Please sign in again.', status: 401 } });
});

test('returns a safe availability error for conflicts', async () => {
  const result = await client(async () => Response.json({ detail: 'internal detail' }, { status: 409 })).reservations.create({ eventId: 1, zoneId: 2, quantity: 1 });

  assert.deepEqual(result, { ok: false, error: { kind: 'conflict', message: 'That ticket selection is no longer available. Please choose again.', status: 409 } });
});

test('preserves gateway field errors when an organizer submits an event', async () => {
  let request;
  const result = await client(async (url, options) => {
    request = { url, options };
    return Response.json({
      message: 'Invalid event',
      fieldErrors: { zones: 'Use STANDING or SEATED' },
    }, { status: 400 });
  }).organizer.submitEvent({ zones: [] });

  assert.equal(request.url, '/api/inventory/events');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.body, JSON.stringify({ zones: [] }));
  assert.equal(result.ok, false);
  assert.equal(result.error.status, 400);
  assert.equal(result.error.message, 'Invalid event');
  assert.deepEqual(result.error.fields, { zones: 'Use STANDING or SEATED' });
});

test('uploads an organizer poster as multipart form data', async () => {
  let request;
  const file = new File(['poster'], 'poster.png', { type: 'image/png' });

  const result = await client(async (url, options) => {
    request = { url, options };
    return Response.json({ url: '/api/inventory/posters/poster.png', contentType: 'image/png', size: 6 });
  }).organizer.uploadPoster(file);

  assert.equal(result.ok, true);
  assert.equal(request.url, '/api/inventory/posters');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.headers['Content-Type'], undefined);
  assert.equal(request.options.body.get('file'), file);
});

test('returns a safe cancellation error when the request is aborted', async () => {
  const api = client(async () => { throw new DOMException('cancelled', 'AbortError'); });
  const result = await api.events.list({ signal: new AbortController().signal });

  assert.deepEqual(result, { ok: false, error: { kind: 'aborted', message: 'The request was cancelled.' } });
});

test('stores login and refreshed tokens in session storage then clears them', async () => {
  const persisted = storage();
  let call = 0;
  const api = createGatewayClient({
    baseUrl: '/api',
    storage: persisted,
    fetch: async (_url, options) => {
      call += 1;
      if (call === 1) {
        assert.equal(options.body, JSON.stringify({ username: 'ada', password: 'secret' }));
        return Response.json({ accessToken: 'first-access', refreshToken: 'first-refresh', tokenType: 'Bearer', expiresInSeconds: 60 });
      }
      assert.equal(options.body, JSON.stringify({ refreshToken: 'first-refresh' }));
      return Response.json({ accessToken: 'second-access', refreshToken: 'second-refresh', tokenType: 'Bearer', expiresInSeconds: 60 });
    },
  });

  assert.equal((await api.session.login('ada', 'secret')).ok, true);
  assert.equal(api.session.accessToken(), 'first-access');
  assert.equal((await api.session.refresh()).ok, true);
  assert.equal(api.session.accessToken(), 'second-access');
  api.session.clear();
  assert.equal(api.session.accessToken(), null);
  assert.equal(persisted.getItem('grabmyseat.session'), null);
});
