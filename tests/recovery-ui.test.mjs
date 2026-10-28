import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

test('loads and selects recovery accounts from the authenticated server session', async () => {
  const calls = [];
  const state = { players: [], reviewRecoveryLog: [], session: { id: 'owner-id' } };
  const M = {
    state,
    UI: { page: 'admin', tab: 'recovery', params: new URLSearchParams() },
    handlers: {},
    forms: {},
    me: () => state.players.find(player => player.id === state.session?.id) || null,
    esc: value => String(value ?? ''),
    btn: () => '<button></button>',
    date: () => '',
    empty: () => '<p>Нет записей</p>',
    save: () => {},
    audit: () => {},
    render: () => {},
    toast: () => {},
    navTo: () => {},
    openModal: () => {},
    closeModal: () => {},
  };
  const V = { after: [], field: () => '', area: () => '', table: () => '' };
  const X = {
    live: true,
    sessionUser: { id: 'owner-id', name: 'Owner', role: 'owner' },
    canAdmin: () => true,
    can: () => true,
    uid: () => 'recovery-id',
  };
  const Q = {
    ready: true,
    offline: false,
    request: async path => {
      calls.push(path);
      if (path === 'recovery-accounts') {
        return { accounts: [{ id: 'owner-id', username: 'owner', display_name: 'Owner', email_verified: true }] };
      }
      return { id: 'recovery-entry', status: 'completed' };
    },
  };
  const window = { CM73: {} };
  const context = {
    CM: M,
    CMV4: V,
    CM5: { head: () => '' },
    CM6: X,
    CM71: {},
    CMReview: Q,
    window,
    document: { getElementById: () => null },
    URLSearchParams,
    Date,
    icon: () => '',
  };
  const source = readFileSync(new URL('../frontend/assets/js/recovery-v73.js', import.meta.url), 'utf8');
  vm.runInNewContext(source, context, { filename: 'recovery-v73.js' });

  V.after[0]('admin', 'recovery');
  await new Promise(resolve => setTimeout(resolve, 0));

  assert.deepEqual(calls, ['recovery-accounts']);
  const html = window.CM73.recoveryView();
  assert.match(html, /Owner/);
  assert.match(html, /value="owner-id" selected/);

  M.handlers['v73-recovery-action']({ dataset: { id: 'sessions.revoke', player: 'owner-id' } });
  const form = { get: key => ({ verified: 'yes', player: 'owner-id', action: 'sessions.revoke', reason: 'Owner identity checked' })[key] };
  await M.forms['v73-recovery']({}, form);
  assert.equal(state.reviewRecoveryLog[0].actor, 'Owner');
});
