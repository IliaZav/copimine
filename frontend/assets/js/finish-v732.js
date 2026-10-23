/* CopiMine v7.3.2. Scoped UI polish only; no server/database contract changes.
 * The footer is a real navigation surface. The hero uses only published
 * project photographs; map images and unpublished drafts never rotate in.
 */
(function () {
  'use strict';
  const M = window.CM, V = window.CMV4, E = M.esc;
  const L = {
    intro: '\u0422\u043e\u0440\u0433\u043e\u0432\u043b\u044f \u0437\u0430 \u0410\u0420, \u0432\u044b\u0431\u043e\u0440\u044b \u0438 \u043f\u043e\u0441\u0442\u0440\u043e\u0439\u043a\u0438 \u0438\u0433\u0440\u043e\u043a\u043e\u0432.',
    nav: '\u041d\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u044f',
    shop: '\u041c\u0430\u0433\u0430\u0437\u0438\u043d',
    map: '\u041a\u0430\u0440\u0442\u0430 \u043c\u0438\u0440\u0430',
    rules: '\u041f\u0440\u0430\u0432\u0438\u043b\u0430',
    contact: '\u0421\u0432\u044f\u0437\u044c',
    help: '\u041f\u043e\u043c\u043e\u0449\u044c \u0438 \u043e\u0431\u0440\u0430\u0449\u0435\u043d\u0438\u044f',
    discord: '\u0421\u043e\u043e\u0431\u0449\u0435\u0441\u0442\u0432\u043e \u0432 Discord',
    server: '\u0410\u0434\u0440\u0435\u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u0430',
    copy: '\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c \u0430\u0434\u0440\u0435\u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u0430',
    copyHint: '\u041d\u0430\u0436\u043c\u0438, \u0447\u0442\u043e\u0431\u044b \u0441\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u0442\u044c',
    cookie: '\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438 cookies',
    disclaimer: '\u041d\u0435\u043e\u0444\u0438\u0446\u0438\u0430\u043b\u044c\u043d\u044b\u0439 Minecraft-\u043f\u0440\u043e\u0435\u043a\u0442. \u041d\u0435 \u0441\u0432\u044f\u0437\u0430\u043d \u0441 Mojang Studios \u0438\u043b\u0438 Microsoft.',
    pause: '\u041f\u0430\u0443\u0437\u0430',
    resume: '\u041f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c',
    next: '\u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439 \u043a\u0430\u0434\u0440',
    stopTitle: '\u041e\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c \u0441\u043c\u0435\u043d\u0443 \u043a\u0430\u0434\u0440\u043e\u0432',
    startTitle: '\u0412\u043e\u0437\u043e\u0431\u043d\u043e\u0432\u0438\u0442\u044c \u0441\u043c\u0435\u043d\u0443 \u043a\u0430\u0434\u0440\u043e\u0432'
  };

  // Header controls keep their existing action handlers. Footer navigation is
  // intentionally smaller than the header and never exposes the admin panel.
  footer = function () {
    const ip = E(M.state.config?.serverIp || 'copimine.ru');
    return `<footer class="site-footer cm74-footer" aria-label="CopiMine">
      <div class="cm74-footer-main">
        <div class="cm74-footer-brand">
          <a href="#home" aria-label="CopiMine"><img src="${M.asset('assets/img/logo.png')}" width="142" height="34" alt="CopiMine" loading="lazy"></a>
          <p>${L.intro}</p>
        </div>
        <nav class="cm74-footer-links" aria-label="${L.nav}">
          <div><strong>${L.nav}</strong><a href="#shop">${L.shop}</a><a href="#map">${L.map}</a><a href="#rules">${L.rules}</a></div>
          <div><strong>${L.contact}</strong><a href="#account/support">${L.help}</a><button type="button" data-action="discord">${L.discord}</button></div>
        </nav>
        <div class="cm74-footer-server"><strong>${L.server}</strong><button type="button" class="cm74-footer-ip" data-action="copy-ip" aria-label="${L.copy}" title="${L.copy}"><span>${ip}</span>${icon('copy')}</button><small>${L.copyHint}</small></div>
      </div>
      <div class="cm74-footer-bottom"><span>\u00a9 ${new Date().getFullYear()} CopiMine</span><span>${L.disclaimer}</span><button type="button" data-action="v6-cookies">${L.cookie}</button></div>
    </footer>`;
  };

  const FALLBACK = [
    'assets/photos/copimine-night.webp',
    'assets/photos/copimine-riverside.webp',
    'assets/photos/copimine-mountain.webp',
    'assets/photos/copimine-arena.webp'
  ];
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let current = null;

  function allowAutoplay() {
    return document.documentElement.dataset.motion !== 'off' &&
      M.state.preferences?.motion !== false &&
      M.state.reviewPersonal?.reduceMotion !== true &&
      !reducedMotion.matches && !document.hidden;
  }
  function projectPhotos() {
    const gallery = Array.isArray(M.state.gallery) ? M.state.gallery : [];
    const photos = gallery.filter(p => p && p.published === true && p.kind !== 'map' &&
      typeof p.src === 'string' && (p.src.startsWith('assets/photos/') || p.src.startsWith('data:image/')) &&
      !String(p.id || '').includes('map-'))
      .sort((a, b) => (Number(a.position) || 0) - (Number(b.position) || 0))
      .map(p => p.src);
    return [...new Set(photos.length ? photos : FALLBACK)].slice(0, 6);
  }
  function clearTimer(session) { if (session?.timer) { clearTimeout(session.timer); session.timer = 0; } }
  function stop() { if (!current) return; clearTimer(current); if (current.fadeTimer) clearTimeout(current.fadeTimer); current = null; }
  function update(session) {
    if (!session?.controls) return;
    session.controls.querySelector('.cm74-hero-counter').textContent =
      `${String(session.index + 1).padStart(2, '0')} / ${String(session.sources.length).padStart(2, '0')}`;
    const b = session.controls.querySelector('.cm74-hero-pause');
    const stopped = session.paused || !allowAutoplay();
    b.textContent = stopped ? L.resume : L.pause;
    b.setAttribute('aria-pressed', String(!stopped));
    b.title = stopped ? L.startTitle : L.stopTitle;
  }
  function schedule(session) {
    clearTimer(session);
    if (current !== session || session.sources.length < 2 || session.paused || !allowAutoplay()) return;
    session.timer = setTimeout(() => switchPhoto(session, 1), 7000);
  }
  function switchPhoto(session, step) {
    if (current !== session || session.busy) return;
    clearTimer(session);
    const nextIndex = (session.index + step + session.sources.length) % session.sources.length;
    const url = M.asset(session.sources[nextIndex]);
    if (!url) return;
    session.busy = true;
    const probe = new Image();
    probe.onload = () => {
      if (current !== session || !session.hero.isConnected) return;
      const instant = !allowAutoplay();
      if (instant) {
        session.base.src = url; session.index = nextIndex; session.busy = false;
        update(session); schedule(session); return;
      }
      session.overlay.src = url;
      // A new frame must paint before transition starts, or the crossfade
      // can jump when the browser decoded the image from cache.
      requestAnimationFrame(() => requestAnimationFrame(() => {
        if (current !== session) return;
        session.overlay.classList.add('cm74-show');
        session.fadeTimer = setTimeout(() => {
          if (current !== session) return;
          session.base.src = url;
          session.index = nextIndex;
          session.overlay.classList.remove('cm74-show');
          session.busy = false;
          update(session);
          schedule(session);
        }, 1650);
      }));
    };
    probe.onerror = () => { if (current !== session) return; session.busy = false; session.index = nextIndex; update(session); schedule(session); };
    probe.src = url;
  }
  function start() {
    stop();
    const hero = document.querySelector('#view .cm72-hero');
    const base = hero?.querySelector('.cm72-hero-photo');
    if (!hero || !base) return;
    const sources = projectPhotos();
    if (sources.length < 2) return;
    // Use the photo already in the HTML as first paint, never replace it
    // with blank slides; subsequent photos fade on the same two layers.
    const first = sources.indexOf('assets/photos/copimine-night.webp');
    const index = Math.max(0, first);
    if (sources[index] !== FALLBACK[0]) base.src = M.asset(sources[index]);
    base.alt = ''; base.setAttribute('aria-hidden', 'true');
    const overlay = document.createElement('img');
    overlay.className = 'cm72-hero-photo cm74-hero-next';
    overlay.alt = ''; overlay.setAttribute('aria-hidden', 'true');
    overlay.decoding = 'async';
    hero.insertBefore(overlay, base.nextSibling);
    const controls = document.createElement('div');
    controls.className = 'cm74-hero-controls';
    controls.innerHTML = `<span class="cm74-hero-counter"></span><button type="button" class="cm74-hero-pause"></button><button type="button" class="cm74-hero-skip" title="${L.next}" aria-label="${L.next}">${icon('arrow-right')}</button>`;
    hero.append(controls);
    const session = { hero, base, overlay, controls, sources, index, paused: false, busy: false, timer: 0, fadeTimer: 0 };
    current = session;
    controls.querySelector('.cm74-hero-pause').addEventListener('click', () => {
      session.paused = !session.paused;
      update(session); schedule(session);
    });
    controls.querySelector('.cm74-hero-skip').addEventListener('click', () => switchPhoto(session, 1));
    update(session); schedule(session);
  }
  document.addEventListener('visibilitychange', () => {
    if (!current) return;
    clearTimer(current);
    update(current);
    if (!document.hidden) schedule(current);
  });
  reducedMotion.addEventListener?.('change', () => { if (current) { update(current); schedule(current); } });
  V.after.push((page) => { if (page === 'home') start(); else stop(); });
  window.CM74 = { get slides() { return current?.sources?.slice() || []; }, get currentSlide() { return current?.index ?? -1; } };
})();
