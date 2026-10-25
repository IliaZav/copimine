/* Account, FAQ and support workflows backed by the production API. */
(function () {
  'use strict';

  const M = window.CM, V = window.CMV4, Q = window.CMReview, A = window.CM71 || {}, W = window.CM5 || {};
  if (!M || !V || !Q) return;
  const R = M.renderers, F = M.forms, H = M.handlers, E = M.esc, state = () => M.state;
  const oldSettings = R.settings, oldPage = R.page, oldAccount = R.account;
  const authReturnKey = 'copimine.auth.return.v1';
  let minecraftPoll = 0, pendingMinecraftLink = null;

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

  function authReturn() {
    try {
      const route = sessionStorage.getItem(authReturnKey) || '';
      sessionStorage.removeItem(authReturnKey);
      return /^account(?:\/[a-z-]+)?(?:\?ticket=[a-f0-9]+)?$/i.test(route) ? route : 'account';
    } catch { return 'account'; }
  }

  function saveAuthReturn(route) {
    try { sessionStorage.setItem(authReturnKey, route); } catch { /* Optional navigation convenience. */ }
  }

  function authMarkup(tab) {
    const registering = tab === 'register';
    const title = registering ? 'Создать аккаунт' : 'Войти';
    const intro = registering ? 'Создай логин сайта и пароль. Затем привяжи Minecraft.' : 'Введи логин и пароль.';
    return `<div class="v7-auth-layout v9-auth-layout"><aside class="v7-auth-aside"><img src="${M.asset('assets/img/world.webp')}" alt="" decoding="async"></aside><section class="v7-auth-main"><h1>${title}</h1><p class="v7-auth-lead">${intro}</p><nav class="v7-auth-tabs" aria-label="Вход или регистрация"><a class="${registering ? '' : 'active'}" href="#auth/login">Вход</a><a class="${registering ? 'active' : ''}" href="#auth/register">Регистрация</a></nav><form data-form="${registering ? 'v7-register' : 'v7-login'}"><label class="field">Логин сайта<input name="username" type="text" required minlength="3" maxlength="32" pattern="[A-Za-z0-9_]{3,32}" autocomplete="username" autocapitalize="none" spellcheck="false" placeholder="Логин" value=""></label>${registering ? '<label class="field">Пароль<input name="password" type="password" required minlength="12" maxlength="256" autocomplete="new-password" placeholder="Не меньше 12 символов"></label><label class="field">Повтори пароль<input name="repeat" type="password" required minlength="12" maxlength="256" autocomplete="new-password"></label><label class="check-row"><input type="checkbox" name="terms" required><span>Принимаю <a href="#rules">правила сервера</a></span></label>' : '<label class="field">Пароль<input name="password" type="password" required maxlength="256" autocomplete="current-password" placeholder="Пароль аккаунта"></label><label class="field v9-totp-field">Код защиты <input name="totp" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" placeholder="Если включён"></label><label class="check-row"><input type="checkbox" name="remember" checked><span>Оставаться в аккаунте на этом устройстве</span></label>'}<div class="form-error" role="alert"></div><button type="submit" class="btn primary wide">${title}${icon('arrow-right')}</button></form>${registering ? '<p class="v7-auth-bottom">Уже зарегистрирован? <a href="#auth/login">Войти</a></p>' : '<p class="v7-auth-bottom">Нет аккаунта? <a href="#auth/register">Создать</a></p>'}</section></div>`;
  }

  R.auth = tab => authMarkup(tab === 'register' ? 'register' : 'login');
  R.inbox = () => R.auth('login');
  F['v7-login'] = async (form, data) => {
    const result = await Q.request('login', { username: String(data.get('username') || '').trim(), password: data.get('password'), totp: String(data.get('totp') || ''), remember: !!data.get('remember') });
    Q.csrf = result.csrf || Q.csrf;
    await Q.reload();
    if (!Q.user) throw Error('Не удалось открыть аккаунт. Обнови страницу и войди ещё раз.');
    Q.hint = false;
    M.closeModal();
    M.navTo((M.me() || Q.user)?.gameLinkVerified ? authReturn() : 'account/link');
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
    M.navTo((M.me() || Q.user)?.gameLinkVerified ? authReturn() : 'account/link');
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
    if (tab !== 'support' || !M.me()) return oldAccount(tab);
    const user = M.me();
    const tickets = (state().tickets || []).filter(ticket => ticket.playerId === user.id || ticket.player === user.name);
    return `<section class="v9-account-support"><header><div><span class="kicker">Личный кабинет</span><h1>Поддержка</h1></div><div class="actions">${M.link('FAQ', '#faq', 'help-circle', 'ghost small')}${M.btn('Новое обращение', 'new-ticket', 'plus', 'primary small')}</div></header>${M.accountTabs('support')}<div class="panel">${tickets.length ? tickets.map(ticket => `<button type="button" class="v9-ticket-row" data-action="ticket-detail" data-id="${E(ticket.id)}"><span><strong>${E(ticket.title)}</strong><small>${E(ticket.id)} · ${M.date(ticket.updated_s ? ticket.updated_s * 1000 : ticket.at, true)}${ticket.messages?.length ? ` · ${ticket.messages.length} сообщений` : ''}</small></span><span>${M.status(ticket.status)}</span>${icon('arrow-right')}</button>`).join('') : M.empty('Обращений пока нет', 'Создай обращение, чтобы написать администрации.', 'chat')}</div></section>`;
  };

  H['new-ticket'] = () => {
    if (!M.me()) { saveAuthReturn('account/support'); M.navTo('auth/login'); return; }
    M.openModal('Новое обращение', `<form data-form="ticket"><label class="field">Тема<select name="type"><option>Покупка</option><option>Вход в игру</option><option>Жалоба</option><option>Другое</option></select></label>${V.field('Коротко о проблеме', 'title', '', 'text', 'required minlength="3" maxlength="160"')}${V.area('Сообщение', 'body', '', 5, 'required minlength="5" maxlength="6000" placeholder="Опиши, что произошло"')}<div class="form-error" role="alert"></div>${V.actions('Отправить обращение')}</form>`);
  };
  F.ticket = async (form, data) => {
    if (!M.me()) throw Error('Войди в аккаунт, чтобы написать в поддержку.');
    const title = String(data.get('title') || '').trim(), body = String(data.get('body') || '').trim();
    if (title.length < 3 || body.length < 5) throw Error('Заполни тему и сообщение.');
    await api('tickets', { title, body, type: String(data.get('type') || 'support') });
    await Q.reload();
    M.navTo('account/support');
    M.toast('Обращение отправлено.');
  };

  H['ticket-detail'] = button => {
    const ticket = (state().tickets || []).find(row => row.id === button.dataset.id);
    if (!ticket) return;
    const admin = M.UI.page === 'admin';
    const messages = ticket.messages?.length ? ticket.messages : [{ actor: ticket.player || 'Игрок', role: 'player', body: ticket.body, at: ticket.at }, ...(ticket.reply ? [{ actor: 'Команда', role: 'owner', body: ticket.reply, at: ticket.firstReplyAt || ticket.updated_s * 1000 }] : [])];
    const thread = `<div class="v9-ticket-thread">${messages.map(message => `<article class="v9-ticket-message ${message.role === 'player' ? 'from-player' : 'from-team'}"><small>${E(message.actor || (message.role === 'player' ? ticket.player : 'Команда'))} · ${M.date(message.at, true)}</small><p>${E(message.body)}</p></article>`).join('')}</div>`;
    const actions = admin
      ? `<form data-form="ticket-answer" class="v9-ticket-reply"><input type="hidden" name="id" value="${E(ticket.id)}">${V.area('Ответ игроку', 'reply', '', 4, 'required minlength="3" maxlength="6000"')}<div class="field-row">${V.select('Статус', 'status', [['open', 'Новое'], ['review', 'В работе'], ['closed', 'Закрыть']], ticket.status)}${V.select('Приоритет', 'priority', [['normal', 'Обычный'], ['high', 'Высокий'], ['urgent', 'Срочный']], ticket.priority || 'normal')}</div><div class="form-error" role="alert"></div>${V.actions('Отправить ответ')}</form><div class="actions mt">${M.btn('Создать расследование', 'case-from-ticket', 'shield', 'ghost small', `data-id="${E(ticket.id)}"`)}</div>`
      : ticket.status === 'closed'
        ? `<p class="v9-ticket-closed">Обращение закрыто. Если вопрос остался, создай новое.</p>${M.btn('Новое обращение', 'new-ticket', 'plus', 'ghost small')}`
        : `<form data-form="ticket-message" class="v9-ticket-reply"><input type="hidden" name="id" value="${E(ticket.id)}">${V.area('Сообщение', 'body', '', 3, 'required minlength="3" maxlength="6000"')}<div class="form-error" role="alert"></div>${V.actions('Отправить')}</form>`;
    M.openModal(`${E(ticket.title)} <span class="v9-ticket-id">${E(ticket.id)}</span>`, `<div class="v9-ticket-meta">${M.status(ticket.status)}${admin ? M.pill(ticket.priority === 'urgent' ? 'Срочно' : ticket.priority === 'high' ? 'Высокий приоритет' : 'Обычный') : ''}${admin ? `<span>${E(ticket.playerName || ticket.player || '')}</span>` : ''}</div>${thread}${actions}`, 'v9-ticket-dialog');
  };

  F['ticket-message'] = async (form, data) => {
    const id = String(data.get('id') || '');
    await api(`tickets/${encodeURIComponent(id)}/messages`, { body: String(data.get('body') || '').trim() });
    await Q.reload();
    M.navTo(`account/support?ticket=${encodeURIComponent(id)}`);
    M.toast('Сообщение отправлено.');
  };
  F['ticket-answer'] = async (form, data) => {
    const id = String(data.get('id') || ''), reply = String(data.get('reply') || '').trim();
    await api(`admin/tickets/${encodeURIComponent(id)}`, { reply, status: data.get('status'), priority: data.get('priority') });
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
      return settingsShell(tab, `<section class="v7-settings-section"><h2>Сменить пароль</h2><form data-form="v7-password" class="v7-form-narrow">${V.field('Текущий пароль', 'current', '', 'password', 'required autocomplete="current-password" maxlength="256"')}${V.field('Новый пароль', 'password', '', 'password', 'required minlength="12" maxlength="256" autocomplete="new-password"')}${V.field('Повтори новый пароль', 'repeat', '', 'password', 'required minlength="12" maxlength="256" autocomplete="new-password"')}<p class="hint">После смены остальные устройства выйдут из аккаунта.</p><div class="form-error" role="alert"></div>${V.actions('Сменить пароль')}</form></section>`);
    }
    if (tab === 'sessions') {
      const rows = state().siteSessions || [];
      return settingsShell(tab, `<section class="v7-settings-section"><div class="panel-head"><h2>Устройства</h2>${rows.some(row => !row.current) ? M.btn('Завершить остальные', 'v9-revoke-others', 'log-out', 'ghost small') : ''}</div>${rows.length ? `<div class="v9-session-list">${rows.map(row => `<article class="v9-session-row"><div>${icon('monitor')}<span><strong>${E(row.device || 'Браузер')}</strong><small>${M.date(row.createdAt, true)} · ${row.current ? 'Это устройство' : 'Активный сеанс'}</small></span></div>${row.current ? M.pill('Текущий', 'green') : M.btn('Завершить', 'v9-revoke-session', 'log-out', 'ghost small', `data-id="${E(row.id)}"`)}</article>`).join('')}</div>` : M.empty('Активных сеансов нет', '', 'monitor')}</section>`);
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

  function stopMinecraftPoll() { clearInterval(minecraftPoll); minecraftPoll = 0; }
  function startMinecraftPoll() {
    stopMinecraftPoll();
    minecraftPoll = setInterval(async () => {
      const user = M.me();
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
          stopMinecraftPoll(); pendingMinecraftLink = null;
          await Q.reload();
          M.toast('Minecraft привязан.');
          M.navTo('account');
        }
      } catch {
        if (status) status.textContent = 'Проверка временно недоступна. Повторяем запрос.';
      }
    }, 3000);
  }
  H['v7-game-link'] = () => {
    const user = M.me();
    if (!user) { saveAuthReturn('settings/security'); M.navTo('auth/login'); return; }
    if (user.gameLinkVerified) return M.openModal('Minecraft привязан', `<p>К аккаунту привязан ник <strong>${E(user.minecraftName || user.name)}</strong>.</p>${M.link('Кабинет', '#account', 'arrow-left', 'ghost')}`);
    M.openModal('Привязать Minecraft', `<ol class="v7-steps"><li><strong>Укажи игровой ник</strong><p>Используй ник аккаунта, которым входишь на сервере.</p></li><li><strong>Получи личное сообщение</strong><p>Сайт отправит одноразовый код через игровой сервер.</p></li><li><strong>Введи код здесь</strong><p>После подтверждения профиль и статус обновятся в кабинете.</p></li></ol><form data-form="production-mc-code">${V.field('Ник Minecraft', 'name', '', 'text', 'required pattern="[A-Za-z0-9_]{3,16}" minlength="3" maxlength="16" autocomplete="off" autocapitalize="none"')}<div class="form-error" role="alert"></div>${V.actions('Получить код')}</form>`);
  };
  F['production-mc-code'] = async (form, data) => {
    const name = String(data.get('name') || '').trim();
    const result = await api('account/minecraft/request', { name });
    pendingMinecraftLink = { name, expiresAt: Number(result.expiresAt || Date.now() + 600000) };
    M.render();
    startMinecraftPoll();
  };
  F['production-mc-confirm'] = async (form, data) => {
    await api('account/minecraft/confirm', { code: String(data.get('code') || '').trim() });
    await Q.reload();
    if ((M.me() || Q.user)?.gameLinkVerified) {
      stopMinecraftPoll(); pendingMinecraftLink = null; M.toast('Minecraft привязан.'); M.navTo('account');
    } else {
      M.render(); startMinecraftPoll();
    }
  };
  H['production-mc-restart'] = () => { stopMinecraftPoll(); pendingMinecraftLink = null; M.render(); };
  window.CMAccountLink = { state: () => ({ user: M.me() || Q.user || null, pending: pendingMinecraftLink ? { ...pendingMinecraftLink } : null }) };

  V.after.push((page, tab) => {
    if (page === 'account' && pendingMinecraftLink) startMinecraftPoll();
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
