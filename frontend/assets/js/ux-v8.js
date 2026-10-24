/* Session-only route state and navigation restoration for the production UI. */
(function () {
  'use strict';
  const M = window.CM;
  if (!M?.UI) return;

  const KEY = 'copimine.site.route-state.v1';
  const currentRoute = () => location.hash.slice(1) || 'home';
  let scrollTimer, controlTimer;
  let pendingRestoreRoute = '';

  function readAll() {
    try {
      const value = JSON.parse(sessionStorage.getItem(KEY) || '{}');
      return value && typeof value === 'object' ? value : {};
    } catch { return {}; }
  }

  function matches(node) {
    if (node.matches('input[type="search"],input[id*="search" i],input[name*="search" i],.search-box input')) return true;
    if (!(node instanceof HTMLSelectElement)) return false;
    const key = `${node.id} ${node.name}`.toLowerCase();
    return /sort|filter|scope|currency|category/.test(key) || Boolean(node.closest('.filter-nav,.v71-history-filters,.v71-directory-controls,[aria-label*="Фильтр"]'));
  }

  function captureControls() {
    const root = document.getElementById('view');
    if (!root) return [];
    const matchesList = [...root.querySelectorAll('input[type="search"],input[id*="search" i],input[name*="search" i],.search-box input,select')].filter(matches);
    const fields = matchesList.map((node, index) => ({
      id: node.id || '',
      name: node.name || '',
      index,
      value: String(node.value || '').slice(0, 500),
    }));
    const filterGroups = [...root.querySelectorAll('.filter-nav,#currency-nav,.v71-history-filters,.v71-action-categories,[role="group"][aria-label*="Фильтр"],.v6-filter-bar')];
    const buttons = filterGroups.flatMap((group, groupIndex) => {
      const selected = group.querySelector('button[aria-pressed="true"],button.active');
      return selected ? [{ groupIndex, action: selected.dataset.action || '', key: selected.dataset.key || '', id: selected.dataset.id || '', unit: selected.dataset.unit || '', category: selected.dataset.category || '', value: selected.dataset.value || '' }] : [];
    });
    return { fields, buttons };
  }

  function save(route = currentRoute()) {
    try {
      const state = readAll();
      state[route] = {
        y: Math.max(0, Math.round(window.scrollY)),
        adminY: Math.max(0, Math.round(document.querySelector('.v71-admin-nav')?.scrollTop || 0)),
        controls: captureControls(),
      };
      const keys = Object.keys(state);
      for (const stale of keys.slice(0, Math.max(0, keys.length - 20))) delete state[stale];
      sessionStorage.setItem(KEY, JSON.stringify(state));
    } catch { /* Route restoration is a convenience and must never block navigation. */ }
  }

  function scheduleSave() {
    clearTimeout(controlTimer);
    controlTimer = setTimeout(() => save(), 0);
  }

  function restore(route = currentRoute()) {
    const saved = readAll()[route];
    if (!saved) {
      if (pendingRestoreRoute === route) pendingRestoreRoute = '';
      return;
    }
    const root = document.getElementById('view');
    if (!root) return;
    const nodes = [...root.querySelectorAll('input[type="search"],input[id*="search" i],input[name*="search" i],.search-box input,select')].filter(matches);
    const fields = Array.isArray(saved.controls) ? saved.controls : saved.controls?.fields || [];
    for (const entry of fields) {
      const node = entry.id
        ? nodes.find(item => item.id === entry.id)
        : nodes.filter(item => item.name === entry.name)[entry.index];
      if (!node) continue;
      if (node instanceof HTMLSelectElement && ![...node.options].some(option => option.value === entry.value)) continue;
      if (node.value === entry.value) continue;
      node.value = entry.value;
      node.dispatchEvent(new Event(node instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
    }
    const filterGroups = [...root.querySelectorAll('.filter-nav,#currency-nav,.v71-history-filters,.v71-action-categories,[role="group"][aria-label*="Фильтр"],.v6-filter-bar')];
    for (const entry of saved.controls?.buttons || []) {
      const group = filterGroups[entry.groupIndex];
      const button = group && [...group.querySelectorAll('button')].find(item =>
        !(item.getAttribute('aria-pressed') === 'true' || item.classList.contains('active')) &&
        (item.dataset.action || '') === entry.action && (item.dataset.key || '') === entry.key && (item.dataset.id || '') === entry.id && (item.dataset.unit || '') === entry.unit && (item.dataset.category || '') === entry.category && (item.dataset.value || '') === entry.value
      );
      button?.click();
    }
    const adminNav = document.querySelector('.v71-admin-nav');
    if (adminNav && Number.isFinite(saved.adminY)) adminNav.scrollTop = saved.adminY;
    requestAnimationFrame(() => {
      window.scrollTo({ top: Number(saved.y) || 0, behavior: 'instant' });
      if (pendingRestoreRoute === route) pendingRestoreRoute = '';
    });
  }

  document.addEventListener('click', event => {
    const anchor = event.target.closest('a[href^="#"]');
    if (anchor && anchor.getAttribute('href') !== `#${currentRoute()}`) save();
    if (event.target.closest('.filter-nav button,#currency-nav button,.v71-history-filters button,.v71-action-categories button,[role="group"][aria-label*="Фильтр"] button,.v6-filter-bar button')) scheduleSave();
  }, true);

  document.addEventListener('input', event => { if (matches(event.target)) scheduleSave(); }, true);
  document.addEventListener('change', event => { if (matches(event.target)) scheduleSave(); }, true);

  window.addEventListener('scroll', () => {
    clearTimeout(scrollTimer);
    scrollTimer = setTimeout(() => save(), 140);
  }, { passive: true });

  window.addEventListener('hashchange', () => requestAnimationFrame(() => restore()));
  window.addEventListener('beforeunload', () => save());

  const originalRender = M.render;
  if (typeof originalRender === 'function') {
    M.render = function (...args) {
      const route = currentRoute();
      const previousRoute = M.UI.renderedRoute || route;
      const hasView = Boolean(document.getElementById('view'));
      const enteringRoute = !hasView || previousRoute !== route;
      if (enteringRoute) pendingRestoreRoute = route;
      if (hasView && pendingRestoreRoute !== previousRoute) save(previousRoute);
      const result = originalRender.apply(this, args);
      requestAnimationFrame(() => restore(route));
      return result;
    };
  }

  const originalNavigate = M.navTo;
  if (typeof originalNavigate === 'function') {
    M.navTo = function (path) {
      save();
      const result = originalNavigate.call(this, path);
      if (`#${path}` === location.hash) requestAnimationFrame(() => restore());
      return result;
    };
  }

  document.addEventListener('DOMContentLoaded', () => requestAnimationFrame(() => restore()), { once: true });
})();
