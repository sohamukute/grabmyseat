import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let vite;
let directoryModule;

before(async () => {
  vite = await createServer({ root: process.cwd(), server: { middlewareMode: true }, appType: 'custom' });
  directoryModule = await vite.ssrLoadModule('/src/features/admin/user-directory.jsx').catch(() => ({}));
});

after(async () => vite?.close());

const emptyPage = { content: [], number: 0, totalPages: 0, totalElements: 0 };
const adminClient = { users: async () => ({ ok: true, data: emptyPage }) };

test('UserDirectory renders its loading, error, and empty states', () => {
  assert.equal(typeof directoryModule.UserDirectory, 'function');
  const { UserDirectory } = directoryModule;

  const loading = renderToStaticMarkup(React.createElement(UserDirectory, { client: adminClient }));
  const error = renderToStaticMarkup(React.createElement(UserDirectory, {
    client: adminClient,
    initialResult: { ok: false, error: { message: 'Directory unavailable.' } },
  }));
  const empty = renderToStaticMarkup(React.createElement(UserDirectory, {
    client: adminClient,
    initialResult: { ok: true, data: emptyPage },
  }));

  assert.match(loading, /Loading users/);
  assert.match(error, /Directory unavailable/);
  assert.match(empty, /No users match this search/);
});

test('UserDirectory identifies the selected visible record action accessibly', () => {
  const { UserDirectory } = directoryModule;
  const user = { id: 7, displayName: 'Ada Lovelace', phone: '+919999999999', email: 'ada@example.test', roles: ['ROLE_CUSTOMER'] };
  const html = renderToStaticMarkup(React.createElement(UserDirectory, {
    client: adminClient,
    initialResult: { ok: true, data: { content: [user], number: 0, totalPages: 1, totalElements: 1 } },
    selectedUserId: 7,
    onSelect: () => {},
  }));

  assert.match(html, /aria-label="Select Ada Lovelace for wallet credit"/);
  assert.match(html, /aria-pressed="true"/);
});

