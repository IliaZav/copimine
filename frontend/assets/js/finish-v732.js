/* CopiMine v7.3.2. Footer navigation and cookie settings. */
(function () {
  'use strict';
  const M = window.CM, E = M.esc;
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
    privacy: '\u041f\u043e\u043b\u0438\u0442\u0438\u043a\u0430 \u043a\u043e\u043d\u0444\u0438\u0434\u0435\u043d\u0446\u0438\u0430\u043b\u044c\u043d\u043e\u0441\u0442\u0438',
    disclaimer: '\u041d\u0435\u043e\u0444\u0438\u0446\u0438\u0430\u043b\u044c\u043d\u044b\u0439 Minecraft-\u043f\u0440\u043e\u0435\u043a\u0442. \u041d\u0435 \u0441\u0432\u044f\u0437\u0430\u043d \u0441 Mojang Studios \u0438\u043b\u0438 Microsoft.',
  };

  // Header controls keep their existing action handlers. Footer navigation is
  // intentionally smaller than the header and never exposes the admin panel.
  footer = function () {
    const ip = E(M.state.config?.serverIp || 'copimine.ru');
    return `<footer class="site-footer cm74-footer" aria-label="CopiMine">
      <div class="cm74-footer-main">
        <div class="cm74-footer-brand">
          <a href="#home" aria-label="CopiMine"><img src="${M.asset('assets/img/logo.webp')}" width="142" height="34" alt="CopiMine" loading="lazy"></a>
          <p>${L.intro}</p>
        </div>
        <nav class="cm74-footer-links" aria-label="${L.nav}">
          <div><strong>${L.nav}</strong><a href="#shop">${L.shop}</a><a href="#map">${L.map}</a><a href="#rules">${L.rules}</a></div>
          <div><strong>${L.contact}</strong><a href="#account/support">${L.help}</a><button type="button" data-action="discord">${L.discord}</button></div>
        </nav>
        <div class="cm74-footer-server"><strong>${L.server}</strong><button type="button" class="cm74-footer-ip" data-action="copy-ip" aria-label="${L.copy}" title="${L.copy}"><span>${ip}</span>${icon('copy')}</button><small>${L.copyHint}</small></div>
      </div>
      <div class="cm74-footer-bottom"><span>\u00a9 ${new Date().getFullYear()} CopiMine</span><span>${L.disclaimer}</span><button type="button" data-action="v6-cookies">${L.cookie}</button><a href="#privacy">${L.privacy}</a></div>
    </footer>`;
  };

})();
