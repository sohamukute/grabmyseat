import test from 'node:test';
import assert from 'node:assert/strict';
import { toFixedLayoutEventRequest, validateEventDraft } from './event-draft.js';

const draft = {
  name: 'Summer concert',
  venue: 'City Arena',
  artworkUrl: '/api/inventory/posters/7d0fa9b8-d175-4e26-940a-2100e88f6449.webp',
  startsAt: '2030-07-21T20:00',
  endsAt: '2030-07-21T22:00',
  saleType: 'STANDARD',
  queueOpensAt: '',
  saleStartsAt: '2030-07-01T10:00',
  saleEndsAt: '2030-07-21T18:00',
  generalAdmissionCapacity: '400',
  generalAdmissionPrice: '499',
  leftPremiumCapacity: '80',
  leftPremiumPrice: '999',
  rightPremiumCapacity: '80',
  rightPremiumPrice: '999',
};

test('fixed layout submits only STANDING and SEATED zone types', () => {
  const request = toFixedLayoutEventRequest(draft);

  assert.deepEqual(request.zones.map(({ name, type }) => ({ name, type })), [
    { name: 'General Admission', type: 'STANDING' },
    { name: 'Left Premium', type: 'SEATED' },
    { name: 'Right Premium', type: 'SEATED' },
  ]);
  assert.deepEqual(request.layout, {
    generalAdmissionCapacity: 400,
    generalAdmissionPrice: 499,
    leftPremiumCapacity: 80,
    leftPremiumPrice: 999,
    rightPremiumCapacity: 80,
    rightPremiumPrice: 999,
  });
});

test('standard request submits a required sale window without a queue time', () => {
  const request = toFixedLayoutEventRequest(draft);

  assert.equal(request.saleType, 'STANDARD');
  assert.equal(request.queueOpensAt, null);
  assert.match(request.saleStartsAt, /^2030-07-01T/);
  assert.match(request.saleEndsAt, /^2030-07-21T/);
});

test('flash draft reports field errors for a missing queue time and invalid window', () => {
  const errors = validateEventDraft({
    ...draft,
    saleType: 'FLASH',
    queueOpensAt: '',
    saleStartsAt: '2030-07-21T19:00',
    saleEndsAt: '2030-07-21T18:00',
  });

  assert.equal(errors.queueOpensAt, 'Choose when the queue opens.');
  assert.equal(errors.saleEndsAt, 'Sale end must be after sale start.');
});
