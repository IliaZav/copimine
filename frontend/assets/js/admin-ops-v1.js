/* CopiMine: админка — поиск игроков, рассылка, бэкапы, бизнес-аналитика. */
(function () {
  'use strict';
  if (typeof window.CM === 'undefined' || typeof window.CM5 === 'undefined' || typeof window.CM6 === 'undefined') return;
  const M = window.CM, W = window.CM5, X = window.CM6;
  const H = M.handlers, F = M.forms;
  const icon = M.icon;
  const can = permission => Boolean((M.me()?.permissions) || []).includes(permission);
  const denied = () => M.empty('Недостаточно прав', 'Этот раздел доступен не всем ролям.', 'lock');

  function sectionHead(title, note, actions) {
    return '<div class="v7-section-label"><div><h2>' + title + '</h2>' +
      (note ? '<p class="hint">' + note + '</p>' : '') + '</div>' + (actions || '') + '</div>';
  }

  W.adminExtensions = W.adminExtensions || {};

  // ---- Поиск игроков -------------------------------------------------------
  const search = { query: '', only: '', result: null, busy: false };

  const priorPlayers = W.adminExtensions.players;
  W.adminExtensions.players = function (sub) {
    const base = priorPlayers ? priorPlayers(sub) : '';
    const filters = [['', 'Все'], ['linked', 'Привязаны к Minecraft'], ['unlinked', 'Без Minecraft'],
      ['verified', 'Почта подтверждена'], ['unverified', 'Почта не подтверждена'], ['blocked', 'Заблокированы']]
      .map(([id, label]) => '<option value="' + id + '"' + (search.only === id ? ' selected' : '') + '>' + label + '</option>').join('');
    const rows = (search.result?.players || []).map(p =>
      '<tr><td><a href="#admin/players?player=' + encodeURIComponent(p.id) + '">' + M.esc(p.name) + '</a></td>' +
      '<td>' + M.esc(p.email || '—') + '</td>' +
      '<td>' + (p.emailVerified ? 'да' : '<span class="muted">нет</span>') + '</td>' +
      '<td>' + M.esc(p.minecraftName || '—') + '</td>' +
      '<td>' + M.esc(p.role) + '</td></tr>').join('');
    const exportHref = '/api/v6/admin/players/export?q=' + encodeURIComponent(search.query) +
      '&only=' + encodeURIComponent(search.only);
    const panel = '<section class="v7-admin-section">' +
      sectionHead('Найти игрока', 'Поиск по логину, почте, нику Minecraft или UUID.',
        '<a class="btn small ghost" href="' + exportHref + '" download>' + icon('download') + 'Выгрузить в CSV</a>') +
      '<form data-form="admin-player-search" class="v7-filter-row">' +
      '<label class="input-wrap">' + icon('search') +
      '<input type="search" name="q" value="' + M.esc(search.query) +
      '" placeholder="Логин, почта или ник" aria-label="Поиск игроков"></label>' +
      '<select name="only" aria-label="Фильтр">' + filters + '</select>' +
      '<button class="btn small" type="submit">Найти</button></form>' +
      (search.result
        ? '<p class="hint">Найдено: ' + search.result.total + '</p>' +
          (rows ? '<table class="table"><thead><tr><th>Логин</th><th>Почта</th><th>Подтверждена</th><th>Minecraft</th><th>Роль</th></tr></thead><tbody>' +
            rows + '</tbody></table>'
            : M.empty('Ничего не найдено', 'Попробуй другой запрос или сними фильтр.', 'search'))
        : '<p class="hint">Введи запрос и нажми «Найти». Без запроса выгрузка вернёт всех игроков.</p>') +
      '</section>';
    return base + panel;
  };

  // ---- Рассылка ------------------------------------------------------------
  W.adminExtensions.broadcast = function () {
    if (!can('players.view')) return denied();
    return '<section class="v7-admin-section">' +
      sectionHead('Рассылка игрокам', 'Письмо уходит только на подтверждённые адреса. Сначала отправь тест себе.') +
      '<form data-form="admin-broadcast">' +
      '<label class="field">Тема<input name="subject" type="text" required maxlength="140" placeholder="Сервер открыт"></label>' +
      '<label class="field">Текст письма<textarea name="body" required rows="10" maxlength="8000" ' +
      'placeholder="Заходите в игру. Быстрый старт займёт пару минут."></textarea></label>' +
      '<label class="check-row"><input type="checkbox" name="testonly"><span>Отправить только мне (тест)</span></label>' +
      '<div class="form-error" role="alert"></div>' +
      '<div class="actions"><button class="btn primary" type="submit">Поставить в очередь</button></div>' +
      '</form><p class="hint">Письма уходят очередью, чтобы не упереться в лимит почтового сервиса.</p></section>';
  };

  // ---- Бэкапы --------------------------------------------------------------
  const backups = { list: null, busy: false };

  W.adminExtensions.backups = function () {
    if (!can('release.manage')) return denied();
    const items = (backups.list?.backups || []).map(b =>
      '<tr><td>' + M.esc(b.name) + '</td><td>' + (b.bytes / 1048576).toFixed(1) + ' МБ</td>' +
      '<td>' + M.date(b.at) + '</td></tr>').join('');
    return '<section class="v7-admin-section">' +
      sectionHead('Резервные копии базы', 'Копии создаются автоматически каждый день. Здесь можно сделать внеплановую.',
        '<button class="btn small" type="button" data-action="admin-backup-run">Создать копию</button>') +
      (items ? '<table class="table"><thead><tr><th>Файл</th><th>Размер</th><th>Создан</th></tr></thead><tbody>' + items + '</tbody></table>'
        : '<p class="hint">' + (backups.list === null ? 'Открываем список…' : 'Копий пока нет.') + '</p>') +
      '</section>';
  };

  // ---- Бизнес-аналитика ----------------------------------------------------
  const business = { data: null, days: 30 };

  function sparkline(series) {
    if (!series || !series.length) return '<p class="hint">Нет данных за период.</p>';
    const max = Math.max(1, ...series.map(p => p.count));
    return '<div class="v7-bars" role="img" aria-label="График по дням">' +
      series.map(p => '<span title="' + p.day + ': ' + p.count + '" style="height:' +
        Math.round(6 + 36 * p.count / max) + '%"></span>').join('') + '</div>';
  }

  const priorStatistics = W.adminExtensions.statistics;
  W.adminExtensions.statistics = function (sub) {
    const data = business.data;
    const base = priorStatistics ? priorStatistics(sub) : '';
    if (!data) return base + '<section class="v7-admin-section">' + sectionHead('Аналитика проекта', 'Считаем показатели…') + '</section>';
    const t = data.totals || {};
    const cards = [['Аккаунты', t.users ?? 0], ['Почта подтверждена', t.verified ?? 0],
      ['Привязаны к игре', t.linked ?? 0], ['Активны за неделю', t.active ?? 0],
      ['Открытых обращений', t.open_tickets ?? 0]];
    return base + '<section class="v7-admin-section">' +
      sectionHead('Аналитика проекта', 'Период: последние ' + data.days + ' дней',
        '<form data-form="admin-business-period" class="v7-filter-row"><select name="days" aria-label="Период">' +
        [7, 30, 90, 365].map(d => '<option value="' + d + '"' + (business.days === d ? ' selected' : '') + '>' +
          d + ' дней</option>').join('') + '</select>' +
        '<button class="btn small" type="submit">Показать</button></form>') +
      '<div class="v7-metric-strip">' + cards.map(([label, value]) =>
        '<div><span>' + label + '</span><strong>' + value + '</strong></div>').join('') + '</div>' +
      '<div class="v7-dashboard-grid">' +
      '<div><h3>Регистрации</h3>' + sparkline(data.series?.registrations) + '</div>' +
      '<div><h3>Привязки Minecraft</h3>' + sparkline(data.series?.linked) + '</div>' +
      '<div><h3>Подтверждения почты</h3>' + sparkline(data.series?.verified) + '</div>' +
      '<div><h3>Обращения</h3>' + sparkline(data.series?.tickets) + '</div>' +
      '</div></section>';
  };

  // ---- Загрузка данных при открытии разделов -------------------------------
  W.after = W.after || [];
  W.after.push(() => {
    if (!X.canAdmin || !X.canAdmin()) return;
    const route = (location.hash.slice(1) || 'home').split('?')[0];
    if (route === 'admin/players' && search.result === null && !search.busy) {
      search.busy = true;
      X.request('/admin/players/search?limit=200')
        .then(data => { search.result = data; })
        .catch(() => { search.result = { total: 0, players: [] }; })
        .finally(() => { search.busy = false; M.render(); });
    }
    if (route === 'admin/backups' && backups.list === null && !backups.busy) {
      backups.busy = true;
      X.request('/admin/backups')
        .then(data => { backups.list = data; })
        .catch(() => { backups.list = { backups: [] }; })
        .finally(() => { backups.busy = false; M.render(); });
    }
    if (route === 'admin/statistics' && business.data === null && !business.busy) {
      business.busy = true;
      X.request('/admin/analytics/business?days=' + business.days)
        .then(data => { business.data = data; })
        .catch(() => { business.data = { days: business.days, totals: {}, series: {} }; })
        .finally(() => { business.busy = false; M.render(); });
    }
  });

  // ---- Формы ---------------------------------------------------------------
  F['admin-player-search'] = async (form, data) => {
    search.query = String(data.get('q') || '').trim();
    search.only = String(data.get('only') || '');
    search.result = await X.request('/admin/players/search?limit=200&q=' + encodeURIComponent(search.query) +
      '&only=' + encodeURIComponent(search.only));
    M.render();
  };

  F['admin-broadcast'] = async (form, data) => {
    const testonly = Boolean(data.get('testonly'));
    const payload = { subject: data.get('subject'), body: data.get('body') };
    if (testonly) {
      payload.test = M.me()?.email || '';
      if (!payload.test) throw Error('У твоего аккаунта нет почты — тест отправить некуда.');
    }
    const result = await X.request('/admin/broadcast', { method: 'POST', body: payload });
    M.toast(result.message || 'Письма поставлены в очередь.');
    form.reset();
  };

  F['admin-business-period'] = async (form, data) => {
    business.days = Number(data.get('days')) || 30;
    business.data = null;
    business.busy = false;
    M.render();
  };

  // ---- Кнопки --------------------------------------------------------------
  H['admin-backup-run'] = async () => {
    const result = await X.request('/admin/backups', { method: 'POST', body: {} });
    M.toast(result.message || 'Копия создана.');
    backups.list = await X.request('/admin/backups');
    M.render();
  };
}());
