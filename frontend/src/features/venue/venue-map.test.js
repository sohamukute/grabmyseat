import assert from 'node:assert/strict';
import test from 'node:test';
import { layoutFor, mapModel, standingQuantity } from './venue-map-model.js';

test('all zones expose a quantity-only allocation model', () => {
  const model = mapModel({ layoutKey: 'THEATRE' }, { type: 'SEATED', availableSeats: 73, price: 499, seats: [] });

  assert.equal(model.interaction, 'zone');
  assert.equal(model.available, 73);
  assert.equal(model.price, 499);
});

test('layout metadata recognizes stadium and falls back to auditorium', () => {
  assert.deepEqual(layoutFor('STADIUM'), { kind: 'seated', label: 'Pitch' });
  assert.equal(layoutFor('not-a-layout').label, 'Stage');
});

test('unknown layouts use auditorium presentation metadata', () => {
  const model = mapModel({ layoutKey: 'not-a-layout' }, { type: 'SEATED', availableSeats: 1, seats: [] });

  assert.equal(model.layoutKey, 'AUDITORIUM');
  assert.equal(model.presentationClass, 'venue-map--AUDITORIUM');
});

test('standing capacity never falls below zero', () => {
  const model = mapModel({ layoutKey: 'FESTIVAL_FIELD' }, { type: 'STANDING', availableSeats: 0, price: 499, seats: [] });

  assert.equal(model.available, 0);
  assert.equal(model.maximumQuantity, 0);
  assert.equal(standingQuantity(model.available, 1), 0);
});

test('seated zone diagrams do not expose individual seat status', () => {
  const model = mapModel({ layoutKey: 'THEATRE' }, { type: 'SEATED', availableSeats: 1, seats: [] });
  assert.equal('seats' in model, false);
});
