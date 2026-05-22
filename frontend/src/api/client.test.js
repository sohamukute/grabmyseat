import assert from 'node:assert/strict';
import test from 'node:test';
import { createGatewayClient } from './client.js';

function storage() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) };
}
function client(fetch) { return createGatewayClient({ baseUrl: '/api', fetch, storage: storage() }); }

test('returns JSON responses and sends the stored bearer token', async () => {
  let request;
  const api = client(async (url, options) => {
    request = { url, options };
    return url.endsWith('/auth/login') ? Response.json({ accessToken: 'access-token', refreshToken: 'refresh-token', tokenType: 'Bearer', expiresInSeconds: 60 }) : Response.json([{ id: 1 }]);
  });
  await api.session.login('ada', 'secret');
  assert.deepEqual(await api.events.list(), { ok: true, status: 200, data: [{ id: 1 }] });
  assert.equal(request.url, '/api/inventory/events');
  assert.equal(request.options.headers.Authorization, 'Bearer access-token');
});

test('returns a safe error when a successful response is not JSON', async () => {
  assert.deepEqual(await client(async () => new Response('upstream html', { status: 200 })).events.list(), { ok: false, error: { kind: 'invalid-response', message: 'The service returned an unexpected response.', status: 200 } });
});

test('returns a safe session-expired error for unauthorized responses', async () => {
  assert.deepEqual(await client(async () => Response.json({ detail: 'internal' }, { status: 401 })).wallet.balance(), { ok: false, error: { kind: 'unauthorized', message: 'Your session has expired. Please sign in again.', status: 401 } });
});

test('returns a context-neutral error for conflicts', async () => {
  assert.deepEqual(await client(async () => Response.json({ detail: 'internal' }, { status: 409 })).reservations.create({ eventId: 1, zoneId: 2, quantity: 1 }), { ok: false, error: { kind: 'conflict', message: 'That action conflicts with the current state. Please refresh and try again.', status: 409 } });
});

test('returns server field errors for invalid event submissions', async () => {
  const result = await client(async () => Response.json({
    message: 'Invalid event.',
    fieldErrors: { 'layout.rightPremiumCapacity': 'Capacity must be at least 1.' },
  }, { status: 400 })).organizer.submitEvent({});

  assert.deepEqual(result, {
    ok: false,
    error: {
      kind: 'request-failed',
      message: 'Invalid event.',
      status: 400,
      fields: { 'layout.rightPremiumCapacity': 'Capacity must be at least 1.' },
    },
  });
});

test('uploads organizer posters as multipart form data', async () => {
  let request;
  const api = client(async (url, options) => {
    request = { url, options };
    return Response.json({ url: '/api/inventory/posters/poster.webp', contentType: 'image/webp', size: 4 });
  });
  const file = new File(['test'], 'poster.webp', { type: 'image/webp' });

  await api.organizer.uploadPoster(file);

  assert.equal(request.url, '/api/inventory/posters');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.headers['Content-Type'], undefined);
  assert.equal(request.options.body.get('file'), file);
});

test('returns a safe cancellation error when the request is aborted', async () => {
  const result = await client(async () => { throw new DOMException('cancelled', 'AbortError'); }).events.list({ signal: new AbortController().signal });
  assert.deepEqual(result, { ok: false, error: { kind: 'aborted', message: 'The request was cancelled.' } });
});

test('stores login and refreshed tokens in session storage then clears them', async () => {
  const persisted = storage(); let call = 0;
  const api = createGatewayClient({ baseUrl: '/api', storage: persisted, fetch: async (_url, options) => {
    call += 1;
    if (call === 1) { assert.equal(options.body, JSON.stringify({ username: 'ada', password: 'secret' })); return Response.json({ accessToken: 'first-access', refreshToken: 'first-refresh', tokenType: 'Bearer', expiresInSeconds: 60 }); }
    assert.equal(options.body, JSON.stringify({ refreshToken: 'first-refresh' }));
    return Response.json({ accessToken: 'second-access', refreshToken: 'second-refresh', tokenType: 'Bearer', expiresInSeconds: 60 });
  } });
  assert.equal((await api.session.login('ada', 'secret')).ok, true);
  assert.equal(api.session.accessToken(), 'first-access');
  assert.equal((await api.session.refresh()).ok, true);
  assert.equal(api.session.accessToken(), 'second-access');
  api.session.clear();
  assert.equal(api.session.accessToken(), null);
  assert.equal(persisted.getItem('grabmyseat.session'), null);
});

