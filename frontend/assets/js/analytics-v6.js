/* CopiMine v6 · consent-based public analytics and truthful administration reports. */
(function () {
  "use strict";
  const M = window.CM, V = window.CMV4, W = window.CM5, X = window.CM6;
  if (!M || !V || !W || !X) return;
  const E = M.esc, H = M.handlers, DAY = 86400000;
  const publicPages = new Set(["home", "shop", "cart", "donate", "map", "rules", "support"]);
  const pageNames = { home: "Главная", shop: "Магазин", cart: "Корзина", donate: "Донат", map: "Карта", rules: "Правила", support: "Помощь" };
  const eventNames = { page_view: "Открытие страницы", cart_add: "Добавление в корзину", add_to_cart: "Добавление в корзину", item_view: "Просмотр предмета", product_view: "Просмотр предмета", cart_remove: "Удаление из корзины", donation_start: "Начало пополнения", payment_created: "Создание платежа", checkout_start: "Начало оформления", checkout_started: "Начало оформления", checkout_complete: "Заказ оформлен", checkout_completed: "Заказ оформлен", checkout: "Оформление заказа", purchase: "Покупка", download_mods: "Загрузка сборки", download_launcher: "Загрузка лаунчера", play: "Инструкция по входу" };
  const ranges = [["day", "Сегодня"], ["7d", "7 дней"], ["30d", "30 дней"], ["year", "За год"]];
  const tabs = [["overview", "Обзор"], ["audience", "Аудитория"], ["sources", "Источники"], ["pages", "Страницы"], ["events", "События"], ["consents", "Согласия"], ["economy", "Экономика"]];
  const storageKey = "copimine.analytics.visitor.v6", sessionKey = "copimine.analytics.session.v6";
  let queue = [], flushTimer = null, sending = false, generation = 0, lastPage = "", chartCurrency = "CM", cacheOwner = "";
  const cache = new Map(), requests = new Map(), errors = new Map();
  const integer = value => Number.isFinite(Number(value)) ? Math.max(0, Math.trunc(Number(value))) : 0;
  const number = value => Number.isFinite(Number(value)) ? Number(value) : 0;
  const fmt = value => number(value).toLocaleString("ru-RU");
  const rubles = data => data.paymentsAvailable === false ? "—" : fmt(data.rubles) + " ₽";
  const rublesHint = data => data.paymentsAvailable === false ? "Платёжный сервис не подключён" : "Подтверждённые платежи";
  const dayString = value => new Date(value).toISOString().slice(0, 10);
  function consented() { return X.consent?.analytics === true; }
  function currentPage() {
    const page = (location.hash.slice(1).split(/[/?]/)[0] || "home").toLowerCase();
    return publicPages.has(page) ? page : null;
  }
  function cookie(name) {
    const row = document.cookie.split("; ").find(item => item.indexOf(name + "=") === 0);
    if (!row) return "";
    try { return decodeURIComponent(row.slice(name.length + 1)); } catch { return ""; }
  }
  function writeCookie(name, value, days) {
    const secure = location.protocol === "https:" ? "; Secure" : "";
    document.cookie = name + "=" + encodeURIComponent(value) + "; Path=/; Max-Age=" + Math.round(days * 86400) + "; SameSite=Lax" + secure;
  }
  function removeCookie(name) { document.cookie = name + "=; Path=/; Max-Age=0; SameSite=Lax"; }
  function randomId() {
    return crypto.randomUUID ? crypto.randomUUID() : [...crypto.getRandomValues(new Uint8Array(16))].map(n => n.toString(16).padStart(2, "0")).join("");
  }
  function visitor() {
    try {
      let id = cookie("cm_visitor") || sessionStorage.getItem(storageKey);
      if (!/^[a-z0-9-]{16,80}$/i.test(id || "")) {
        id = randomId();
        writeCookie("cm_visitor", id, 365);
        sessionStorage.setItem(storageKey, id);
      }
      return id;
    } catch { return null; }
  }
  X.analyticsVisitor = visitor;
  function session() {
    try {
      let id = sessionStorage.getItem(sessionKey);
      if (!/^[a-z0-9-]{16,80}$/i.test(id || "")) { id = randomId(); sessionStorage.setItem(sessionKey, id); }
      return id;
    } catch { return ""; }
  }
  function browserName() {
    const ua = navigator.userAgent || "";
    if (/Edg\//.test(ua)) return "Edge";
    if (/OPR\/|Opera/.test(ua)) return "Opera";
    if (/YaBrowser/.test(ua)) return "Яндекс";
    if (/Firefox\//.test(ua)) return "Firefox";
    if (/Chrome\//.test(ua)) return "Chrome";
    if (/Safari\//.test(ua)) return "Safari";
    return "Другое";
  }
  function osName() {
    const ua = navigator.userAgent || "";
    if (/Windows/.test(ua)) return "Windows";
    if (/Android/.test(ua)) return "Android";
    if (/iPhone|iPad|iPod/.test(ua)) return "iOS";
    if (/Mac OS X/.test(ua)) return "macOS";
    if (/Linux/.test(ua)) return "Linux";
    return "Другое";
  }
  function context() {
    let referrer = "";
    try { const url = new URL(document.referrer); if (/^https?:$/.test(url.protocol)) referrer = url.hostname === location.hostname ? "internal" : url.hostname.toLowerCase().slice(0, 64); } catch { referrer = ""; }
    const params = new URLSearchParams(location.search), clean = value => String(value || "").slice(0, 64);
    return {
      session: session(),
      device: innerWidth <= 620 ? "mobile" : innerWidth <= 1024 ? "tablet" : "desktop",
      browser: browserName(), os: osName(),
      lang: String(navigator.language || "").slice(0, 12),
      screen: screen.width + "x" + screen.height,
      source: referrer,
      utm: { source: clean(params.get("utm_source")), medium: clean(params.get("utm_medium")), campaign: clean(params.get("utm_campaign")) },
    };
  }
  let pageContext = null;
  function schedule(ms = 1500) {
    if (flushTimer || !X.live || !queue.length || !consented()) return;
    flushTimer = setTimeout(() => { flushTimer = null; flush(); }, ms);
  }
  async function flush() {
    if (sending || !consented() || !X.live || !queue.length || typeof X.request !== "function") return;
    const batch = queue.splice(0, 40), epoch = generation;
    if (!pageContext) pageContext = context();
    sending = true;
    try { await X.request("/analytics/events", { method: "POST", body: { events: batch, consent: true, context: pageContext } }); }
    catch {
      if (epoch === generation && consented()) queue = batch.concat(queue).slice(-40);
    } finally {
      sending = false;
      schedule(queue.length ? 10000 : 1500);
    }
  }
  X.track = function (type, payload = {}) {
    if (!consented() || !/^[a-z][a-z0-9_]{0,39}$/.test(String(type))) return false;
    const actualPage = currentPage();
    if (!actualPage) return false;
    const page = String(payload.page || actualPage);
    if (!publicPages.has(page) || page !== actualPage) return false;
    const token = visitor();
    if (!token) return false;
    const event = { type: String(type), page, visitor: token, at: Date.now() };
    if (/^[a-z0-9_.:-]{1,128}$/i.test(String(payload.itemId || ""))) event.itemId = String(payload.itemId);
    if (Number.isSafeInteger(Number(payload.quantity)) && Number(payload.quantity) > 0) event.quantity = Math.min(2304, Number(payload.quantity));
    if (X.live) { queue.push(event); queue = queue.slice(-40); schedule(); }
    else {
      if (!Array.isArray(M.state.analyticsEvents)) M.state.analyticsEvents = [];
      M.state.analyticsEvents.push(event);
      M.state.analyticsEvents = M.state.analyticsEvents.slice(-5000);
      M.save();
    }
    return true;
  };
  function routeView() {
    const page = currentPage();
    if (page === lastPage) return;
    lastPage = page;
    if (page) X.track("page_view", { page });
  }
  document.addEventListener("cm-consent", () => {
    generation++; queue = []; pageContext = null;
    clearTimeout(flushTimer); flushTimer = null;
    if (!consented()) { removeCookie("cm_visitor"); try { sessionStorage.removeItem(storageKey); sessionStorage.removeItem(sessionKey); } catch {} }
    lastPage = "";
    routeView();
  });
  window.addEventListener("hashchange", routeView);
  document.addEventListener("visibilitychange", () => { if (document.visibilityState === "hidden") flush(); });
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", routeView, { once: true }); else routeView();
  function selectedRange() {
    const raw = new URLSearchParams(location.hash.split("?")[1] || "").get("range");
    return raw === "all" ? "year" : ranges.some(([id]) => id === raw) ? raw : "7d";
  }
  function bounds(range) {
    const today = Date.parse(dayString(Date.now()) + "T00:00:00Z");
    const start = today - (range === "year" || range === "all" ? 364 : range === "30d" ? 29 : range === "7d" ? 6 : 0) * DAY;
    return { from: dayString(start), to: dayString(today), start, end: today + DAY };
  }
  function timestamp(row) {
    const value = row?.paidAt || row?.at || row?.createdAt || 0;
    const result = typeof value === "string" ? Date.parse(value) : Number(value);
    return Number.isFinite(result) ? result : 0;
  }
  function localSummary(range) {
    const b = bounds(range), st = M.state;
    const inside = row => timestamp(row) >= b.start && timestamp(row) < b.end;
    const orders = (st.orders || []).filter(inside);
    const sold = orders.filter(o => o.status === "delivered" && !["cancelled", "refunded"].includes(o.orderStatus));
    const paid = (st.payments || []).filter(p => ["paid", "completed"].includes(p.status) && inside(p));
    const events = (st.analyticsEvents || []).filter(e => inside(e) && publicPages.has(e.page));
    const revenue = { AR: 0, CM: 0 }, dayMap = new Map(), top = new Map(), pageMap = new Map(), eventMap = new Map(), unique = new Set();
    const daily = at => { const key = dayString(at); if (!dayMap.has(key)) dayMap.set(key, { at: key, AR: 0, CM: 0, rubles: 0 }); return dayMap.get(key); };
    const orderKey = o => o.orderId || o.id, orderCount = rows => new Set(rows.map(orderKey)).size;
    for (const o of sold) {
      if (o.currency in revenue) { revenue[o.currency] += number(o.total); daily(timestamp(o))[o.currency] += number(o.total); }
      const key = (o.item || o.name) + ":" + o.currency;
      if (!top.has(key)) top.set(key, { id: o.item, name: o.name || o.item, qty: 0, revenue: 0, currency: o.currency, orderKeys: new Set() });
      const row = top.get(key); row.qty += integer(o.qty); row.revenue += number(o.total); row.orderKeys.add(orderKey(o));
    }
    for (const p of paid) daily(timestamp(p)).rubles += number(p.rubles);
    for (const e of events) {
      const day = dayString(timestamp(e));
      if (e.type === "page_view") { pageMap.set(e.page, (pageMap.get(e.page) || 0) + 1); if (e.visitor) unique.add(day + ":" + e.visitor); }
      const key = day + "|" + e.type + "|" + e.page;
      if (!eventMap.has(key)) eventMap.set(key, { day, type: e.type, page: e.page, count: 0 });
      eventMap.get(key).count++;
    }
    return {
      revenue, rubles: paid.reduce((n, p) => n + number(p.rubles), 0), orders: orderCount(sold),
      buyers: new Set(sold.map(o => o.playerId || o.player).filter(Boolean)).size,
      items: sold.reduce((n, o) => n + integer(o.qty), 0), visits: events.filter(e => e.type === "page_view").length, visitors: unique.size,
      top: [...top.values()].map(({ orderKeys, ...row }) => ({ ...row, orders: orderKeys.size })).sort((a, b) => b.revenue - a.revenue),
      pending: orderCount(orders.filter(o => ["queued", "pending", "awaiting_delivery", "partially_delivered"].includes(o.orderStatus || o.status))),
      failed: orderCount(orders.filter(o => ["failed", "error", "partially_failed", "refunded"].includes(o.orderStatus || o.status))),
      refunded: orderCount(orders.filter(o => (o.orderStatus || o.status) === "refunded")),
      days: [...dayMap.values()].sort((a, b) => a.at.localeCompare(b.at)),
      pages: [...pageMap].map(([page, count]) => ({ page, count })).sort((a, b) => b.count - a.count),
      events: [...eventMap.values()], from: b.from, to: b.to
    };
  }
  function adminPage() {
    const route = location.hash.slice(1).split("?")[0];
    return route === "admin/statistics" || route === "admin/overview" || route === "admin";
  }
  function canRead() { return X.canAdmin() && (!X.live || (typeof X.can === "function" && X.can("analytics.read"))); }
  async function loadSummary(range, force = false) {
    if (!X.live || typeof X.request !== "function" || !canRead()) return;
    const key = range;
    if (requests.has(key)) return requests.get(key);
    if (!force && cache.has(key)) return cache.get(key).data;
    const b = bounds(range), owner = M.me?.()?.id || "";
    errors.delete(key);
    const request = X.request("/admin/analytics?from=" + b.from + "&to=" + b.to).then(result => {
      const data = result?.data || result;
      if (!data || typeof data !== "object" || !("visits" in data || "orders" in data || "revenue" in data)) throw new Error("Не удалось прочитать статистику.");
      if (owner !== (M.me?.()?.id || "") || !canRead()) return null;
      cache.set(key, { data, at: Date.now() });
      return data;
    }).catch(error => { if (owner === (M.me?.()?.id || "")) errors.set(key, error.message || "Статистика пока недоступна."); return null; }).finally(() => {
      if (requests.get(key) === request) requests.delete(key);
      if (adminPage() && selectedRange() === range) M.render();
    });
    requests.set(key, request);
    return request;
  }
  function table(headers, rows) { return V.table(headers, rows); }
  function empty(title, text) { return `<div class="v6-analytics-empty"><span>${window.icon("chart")}</span><h3>${E(title)}</h3><p>${E(text)}</p></div>`; }
  function kpi(title, value, hint = "") { return `<article class="v6-analytics-kpi"><span class="label">${E(title)}</span><strong class="numeric">${E(value)}</strong><small>${E(hint)}</small></article>`; }
  function dailySeries(source, data) {
    if (!source.length) return [];
    const values = new Map(source.filter(row => /^\d{4}-\d{2}-\d{2}$/.test(row.day)).map(row => [row.day, number(row.value)]));
    if (!values.size) return [];
    const keys = [...values.keys()].sort(), first = Date.parse(keys[0] + "T00:00:00Z");
    const end = Date.parse((data.to || keys[keys.length - 1]) + "T00:00:00Z");
    let start = data.from && data.from !== "1970-01-01" ? Date.parse(data.from + "T00:00:00Z") : first;
    const windowed = end - start >= 90 * DAY;
    start = Math.max(start, end - 89 * DAY);
    const rows = [];
    for (let at = start; at <= end; at += DAY) { const day = dayString(at); rows.push({ day, value: values.get(day) || 0 }); }
    rows.windowed = windowed;
    return rows;
  }
  function barChart() { return '<div id="cm-react-analytics-charts"></div>'; }
  function trafficDays(data) {
    const map = new Map();
    for (const e of data.events || []) if (e.type === "page_view") map.set(e.day, (map.get(e.day) || 0) + integer(e.count));
    return dailySeries([...map].map(([day, value]) => ({ day, value })), data);
  }
  function pagesPanel(data, compact = false) {
    const pages = (data.pages || []).filter(row => publicPages.has(row.page)).slice().sort((a, b) => number(b.count) - number(a.count));
    if (!pages.length) return empty("Посещений ещё нет", "Здесь появятся публичные страницы, открытые с согласием на аналитику.");
    const total = pages.reduce((n, row) => n + integer(row.count), 0);
    return table(["Страница", "Просмотры", "Доля"], pages.slice(0, compact ? 6 : 50).map(row => [E(pageNames[row.page] || row.page), `<strong class="numeric">${fmt(row.count)}</strong>`, `<span class="v6-analytics-share"><i style="width:${Math.round(integer(row.count) / Math.max(1, total) * 100)}%"></i><span>${Math.round(integer(row.count) / Math.max(1, total) * 100)}%</span></span>`]));
  }
  function eventPanel(data) {
    const map = new Map();
    for (const row of data.events || []) {
      if (!publicPages.has(row.page)) continue;
      const key = row.type + "|" + row.page;
      if (!map.has(key)) map.set(key, { type: row.type, page: row.page, count: 0 });
      map.get(key).count += integer(row.count);
    }
    const rows = [...map.values()].sort((a, b) => b.count - a.count);
    return rows.length ? table(["Событие", "Страница", "Количество"], rows.map(row => [E(eventNames[row.type] || row.type.replace(/_/g, " ")), E(pageNames[row.page] || row.page), `<strong class="numeric">${fmt(row.count)}</strong>`])) : empty("Событий пока нет", "Добавления в корзину и начало пополнения появятся после согласия посетителей.");
  }
  const deviceNames = { desktop: "Компьютер", mobile: "Телефон", tablet: "Планшет", other: "Другое" };
  const sourceNames = { direct: "Прямые переходы", internal: "Внутри сайта" };
  function shareBar(count, total) { const pct = Math.round(integer(count) / Math.max(1, integer(total)) * 100); return `<span class="v6-analytics-share"><i style="width:${pct}%"></i><span>${pct}%</span></span>`; }
  function shareTable(title, rows, nameLabel, formatter = name => name, hint = "") {
    const list = (rows || []).filter(row => row && row.name);
    const head = `<div class="panel-head"><h3>${E(title)}</h3>${hint ? `<span class="hint">${E(hint)}</span>` : ""}</div>`;
    if (!list.length) return `<section class="panel mt">${head}${empty("Данных пока нет", "Значения появятся после согласия посетителей на аналитику.")}</section>`;
    const total = list.reduce((n, row) => n + integer(row.count), 0);
    return `<section class="panel mt">${head}${table([nameLabel, "Просмотры", "Доля"], list.slice(0, 20).map(row => [E(formatter(row.name)), `<strong class="numeric">${fmt(row.count)}</strong>`, shareBar(row.count, total)]))}</section>`;
  }
  function audiencePanel(data) {
    const e = data.engagement || {}, accounts = data.accounts || {}, audience = data.audience || {};
    return `<div class="v6-analytics-kpis">${kpi("Уникальные посетители", fmt(e.uniqueVisitors), "Согласие на аналитику")}${kpi("Сеансы", fmt(e.sessions), "Открытые вкладки браузера")}${kpi("Вернулись", fmt(e.returning), "Приходили до начала периода")}${kpi("Аккаунты", fmt(accounts.total), "Создано всего")}${kpi("Новые аккаунты", fmt(accounts.new), "За выбранный период")}${kpi("Активные аккаунты", fmt(accounts.active), "Входили в аккаунт")}</div>`
      + shareTable("Устройства", audience.devices, "Тип", name => deviceNames[name] || name)
      + shareTable("Браузеры", audience.browsers, "Браузер")
      + shareTable("Операционные системы", audience.os, "Система")
      + shareTable("Языки браузера", audience.languages, "Язык")
      + shareTable("Разрешения экрана", audience.screens, "Экран")
      + `<p class="hint mt12">Minecraft привязан у ${fmt(accounts.linked)} из ${fmt(accounts.total)} аккаунтов. Статистика обезличена: IP-адрес и точное местоположение не собираются.</p>`;
  }
  function sourcesPanel(data) {
    const sources = (data.sources || []).filter(row => row && row.name);
    const campaigns = (data.campaigns || []).slice(0, 30);
    const total = sources.reduce((n, row) => n + integer(row.count), 0);
    return `<section class="panel"><div class="panel-head"><h3>Откуда пришли</h3><span class="hint">Домен предыдущей страницы</span></div>${sources.length ? table(["Источник", "Переходы", "Доля"], sources.map(row => [E(sourceNames[row.name] || row.name), `<strong class="numeric">${fmt(row.count)}</strong>`, shareBar(row.count, total)])) : empty("Источников пока нет", "Прямые переходы и внешние сайты появятся после согласия на аналитику.")}</section>`
      + `<section class="panel mt"><div class="panel-head"><h3>Метки ссылок</h3><span class="hint">utm_source · utm_medium · utm_campaign</span></div>${campaigns.length ? table(["Кампания", "Источник", "Тип", "Переходы"], campaigns.map(row => [E(row.campaign || "—"), E(row.source || "—"), E(row.medium || "—"), `<strong class="numeric">${fmt(row.count)}</strong>`])) : empty("Меток нет", "Ссылки с utm-метками ещё не открывали.")}</section>`
      + `<p class="hint mt12">Путь и содержимое внешних ссылок не сохраняются, только домен и метки.</p>`;
  }
  function consentsPanel(data) {
    const consent = data.consents || {}, recent = consent.recent || [], total = integer(consent.total);
    const share = total ? Math.round(integer(consent.analytics) / total * 100) : 0;
    const inventory = [
      ["Просмотры страниц", "публичные страницы: главная, магазин, корзина, донат, карта, правила, помощь", "оценивать интерес к разделам", "13 месяцев"],
      ["Действия в магазине", "событие, предмет, количество", "понимать спрос и ошибки выдачи", "13 месяцев"],
      ["Псевдоним посетителя", "случайный cookie cm_visitor", "отличать посетителей, считать возвраты", "13 месяцев"],
      ["Сеанс", "случайный идентификатор вкладки", "считать глубину визита", "до закрытия вкладки"],
      ["Устройство", "тип, браузер, ОС, язык, размер экрана", "правильно поддерживать интерфейсы", "13 месяцев"],
      ["Источник перехода", "домен предыдущей страницы и utm-метки", "оценивать эффективность ссылок", "13 месяцев"],
      ["Выбор cookie", "разрешённые категории, время, версия политики, браузер", "подтверждать и отзывать согласие", "3 года"],
      ["Аккаунт", "логин, почта, хэш пароля, привязка Minecraft, покупки, обращения", "работа аккаунта и безопасность", "пока аккаунт существует"],
    ];
    return `<div class="v6-analytics-kpis">${kpi("Записей о выборе", fmt(total), "За выбранный период")}${kpi("Аналитика разрешена", fmt(consent.analytics), share + "% записей")}${kpi("Настройки разрешены", fmt(consent.preferences), "Тема, анимации, корзина")}${kpi("Версия политики", consent.policyVersion || "—", "Отмечается в каждой записи")}</div>`
      + `<section class="panel mt"><div class="panel-head"><h3>Последние решения</h3><span class="hint">IP и идентификаторы не показаны</span></div>${recent.length ? table(["Время", "Аналитика", "Настройки", "Где выбрано"], recent.map(row => [E(new Date(integer(row.at)).toLocaleString("ru-RU", { dateStyle: "short", timeStyle: "short" })), M.pill(row.analytics ? "Разрешена" : "Отклонена", row.analytics ? "green" : "amber"), M.pill(row.preferences ? "Включены" : "Отключены", row.preferences ? "green" : ""), E(row.source === "settings" ? "Настройки" : "Баннер")])) : empty("Выборов пока нет", "Записи появляются, когда посетитель принимает или отклоняет cookie.")}</section>`
      + `<section class="panel mt"><div class="panel-head"><h3>Что собирается</h3><a class="text-link" href="#privacy">Официальная политика</a></div>${table(["Данные", "Как собираем", "Зачем", "Срок хранения"], inventory.map(row => row.map(E)))}</section>`
      + `<p class="hint mt12">Не собираем IP-адрес в статистике, точную геолокацию, данные платежей, рекламные идентификаторы и ничего вне copimine.ru.</p>`;
  }
  function economyPanel(data) {
    if (data.paymentsAvailable === false && chartCurrency === "RUB") chartCurrency = "CM";
    const daily = dailySeries((data.days || []).map(row => ({ day: typeof row.at === "number" ? dayString(row.at) : String(row.at).slice(0, 10), value: number(row[chartCurrency === "RUB" ? "rubles" : chartCurrency]) })), data);
    const top = (data.top || []).slice(0, 30);
    const units = ["AR", "CM", ...(data.paymentsAvailable === false ? [] : ["RUB"])];
    const chartTitle = chartCurrency === "RUB" ? "Оплаченные пополнения, ₽" : "Оборот, " + chartCurrency;
    return `<div class="v6-analytics-kpis v6-analytics-kpis-compact">${kpi("Оборот AR", fmt(data.revenue?.AR) + " AR", "Выданные предметы за AR")}${kpi("Оборот CM", fmt(data.revenue?.CM) + " CM", "Выданные предметы за CM")}${kpi("Пополнения", rubles(data), rublesHint(data))}</div><section class="panel"><div class="panel-head"><h3>Оборот по дням</h3><div class="v6-analytics-units" aria-label="Валюта графика">${units.map(unit => `<button type="button" data-action="v6-analytics-unit" data-unit="${unit}" aria-pressed="${unit === chartCurrency}">${unit === "RUB" ? "₽" : unit}</button>`).join("")}</div></div>${barChart(daily, chartTitle, " " + (chartCurrency === "RUB" ? "₽" : chartCurrency))}</section><section class="panel mt"><div class="panel-head"><h3>Покупаемые предметы</h3><span class="hint">По количеству</span></div>${top.length ? table(["Предмет", "Количество", "Оборот", "Заказы"], top.map(row => [E(row.name || row.id), fmt(row.qty), M.money(row.revenue, row.currency), fmt(row.orders)])) : empty("Покупок пока нет", "Предметы появятся здесь после подтверждения выдачи.")}</section><div class="v6-analytics-note"><span>Ожидают выдачи: <strong>${fmt(data.pending)}</strong></span><span>Ошибки выдачи: <strong>${fmt(data.failed)}</strong></span><span>Полностью возвращено: <strong>${fmt(data.refunded)}</strong></span><span>Покупатели: <strong>${fmt(data.buyers)}</strong></span></div><p class="hint mt12">Оборот включает подтверждённую выдачу. В частично выполненном заказе учитываются только выданные предметы.</p>`;
  }
  function render(sub = "overview") {
    const owner = M.me?.()?.id || "";
    if (cacheOwner !== owner) { cache.clear(); requests.clear(); errors.clear(); cacheOwner = owner; }
    if (!canRead()) { cache.clear(); requests.clear(); errors.clear(); return M.empty("Нет доступа", "Статистика доступна сотрудникам с правом просмотра аналитики.", "lock"); }
    if (!tabs.some(([id]) => id === sub)) sub = "overview";
    const range = selectedRange(), b = bounds(range), cached = cache.get(range);
    if (X.live && !cached && !requests.has(range) && !errors.has(range)) Promise.resolve().then(() => loadSummary(range));
    const data = X.live ? cached?.data : localSummary(range);
    window.CMAnalyticsReactModel = { data: data || null, sub, range, loading: !data && !errors.has(range), error: errors.get(range) || '' };
    const heading = `<div class="v6-analytics-toolbar"><div class="v6-analytics-range" aria-label="Период отчёта">${ranges.map(([id, title]) => `<button type="button" data-action="v6-analytics-range" data-range="${id}" aria-pressed="${range === id}">${title}</button>`).join("")}</div><div class="actions">${M.btn("Обновить", "v6-analytics-refresh", "refresh", "small", requests.has(range) ? "disabled" : "")}${M.btn("CSV", "v6-analytics-export", "download", "small", data ? "" : "disabled")}</div></div><nav class="tabs" aria-label="Разделы статистики">${tabs.map(([id, label]) => `<a href="#admin/statistics?sub=${id}&range=${range}" class="${sub === id ? "active" : ""}">${E(label)}</a>`).join("")}</nav>`;
    if (!data) return heading + (errors.has(range) ? `<section class="panel">${empty("Не удалось загрузить статистику", errors.get(range))}${M.btn("Повторить", "v6-analytics-refresh", "refresh", "primary")}</section>` : `<section class="panel v6-analytics-loading"><div id="cm-react-analytics-charts"></div></section>`);
    let body;
    const engagement = data.engagement || {};
    if (sub === "economy") body = economyPanel(data);
    else if (sub === "audience") body = audiencePanel(data);
    else if (sub === "sources") body = sourcesPanel(data);
    else if (sub === "consents") body = consentsPanel(data);
    else if (sub === "pages") body = `<section class="panel"><div class="panel-head"><h3>Посещения по страницам</h3></div>${pagesPanel(data)}</section><section class="panel mt">${barChart(trafficDays(data), "Просмотры по дням")}</section>`;
    else if (sub === "events") body = `<section class="panel"><div class="panel-head"><h3>Действия на публичных страницах</h3></div>${eventPanel(data)}</section><div id="cm-react-analytics-charts"></div>`;
    else body = `<div class="v6-analytics-kpis">${kpi("Просмотры", fmt(data.visits), "Открытия публичных страниц")}${kpi("Уникальные посетители", fmt(engagement.uniqueVisitors), "С согласием на аналитику")}${kpi("Сеансы", fmt(engagement.sessions), "Открытые вкладки")}${kpi("Посетители по дням", fmt(data.visitors), "Сумма дневных уникальных")}${kpi("Заказы", fmt(data.orders), "С выданными предметами")}${kpi("Выдано предметов", fmt(data.items), "Штук за выбранный период")}${kpi("Оборот CM", fmt(data.revenue?.CM) + " CM", "Покупки в магазине")}${kpi("Пополнения", rubles(data), rublesHint(data))}</div><section class="panel"><div class="panel-head"><h3>Посещения</h3></div>${barChart(trafficDays(data), "Просмотры по дням")}</section>`;
    return heading + body + `<footer class="v6-analytics-footer"><span>${X.live ? "Данные сайта" : "Локальная проверка"} · ${E(b.from + " — " + b.to)} · UTC${cached ? " · обновлено " + E(new Date(cached.at).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })) : ""}</span><p>Учитываются публичные страницы и согласие на аналитику. Имена игроков и содержимое форм в отчёт не попадают. <a href="#privacy">Какие данные собираются</a>.</p></footer>`;
  }
  function dashboardCharts() {
    const owner = M.me?.()?.id || "";
    if (cacheOwner !== owner) { cache.clear(); requests.clear(); errors.clear(); cacheOwner = owner; }
    if (!canRead()) return "";
    const range = selectedRange(), cached = cache.get(range), error = errors.get(range) || "";
    if (X.live && !cached && !requests.has(range) && !error) Promise.resolve().then(() => loadSummary(range));
    const data = X.live ? cached?.data : localSummary(range);
    window.CMAnalyticsReactModel = { data: data || null, sub: "overview", range, loading: !data && !error, error };
    return `<section class="panel mt v71-overview-analytics"><div class="panel-head"><h2>Аналитика сайта</h2>${M.link("Все отчёты", "#admin/statistics", "arrow-right", "ghost small")}</div><div id="cm-react-analytics-charts"></div></section>`;
  }
  W.adminExtensions.statistics = render;
  W.adminExtensions.dashboardAnalytics = dashboardCharts;
  H["v6-analytics-range"] = button => {
    const range = button.dataset.range;
    if (!ranges.some(([id]) => id === range)) return;
    const params = new URLSearchParams(location.hash.split("?")[1] || ""), sub = params.get("sub") || "overview";
    M.navTo("admin/statistics?sub=" + (tabs.some(([id]) => id === sub) ? sub : "overview") + "&range=" + range);
  };
  H["v6-analytics-unit"] = button => { if (["AR", "CM", "RUB"].includes(button.dataset.unit)) { chartCurrency = button.dataset.unit; M.render(); } };
  H["v6-analytics-refresh"] = async () => {
    const range = selectedRange();
    errors.delete(range); cache.delete(range);
    if (X.live) { const pending = loadSummary(range, true); M.render(); await pending; } else M.render();
  };
  H["v6-analytics-export"] = () => {
    if (!canRead()) return;
    const range = selectedRange(), data = X.live ? cache.get(range)?.data : localSummary(range);
    if (!data) return;
    const e = data.engagement || {}, consent = data.consents || {}, accounts = data.accounts || {};
    const rows = [["Показатель", "Значение"], ["С", data.from || bounds(range).from], ["По", data.to || bounds(range).to], ["Просмотры", data.visits], ["Посетители по дням", data.visitors], ["Уникальные посетители", e.uniqueVisitors], ["Сеансы", e.sessions], ["Вернулись", e.returning], ["Заказы", data.orders], ["Покупатели", data.buyers], ["Предметы", data.items], ["Оборот AR", data.revenue?.AR], ["Оборот CM", data.revenue?.CM], ["Оплачено RUB", data.paymentsAvailable === false ? "Платёжный сервис не подключён" : data.rubles], ["Записей о согласии", consent.total], ["Аналитика разрешена", consent.analytics], ["Настройки разрешены", consent.preferences], ["Аккаунтов всего", accounts.total], ["Новых аккаунтов", accounts.new], ["Активных аккаунтов", accounts.active], ["Minecraft привязан", accounts.linked], [], ["Страница", "Просмотры"], ...(data.pages || []).filter(row => publicPages.has(row.page)).map(row => [pageNames[row.page], row.count]), [], ["Устройство", "Просмотры"], ...(data.audience?.devices || []).map(row => [row.name, row.count]), [], ["Браузер", "Просмотры"], ...(data.audience?.browsers || []).map(row => [row.name, row.count]), [], ["Система", "Просмотры"], ...(data.audience?.os || []).map(row => [row.name, row.count]), [], ["Язык", "Просмотры"], ...(data.audience?.languages || []).map(row => [row.name, row.count]), [], ["Экран", "Просмотры"], ...(data.audience?.screens || []).map(row => [row.name, row.count]), [], ["Источник", "Переходы"], ...(data.sources || []).map(row => [row.name, row.count]), [], ["utm_source", "utm_medium", "utm_campaign", "Переходы"], ...(data.campaigns || []).map(row => [row.source, row.medium, row.campaign, row.count])];
    const csv = "\uFEFF" + rows.map(row => row.map(value => '"' + String(value ?? "").replace(/"/g, '""') + '"').join(";")).join("\r\n");
    M.downloadFile("CopiMine-analytics-" + dayString(Date.now()) + ".csv", csv);
  };
  X.analytics = { flush, localSummary, bounds, render, queueSize: () => queue.length };
})();
