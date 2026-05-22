import assert from 'node:assert/strict';
import test from 'node:test';
import { createSession } from './session.js';

const storage = () => { const values = new Map(); return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }; };
const token = (claims) => `header.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.signature`;

test('reads role claims from the saved access token', () => {
  const session = createSession(storage());
  session.save({ accessToken: token({ roles: ['ROLE_ORGANIZER'] }), refreshToken: 'refresh' });
  assert.deepEqual(session.roles(), ['ROLE_ORGANIZER']);
});

test('treats malformed role claims as no roles', () => {
  const session = createSession(storage());
  session.save({ accessToken: 'not-a-jwt', refreshToken: 'refresh' });
  assert.deepEqual(session.roles(), []);
});
