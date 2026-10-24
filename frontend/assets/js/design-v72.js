/* CopiMine 7.2 — visual revision of the existing local review build.
 * Actual project photos and skin atlases are used, never another server's media.
 * No SMTP, OAuth, payment or game-server implementation is added here.
 */
(function () {
  'use strict';
  const M = CM, V = CMV4, A = CM71, W = CM5, Q = CMReview;
  const R = M.renderers, H = M.handlers, E = M.esc, state = () => M.state;
  const api = window.CM72 = { galleryIndex: 0, skinYaw: -25, skinPitch: -6 };
  const photo = 'assets/photos/copimine-night.webp';
  const thumb = 'assets/photos/copimine-night-small.webp';
  const asset = path => String(path || '').startsWith('/api/v6/') ? path : M.asset(path);
  const motion = () => state().preferences.motion !== false && !state().reviewPersonal?.reduceMotion && !matchMedia('(prefers-reduced-motion: reduce)').matches;
  const el = id => document.getElementById(id);
  const publicName = p => E(p?.displayName || p?.name || 'Игрок');


  function publishedPagesInNavigation() {
    return (state().pages || []).filter(page => page?.published?.nav === true && !['rules', 'help', 'privacy'].includes(page.slug) && /^[a-z0-9][a-z0-9-]{1,39}$/.test(page.slug));
  }

  function publicNavLink(slug, label, active) {
    const selected = active === slug || active === 'page' && M.UI.tab === slug;
    return `<a href="#${E(slug)}" class="${selected ? 'active' : ''}" ${selected ? 'aria-current="page"' : ''}>${E(label)}</a>`;
  }
  const arrow = (name, action, extra = '') => `<button type="button" class="cm72-round" data-action="${action}" ${extra} aria-label="${name}" title="${name}">${icon(action.includes('prev') ? 'arrow-left' : 'arrow-right')}</button>`;

  // There is exactly one account menu per public page, including narrow layouts.
  // Sign-out lives here, not in the header, mobile menu and settings at once.
  header = function (active) {
    if (active === 'admin') return '';
    const p = M.me(), count = state().cart.reduce((n, x) => n + x.qty, 0);
    const unread = state().reviewPersonal?.notifyTransfers === false ? 0 : (A.wallet?.unread || 0);
    const menu = p ? `<div class="v71-user-menu"><button type="button" class="v71-user-trigger" data-action="v71-user-menu" aria-haspopup="menu" aria-expanded="false" aria-label="Меню аккаунта">${W.head(p, 30)}<span>${publicName(p)}</span>${icon('chevron-down')}</button><div class="v71-user-popover" role="menu" hidden><div class="v71-popover-user"><strong>${publicName(p)}</strong></div><a role="menuitem" href="#account">${icon('user')}Личный кабинет</a><a role="menuitem" href="#settings/profile">${icon('settings')}Настройки</a>${CM6.canAdmin() ? `<a role="menuitem" href="#admin/overview">${icon('sliders')}Управление</a>` : ''}<button role="menuitem" type="button" class="v71-exit-link v71-danger" data-action="logout">${icon('log-out')}Выйти</button></div></div>` : '<a class="btn cm72-signin" href="#auth/login">Войти</a>';
    return `<div class="container cm72-header-container"><header class="site-header"><a class="brand" href="#home" aria-label="CopiMine — главная"><img class="wordmark" src="${asset('assets/img/logo.png')}" alt="CopiMine" width="166" height="34"></a><nav class="site-nav" aria-label="Основное меню">${[['shop', 'Магазин'], ['donate', 'Донат'], ['map', 'Карта'], ['rules', 'Правила'], ['faq', 'FAQ']].map(([id, label]) => publicNavLink(id,label,active)).join('')}${publishedPagesInNavigation().map(page=>publicNavLink(page.slug,page.published.title||page.slug,active)).join('')}</nav><div class="header-actions">${p ? `<button type="button" class="v71-notification-button icon-btn" data-action="v71-notifications" aria-label="Уведомления${unread ? ': ' + unread : ''}" title="Уведомления">${icon('bell')}${unread ? `<b>${unread}</b>` : ''}</button>` : ''}<a href="#cart" class="v7-cart-link" aria-label="Корзина" title="Корзина">${icon('bag')}<span data-cart-count ${count ? '' : 'hidden'}>${count}</span></a>${menu}<button type="button" class="icon-btn v7-menu-toggle" data-action="v7-menu" aria-label="Открыть меню">${icon('menu')}</button></div></header></div>`;
  };
  H['v7-menu'] = () => M.openModal('Разделы сайта', `<nav class="v7-mobile-menu">${[['home', 'Главная', 'home'], ['shop', 'Магазин', 'bag'], ['donate', 'Донат', 'heart'], ['map', 'Карта', 'map'], ['rules', 'Правила', 'shield'], ['faq', 'FAQ', 'help-circle']].concat(publishedPagesInNavigation().map(page => [page.slug,page.published.title||page.slug,'file'])).map(([url, text, ic]) => `<a href="#${url}">${icon(ic)}${text}${icon('arrow-right')}</a>`).join('')}</nav>`);

  function seedGallery() {
    if (state().design73Gallery) return;
    state().gallery=(state().gallery||[]).filter(x=>x.kind!=='map'&&!['world-save','map-nether','map-end'].includes(x.id));
    const first=state().gallery.find(x=>x.id==='world-original');
    if(first)Object.assign(first,{kind:'photo',src:photo,title:'Вечер в CopiMine',caption:'Ночь над холмами',position:0});
    const incoming=[{"id":"project-riverside","title":"Над рекой","caption":"Скриншот проекта","src":"assets/photos/copimine-riverside.webp","alt":"Холмы, река и деревянный мост","kind":"photo","published":true,"position":1},{"id":"project-mountain","title":"На вершине","caption":"Скриншот проекта","src":"assets/photos/copimine-mountain.webp","alt":"Игроки на снежном склоне","kind":"photo","published":true,"position":2},{"id":"project-arena","title":"Вечер на арене","caption":"Скриншот проекта","src":"assets/photos/copimine-arena.webp","alt":"Игровой кадр с существами CopiMine","kind":"photo","published":true,"position":3}];
    incoming.forEach(x=>{if(!state().gallery.some(y=>y.id===x.id))state().gallery.push(x);});
    state().design72Gallery=true;state().design73Gallery=true;
  }
  function galleryItems() {
    seedGallery();
    return state().gallery.filter(i => i.published && i.kind !== 'map' && !String(i.src || '').includes('/map/')).sort((a, b) => (a.position || 0) - (b.position || 0));
  }
  function carousel() {
    const list = galleryItems();
    if (!list.length) return '';
    api.galleryIndex = Math.min(api.galleryIndex, list.length - 1);
    const current = api.galleryIndex;
    return `<section id="cm72-carousel" class="cm72-gallery cm72-reveal" role="region" aria-roledescription="карусель" aria-label="Скриншоты CopiMine" tabindex="0"><div class="cm72-section-heading"><div><h2>Скриншоты CopiMine</h2><p>Игровые места и постройки.</p></div><div class="cm72-gallery-controls"><span id="cm72-gallery-count" aria-live="polite">${String(current + 1).padStart(2, '0')} <i>/ ${String(list.length).padStart(2, '0')}</i></span>${list.length > 1 ? arrow('Предыдущий кадр', 'cm72-gallery-prev') + arrow('Следующий кадр', 'cm72-gallery-next') : ''}</div></div><div class="cm72-gallery-stage" id="cm72-gallery-stage" aria-label="Перетаскивай кадры мышью или пальцем"><div class="cm72-gallery-track">${list.map((item, i) => {
      const offset = i === current ? 0 : i === (current + 1) % list.length ? 1 : i === (current + list.length - 1) % list.length ? -1 : 2;
      const visible = Math.abs(offset) <= 1;
      return `<button class="cm72-carousel-slide" type="button" data-action="cm72-gallery-open" data-index="${i}" data-position="${offset}" aria-label="${E(item.title)}. ${E(item.caption || '')}" aria-current="${String(i === current)}" aria-hidden="${String(!visible)}" tabindex="${visible ? '0' : '-1'}"><img src="${asset(item.src)}" alt="${E(item.alt || item.title)}" loading="lazy" decoding="async" draggable="false"><span class="cm72-carousel-caption"><strong>${E(item.title)}</strong><small>${E(item.caption || '')}</small></span></button>`;
    }).join('')}</div></div></section>`;
  }
  function setGallery(index) {
    const list = galleryItems();
    if (!list.length) return;
    api.galleryIndex = (index + list.length) % list.length;
    const count = el('cm72-gallery-count');
    if (count) count.innerHTML = String(api.galleryIndex + 1).padStart(2, '0') + ' <i>/ ' + String(list.length).padStart(2, '0') + '</i>';
    const slides = document.querySelectorAll('.cm72-carousel-slide');
    if (slides.length) window.CMSiteMotion?.carousel(slides, api.galleryIndex, motion());
  }
  H['cm72-gallery-prev'] = () => setGallery(api.galleryIndex - 1);
  H['cm72-gallery-next'] = () => setGallery(api.galleryIndex + 1);
  H['cm72-gallery-pick'] = b => setGallery(Number(b.dataset.index));
  function galleryModal() {
    const list = galleryItems(), x = list[api.galleryIndex]; if (!x) return;
    if (!el('modal')?.open) api.galleryOpener = document.activeElement;
    M.openModal(x.title, `<div class="cm72-lightbox"><img src="${asset(x.src)}" alt="${E(x.alt || x.title)}"><div class="cm72-lightbox-bottom"><p>${E(x.caption || '')}</p><div>${arrow('Предыдущий кадр', 'cm72-lightbox-prev')}<span>${api.galleryIndex + 1} / ${list.length}</span>${arrow('Следующий кадр', 'cm72-lightbox-next')}</div></div></div>`, 'cm72-lightbox-dialog');
    if (api.galleryOpener?.isConnected) M.UI.backFocus = api.galleryOpener;
  }


  function heroCopy() {
    const config = state().config || {};
    const lines = String(config.headline || 'Строй. Торгуй.\nИграй с друзьями.').split(/\r?\n/);
    const title = lines.shift()?.trim() || 'CopiMine';
    const accent = lines.join(' ').trim();
    const description = String(config.description || 'Minecraft-сервер с экономикой, выборами и событиями.');
    return `<h1>${E(title)}${accent ? `<br><span>${E(accent)}</span>` : ''}</h1><p>${E(description)}</p>`;
  }


  function mapPreview() {
    const mapState = window.CMBlueMap?.state?.() || {};
    const info = mapState.info || null;
    const dimensions = info?.dimensions || [];
    const available = dimensions.filter(item => item.available).map(item => item.name);
    const title = !mapState.loaded ? 'Проверяем публикацию' : mapState.error ? 'Карта временно недоступна' : !info ? 'Публикации пока нет' : info.enabled === false ? 'Карта скрыта' : available.length ? 'Карта опубликована' : 'Публикации пока нет';
    const detail = available.length ? `Доступны: ${available.join(', ')}` : mapState.error ? 'Открой карту и повтори запрос.' : !mapState.loaded ? 'Проверяем доступность миров.' : 'Сохранённые участки появятся после первой публикации.';
    return `<a href="#map" class="cm72-map-image cm72-map-preview" aria-label="${E(`Карта миров. ${title}. ${detail} Открыть карту.`)}"><img src="${asset('assets/img/map-world.webp')}" alt="" loading="lazy" decoding="async"><div class="cm72-map-preview-mark">${icon('map')}</div><div class="cm72-map-preview-copy"><span>Обычный мир · Незер · Энд</span><strong>${E(title)}</strong><small>${E(detail)}</small></div><span class="cm72-map-preview-link">Открыть карту ${icon('arrow-up-right')}</span></a>`;
  }
  function mapSchedule() {
    const mapState = window.CMBlueMap?.state?.() || {};
    const available = mapState.info?.dimensions?.some(item => item.available);
    if (!mapState.loaded) return 'Проверяем статус карты';
    if (mapState.error) return 'Статус карты недоступен';
    if (!available || mapState.info?.enabled === false) return 'Публикации пока нет';
    return CM6Domain.mapInterval(state().mapSettings.intervalMinutes, state().mapSettings.enabled && state().mapSettings.autoRefresh !== false);
  }
  let draggedGallery = false;
  H['cm72-gallery-open'] = b => {
    if (draggedGallery) { draggedGallery = false; return; }
    const selected = Number(b?.dataset.index);
    if (Number.isInteger(selected) && selected !== api.galleryIndex) { setGallery(selected); return; }
    galleryModal();
  };
  H['gallery-open'] = b => { const i = galleryItems().findIndex(x => x.id === b.dataset.id); if (i >= 0) setGallery(i); galleryModal(); };
  H['cm72-lightbox-prev'] = () => { setGallery(api.galleryIndex - 1); galleryModal(); };
  H['cm72-lightbox-next'] = () => { setGallery(api.galleryIndex + 1); galleryModal(); };
  document.addEventListener('keydown', e => {
    const lightbox = el('modal')?.open && el('modal').classList.contains('cm72-lightbox-dialog');
    if (api.galleryOpener?.isConnected) M.UI.backFocus = api.galleryOpener;
    if (!lightbox && !e.target.closest?.('#cm72-carousel')) return;
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
      e.preventDefault(); setGallery(api.galleryIndex + (e.key === 'ArrowRight' ? 1 : -1)); if (lightbox) galleryModal();
    }
  });

  R.home = () => `<section class="cm72-hero"><img class="cm72-hero-photo" src="${asset(photo)}" alt="Ночной пейзаж CopiMine" fetchpriority="high"><div class="cm72-hero-content">${heroCopy()}${M.btn('Начать играть', 'play', 'arrow-right', 'primary cm72-start')}<button type="button" class="cm72-address" data-action="copy-ip" aria-label="Скопировать адрес сервера"><span>${E(state().config.serverIp)}</span>${icon('copy')}</button></div></section>
  <section id="cm72-about" class="cm72-about cm72-reveal"><div class="cm72-intro"><h2>Банк, выборы, магазин</h2><p>Переводи АР, голосуй за президента и покупай игровые предметы.</p></div><div class="cm72-feature-list"><a href="#account/transfers"><span class="cm72-feature-number">01</span><div><h3>Банк и переводы</h3><p>Переводи АР игрокам и проверяй историю операций.</p></div>${icon('arrow-up-right')}</a><a href="#account/elections"><span class="cm72-feature-number">02</span><div><h3>Выборы и казна</h3><p>Читай программы кандидатов, голосуй и смотри расходы казны.</p></div>${icon('arrow-up-right')}</a><a href="#shop"><span class="cm72-feature-number">03</span><div><h3>Предметы CopiMine</h3><p>Перед покупкой проверь описание, цену и валюту.</p></div>${icon('arrow-up-right')}</a></div></section>
  ${carousel()}
  <section class="cm72-shop-section cm72-reveal"><div class="cm72-section-heading"><div><h2>Предметы из каталога</h2><p>Проверь описание и цену перед покупкой.</p></div><a class="text-link" href="#shop">Весь каталог ${icon('arrow-right')}</a></div><div class="product-grid four">${['return_stone', 'infinite_torch', 'pohuy_na_debaffy_amulet', 'gde_moy_lut_blyat_compass'].map(M.item).filter(p => p?.enabled).map(M.productCard).join('')}</div></section>
  <section class="cm72-map-section cm72-reveal"><div class="cm72-map-copy"><h2>Карта миров</h2><p>Проверь, какие миры уже опубликованы, и выбери измерение.</p>${M.link('Открыть карту', '#map', 'arrow-up-right', 'primary')}<small>${E(mapSchedule())}</small></div>${mapPreview()}</section>
  <section class="cm72-faq cm72-reveal"><div><h2>Перед первым<br>входом</h2><p>О входе, покупках и карте.</p><a class="text-link" href="#rules">Правила сервера ${icon('arrow-right')}</a></div><div class="faq-list">${state().faq.filter(x => x.published).map(x => `<details name="cm72-faq"><summary>${E(x.question)}${icon('plus')}</summary><p>${E(x.answer)}</p></details>`).join('')}</div></section>
  <section class="cm72-community cm72-reveal"><img src="${asset(thumb)}" alt="" loading="lazy"><div><h2>Есть вопрос?</h2><p>Открой раздел «Поддержка» в кабинете и напиши администрации.</p></div>${M.link('Написать в поддержку', '#account/support', 'chat', 'primary')}</section>`;
  H['cm72-scroll-about'] = () => el('cm72-about')?.scrollIntoView({ behavior: motion() ? 'smooth' : 'instant', block: 'start' });

  // Skin geometry uses the Minecraft PNG atlas. The fallback is visibly labelled,
  // not presented as a downloaded skin of the connected player.
  const facesCache = new Map();
  function loadImage(src) { return new Promise((resolve, reject) => { const im = new Image(); im.onload = () => resolve(im); im.onerror = () => reject(new Error('Не удалось открыть текстуру скина.')); im.src = src; }); }
  async function skinGeometry(src, slim) {
    const key = src + ':' + slim; if (facesCache.has(key)) return facesCache.get(key);
    const promise = (async () => {
      const image = await loadImage(src), modern = image.height === 64, arm = slim && modern ? 3 : 4;
      const parts = [
        { name: 'head', x: 4, y: 0, w: 8, h: 8, d: 8, uv: [0, 0], overlay: [32, 0] },
        { name: 'body', x: 4, y: 8, w: 8, h: 12, d: 4, uv: [16, 16], overlay: modern ? [16, 32] : null },
        { name: 'right-arm', x: 4 - arm, y: 8, w: arm, h: 12, d: 4, uv: [40, 16], overlay: modern ? [40, 32] : null },
        { name: 'left-arm', x: 12, y: 8, w: arm, h: 12, d: 4, uv: modern ? [32, 48] : [40, 16], overlay: modern ? [48, 48] : null, mirror: !modern },
        { name: 'right-leg', x: 4, y: 20, w: 4, h: 12, d: 4, uv: [0, 16], overlay: modern ? [0, 32] : null },
        { name: 'left-leg', x: 8, y: 20, w: 4, h: 12, d: 4, uv: modern ? [16, 48] : [0, 16], overlay: modern ? [0, 48] : null, mirror: !modern }
      ];
      const slice = (u, v, w, h, mirror) => { const c = document.createElement('canvas'); c.width = w; c.height = h; const ctx = c.getContext('2d'); ctx.imageSmoothingEnabled = false; if (mirror) { ctx.translate(w, 0); ctx.scale(-1, 1); } ctx.drawImage(image, u, v, w, h, 0, 0, w, h); return c.toDataURL('image/png'); };
      const scale = 7;
      function cube(p, uv, overlay) {
        const [u, v] = uv, { w, h, d } = p;
        const faces = [ ['front', u + d, v + d, w, h], ['back', u + 2 * d + w, v + d, w, h], ['left', u, v + d, d, h], ['right', u + d + w, v + d, d, h], ['top', u + d, v, w, d], ['bottom', u + d + w, v, w, d] ];
        return `<div class="cm72-cube ${overlay ? 'cm72-overlay' : ''}" style="--w:${w * scale}px;--h:${h * scale}px;--d:${d * scale}px;--x:${p.x * scale}px;--y:${p.y * scale}px">${faces.map(([name, x, y, fw, fh]) => `<span class="cm72-face ${name}" style="background-image:url('${slice(x, y, fw, fh, p.mirror)}')"></span>`).join('')}</div>`;
      }
      return parts.map(part => cube(part, part.uv, false) + (part.overlay ? cube(part, part.overlay, true) : '')).join('');
    })();
    facesCache.set(key, promise); try { return await promise; } catch (error) { facesCache.delete(key); throw error; }
  }
  function skinStage(p, interactive = true) {
    const texture = asset(p.skin?.texture || 'assets/skins/steve.png');
    return `<div class="cm72-skin-view" ${interactive ? 'tabindex="0" role="group" aria-label="Скин игрока. Перетащи для поворота или используй стрелки клавиатуры."' : ''}><div class="cm72-skin-shadow"></div><div class="cm72-skin-model" data-texture="${E(texture)}" data-slim="${p.skin?.model === 'slim'}"><img class="cm72-skin-fallback" src="${asset(p.skin?.front || 'assets/skins/steve-front.png')}" alt="Скин игрока"></div>${interactive ? `<button type="button" class="cm72-skin-reset icon-btn" data-action="cm72-skin-reset" title="Вернуть ракурс" aria-label="Вернуть ракурс">${icon('refresh')}</button><span class="cm72-skin-instruction">Перетащи, чтобы повернуть</span>` : ''}</div>`;
  }
  function restoreSkin() {
    const p = M.me(), saved = state().reviewPersonal?.skin;
    if (p && saved && state().reviewPersonal.skinOwner === p.id) p.skin = saved;
  }
  const previousAccount = R.account;
  R.account = function (tab) {
    restoreSkin(); let html = previousAccount(tab);
    if (!M.me()) return html;
    html = html.replace(/<img class="v71-account-skin"[^>]+>/, `<div class="cm72-account-avatar">${skinStage(M.me())}<a href="#settings/profile?skin=1">Изменить скин ${icon('arrow-up-right')}</a></div>`);
    html = html.replace(/src="[^\"]*assets\/img\/world.webp"/, `src="${asset(photo)}"`);
    // The full tab row already lists these destinations; avoid repeating it in the aside.
    const t = document.createElement('template'); t.innerHTML = html;
    t.content.querySelectorAll('.v71-account-aside section').forEach(section => { if (section.querySelector('h3')?.textContent === 'Игровые дела') section.remove(); });
    return t.innerHTML;
  };
  function skinSettings() {
    const p = M.me();
    return `<section class="v7-settings-section cm72-skin-settings" id="cm72-skin-settings"><div class="cm72-skin-settings-preview">${skinStage(p)}</div><div class="cm72-skin-settings-copy"><h2>Твой скин</h2><p>Он отображается в личном кабинете и рядом с твоим именем.</p><span class="cm72-skin-source">${p.skin?.source === 'upload' ? 'Загруженный скин' : 'Стандартный скин'}</span><div class="cm72-upload-row"><label class="btn primary" tabindex="0" role="button" id="cm72-upload-trigger">${icon('upload')}Загрузить PNG<input type="file" accept="image/png" id="cm72-skin-file" hidden></label>${p.skin?.source === 'upload' ? M.btn('Сбросить', 'cm72-skin-default', '', 'ghost') : ''}</div><label class="field cm72-skin-model-field">Модель<select id="cm72-skin-kind"><option value="classic" ${p.skin?.model !== 'slim' ? 'selected' : ''}>Обычная — широкие руки</option><option value="slim" ${p.skin?.model === 'slim' ? 'selected' : ''}>Тонкие руки</option></select></label><small>PNG 64×64 или 64×32, до 2 МБ. В этой копии меняется только отображение на сайте.</small><p class="form-error" id="cm72-skin-error" role="alert"></p></div></section>`;
  }
  const previousSettings = R.settings;
  R.settings = function (tab = 'profile') {
    restoreSkin(); const html = previousSettings(tab); if (!M.me()) return html;
    const t = document.createElement('template'); t.innerHTML = html;
    t.content.querySelectorAll('[data-action="logout"]').forEach(n => n.remove());
    if (tab === 'profile') {
      const box = t.content.querySelector('.v71-settings-content');
      box?.querySelectorAll('.v7-settings-section').forEach(section => { if (section.querySelector('h2')?.textContent === 'Контакты и привязки') section.remove(); });
      const marker = box?.querySelector('.v71-save-hint');
      const target = document.createElement('template'); target.innerHTML = skinSettings(); if (marker) marker.before(target.content); else box?.append(target.content);
    }
    return t.innerHTML;
  };

  async function readSkin(file, model) {
    if (file.type !== 'image/png' || file.size > 2 * 1024 * 1024) throw new Error('Нужен PNG до 2 МБ.');
    const texture = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(new Error('Не удалось прочитать файл.')); reader.readAsDataURL(file); });
    const im = await loadImage(texture);
    if (im.width !== 64 || ![32, 64].includes(im.height)) throw new Error('Размер скина: 64×64 или 64×32.');
    const modern = im.height === 64, arm = modern && model === 'slim' ? 3 : 4;
    const flat = (w, h, parts) => {
      const canvas = document.createElement('canvas'); canvas.width = w; canvas.height = h;
      const ctx = canvas.getContext('2d'); ctx.imageSmoothingEnabled = false;
      for (const [x, y, sw, sh, dx, dy, mirror] of parts) {
        ctx.save(); if (mirror) { ctx.translate(dx + sw, dy); ctx.scale(-1, 1); ctx.drawImage(im, x, y, sw, sh, 0, 0, sw, sh); }
        else ctx.drawImage(im, x, y, sw, sh, dx, dy, sw, sh); ctx.restore();
      }
      return canvas.toDataURL('image/png');
    };
    const front = [[8,8,8,8,4,0],[20,20,8,12,4,8],[44,20,arm,12,4-arm,8],[4,20,4,12,4,20]];
    const back = [[24,8,8,8,4,0],[32,20,8,12,4,8],[48+arm,20,arm,12,12,8],[12,20,4,12,8,20]];
    if (modern) {
      front.push([36,52,arm,12,12,8],[20,52,4,12,8,20],[20,36,8,12,4,8],[44,36,arm,12,4-arm,8],[52,52,arm,12,12,8],[4,36,4,12,4,20],[4,52,4,12,8,20]);
      back.push([40+arm,52,arm,12,4-arm,8],[28,52,4,12,4,20],[32,36,8,12,4,8],[48+arm,36,arm,12,12,8],[56+arm,52,arm,12,4-arm,8],[12,36,4,12,8,20],[12,52,4,12,4,20]);
    } else { front.push([44,20,4,12,12,8,true],[4,20,4,12,8,20,true]); back.push([52,20,4,12,0,8,true],[12,20,4,12,4,20,true]); }
    front.push([40,8,8,8,4,0]); back.push([56,8,8,8,4,0]);
    return { texture, model: modern ? model : 'classic', head: flat(8,8,[[8,8,8,8,0,0],[40,8,8,8,0,0]]), front: flat(16,32,front), back: flat(16,32,back), source: 'upload', at: Date.now() };
  }

  function saveSkin() { const p = M.me(); state().reviewPersonal ||= {}; state().reviewPersonal.skin = M.clone(p.skin); state().reviewPersonal.skinOwner = p.id; M.save(); }
  H['cm72-skin-default'] = () => {
    const p = M.me(), name = p.id === 'demo-admin' ? 'alex' : 'steve';
    p.skin = { texture: `assets/skins/${name}.png`, head: `assets/skins/${name}-head.png`, front: `assets/skins/${name}-front.png`, back: `assets/skins/${name}-back.png`, source: 'default', model: name === 'alex' ? 'slim' : 'classic' };
    saveSkin(); M.render(); M.toast('Стандартный скин восстановлен.');
  };
  H['cm72-skin-reset'] = b => { api.skinYaw = -25; api.skinPitch = -6; rotateSkin(b.closest('.cm72-skin-view')); };
  function rotateSkin(stage) { const model = stage?.querySelector('.cm72-skin-model'); if (model) model.style.transform = `rotateX(${api.skinPitch}deg) rotateY(${api.skinYaw}deg)`; }
  async function bindSkins() {
    for (const model of document.querySelectorAll('.cm72-skin-model:not([data-bound])')) {
      model.dataset.bound = 'true'; const stage = model.closest('.cm72-skin-view'); rotateSkin(stage);
      try { const html = await skinGeometry(model.dataset.texture, model.dataset.slim === 'true'); if (model.isConnected) { model.innerHTML = html; model.dataset.rendered = 'true'; } }
      catch (_) { stage?.classList.add('cm72-skin-static'); }
      if (!stage || stage.dataset.dragBound) continue; stage.dataset.dragBound = 'true';
      let dragging = null;
      stage.addEventListener('pointerdown', e => { if (e.target.closest('button,a')) return; dragging = { x: e.clientX, yaw: api.skinYaw }; stage.setPointerCapture(e.pointerId); stage.classList.add('is-dragging'); });
      stage.addEventListener('pointermove', e => { if (!dragging) return; api.skinYaw = dragging.yaw + (e.clientX - dragging.x) * .7; rotateSkin(stage); });
      const stop = () => { dragging = null; stage.classList.remove('is-dragging'); };
      stage.addEventListener('pointerup', stop); stage.addEventListener('pointercancel', stop);
      stage.addEventListener('keydown', e => { if (e.target !== stage) return; if (['ArrowRight', 'ArrowLeft', 'Home'].includes(e.key)) { e.preventDefault(); api.skinYaw = e.key === 'Home' ? -25 : api.skinYaw + (e.key === 'ArrowRight' ? 15 : -15); rotateSkin(stage); } });
    }
  }
  document.addEventListener('change', async e => {
    if (e.target.id === 'cm72-skin-file') {
      const file = e.target.files?.[0]; if (!file || !M.me()) return;
      const oldModel = el('cm72-skin-kind')?.value || 'classic';
      try { const skin = await readSkin(file, oldModel); M.me().skin = skin; saveSkin(); M.render(); M.toast('Скин обновлён.'); }
      catch (err) { if (el('cm72-skin-error')) el('cm72-skin-error').textContent = err.message; else M.toast(err.message, true); }
    }
    if (e.target.id === 'cm72-skin-kind' && M.me()) { M.me().skin ||= { texture: 'assets/skins/steve.png' }; M.me().skin.model = e.target.value; saveSkin(); M.render(); }
  });
  document.addEventListener('keydown', e => { if (e.target.id === 'cm72-upload-trigger' && ['Enter', ' '].includes(e.key)) { e.preventDefault(); el('cm72-skin-file')?.click(); } });

  // Local quality-of-life feedback, without extra calls to the Minecraft server.
  H['cm72-top'] = () => window.scrollTo({ top: 0, behavior: motion() ? 'smooth' : 'instant' });
  H['cm72-search-clear'] = b => { const input = b.parentElement.querySelector('input'); input.value = ''; input.dispatchEvent(new Event('input', { bubbles: true })); input.focus(); };
  const dirtyForms = () => [...document.querySelectorAll('form[data-cm72-dirty="true"]')];
  window.addEventListener('beforeunload', e => { if (dirtyForms().length) { e.preventDefault(); e.returnValue = ''; } });
  document.addEventListener('click', e => {
    const link = e.target.closest('a[href^="#"]');
    if (!link || link.getAttribute('href') === location.hash || !dirtyForms().length) return;
    if (!window.confirm('Есть несохранённые изменения. Перейти без сохранения?')) { e.preventDefault(); e.stopImmediatePropagation(); }
    else dirtyForms().forEach(f => { f.dataset.cm72Dirty = 'false'; });
  }, true);
  function bindFormFeedback() {
    document.querySelectorAll('#view form[data-form]').forEach(form => {
      const submit = form.querySelector('[type="submit"]');
      if (!submit || !/Сохранить/.test(submit.textContent) || form.dataset.cm72Feedback) return;
      form.dataset.cm72Feedback = 'true';
      const hint = document.createElement('span'); hint.className = 'cm72-dirty-note'; hint.setAttribute('aria-live', 'polite'); submit.after(hint);
      const change = () => { form.dataset.cm72Dirty = 'true'; hint.textContent = 'Есть несохранённые изменения'; };
      form.addEventListener('input', change); form.addEventListener('change', change);
      form.addEventListener('submit', () => { hint.textContent = 'Сохраняем…'; const settled = () => { if (!form.isConnected) return; if (submit.disabled) { setTimeout(settled, 100); return; } const error = form.querySelector('.form-error'); if (error?.textContent) hint.textContent = 'Изменения не сохранены'; else { form.dataset.cm72Dirty = 'false'; hint.textContent = 'Сохранено'; } }; setTimeout(settled, 100); });
    });
  }
  V.after.push((page, tab) => {
    document.body.classList.add('cm72');
    document.documentElement.style.setProperty('--bg', '#171b1e');
    if (el('v7-save-status')) el('v7-save-status').setAttribute('aria-live', 'polite');
    const version = document.querySelector('.v71-reviewbar>span:first-child'); if (version) version.innerHTML = '<i></i>v7.3.2 <b>Локальный просмотр</b>';
    // Hidden is a semantic state. Some inherited component rules set display:flex;
    // the final CSS explicitly preserves [hidden] across all account menus.
    document.querySelectorAll('.v71-header-exit').forEach(n => n.remove());
    document.querySelectorAll('.v7-test-hint,.event-rail').forEach(n => n.remove());
    if (page === 'home') {
      const stage = el('cm72-gallery-stage'); let startX = null, lastX = 0, raf = 0;
      stage?.addEventListener('pointerdown', e => {
        if (e.button !== 0) return;
        const slide = e.target.closest('.cm72-carousel-slide'); if (!slide) return;
        startX = e.clientX; lastX = 0; draggedGallery = false;
        try { slide.setPointerCapture(e.pointerId); } catch { /* Pointer capture is optional. */ }
      });
      stage?.addEventListener('pointermove', e => {
        if (startX === null) return;
        lastX = e.clientX - startX;
        if (Math.abs(lastX) < 7) return;
        draggedGallery = true; stage.classList.add('is-dragging');
        if (!raf) raf = requestAnimationFrame(() => { raf = 0; window.CMSiteMotion?.drag(stage, lastX, motion()); });
      });
      const finishDrag = e => {
        if (startX === null) return;
        const delta = e.clientX - startX;
        if (Math.abs(delta) > 45) {
          draggedGallery = true;
          setGallery(api.galleryIndex + (delta < 0 ? 1 : -1));
          setTimeout(() => { draggedGallery = false; }, 0);
        } else draggedGallery = false;
        startX = null; stage.classList.remove('is-dragging');
        window.CMSiteMotion?.settle(stage, motion());
      };
      stage?.addEventListener('pointerup', finishDrag);
      stage?.addEventListener('pointercancel', e => { startX = null; draggedGallery = false; stage.classList.remove('is-dragging'); window.CMSiteMotion?.settle(stage, motion()); });
      window.CMSiteMotion?.carousel(document.querySelectorAll('.cm72-carousel-slide'), api.galleryIndex, motion());
    }
    if (page !== 'admin') {
      const title = document.querySelector('.page-head,.v7-page-heading,.v71-page-head');
      if (title && ['shop', 'donate', 'rules'].includes(page)) title.classList.add('cm72-photo-heading');
    }
    document.querySelectorAll('.input-wrap').forEach(wrap => {
      const input = wrap.querySelector('input[type="search"],#shop-search,#v71-player-search');
      if (!input || wrap.querySelector('[data-action="cm72-search-clear"]')) return;
      input.type = 'search'; const b = document.createElement('button'); b.type = 'button'; b.className = 'cm72-search-clear'; b.dataset.action = 'cm72-search-clear'; b.setAttribute('aria-label', 'Очистить поиск'); b.innerHTML = icon('x'); wrap.append(b);
      const update = () => { b.hidden = !input.value; }; input.addEventListener('input', update); update();
    });
    window.CMSiteMotion?.route(el('view'), motion());
    window.CMSiteMotion?.reveal(document.querySelectorAll('.cm72-reveal'), motion());
    bindSkins(); bindFormFeedback();
    if (page === 'settings' && M.UI.params.get('skin')) setTimeout(() => el('cm72-skin-settings')?.scrollIntoView({ block: 'center', behavior: motion() ? 'smooth' : 'instant' }), 100);
    const top = el('cm72-back-top'); if (top) top.hidden = page === 'admin' || window.scrollY < 750;
  });
  window.addEventListener('scroll', () => { const top = el('cm72-back-top'); if (top) top.hidden = M.UI.page === 'admin' || window.scrollY < 750; }, { passive: true });
  const previousDock = dock;
  dock = () => previousDock() + `<button id="cm72-back-top" class="cm72-back-top" type="button" data-action="cm72-top" aria-label="Наверх" title="Наверх" hidden>${icon('arrow-left')}</button>`;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => M.render(), { once: true }); else M.render();
})();
