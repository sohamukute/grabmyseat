import assert from 'node:assert/strict';
import test from 'node:test';
import { signOutWorkspace } from './workspace-state.js';

test('signing out closes the workspace before rendering the catalogue', () => {
  assert.deepEqual(signOutWorkspace(), { authenticated: false, workspaceOpen: false });
});
