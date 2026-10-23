/* CopiMine v6 · consent-based public analytics and truthful administration reports. */
(function () {
  "use strict";
  const M = window.CM, V = window.CMV4, W = window.CM5, X = window.CM6;
  if (!M || !V || !W || !X) return;
  const E = M.esc, H = M.handlers, DAY = 86400000;
  const publicPages = new Set(["home", "shop", "donate", "map", "rules", "support"]);
  const pageNames = { home: "Главная", shop: "Магазин", donate: "Донат", map: "Карта", rules: "Правила", support: "Помощь" };
  const eventNames = { page_view: "Открытие страницы", cart_add: "Добавление в корзину", add_to_cart: "Добавление в корзину", item_view: "Просмотр предмета", product_view: "Просмотр предмета", cart_remove: "Удаление из корзины", donation_start: "Начало пополнения", payment_created: "Создание платежа", checkout_start: "Начало оформления", checkout_started: "Начало оформления", checkout_complete: "Заказ оформлен", checkout_completed: "Заказ оформлен", checkout: "Оформление заказа", purchase: "Покупка", download_mods: "Загрузка сборки", download_launcher: "Загрузка лаунчера", play: "Инструкция по входу" };
  const ranges = [["day", "Сегодня"], ["7d", "7 дней"], ["30d", "30 дней"], ["year", "За год"]];
  const tabs = [["overview", "Обзор"], ["pages", "Страницы"], ["events", "События"], ["economy", "Экономика"]];
  const storageKey = "copimine.analytics.visitor.v6";
  let queue = [], flushTimer = null, sending = false, generation = 0, lastPage = "", chartCurrency = "CM", cacheOwner = "";
  const cache = new Map(), requests = new Map(), errors = new Map();
  const integer = value => Number.isFinite(Number(value)) ? Math.max(0, Math.trunc(Number(value))) : 0;
  const number = value => Number.isFinite(Number(value)) ? Number(value) : 0;
  const fmt = value => number(value).toLocaleString("ru-RU");
  const dayString = value => new Date(value).toISOString().slice(0, 10);
  function consented() { return X.consent?.analytics === true; }
  function currentPage() {
    const page = (location.hash.slice(1).split(/[/?]/)[0] || "home").toLowerCase();
    return publicPages.has(page) ? page : null;
  }
  function visitor() {
    try {
      let id = sessionStorage.getItem(storageKey);
      if (!/^[a-z0-9-]{16,80}$/i.test(id || "")) {
        id = crypto.randomUUID ? crypto.randomUUID() : [...crypto.getRandomValues(new Uint8Array(16))].map(n => n.toString(16).padStart(2, "0")).join("");
        sessionStorage.setItem(storageKey, id);
      }
      return id;
    } catch { return null; }
  }
  function schedule(ms = 1500) {
    if (flushTimer || !X.live || !queue.length || !consented()) return;
    flushTimer = setTimeout(() => { flushTimer = null; flush(); }, ms);
  }
  async function flush() {
    if (sending || !consented() || !X.live || !queue.length || typeof X.request !== "function") return;
    const batch = queue.splice(0, 40), epoch = generation;
    sending = true;
    try { await X.request("/analytics/events", { method: "POST", body: { events: batch, consent: true } }); }
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
    generation++; queue = [];
    clearTimeout(flushTimer); flushTimer = null;
    if (!consented()) { try { sessionStorage.removeItem(storageKey); } catch {} }
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
  function adminPage() { return location.hash.startsWith("#admin/statistics"); }
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
  function barChart(rows, title, suffix = "", color = "green") {
    if (!rows.length || !rows.some(row => number(row.value) > 0)) return rows.windowed ? empty("Нет событий за последние 90 дней", "Итоги всего выбранного периода доступны в показателях выше.") : empty("Пока нет данных", "За выбранный период нет событий для этого графика.");
    if (rows.windowed) title += " · последние 90 дней";
    const sorted = rows.slice().sort((a, b) => String(a.day).localeCompare(String(b.day))).slice(-90);
    const max = Math.max(1, ...sorted.map(row => number(row.value))), width = 800, top = 20, base = 175, height = 180, step = 740 / sorted.length;
    const bars = sorted.map((row, index) => {
      const h = Math.max(0, number(row.value) / max * 145), barWidth = Math.max(1, Math.min(84, step * .76));
      return `<rect x="${30 + index * step + (step - barWidth) / 2}" y="${base - h}" width="${barWidth}" height="${h}" rx="${Math.min(4, step / 5)}"><title>${E(row.day)}: ${E(fmt(row.value) + suffix)}</title></rect>`;
    }).join("");
    const middle = Math.floor((sorted.length - 1) / 2);
    return `<figure class="v6-analytics-chart ${color}"><figcaption>${E(title)}</figcaption><div class="v6-analytics-chart-max">${E(fmt(max) + suffix)}</div><svg viewBox="0 0 ${width} ${height}" role="img" aria-label="${E(title)}. Максимум: ${E(fmt(max) + suffix)}" preserveAspectRatio="none"><line x1="30" x2="770" y1="${top}" y2="${top}"></line><line x1="30" x2="770" y1="${base}" y2="${base}"></line>${bars}</svg><div class="v6-analytics-chart-axis"><span>${E(sorted[0].day)}</span>${sorted.length > 2 ? `<span class="v6-axis-middle">${E(sorted[middle].day)}</span>` : ""}${sorted.length > 1 ? `<span>${E(sorted[sorted.length - 1].day)}</span>` : ""}</div></figure>`;
  }
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
  function economyPanel(data) {
    const daily = dailySeries((data.days || []).map(row => ({ day: typeof row.at === "number" ? dayString(row.at) : String(row.at).slice(0, 10), value: number(row[chartCurrency === "RUB" ? "rubles" : chartCurrency]) })), data);
    const top = (data.top || []).slice(0, 30);
    return `<div class="v6-analytics-kpis v6-analytics-kpis-compact">${kpi("Оборот AR", fmt(data.revenue?.AR) + " AR", "Выданные предметы за AR")}${kpi("Оборот CM", fmt(data.revenue?.CM) + " CM", "Выданные предметы за CM")}${kpi("Оплаченные пополнения", fmt(data.rubles) + " ₽", "Только подтверждённые платежи")}</div><section class="panel"><div class="panel-head"><h3>Оборот по дням</h3><div class="v6-analytics-units" aria-label="Валюта графика">${["AR", "CM", "RUB"].map(unit => `<button type="button" data-action="v6-analytics-unit" data-unit="${unit}" aria-pressed="${unit === chartCurrency}">${unit === "RUB" ? "₽" : unit}</button>`).join("")}</div></div>${barChart(daily, chartCurrency === "RUB" ? "Оплаченные пополнения, ₽" : "Оборот, " + chartCurrency, " " + (chartCurrency === "RUB" ? "₽" : chartCurrency))}</section><section class="panel mt"><div class="panel-head"><h3>Покупаемые предметы</h3><span class="hint">По количеству</span></div>${top.length ? table(["Предмет", "Количество", "Оборот", "Заказы"], top.map(row => [E(row.name || row.id), fmt(row.qty), M.money(row.revenue, row.currency), fmt(row.orders)])) : empty("Покупок пока нет", "Предметы появятся здесь после подтверждения выдачи.")}</section><div class="v6-analytics-note"><span>Ожидают выдачи: <strong>${fmt(data.pending)}</strong></span><span>Ошибки выдачи: <strong>${fmt(data.failed)}</strong></span><span>Полностью возвращено: <strong>${fmt(data.refunded)}</strong></span><span>Покупатели: <strong>${fmt(data.buyers)}</strong></span></div><p class="hint mt12">Оборот включает подтверждённую выдачу. В частично выполненном заказе учитываются только выданные предметы.</p>`;
  }
  function render(sub = "overview") {
    const owner = M.me?.()?.id || "";
    if (cacheOwner !== owner) { cache.clear(); requests.clear(); errors.clear(); cacheOwner = owner; }
    if (!canRead()) { cache.clear(); requests.clear(); errors.clear(); return M.empty("Нет доступа", "Статистика доступна сотрудникам с правом просмотра аналитики.", "lock"); }
    if (!tabs.some(([id]) => id === sub)) sub = "overview";
    const range = selectedRange(), b = bounds(range), cached = cache.get(range);
    if (X.live && !cached && !requests.has(range) && !errors.has(range)) Promise.resolve().then(() => loadSummary(range));
    const data = X.live ? cached?.data : localSummary(range);
    const heading = `<div class="v6-analytics-toolbar"><div class="v6-analytics-range" aria-label="Период отчёта">${ranges.map(([id, title]) => `<button type="button" data-action="v6-analytics-range" data-range="${id}" aria-pressed="${range === id}">${title}</button>`).join("")}</div><div class="actions">${M.btn("Обновить", "v6-analytics-refresh", "refresh", "small", requests.has(range) ? "disabled" : "")}${M.btn("CSV", "v6-analytics-export", "download", "small", data ? "" : "disabled")}</div></div><nav class="tabs" aria-label="Разделы статистики">${tabs.map(([id, label]) => `<a href="#admin/statistics?sub=${id}&range=${range}" class="${sub === id ? "active" : ""}">${E(label)}</a>`).join("")}</nav>`;
    if (!data) return heading + (errors.has(range) ? `<section class="panel">${empty("Не удалось загрузить статистику", errors.get(range))}${M.btn("Повторить", "v6-analytics-refresh", "refresh", "primary")}</section>` : `<section class="panel v6-analytics-loading" role="status">Загружаем статистику…</section>`);
    let body;
    if (sub === "economy") body = economyPanel(data);
    else if (sub === "pages") body = `<section class="panel"><div class="panel-head"><h3>Посещения по страницам</h3></div>${pagesPanel(data)}</section><section class="panel mt">${barChart(trafficDays(data), "Просмотры по дням")}</section>`;
    else if (sub === "events") body = `<section class="panel"><div class="panel-head"><h3>Действия на публичных страницах</h3></div>${eventPanel(data)}</section>`;
    else body = `<div class="v6-analytics-kpis">${kpi("Просмотры", fmt(data.visits), "Открытия публичных страниц")}${kpi("Посетители по дням", fmt(data.visitors), "Сумма дневных уникальных")}${kpi("Заказы", fmt(data.orders), "С выданными предметами")}${kpi("Выдано предметов", fmt(data.items), "Штук за выбранный период")}${kpi("Оборот CM", fmt(data.revenue?.CM) + " CM", "Покупки в магазине")}${kpi("Пополнения", fmt(data.rubles) + " ₽", "Подтверждённые платежи")}</div><div class="v6-analytics-grid"><section class="panel"><div class="panel-head"><h3>Посещения</h3></div>${barChart(trafficDays(data), "Просмотры по дням")}</section><section class="panel"><div class="panel-head"><h3>Популярные страницы</h3></div>${pagesPanel(data, true)}</section></div>`;
    return heading + body + `<footer class="v6-analytics-footer"><span>${X.live ? "Данные сайта" : "Локальная проверка"} · ${E(b.from + " — " + b.to)} · UTC${cached ? " · обновлено " + E(new Date(cached.at).toLocaleTimeString("ru-RU", { hour: "2-digit", minute: "2-digit" })) : ""}</span><p>Учитываются публичные страницы и согласие на аналитику. Поиск в панели, имена игроков и содержимое форм в отчёт не попадают.</p></footer>`;
  }
  W.adminExtensions.statistics = render;
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
    const rows = [["Показатель", "Значение"], ["С", data.from || bounds(range).from], ["По", data.to || bounds(range).to], ["Просмотры", data.visits], ["Посетители по дням", data.visitors], ["Заказы", data.orders], ["Покупатели", data.buyers], ["Предметы", data.items], ["Оборот AR", data.revenue?.AR], ["Оборот CM", data.revenue?.CM], ["Оплачено RUB", data.rubles], [], ["Страница", "Просмотры"], ...(data.pages || []).filter(row => publicPages.has(row.page)).map(row => [pageNames[row.page], row.count])];
    const csv = "\uFEFF" + rows.map(row => row.map(value => '"' + String(value ?? "").replace(/"/g, '""') + '"').join(";")).join("\r\n");
    M.downloadFile("CopiMine-analytics-" + dayString(Date.now()) + ".csv", csv);
  };
  X.analytics = { flush, localSummary, bounds, render, queueSize: () => queue.length };
})();