test('sends queue permit only with reservation creation', async () => {
  let request;
  const api = client(async (_url, options) => {
    request = options;
    return Response.json({ token: 'hold' }, { status: 201 });
  });
  await api.reservations.create({ eventId: 1, zoneId: 2, seatIds: [3] }, { permitToken: 'permit-1' });
  assert.equal(request.headers['X-Queue-Permit'], 'permit-1');
});

test('exposes only the existing operational API endpoints', async () => {
  const requests = [];
  const api = client(async (url, options) => {
    requests.push({ url, options });
    return Response.json({});
  });
  await api.organizer.events();
  await api.organizer.inviteStaff(7, 'gate-staff');
  await api.staff.validateTicket('ticket-1');
  await api.wallet.credit({ userId: 3, amount: 500, idempotencyKey: 'admin-topup-1' });
  assert.deepEqual(requests.map(({ url, options }) => [url, options.method, options.body]), [
    ['/api/inventory/events/organizer/me', 'GET', undefined],
    ['/api/inventory/events/7/staff', 'POST', JSON.stringify({ username: 'gate-staff' })],
    ['/api/ticketing/tickets/ticket-1/validate', 'POST', undefined],
    ['/api/wallet/admin/topups', 'POST', JSON.stringify({ userId: 3, amount: 500, idempotencyKey: 'admin-topup-1' })],
  ]);
});

test('searches the admin user directory with paging', async () => {
  let request;
  const api = client(async (url, options) => {
    request = { url, options };
    return Response.json({ content: [], number: 2, totalPages: 3 });
  });

  await api.admin.users({ query: 'ada + one', page: 2 });

  assert.equal(request.url, '/api/auth/admin/users?query=ada+%2B+one&page=2');
  assert.equal(request.options.method, 'GET');
});

test('uses available events and ticket endpoints for staff operations', async () => {
  const requests = [];
  const api = client(async (url, options) => {
    requests.push([url, options.method, options.body]);
    return Response.json({});
  });

  await api.staff.events();
  await api.staff.checkInTicket('ticket-1', { attendeesPresent: ['Ada'] });

  assert.deepEqual(requests, [
    ['/api/inventory/events', 'GET', undefined],
    ['/api/ticketing/tickets/ticket-1/check-in', 'POST', JSON.stringify({ attendeesPresent: ['Ada'] })],
  ]);
});

test('exposes customer attendee endpoints', async () => {
  const requests = [];
  const api = client(async (url, options) => {
    requests.push([url, options.method, options.body]);
    return Response.json({});
  });
  await api.attendees.list();
  await api.attendees.create({ name: 'Ada', age: 30, mobile: '+919999999999', email: 'ada@example.test' });
  assert.deepEqual(requests, [
    ['/api/inventory/attendees', 'GET', undefined],
    ['/api/inventory/attendees', 'POST', JSON.stringify({ name: 'Ada', age: 30, mobile: '+919999999999', email: 'ada@example.test' })],
  ]);
});

test('sends phone OTP requests without a bearer token', async () => {
  let request;
  const api = client(async (url, options) => {
    request = { url, options };
    return Response.json({}, { status: 202 });
  });
  await api.session.requestOtp('+919999999999');
  assert.equal(request.url, '/api/auth/otp/request');
  assert.equal(request.options.headers.Authorization, undefined);
  assert.equal(request.options.body, JSON.stringify({ phone: '+919999999999' }));
});
