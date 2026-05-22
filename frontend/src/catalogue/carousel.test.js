import test from 'node:test';
import assert from 'node:assert/strict';
import { canAutoAdvance, cancelFeaturedMotion, displayedFeatured, nextFeaturedIndex, settleFeaturedMotion, slotFor } from './carousel.js';

test('wraps a right navigation step', () => assert.equal(nextFeaturedIndex(6, 1, 7), 0));
test('wraps a left navigation step', () => assert.equal(nextFeaturedIndex(0, -1, 7), 6));
test('keeps one active slot', () => assert.equal(slotFor(2, 2, 7), 'active'));

test('settles an in-flight exit when motion is reduced', () => assert.deepEqual(settleFeaturedMotion(2, 1, 0, 7), { active: 3, transition: null, entering: null }));

test('cancels an in-flight exit without committing its pending index', () => {
  assert.deepEqual(cancelFeaturedMotion(2), { active: 2, transition: null, entering: null });
});

test('keeps the full deck rendered during an exit transition', () => {
  const featured = [{ id: 1 }, { id: 2 }, { id: 3 }];
  assert.equal(displayedFeatured(featured, 1), featured);
});

test('stops automatic advancement while a booking is selected', () => {
  assert.equal(canAutoAdvance({ paused: false, reducedMotion: false, length: 2, eventSelected: true }), false);
  assert.equal(canAutoAdvance({ paused: false, reducedMotion: false, length: 2, eventSelected: false }), true);
});
