import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

test('auth, FAQ route and admin breadcrumb expose the intended navigation', () => {
  let replaced = null;
  const root = { tagName: 'SPAN', textContent: 'CopiMine', replaceWith(node) { replaced = node; } };
  const current = { tagName: 'STRONG', setAttribute(name, value) { this[name] = value; } };
  const crumb = { children: [root, current] };
  const managementRoot = { tagName: 'SPAN', textContent: 'Управление', replaceWith(node) { this.replaced = node; } };
  const managementCurrent = { tagName: 'STRONG', setAttribute(name, value) { this[name] = value; } };
  const managementCrumb = { children: [managementRoot, managementCurrent] };
  const M = {
    renderers: { settings: () => '<div></div>', page: slug => `<p>${slug}</p>`, shop: () => 'shop', account: () => 'account' },
    forms: {}, handlers: {}, UI: {}, state: { faq: [{ question: 'Как войти?', answer: 'По нику и паролю.', published: true }] },
    esc: value => String(value ?? ''), asset: path => path, me: () => ({ id: 'user-1', name: 'PlayerOne', displayName: 'Player One' }),
    empty: () => '<p>Нет ответов</p>', btn: () => '<button>Написать</button>', link: () => '<a>FAQ</a>',
  };
  const V = { after: [], field: () => '', area: () => '', actions: () => '' };
  const Q = { csrf: '', user: null, hint: false };
  const document = {
    querySelectorAll: selector => selector === '.breadcrumb' ? [crumb, managementCrumb] : [],
    createElement: () => ({ setAttribute(name, value) { this[name] = value; } }),
  };
  const context = { window: { CM: M, CMV4: V, CMReview: Q, CM71: { heading: () => '' }, CM5: { head: () => '' } }, document, CM71: { heading: () => '' }, CM5: { head: () => '' }, icon: () => '' };
  vm.runInNewContext(readFileSync(new URL('../frontend/assets/js/account-v9.js', import.meta.url), 'utf8'), context);

  const register = M.renderers.auth('register');
  const login = M.renderers.auth('login');
  assert.match(register, /name="displayName"/);
  assert.match(register, /Ник профиля/);
  assert.match(register, /Логин/);
  assert.match(register, /name="username"/);
  assert.match(register, /name="password"/);
  assert.doesNotMatch(register, /type="email"|Почт|VK ID|VK/iu);
  assert.doesNotMatch(login, /type="email"|Почт|VK ID|VK|код на почту/iu);
  const settings = M.renderers.settings('profile');
  assert.match(settings, /Ник на сайте/);
  assert.doesNotMatch(settings, /Почт|VK ID|VK/iu);
  assert.match(M.renderers.page('help'), /Как войти\?/);
  assert.match(M.renderers.faq(), /FAQ/);
  assert.equal(typeof M.handlers['faq-contact-support'], 'function');

  V.after[0]('admin', 'overview');
  assert.equal(replaced.href, '#admin/overview');
  assert.equal(replaced.textContent, 'CopiMine');
  assert.equal(current['aria-current'], 'page');
  assert.equal(managementRoot.replaced.href, '#admin/overview');
  assert.equal(managementCurrent['aria-current'], 'page');
});
