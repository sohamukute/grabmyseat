import assert from 'node:assert/strict';
import test from 'node:test';
import { roleWorkspace } from './workspace.js';

test('selects the customer workspace by default', () => {
  assert.equal(roleWorkspace([]), 'customer');
});

test('selects the operational workspace from JWT roles', () => {
  assert.equal(roleWorkspace(['ROLE_ADMIN']), 'admin');
  assert.equal(roleWorkspace(['ROLE_ORGANIZER']), 'organizer');
  assert.equal(roleWorkspace(['ROLE_STAFF']), 'staff');
});

test('prioritizes admin controls for multi-role identities', () => {
  assert.equal(roleWorkspace(['ROLE_CUSTOMER', 'ROLE_STAFF', 'ROLE_ADMIN']), 'admin');
});
