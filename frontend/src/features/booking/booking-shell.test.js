import assert from 'node:assert/strict';
import test from 'node:test';
import { bookingShellState, changeQuantity, closeBookingShell, hasLiveHold, isSelectionLocked, recoverFromExpiredHold, requiresFreshBooking, selectZone } from './booking-shell-state.js';

const event = {
  id: 1,
  zones: [
    { id: 2, name: 'Floor', type: 'STANDING', availableSeats: 3 },
    { id: 3, name: 'Balcony', type: 'SEATED', availableSeats: 4 },
  ],
};

test('standing selection sends quantity without seat IDs', () => {
  const state = bookingShellState(event);
  const standing = selectZone(state, event.zones[0]);
  assert.deepEqual(standing.selection, { zoneId: 2, quantity: 1 });
});

test('seated zones use a party quantity for automatic allocation', () => {
  const state = selectZone(bookingShellState(event), event.zones[1]);
  assert.deepEqual(changeQuantity(state, 4).selection, { zoneId: 3, quantity: 4 });
});

test('back retains the selected event until the user closes the shell', () => {
  const state = bookingShellState(event);
  assert.equal(state.event, event);
  assert.equal(closeBookingShell(state).event, null);
});

test('locks tier, seat, and quantity selection after a hold is created', () => {
  const state = bookingShellState(event);
  const locked = isSelectionLocked('HOLD');

  assert.equal(locked, true);
  assert.equal(isSelectionLocked('SELECT'), false);
  assert.equal(selectZone(state, event.zones[1], locked), state);
  assert.equal(changeQuantity(state, 2, locked), state);
  assert.equal(changeQuantity(state, 2, locked), state);
});

test('keeps selection locked on an error while a reservation hold remains', () => {
  assert.equal(isSelectionLocked('ERROR', true), true);
  assert.equal(isSelectionLocked('ERROR', false), false);
});

test('keeps a hold live through confirmation and recovery until a ticket is issued', () => {
  const hold = { token: 'reservation', expiresAt: '2030-01-01T00:00:00Z' };
  assert.equal(hasLiveHold(hold, null), true);
  assert.equal(hasLiveHold(hold, { id: 'ticket' }), false);
});

test('expired hold clears admission and retries from queue entry', () => {
  let hold = { token: 'hold' };
  let permitToken = 'consumed-permit';
  let queueToken = 'admission';
  const joinQueue = () => {};

  const retry = recoverFromExpiredHold((value) => { hold = value; }, (value) => { permitToken = value; }, (value) => { queueToken = value; }, joinQueue);

  assert.equal(hold, null);
  assert.equal(permitToken, null);
  assert.equal(queueToken, null);
  assert.equal(retry, joinQueue);
});

test('terminal saga statuses require a fresh booking attempt', () => {
  assert.equal(requiresFreshBooking('FAILED'), true);
  assert.equal(requiresFreshBooking('COMPENSATED'), true);
  assert.equal(requiresFreshBooking('CONFIRMED'), false);
});
