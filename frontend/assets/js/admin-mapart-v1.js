/* Map-art upload journal for the admin panel. */
(function () {
  'use strict';
  const M = CM, V = CMV4, X = CM6, E = M.esc;
  const worlds = { overworld: 'Обычный', nether: 'Незер', end: 'Энд' };
  const when = (value) => value ? new Date(value * 1000).toLocaleString('ru-RU', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

  function mapLink(viewer, item) {
    if (!viewer || item.x == null) return '';
    const dimension = item.world || 'overworld';
    return `${viewer}#admin_${dimension}:${item.x}:${item.y}:${item.z}:240:0:0:0:1:flat`;
  }

  function row(item, viewer) {
    const link = mapLink(viewer, item);
    const where = item.x == null ? 'Точка не определилась' : `${worlds[item.world] || 'Обычный'} · ${item.x} ${item.y} ${item.z}`;
    const thumb = item.image
      ? `<img src="${E(item.url)}" alt="" loading="lazy" width="72" height="72">`
      : `<span class="cm-mapart-log-thumb">${icon('grid')}</span>`;
    return `<article class="cm-mapart-log-row">
      ${thumb}
      <div class="cm-mapart-log-main"><strong>${E(item.name)}</strong><span>${E(item.author_name)} · ${E(when(item.created_s))}</span></div>
      <div class="cm-mapart-log-where">${E(where)}</div>
      <div class="cm-mapart-log-actions">
        ${link ? `<a class="btn ghost small" href="${E(link)}" target="_blank" rel="noopener">${icon('map')}На карте</a>` : ''}
        <button type="button" class="btn ghost small" data-action="mapart-admin-copy" data-command="${E(item.command)}">${icon('copy')}Команда</button>
      </div>
    </article>`;
  }

  function view(items, viewer) {
    if (!items.length) return `<div class="cm-mapart-log-empty">${icon('grid')}<strong>Пока пусто</strong><span>Игроки ещё не загружали картины.</span></div>`;
    const located = items.filter((item) => item.x != null).length;
    const authors = new Set(items.map((item) => item.author_name)).size;
    return `<div class="cm-mapart-log-stats"><div><span>Загрузок</span><strong>${items.length}</strong></div><div><span>С точкой</span><strong>${located}</strong></div><div><span>Авторов</span><strong>${authors}</strong></div></div>
      <div class="cm-mapart-log-list">${items.map((item) => row(item, viewer)).join('')}</div>`;
  }

  async function mount() {
    const root = document.querySelector('[data-mapart-admin]');
    if (!root || root.dataset.loaded) return;
    root.dataset.loaded = '1';
    root.innerHTML = `<div class="cm-mapart-log-loading"><span></span><span></span><span></span></div>`;
    try {
      const [log, map] = await Promise.all([X.request('/admin/mapart'), X.request('/admin/map').catch(() => null)]);
      root.innerHTML = `<div class="cm-mapart-log-head"><div><p>Снимки, которые игроки загрузили с сайта. Точка на карте ставится автоматически.</p></div><button type="button" class="btn ghost small" data-action="mapart-admin-refresh">${icon('refresh')}Обновить</button></div>${view(log.items || [], map?.viewerUrl || '')}`;
    } catch (error) {
      root.innerHTML = `<div class="cm-mapart-log-empty">${icon('alert')}<strong>Журнал не открылся</strong><span>${E(error?.message || 'Повтори попытку.')}</span><button type="button" class="btn ghost small" data-action="mapart-admin-refresh">Повторить</button></div>`;
    }
  }

  M.handlers['mapart-admin-refresh'] = () => {
    const root = document.querySelector('[data-mapart-admin]');
    if (root) root.dataset.loaded = '';
    else M.render();
    void mount();
  };
  M.handlers['mapart-admin-copy'] = (button) => {
    M.copyText(button.dataset.command || '');
    M.toast('Команда скопирована.');
  };

  V.after.push((page, tab) => {
    if (page === 'admin' && tab === 'mapart') void mount();
  });
})();
