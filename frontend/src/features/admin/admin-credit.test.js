import assert from 'node:assert/strict';
import test from 'node:test';
import { createCreditConfirmation, creditRequest } from './admin-credit.js';

test('binds a credit confirmation to the reviewed user and decimal string', () => {
  const user = { id: 7, displayName: 'Ada', phone: '+919999999999', email: null, roles: ['ROLE_CUSTOMER'] };
  const confirmation = createCreditConfirmation(user, '999999999999999.9999', () => 'credit-key');
  user.id = 99;
  user.displayName = 'Changed';

  assert.deepEqual(creditRequest(confirmation), {
    userId: 7,
    amount: '999999999999999.9999',
    idempotencyKey: 'credit-key',
  });
  assert.equal(confirmation.user.displayName, 'Ada');
});

test('rejects nonpositive and over-precision credit amounts', () => {
  const user = { id: 7, displayName: 'Ada', roles: [] };
  assert.equal(createCreditConfirmation(user, '0.00', () => 'key'), null);
  assert.equal(createCreditConfirmation(user, '-1.00', () => 'key'), null);
  assert.equal(createCreditConfirmation(user, '0.01001', () => 'key'), null);
  assert.equal(createCreditConfirmation(user, '1000000000000000.00', () => 'key'), null);
});
