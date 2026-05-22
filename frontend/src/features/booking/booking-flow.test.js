import assert from 'node:assert/strict';
import test from 'node:test';
import { canBookDirectly, reservationBody, shouldJoinQueue } from './booking-request.js';

const zone = { id: 7 };
const attendee = { id: 3, name: 'Asha Rao', age: 28, mobile: '+919876543210', email: 'asha@example.test' };

test('reservation payload requires one saved attendee per ticket', () => {
  assert.throws(
    () => reservationBody({ eventId: 5, zone, quantity: 2, attendees: [attendee] }),
    /Choose one saved attendee for each ticket\./,
  );
});

test('reservation payload snapshots selected attendee names', () => {
  const body = reservationBody({ eventId: 5, zone, quantity: 1, attendees: [attendee] });

  assert.deepEqual(body, {
    eventId: 5,
    zoneId: 7,
    quantity: 1,
    attendeeNames: ['Asha Rao'],
  });
});

test('standard sales proceed directly while flash sales require queue admission', () => {
  assert.equal(canBookDirectly({ saleType: 'STANDARD', status: 'ON_SALE' }), true);
  assert.equal(shouldJoinQueue({ saleType: 'FLASH', canJoinQueue: true }), true);
  assert.equal(canBookDirectly({ saleType: 'FLASH', status: 'ON_SALE' }), false);
  assert.equal(shouldJoinQueue({ saleType: 'FLASH', canJoinQueue: false }), false);
});
