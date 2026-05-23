import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let vite;
let editorModule;
let applicationsModule;

before(async () => {
  vite = await createServer({ root: process.cwd(), server: { middlewareMode: true }, appType: 'custom' });
  editorModule = await vite.ssrLoadModule('/src/features/organizer/event-editor.jsx').catch(() => ({}));
  applicationsModule = await vite.ssrLoadModule('/src/features/organizer/staff-applications.jsx').catch(() => ({}));
});

after(async () => vite?.close());

const validDraft = {
  name: 'Summer concert', venue: 'City Arena', artworkUrl: '/api/inventory/posters/poster.webp',
  startsAt: '2030-07-21T20:00', endsAt: '2030-07-21T22:00', saleType: 'STANDARD', queueOpensAt: '',
  saleStartsAt: '2030-07-01T10:00', saleEndsAt: '2030-07-21T18:00',
  generalAdmissionCapacity: '400', generalAdmissionPrice: '499', leftPremiumCapacity: '80',
  leftPremiumPrice: '999', rightPremiumCapacity: '80', rightPremiumPrice: '999',
};

test('EventEditor does not emit before a successful poster upload', () => {
  assert.equal(typeof editorModule.prepareCreateEvent, 'function');
  let emitted;

  const errors = editorModule.prepareCreateEvent(validDraft, false, (request) => { emitted = request; });

  assert.equal(emitted, undefined);
  assert.equal(errors.artworkUrl, 'Upload the event poster before submitting.');
});

test('EventEditor emits one validated CreateEventRequest after poster upload', () => {
  let emitted;

  const errors = editorModule.prepareCreateEvent(validDraft, true, (request) => { emitted = request; });

  assert.deepEqual(errors, {});
  assert.equal(emitted.name, 'Summer concert');
  assert.equal(emitted.artworkUrl, '/api/inventory/posters/poster.webp');
  assert.deepEqual(emitted.zones.map(({ type }) => type), ['STANDING', 'SEATED', 'SEATED']);
});

test('EventEditor displays field-level errors beside their controls', () => {
  assert.equal(typeof editorModule.EventEditor, 'function');
  const html = renderToStaticMarkup(React.createElement(editorModule.EventEditor, {
    client: { uploadPoster: async () => ({ ok: true, data: { url: '/poster.webp' } }) },
    onCreate: async () => ({ ok: true, data: {} }),
    initialDraft: validDraft,
    initialFieldErrors: { name: 'Use a unique event name.' },
  }));

  assert.match(html, /aria-describedby="event-name-error"/);
  assert.match(html, /id="event-name-error"[^>]*>Use a unique event name/);
});

test('StaffApplications owns empty and error states and names selected-record actions', () => {
  assert.equal(typeof applicationsModule.StaffApplications, 'function');
  const event = { id: 3, name: 'Summer concert' };
  const client = { staffApplications: async () => ({ ok: true, data: [] }) };
  const empty = renderToStaticMarkup(React.createElement(applicationsModule.StaffApplications, {
    events: [event], client, initialEventId: '3', initialResult: { ok: true, data: [] },
  }));
  const error = renderToStaticMarkup(React.createElement(applicationsModule.StaffApplications, {
    events: [event], client, initialEventId: '3', initialResult: { ok: false, error: { message: 'Staff unavailable.' } },
  }));
  const ready = renderToStaticMarkup(React.createElement(applicationsModule.StaffApplications, {
    events: [event], client, initialEventId: '3', initialResult: { ok: true, data: [{ userId: 9, username: 'grace', status: 'PENDING', invitedAt: '2030-07-01T10:00:00Z' }] },
  }));

  assert.match(empty, /No applications need a decision/);
  assert.match(error, /Staff unavailable/);
  assert.match(ready, /aria-label="Approve grace for Summer concert"/);
  assert.match(ready, /aria-label="Reject grace for Summer concert"/);
});

test('StaffApplications maps username field errors and exposes contextual revocation', () => {
  assert.equal(
    applicationsModule.inviteUsernameError({ ok: false, error: { fields: { username: 'Choose an existing staff username.' } } }),
    'Choose an existing staff username.',
  );
  const event = { id: 3, name: 'Summer concert' };
  const html = renderToStaticMarkup(React.createElement(applicationsModule.StaffApplications, {
    events: [event],
    client: { staffApplications: async () => ({ ok: true, data: [] }) },
    initialEventId: '3',
    initialTab: 'assigned',
    initialResult: { ok: true, data: [{ userId: 10, username: 'alan', status: 'ACTIVE', invitedAt: '2030-07-01T11:00:00Z' }] },
  }));

  assert.match(html, /aria-label="Revoke alan from Summer concert"/);
});
