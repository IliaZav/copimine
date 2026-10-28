/* Account, FAQ and support workflows backed by the production API. */
(function () {
  'use strict';

  const M = window.CM, V = window.CMV4, Q = window.CMReview, A = window.CM71 || {}, W = window.CM5 || {};
  if (!M || !V || !Q) return;
  const R = M.renderers, F = M.forms, H = M.handlers, E = M.esc, state = () => M.state;
  const oldSettings = R.settings, oldPage = R.page, oldAccount = R.account;
  const authReturnKey = 'copimine.auth.return.v1';
  const pendingLinkKey = 'copimine.minecraft-link.pending.v1';
  const linkedCabinetKey = 'copimine.minecraft-link.complete.v1';
  let minecraftPoll = 0, pendingMinecraftLink = null;

  const currentAccountUser = () => Q.user || M.me() || null;
  function syncLinkedUser(user) {
    if (!user?.id || !user.gameLinkVerified) return;
    Q.user = user;
    const players = state().players || (state().players = []);
    let player = players.find(item => item.id === user.id);
    if (!player) {
      player = { id: user.id, name: user.name, AR: null, CM: null, role: 'Игрок', status: 'active', purchaseLimits: [] };
      players.push(player);
    }
    Object.assign(player, {
      name: user.name,
      displayName: user.displayName || user.name,
      minecraftName: user.minecraftName,
      minecraftUuid: user.minecraftUuid,
      gameUuid: user.gameUUID || user.minecraftUuid,
      gameLinkVerified: true,
      linked: true
    });
    state().session = { ...(state().session || {}), id: user.id };
  }
  function loadPendingLink(user) {
    if (!user?.id || user.gameLinkVerified) return null;
    try {
      const saved = JSON.parse(sessionStorage.getItem(pendingLinkKey) || 'null');
      if (saved?.userId === user.id && /^[A-Za-z0-9_]{3,16}$/.test(saved.name || '') && Number.isFinite(Number(saved.expiresAt))) {
        return { name: saved.name, expiresAt: Number(saved.expiresAt), userId: user.id };
      }
      if (saved) sessionStorage.removeItem(pendingLinkKey);
    } catch { /* The saved step is optional; the player can request another code. */ }
    return null;
  }
  function savePendingLink(user, pending) {
    if (!user?.id || !pending) return;
    pendingMinecraftLink = { ...pending, userId: user.id };
    try { sessionStorage.setItem(pendingLinkKey, JSON.stringify(pendingMinecraftLink)); } catch { /* The flow still works without session storage. */ }
  }
  function clearPendingLink() {
    pendingMinecraftLink = null;
    try { sessionStorage.removeItem(pendingLinkKey); } catch { /* Optional state. */ }
  }
  function motionEnabled() { return document.documentElement.dataset.motion !== 'off'; }
  function animateLinkStep() {
    requestAnimationFrame(() => window.CMSiteMotion?.linkStep?.(document.querySelector('[data-link-panel]'), motionEnabled()));
  }
  function finishLinkTransition() {
    return new Promise(resolve => {
      const root = document.querySelector('[data-link-gate]');
      const complete = () => resolve();
      if (root && window.CMSiteMotion?.completeLink) window.CMSiteMotion.completeLink(root, complete, motionEnabled());
      else complete();
    });
  }
  async function openLinkedCabinet() {
    await finishLinkTransition();
    try { sessionStorage.setItem(linkedCabinetKey, '1'); } catch { /* Animation is optional. */ }
    M.toast('Minecraft привязан.');
    M.navTo('account');
  }
  const previousLogoutCommit = H['logout-commit'];
  H['logout-commit'] = async (...args) => {
    stopMinecraftPoll(); clearPendingLink();
    try { sessionStorage.removeItem(linkedCabinetKey); } catch { /* Optional state. */ }
    return previousLogoutCommit?.(...args);
  };

async function api(path, body, method) {
    const headers = { Accept: 'application/json' };
    const options = { method: method || (body === undefined ? 'GET' : 'POST'), credentials: 'same-origin', headers };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      headers['X-CSRF-Token'] = Q.csrf;
      options.body = JSON.stringify(body);
    }
    const response = await fetch('/api/v6/' + path, options);
    const result = await response.json();
    if (!response.ok) throw Error(result.error?.message || result.message || 'Не удалось выполнить запрос.');
    return result;
  }

  // Файл уходит base64 в JSON: сервер проверяет тип, размер и антивирус.
  async function uploadAttachment(form, id) {
    const input = form.querySelector('input[type="file"][data-ticket-file]');
    const file = input?.files?.[0];
    if (!file) return;
    if (file.size > 8 * 1024 * 1024) throw Error('Файл больше 8 МБ.');
    const data = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1] || '');
      reader.onerror = () => reject(Error('Не удалось прочитать файл.'));
      reader.readAsDataURL(file);
    });
    await api(`tickets/${encodeURIComponent(id)}/files`, { name: file.name, mime: file.type || 'application/octet-stream', data });
  }

  function authReturn() {
    try {
      const route = sessionStorage.getItem(authReturnKey) || '';
      sessionStorage.removeItem(authReturnKey);
      return /^account(?:\/[a-z-]+)?(?:\?ticket=[a-f0-9]+)?$/i.test(route) ? route : 'account/link';
    } catch { return 'account/link'; }
  }

  function saveAuthReturn(route) {
    try { sessionStorage.setItem(authReturnKey, route); } catch { /* Optional navigation convenience. */ }
  }

  function passField(label, name, autocomplete, placeholder, minlength = 0, hint = '') {
    return `<label class="field">${label}<span class="v7-pass-wrap"><input name="${name}" type="password" required${minlength ? ` minlength="${minlength}"` : ''} maxlength="256" autocomplete="${autocomplete}" placeholder="${placeholder}"${hint ? ` aria-describedby="auth-${name}-hint"` : ''}><button type="button" class="v7-pass-toggle" data-action="v7-pass-toggle" aria-label="Показать пароль" aria-pressed="false">${icon('eye')}</button></span>${hint ? `<small class="field-hint" id="auth-${name}-hint">${hint}</small>` : ''}<em class="v7-caps" data-caps-lock role="status" hidden>Caps Lock включён</em></label>`;
  }

  function authMarkup(tab) {
    const registering = tab === 'register';
    const title = registering ? 'Создать аккаунт' : 'Войти';
    const intro = registering ? 'Создай логин и пароль. Затем привяжи Minecraft, чтобы видеть баланс и покупки.' : 'Введи логин и пароль.';
    return `<div class="v7-auth-layout v9-auth-layout"><aside class="v7-auth-aside"><img src="${M.asset('assets/img/world.webp')}" alt="" decoding="async"></aside><section class="v7-auth-main"><h1>${title}</h1><p class="v7-auth-lead">${intro}</p><nav class="v7-auth-tabs" aria-label="Вход или регистрация"><a class="${registering ? '' : 'active'}" href="#auth/login"${registering ? '' : ' aria-current="page"'}>Вход</a><a class="${registering ? 'active' : ''}" href="#auth/register"${registering ? ' aria-current="page"' : ''}>Регистрация</a></nav><form data-form="${registering ? 'v7-register' : 'v7-login'}"><label class="field">Логин сайта<input name="username" type="text" required minlength="3" maxlength="32" pattern="[A-Za-z0-9_]{3,32}" autocomplete="username" autocapitalize="none" spellcheck="false" placeholder="Логин сайта" value=""></label>${registering ? passField('Пароль', 'password', 'new-password', 'Пароль', 10, 'Минимум 10 символов.') + passField('Повтори пароль', 'repeat', 'new-password', 'Ещё раз', 10) + '<label class="check-row"><input type="checkbox" name="terms" required><span>Принимаю <a href="#rules">правила сервера</a></span></label>' : passField('Пароль', 'password', 'current-password', 'Пароль аккаунта') + '<label class="check-row"><input type="checkbox" name="remember" checked><span>Оставаться в аккаунте на этом устройстве<small class="v7-remember-hint">30 дней без повторного входа. Без галочки сеанс завершится при закрытии браузера.</small></span></label>' + '<details class="v7-2fa"><summary>Включена двухфакторная защита</summary><label class="field">Код из приложения<input name="totp" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="8" placeholder="6 цифр"></label></details>'}<div class="form-error" role="alert"></div><button type="submit" class="btn primary wide">${title}${icon('arrow-right')}</button></form>${registering ? '<p class="v7-auth-bottom">Уже зарегистрирован? <a href="#auth/login">Войти</a></p>' : '<p class="v7-auth-bottom">Нет аккаунта? <a href="#auth/register">Создать</a></p>'}</section></div>`;
  }

  const capsHint = event => {
    const input = event.target;
    if (!(input instanceof HTMLInputElement) || !input.closest('.v7-pass-wrap')) return;
    const hint = input.closest('.field')?.querySelector('[data-caps-lock]');
    if (hint) hint.hidden = !(typeof event.getModifierState === 'function' && event.getModifierState('CapsLock'));
  };
  document.addEventListener('keydown', capsHint, true);
  document.addEventListener('keyup', capsHint, true);
  document.addEventListener('focusout', event => {
    const hint = event.target?.closest?.('.field')?.querySelector?.('[data-caps-lock]');
    if (hint) hint.hidden = true;
  }, true);
  H['v7-pass-toggle'] = button => {
    const input = button.closest('.v7-pass-wrap')?.querySelector('input');
    if (!input) return;
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.innerHTML = icon(show ? 'eye-off' : 'eye');
    button.setAttribute('aria-label', show ? 'Скрыть пароль' : 'Показать пароль');
    button.setAttribute('aria-pressed', String(show));
    input.focus({ preventScroll: true });
  };

  // Every password field gets the same reveal and Caps Lock hint, including
  // settings, recovery and admin forms that are rendered outside this file.
  function enhancePasswordFields(root = document) {
    root.querySelectorAll('input[type="password"]').forEach(input => {
      if (input.closest('.v7-pass-wrap') || input.dataset.cmPassReady) return;
      input.dataset.cmPassReady = '1';
      const wrap = document.createElement('span');
      wrap.className = 'v7-pass-wrap';
      input.parentNode.insertBefore(wrap, input);
      wrap.appendChild(input);
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'v7-pass-toggle';
      button.dataset.action = 'v7-pass-toggle';
      button.setAttribute('aria-label', 'Показать пароль');
      button.setAttribute('aria-pressed', 'false');
      button.innerHTML = icon('eye');
      wrap.appendChild(button);
      const field = input.closest('.field');
      if (field && !field.querySelector('[data-caps-lock]')) {
        const hint = document.createElement('em');
        hint.className = 'v7-caps';
        hint.dataset.capsLock = '';
        hint.setAttribute('role', 'status');
        hint.hidden = true;
        hint.textContent = 'Caps Lock включён';
        field.appendChild(hint);
      }
    });
  }
  let passEnhanceFrame = 0;
  function schedulePasswordEnhance() {
    if (passEnhanceFrame) return;
    passEnhanceFrame = requestAnimationFrame(() => { passEnhanceFrame = 0; enhancePasswordFields(document); });
  }
  new MutationObserver(schedulePasswordEnhance).observe(document.body, { childList: true, subtree: true });
  schedulePasswordEnhance();

  R.auth = tab => authMarkup(tab === 'register' ? 'register' : 'login');
  R.inbox = () => R.auth('login');
  F['v7-login'] = async (form, data) => {
    let result;
    try {
      result = await Q.request('login', { username: String(data.get('username') || '').trim(), password: data.get('password'), totp: String(data.get('totp') || '').trim(), remember: !!data.get('remember') });
    } catch (error) {
      if (/двухфактор/i.test(error.message || '') && form) {
        const block = form.querySelector('details.v7-2fa');
        if (block && !block.open) { block.open = true; block.querySelector('input')?.focus(); }
      }
      throw error;
    }
    Q.csrf = result.csrf || Q.csrf;
    await Q.reload();
    if (!Q.user) throw Error('Не удалось открыть аккаунт. Обнови страницу и войди ещё раз.');
    Q.hint = false;
    M.closeModal();
    M.navTo(authReturn());
  };
  F['v7-register'] = async (form, data) => {
    if (!data.get('terms')) throw Error('Прими правила сервера, чтобы продолжить.');
    if (data.get('password') !== data.get('repeat')) throw Error('Пароли не совпадают.');
    const result = await api('auth/register', { username: String(data.get('username') || '').trim(), password: data.get('password'), terms: true, remember: false });
    Q.csrf = result.csrf || Q.csrf;
    await Q.reload();
    if (!Q.user) throw Error('Аккаунт создан, но войти не удалось. Попробуй выполнить вход.');
    Q.hint = false;
    M.closeModal();
    M.navTo(authReturn());
  };
  F['v7-code'] = F['v7-reset'] = F['v7-confirm-code'] = async () => { throw Error('Войди по нику и паролю.'); };
  H['v7-vk'] = undefined;
  H['v7-link-email'] = H['v7-unlink'] = H['v7-unlink-confirm'] = H['v7-recovery-help'] = undefined;

  const faqRows = () => (state().faq || []).filter(row => row?.published !== false);
  R.faq = () => {
    const rows = faqRows();
    return `<section class="v9-faq"><header><span class="kicker">CopiMine</span><h1>FAQ</h1></header><div class="v9-faq-list">${rows.map(row => `<details><summary>${E(row.question || row.q || row.title || 'Вопрос')}${icon('plus')}</summary><p>${E(row.answer || row.a || row.body || '')}</p></details>`).join('') || M.empty('Ответы скоро появятся','','message')}</div><footer><span>Не нашёл ответ?</span>${M.btn('Написать в поддержку','faq-contact-support','arrow-right','primary')}</footer></section>`;
  };
  R.page = slug => slug === 'help' ? R.faq() : oldPage(slug);
  H['faq-contact-support'] = () => {
    if (M.me()) return M.navTo('account/support');
    saveAuthReturn('account/support');
    M.navTo('auth/login');
  };
  H['how-buy'] = () => M.navTo('faq');
  const oldShop = R.shop;
  if (oldShop) R.shop = (...args) => oldShop(...args).replaceAll('Как получить покупку', 'FAQ');

  R.account = tab => {
    Q.hint = false;
    if (tab === 'link' && M.me()) return A.heading('Minecraft', 'Привяжи игровой аккаунт по коду из игры.', M.link('В кабинет', '#account', 'arrow-left', 'ghost')) + M.accountTabs('link');
    if (tab !== 'support' || !M.me()) return oldAccount(tab);
    const user = M.me();
    const tickets = (state().tickets || []).filter(ticket => ticket.playerId === user.id || ticket.player === user.name);
    return `<section class="v9-account-support"><header><div><span class="kicker">Личный кабинет</span><h1>Поддержка</h1></div><div class="actions">${M.link('FAQ', '#faq', 'help-circle', 'ghost small')}${M.btn('Новое обращение', 'new-ticket', 'plus', 'primary small')}</div></header>${M.accountTabs('support')}<div class="panel">${tickets.length ? tickets.map(ticket => `<button type="button" class="v9-ticket-row" data-action="ticket-detail" data-id="${E(ticket.id)}"><span><strong>${E(ticket.title)}</strong><small>${E(ticket.id)} · ${M.date(ticket.updated_s ? ticket.updated_s * 1000 : ticket.at, true)}${ticket.messages?.length ? ` · ${ticket.messages.length} сообщений` : ''}</small></span><span>${M.status(ticket.status)}</span>${icon('arrow-right')}</button>`).join('') : M.empty('Обращений пока нет', 'Создай обращение, чтобы написать администрации.', 'chat')}</div></section>`;
  };

  H['new-ticket'] = () => {
    if (!M.me()) { saveAuthReturn('account/support'); M.navTo('auth/login'); return; }
    M.openModal('Новое обращение', `<form data-form="ticket"><label class="field">Тема<select name="type"><option>Покупка</option><option>Вход в игру</option><option>Жалоба</option><option>Другое</option></select></label>${V.field('Коротко о проблеме', 'title', '', 'text', 'required minlength="3" maxlength="160"')}${V.area('Сообщение', 'body', '', 5, 'required minlength="5" maxlength="6000" placeholder="Опиши, что произошло"')}<label class="field">Приложить фото или файл<small>Фото, PDF, лог или архив до 8 МБ. Файл проверяется антивирусом до сохранения.</small><input type="file" name="attachment" accept="image/png,image/jpeg,image/webp,image/gif,text/plain,text/csv,application/json,application/pdf,application/zip,application/gzip" data-ticket-file="new"></label><div class="form-error" role="alert"></div>${V.actions('Отправить обращение')}</form>`);
  };
  F.ticket = async (form, data) => {
    if (!M.me()) throw Error('Войди в аккаунт, чтобы написать в поддержку.');
    const title = String(data.get('title') || '').trim(), body = String(data.get('body') || '').trim();
    if (title.length < 3 || body.length < 5) throw Error('Заполни тему и сообщение.');
    const result = await api('tickets', { title, body, type: String(data.get('type') || 'support') });
    if (result?.ticket?.id) await uploadAttachment(form, result.ticket.id);
    await Q.reload();
    // navTo на текущий маршрут не меняет хеш и не вызывает render(), поэтому
    // окно закрываем сами — иначе форма висит поверх списка.
    M.closeModal();
    M.navTo('account/support');
    M.toast('Обращение отправлено.');
  };

  const filesRow = (ticket) => {
    const files = ticket.files || [];
    if (!files.length) return '';
    return `<div class="v9-ticket-files">${files.map(file => `<a class="v9-ticket-file" href="/ticket-file/${E(file.id)}" target="_blank" rel="noopener">${E(file.name)}<small>${Math.max(1, Math.round((file.bytes || 0) / 1024))} КБ</small></a>`).join('')}</div>`;
  };

  H['ticket-detail'] = button => {
    const ticket = (state().tickets || []).find(row => row.id === button.dataset.id);
    if (!ticket) return;
    const admin = M.UI.page === 'admin';
    const messages = ticket.messages?.length ? ticket.messages : [{ actor: ticket.player || 'Игрок', role: 'player', body: ticket.body, at: ticket.at }, ...(ticket.reply ? [{ actor: 'Команда', role: 'owner', body: ticket.reply, at: ticket.firstReplyAt || ticket.updated_s * 1000 }] : [])];
    const thread = `<div class="v9-ticket-thread">${messages.map(message => `<article class="v9-ticket-message ${message.role === 'player' ? 'from-player' : 'from-team'}"><small>${E(message.actor || (message.role === 'player' ? ticket.player : 'Команда'))} · ${M.date(message.at, true)}</small><p>${E(message.body)}</p></article>`).join('')}</div>${filesRow(ticket)}`;
    const actions = admin
      ? `<form data-form="ticket-answer" class="v9-ticket-reply"><input type="hidden" name="id" value="${E(ticket.id)}">${V.area('Ответ игроку', 'reply', '', 4, 'required minlength="3" maxlength="6000"')}<div class="field-row">${V.select('Статус', 'status', [['open', 'Новое'], ['review', 'В работе'], ['closed', 'Закрыть']], ticket.status)}${V.select('Приоритет', 'priority', [['normal', 'Обычный'], ['high', 'Высокий'], ['urgent', 'Срочный']], ticket.priority || 'normal')}</div><div class="form-error" role="alert"></div>${V.actions('Отправить ответ')}</form><div class="actions mt">${M.btn('Создать расследование', 'case-from-ticket', 'shield', 'ghost small', `data-id="${E(ticket.id)}"`)}</div>`
      : ticket.status === 'closed'
        ? `<p class="v9-ticket-closed">Обращение закрыто. Если вопрос остался, создай новое.</p>${M.btn('Новое обращение', 'new-ticket', 'plus', 'ghost small')}`
        : `<form data-form="ticket-message" class="v9-ticket-reply"><input type="hidden" name="id" value="${E(ticket.id)}">${V.area('Сообщение', 'body', '', 3, 'required minlength="3" maxlength="6000"')}<label class="field">Приложить фото или файл<small>Фото, PDF, лог или архив до 8 МБ. Файл проверяется антивирусом до сохранения.</small><input type="file" name="attachment" accept="image/png,image/jpeg,image/webp,image/gif,text/plain,text/csv,application/json,application/pdf,application/zip,application/gzip" data-ticket-file="${E(ticket.id)}"></label><div class="form-error" role="alert"></div>${V.actions('Отправить')}</form>`;
    M.openModal(`${E(ticket.title)} <span class="v9-ticket-id">${E(ticket.id)}</span>`, `<div class="v9-ticket-meta">${M.status(ticket.status)}${admin ? M.pill(ticket.priority === 'urgent' ? 'Срочно' : ticket.priority === 'high' ? 'Высокий приоритет' : 'Обычный') : ''}${admin ? `<span>${E(ticket.playerName || ticket.player || '')}</span>` : ''}</div>${thread}${actions}`, 'v9-ticket-dialog');
  };

  F['ticket-message'] = async (form, data) => {
    const id = String(data.get('id') || '');
    await api(`tickets/${encodeURIComponent(id)}/messages`, { body: String(data.get('body') || '').trim() });
    await uploadAttachment(form, id);
    await Q.reload();
    M.closeModal();
    M.navTo(`account/support?ticket=${encodeURIComponent(id)}`);
    M.toast('Сообщение отправлено.');
  };
  F['ticket-answer'] = async (form, data) => {
    const id = String(data.get('id') || ''), reply = String(data.get('reply') || '').trim();
    await api(`admin/tickets/${encodeURIComponent(id)}`, { reply, status: data.get('status'), priority: data.get('priority') }, 'PATCH');
    await Q.reload();
    H['ticket-detail']({ dataset: { id } });
    M.toast(reply ? 'Ответ сохранён.' : 'Изменения сохранены.');
  };

  function settingsShell(tab, content) {
    const tabs = [['profile', 'Профиль', 'user'], ['security', 'Безопасность', 'shield'], ['sessions', 'Устройства', 'monitor'], ['notifications', 'Уведомления', 'bell'], ['privacy', 'Приватность', 'eye'], ['interface', 'Интерфейс', 'sliders']];
    return `${A.heading ? A.heading('Настройки', 'Профиль, безопасность и личные предпочтения.', M.link('В кабинет', '#account', 'arrow-left', 'ghost')) : ''}<div class="v71-settings-layout"><aside class="v71-settings-side"><div class="v71-settings-identity">${W.head(M.me(), 40)}<div><strong>${E(M.me()?.displayName || M.me()?.name)}</strong><small>Твой аккаунт</small></div></div><nav aria-label="Настройки">${tabs.map(([key, label, ic]) => `<a href="#settings/${key}" class="${tab === key ? 'active' : ''}" ${tab === key ? 'aria-current="page"' : ''}>${icon(ic)}${label}${icon('chevron-right')}</a>`).join('')}</nav></aside><div class="v71-settings-content">${content}</div></div>`;
  }

  R.settings = (requested = 'profile') => {
    if (!M.me()) return R.auth('login');
    const tab = requested === 'connections' ? 'security' : requested;
    if (tab === 'profile') {
      const p = M.me();
    return settingsShell(tab, `<section class="v7-settings-section"><h2>Профиль</h2><form data-form="v7-profile" class="v7-form-narrow">${V.field('Отображаемое имя', 'displayName', p.displayName || p.name, 'text', 'required minlength="1" maxlength="32" autocomplete="nickname"')}<label class="field">Ник на сайте<input value="${E(p.name)}" readonly aria-readonly="true"></label><p class="hint">Изменение отображаемого имени не меняет игровой ник.</p><div class="form-error" role="alert"></div>${V.actions('Сохранить')}</form></section>`);
    }
    if (tab === 'security') {
      return settingsShell(tab, `<section class="v7-settings-section"><h2>Сменить пароль</h2><form data-form="v7-password" class="v7-form-narrow">${V.field('Текущий пароль', 'current', '', 'password', 'required autocomplete="current-password" maxlength="256"')}${V.field('Новый пароль', 'password', '', 'password', 'required minlength="10" maxlength="256" autocomplete="new-password"')}${V.field('Повтори новый пароль', 'repeat', '', 'password', 'required minlength="10" maxlength="256" autocomplete="new-password"')}<p class="hint">После смены остальные устройства выйдут из аккаунта.</p><div class="form-error" role="alert"></div>${V.actions('Сменить пароль')}</form></section>`);
    }
    if (tab === 'sessions') {
      const rows = state().siteSessions || [];
      return settingsShell(tab, `<section class="v7-settings-section"><div class="panel-head"><h2>Устройства</h2>${rows.some(row => !row.current) ? M.btn('Завершить остальные', 'v9-revoke-others', 'log-out', 'ghost small') : ''}</div>${rows.length ? `<div class="v9-session-list">${rows.map(row => `<article class="v9-session-row"><div>${icon('monitor')}<span><strong>${E(row.device || 'Браузер')}</strong><small>${M.date(row.createdAt, true)} · ${row.current ? 'Это устройство' : 'Активный сеанс'}</small></span></div>${row.current ? M.pill('Текущий', 'green') : M.btn('Завершить', 'v9-revoke-session', 'log-out', 'ghost small', `data-id="${E(row.id)}"`)}</article>`).join('')}</div>` : M.empty('Активных сеансов нет', '', 'monitor')}</section>`);
    }
    if (tab === 'privacy') {
      return settingsShell(tab, `<section class="v7-settings-section"><h2>Приватность</h2><p class="muted">Сайт хранит только то, что нужно для входа, покупок и обращений. Полный перечень — в <a href="#privacy">политике конфиденциальности</a>.</p><div class="v7-form-narrow"><h3>Файлы cookie</h3><p class="hint">Аналитику и настройки интерфейса можно включать и выключать в любой момент.</p>${M.btn('Изменить выбор cookie', 'v6-cookies', 'settings', 'ghost')}<h3 class="mt">Мои данные</h3><p class="hint">Выгрузим аккаунт, сеансы, историю входов, обращения и заказы одним файлом JSON.</p>${M.btn('Скачать мои данные', 'v9-export-data', 'download', 'ghost')}<h3 class="mt">Удаление аккаунта</h3><p class="hint">Аккаунт обезличивается и не может войти. Заказы и журнал действий администрации остаются — этого требует закон. Вернуть аккаунт после удаления нельзя.</p>${M.btn('Удалить аккаунт', 'v9-delete-account', 'trash', 'danger-btn')}</div></section>`);
    }
    const html = oldSettings(tab);
    const parsed = new DOMParser().parseFromString(html, 'text/html');
    const content = parsed.querySelector('.v71-settings-content')?.innerHTML || parsed.querySelector('.v7-settings-layout > div')?.innerHTML || html;
    parsed.querySelector('a[href="#settings/connections"]')?.remove();
    return settingsShell(tab, content.replace(/Профиль, связь и личные предпочтения\./g, 'Профиль, безопасность и настройки.'));
  };

  H['v9-revoke-session'] = button => M.openModal('Завершить сеанс?', `<p>На этом устройстве нужно будет войти снова.</p><div class="v71-dialog-actions">${M.btn('Отмена', 'close', '', 'ghost')}${M.btn('Завершить', 'v9-revoke-session-confirm', 'log-out', 'primary', `data-id="${E(button.dataset.id)}"`)}</div>`);
  H['v9-revoke-session-confirm'] = async button => {
    await api(`account/sessions/${encodeURIComponent(button.dataset.id)}/revoke`, {});
    M.closeModal(); await Q.reload(); M.toast('Сеанс завершён.');
  };
  H['v9-revoke-others'] = () => M.openModal('Завершить остальные сеансы?', `<p>Другие устройства выйдут из аккаунта. Это устройство останется активным.</p><div class="v71-dialog-actions">${M.btn('Отмена', 'close', '', 'ghost')}${M.btn('Завершить сеансы', 'v9-revoke-others-confirm', 'log-out', 'primary')}</div>`);
  H['v9-revoke-others-confirm'] = async () => {
    const result = await api('account/sessions/revoke-others', {});
    M.closeModal(); await Q.reload(); M.toast(`Завершено сеансов: ${result.revoked}.`);
  };

  const call = (path, body) => (window.CM6 && window.CM6.request ? window.CM6.request(path, { method: 'POST', body }) : api(path, body));

  H['v9-export-data'] = async () => {
    const result = await call('account/export', {});
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    link.href = url; link.download = `copimine-account-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 4000);
    M.toast('Файл с данными скачан.');
  };
  H['v9-delete-account'] = () => M.openModal('Удалить аккаунт?', `<p>Аккаунт будет обезличен и больше не сможет войти. Заказы и журнал администрации сохранятся.</p><form data-form="v9-delete-account"><label class="field">Введи слово УДАЛИТЬ<input name="confirm" required autocomplete="off" maxlength="20"></label><label class="field">Пароль<input name="password" type="password" required autocomplete="current-password" maxlength="256"></label><div class="form-error" role="alert"></div><div class="v71-dialog-actions">${M.btn('Отмена', 'close', '', 'ghost')}<button class="btn danger-btn" type="submit">Удалить аккаунт</button></div></form>`);
  F['v9-delete-account'] = async (form, data) => {
    await api('account/delete', { confirm: String(data.get('confirm') || ''), password: String(data.get('password') || '') });
    M.closeModal();
    Q.user = null;
    await Q.reload();
    M.navTo('home');
    M.toast('Аккаунт удалён.');
  };

  function stopMinecraftPoll() { clearInterval(minecraftPoll); minecraftPoll = 0; }
  function startMinecraftPoll() {
    stopMinecraftPoll();
    minecraftPoll = setInterval(async () => {
      const user = currentAccountUser();
      const onLinkPage = M.UI?.page === 'account' && (M.UI.tab === 'link' || user && !user.gameLinkVerified);
      if (!pendingMinecraftLink || !onLinkPage) { stopMinecraftPoll(); return; }
      const status = document.querySelector('[data-link-status]');
      if (Date.now() > pendingMinecraftLink.expiresAt) {
        stopMinecraftPoll();
        if (status) status.textContent = 'Код истёк. Запроси новый.';
        return;
      }
      try {
        const session = await api('session');
        if (session.user?.gameLinkVerified) {
          stopMinecraftPoll(); clearPendingLink();
          try { await Q.reload({ silent: true }); } catch { /* The authenticated session response still carries the confirmed link. */ }
          syncLinkedUser(session.user);
          await openLinkedCabinet();
        }
      } catch {
        if (status) status.textContent = 'Проверка временно недоступна. Повторяем запрос.';
      }
    }, 3000);
  }
  H['v7-game-link'] = () => {
    const user = currentAccountUser();
    if (!user) { saveAuthReturn('account/link'); M.navTo('auth/login'); return; }
    M.navTo('account/link');
  };
  F['production-mc-code'] = async (form, data) => {
    const name = String(data.get('name') || '').trim();
    const result = await api('account/minecraft/request', { name });
    savePendingLink(currentAccountUser(), { name, expiresAt: Number(result.expiresAt || Date.now() + 600000) });
    M.render();
    startMinecraftPoll();
    animateLinkStep();
  };
  F['production-mc-confirm'] = async (form, data) => {
    const result = await api('account/minecraft/confirm', { code: String(data.get('code') || '').trim() });
    try { await Q.reload({ silent: true }); } catch { /* The confirmation response still carries the linked account. */ }
    syncLinkedUser(result.user);
    if (result.user?.gameLinkVerified || currentAccountUser()?.gameLinkVerified) {
      stopMinecraftPoll(); clearPendingLink();
      await openLinkedCabinet();
    } else {
      M.render(); startMinecraftPoll(); animateLinkStep();
    }
  };
  H['production-mc-restart'] = () => { stopMinecraftPoll(); clearPendingLink(); M.render(); animateLinkStep(); };
  window.CMAccountLink = { state: () => {
    const user = currentAccountUser();
    if (pendingMinecraftLink && pendingMinecraftLink.userId !== user?.id) pendingMinecraftLink = null;
    if (!pendingMinecraftLink && user) pendingMinecraftLink = loadPendingLink(user);
    return { user, pending: pendingMinecraftLink ? { name: pendingMinecraftLink.name, expiresAt: pendingMinecraftLink.expiresAt } : null };
  } };

  V.after.push((page, tab) => {
    const user = currentAccountUser();
    const admin = !!window.CM6?.canAdmin?.();
    if (user && !user.gameLinkVerified && !admin && page === 'account') {
      document.title = 'Привязка Minecraft · CopiMine';
      if (tab !== 'link') { M.navTo('account/link'); return; }
      if (pendingMinecraftLink) startMinecraftPoll();
      return;
    }
    if (user?.gameLinkVerified && page === 'account' && tab === 'link') { M.navTo('account'); return; }
    if (page === 'account' && !tab) {
      try {
        if (sessionStorage.getItem(linkedCabinetKey) === '1') {
          sessionStorage.removeItem(linkedCabinetKey);
          requestAnimationFrame(() => window.CMSiteMotion?.accountReveal?.(document.getElementById('view'), motionEnabled()));
        }
      } catch { /* The cabinet remains available without session storage. */ }
    }
    if (page === 'faq') document.title = 'FAQ · CopiMine';
    document.querySelectorAll('.breadcrumb').forEach(crumb => {
      const root = [...crumb.children].find(node => node.tagName === 'SPAN' && ['CopiMine', 'Управление'].includes(node.textContent.trim()));
      if (root) {
        const link = document.createElement('a'); link.href = '#admin/overview'; link.className = 'v9-breadcrumb-home'; link.textContent = root.textContent.trim(); link.setAttribute('aria-label', 'Открыть обзор управления'); root.replaceWith(link);
      }
      [...crumb.children].find(node => node.tagName === 'STRONG')?.setAttribute('aria-current', 'page');
    });
  });
})();
