import assert from 'node:assert/strict';
import test from 'node:test';
import { formatIndianCurrency } from './format.js';

test('formats wallet balance as Indian rupees', () => {
  assert.equal(formatIndianCurrency('1234567.5'), '₹12,34,567.50');
});
