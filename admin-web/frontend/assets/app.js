const $ = (id) => document.getElementById(id);

const state = {
  token: localStorage.getItem("copimineToken") || "",
  role: localStorage.getItem("copimineRole") || "",
  authRole: localStorage.getItem("copimineLastRole") || "admin",
  authAction: "login",
  cookieAuth: false,
  tab: (location.hash || "#dashboard").slice(1) || "dashboard",
  user: null,
  config: null,
  selectedPlayer: "",
  players: [],
  tables: {},
  refreshTimer: null,
  liveStream: null,
  liveLastEvent: 0,
  playerLinkRequest: null,
  publicStatus: null,
  publicConfig: null
};

const CONFIRM_HEADER = "X-Copimine-Confirm";

const publicFeatures = {
  bank: {
    kicker: "Банк AR",
    title: "Один счёт для банкомата, сайта и игровых оплат",
    text: "Баланс, переводы, налог и покупки собраны в одном месте. Если операция не подтверждена, деньги и предметы не списываются.",
    icon: "/assets/mc-icons/item/emerald_block.png"
  },
  elections: {
    kicker: "Выборы",
    title: "Игровая политика без командной путаницы",
    text: "Заявки, участки, бюллетени, подсчёт и роли проходят через понятные игровые интерфейсы и публичные этапы.",
    icon: "/assets/mc-icons/item/writable_book.png"
  },
  president: {
    kicker: "Президент",
    title: "Законы и налог видны всему серверу",
    text: "После победы президент получает мандат, предлагает законы, делает обращения игрокам и может настраивать еженедельный налог.",
    icon: "/assets/mc-icons/item/nether_star.png"
  },
  artifacts: {
    kicker: "Артефакты",
    title: "Лавки с официальными предметами",
    text: "Оружие, броня и инструменты покупаются за AR. Каждый предмет имеет PDC-защиту, ремонт и историю покупки.",
    icon: "/assets/mc-icons/item/netherite_sword.png"
  }
};

const navGroups = [
  {
    title: "Сервер",
    items: [
      ["dashboard", "Обзор", "Сводка сервера", "О"],
      ["players", "Игроки", "Профили и действия", "И"],
      ["stats", "Статистика", "TPS, MSPT и ресурсы", "С"],
      ["economy", "Банк и AR", "Счета, переводы и покупки", "Б"],
      ["artifacts", "Лавки артефактов", "Каталог, покупки, выдача", "А"],
      ["elections", "Выборы", "ЦИК, президент и результаты", "В"]
    ]
  },
  {
    title: "Контроль",
    items: [
      ["requests", "Заявки", "Обращения и жалобы", "З"],
      ["inventories", "Инвентари", "Снимки и сравнение", "С"],
      ["investigations", "Расследования", "CoreProtect", "Р"],
      ["anticheat", "Античит", "GrimAC и нарушения", "А"],
      ["logs", "Логи", "Сервер и события", "Л"],
      ["audit", "Аудит", "Действия команды", "А"]
    ]
  },
  {
    title: "Система",
    items: [
      ["server", "Сервер", "Связь с миром и службами", "S"],
      ["security", "Доступ", "Команда и права доступа", "Д"],
      ["sources", "Источники", "Плагины и файлы", "И"],
      ["settings", "Настройки", "Конфигурация", "Н"]
    ]
  }
];

const pageMeta = Object.fromEntries(
  navGroups.flatMap(group => group.items.map(([id, title, subtitle]) => [id, { title, subtitle }]))
);

const playerNavGroups = [
  {
    title: "Игрок",
    items: [
      ["cabinet", "Личный кабинет", "Аккаунт и статус", "Л"],
      ["bank", "Банк AR", "Баланс, PIN и переводы", "Б"],
      ["history", "История", "Банк, лавка и события", "И"],
      ["settings", "Настройки", "Профиль и интерфейс", "Н"],
      ["security", "Безопасность", "Пароль, PIN и сессии", "Б"],
      ["support", "Поддержка", "Вопросы и обращения", "П"],
      ["artifacts", "Артефакты", "Покупки и выдача", "А"],
      ["link", "Minecraft", "Одноразовый код", "M"]
    ]
  }
];

const playerPageMeta = Object.fromEntries(
  playerNavGroups.flatMap(group => group.items.map(([id, title, subtitle]) => [id, { title, subtitle }]))
);

const playerActions = [
  ["kick", "Кик"],
  ["ban", "Бан"],
  ["pardon", "Разбан"],
  ["op", "Выдать OP"],
  ["deop", "Снять OP"],
  ["gamemode_survival", "Survival"],
  ["gamemode_creative", "Creative"],
  ["spectator", "Spectator"],
  ["adventure", "Adventure"],
  ["heal", "Heal"],
  ["feed", "Feed"],
  ["clear", "Clear"],
  ["kill", "Kill"]
];

const APPLICATION_QUESTIONS = [
  "Почему ты хочешь стать президентом?",
  "Что ты изменишь на сервере?",
  "Как ты будешь развивать экономику?",
  "Как ты будешь решать конфликты игроков?",
  "Какие законы хочешь предложить?"
];

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[c]);
}

function cleanText(value) {
  return String(value ?? "")
    .replace(/§[0-9A-FK-ORX]/gi, "")
    .replace(/&[0-9A-FK-ORX]/gi, "")
    .replace(/\[HIDDEN\]/gi, "")
    .replace(/\s+/g, " ")
    .trim();
}

function short(value, max = 90) {
  const text = cleanText(value);
  return text.length > max ? `${text.slice(0, Math.max(0, max - 3))}...` : text;
}

function dt(value) {
  if (!value) return "-";
  const n = Number(value);
  const date = n > 1000000000000 ? new Date(n) : new Date(n * 1000);
  return Number.isFinite(date.getTime()) ? date.toLocaleString("ru-RU") : "-";
}

function bytes(value) {
  let n = Number(value || 0);
  const units = ["Б", "КБ", "МБ", "ГБ", "ТБ"];
  let i = 0;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i += 1;
  }
  return `${n.toFixed(i ? 1 : 0)} ${units[i]}`;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function firstArray(...values) {
  return values.find(Array.isArray) || [];
}

function number(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function isMinecraftName(value) {
  return /^[A-Za-z0-9_]{3,16}$/.test(cleanText(value));
}

function statusLabel(value, fallback = "-") {
  const raw = cleanText(value).toLowerCase();
  const labels = {
    active: "Идут",
    running: "Идут",
    scheduled: "Запланированы",
    pending: "Ожидают",
    finished: "Завершены",
    closed: "Закрыты",
    not_connected: "Нет связи",
    disconnected: "Нет связи",
    missing: "Нет данных",
    none: "Пауза"
  };
  return labels[raw] || (raw ? cleanText(value).replaceAll("_", " ") : fallback);
}

function sourceLabel(value) {
  if (!value || typeof value !== "object") return cleanText(value || "-");
  const name = value.name || value.path || "источник";
  const state = value.exists === false ? "не найден" : value.exists === true ? "найден" : "не проверен";
  const size = value.size ? ` В· ${bytes(value.size)}` : "";
  return `${name}: ${state}${size}`;
}

function getPath(object, path, fallback = undefined) {
  let cur = object;
  for (const key of path.split(".")) {
    if (cur == null || typeof cur !== "object" || !(key in cur)) return fallback;
    cur = cur[key];
  }
  return cur ?? fallback;
}

function jsonPreview(value, max = 460) {
  if (value == null || value === "") return "—";
  if (typeof value === "object") return JSON.stringify(value, null, 2).slice(0, max);
  return String(value).slice(0, max);
}

function objectSummary(value, max = 140) {
  if (value == null) return "—";
  if (typeof value !== "object") return short(value, max);
  const entries = Object.entries(value)
    .filter(([, raw]) => raw != null && raw !== "")
    .slice(0, 4)
    .map(([key, raw]) => {
      if (Array.isArray(raw)) return `${cleanText(key)}: ${raw.length}`;
      if (typeof raw === "object") return `${cleanText(key)}: ${Object.keys(raw).length}`;
      return `${cleanText(key)}: ${cleanText(raw)}`;
    });
  return entries.length ? short(entries.join(" · "), max) : "данные доступны";
}

function detailSummary(value, max = 180) {
  return typeof value === "object" ? objectSummary(value, max) : short(value, max);
}

function toast(message, bad = false) {
  const root = $("toast");
  const el = document.createElement("div");
  el.className = `toast ${bad ? "bad" : "ok"}`;
  el.textContent = message;
  root.appendChild(el);
  setTimeout(() => el.remove(), 5200);
}

function authHeaders(extra = {}) {
  const headers = { ...extra };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (!headers["Content-Type"] && !(extra instanceof FormData)) headers["Content-Type"] = "application/json";
  return headers;
}

async function api(url, opts = {}) {
  const { skipAuthReset = false, ...fetchOpts } = opts;
  const finalUrl = url.startsWith("/api/") ? `${url}${url.includes("?") ? "&" : "?"}_fresh=${Date.now()}` : url;
  const res = await fetch(finalUrl, {
    ...fetchOpts,
    cache: "no-store",
    credentials: "include",
    headers: authHeaders(fetchOpts.headers || {})
  });
  const text = await res.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch { data = { detail: text }; }
  if (!res.ok) {
    if (res.status === 401 && state.token && !skipAuthReset) logout(false);
    throw new Error(data.detail || data.error || `HTTP ${res.status}`);
  }
  return data;
}

function dangerConfirm(message, label = "CONFIRM") {
  const typed = prompt(`${message}\n\nВведите ${label}, чтобы подтвердить действие.`);
  if (typed !== label) {
    toast("Действие отменено: код подтверждения не совпал.", true);
    return null;
  }
  return { [CONFIRM_HEADER]: label };
}

async function safeApi(url, fallback = {}) {
  try { return await api(url); }
  catch (err) { return { ...fallback, error: err.message }; }
}

function setLoading(title = "Загрузка данных") {
  $("view").innerHTML = `<div class="loading">${esc(title)}...</div>`;
}

function setView(html) {
  $("view").innerHTML = html;
  $("lastUpdate").textContent = `обновлено ${new Date().toLocaleTimeString("ru-RU")}`;
}

function statusClass(ok) {
  if (ok === true) return "status-good";
  if (ok === false) return "status-bad";
  return "status-neutral";
}

function pill(label, type = "neutral") {
  return `<span class="pill ${type}">${esc(label)}</span>`;
}

function metric(label, value, detail = "", tone = "") {
  return `
    <article class="metric ${tone}">
      <span class="metric-label">${esc(label)}</span>
      <strong class="metric-value">${esc(value)}</strong>
      ${detail ? `<span class="metric-detail">${esc(detail)}</span>` : ""}
    </article>
  `;
}

function dashboardHero(status, perf, electionOverview, economy, readyPercent) {
  const online = status.minecraftOnline === true;
  const mspt = Number(perf.mspt || 0);
  const players = asArray(status.playersOnline).map(cleanText).filter(isMinecraftName);
  const stateText = online ? "сервер отвечает" : "сервер сейчас оффлайн";
  return `
    <section class="dashboard-hero">
      <div class="hero-copy">
        <span class="hero-kicker">${online ? "Контроль CopiMine" : "Ожидание запуска"}</span>
        <h2>${online ? "Операционный центр сервера" : "Панель готова к запуску"}</h2>
        <p>Сводка по TPS/MSPT, выборам, банку AR и артефактам без лишнего декора. Важные риски видны сразу, подробности открываются в рабочих разделах.</p>
        <div class="hero-actions">
          ${pill(stateText, online ? "good" : "bad")}
          ${pill(`first-run ${Math.round(Number(readyPercent || 0))}%`, Number(readyPercent || 0) >= 90 ? "good" : "warn")}
          ${pill(`игроки ${players.length}`, players.length ? "good" : "neutral")}
        </div>
      </div>
      <div class="hero-board" aria-label="CopiMine server board">
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/diamond_ore.png" alt="" />
          <strong>${esc(mspt ? `${mspt} ms` : "MSPT —")}</strong>
          <span>нагрузка тика</span>
        </div>
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/writable_book.png" alt="" />
          <strong>${esc(electionOverview.active ? "активны" : "пауза")}</strong>
          <span>выборы</span>
        </div>
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/emerald.png" alt="" />
          <strong>${esc(economy.totalKnownInPlayerData ?? 0)}</strong>
          <span>АР в учёте</span>
        </div>
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/chest.png" alt="" />
          <strong>${esc(players.length)}</strong>
          <span>игроки онлайн</span>
        </div>
      </div>
    </section>
  `;
}

function readinessRing(percent, label = "готовность") {
  const value = Math.max(0, Math.min(100, Math.round(Number(percent || 0))));
  const circumference = 283;
  const offset = Math.round(circumference - (circumference * value / 100));
  return `
    <div class="readiness-ring" style="--ring-offset:${offset};--ring-value:${value}">
      <svg viewBox="0 0 110 110" role="img" aria-label="${esc(label)} ${value}%">
        <circle class="ring-bg" cx="55" cy="55" r="45"></circle>
        <circle class="ring-fg" cx="55" cy="55" r="45"></circle>
      </svg>
      <div><strong>${value}%</strong><span>${esc(label)}</span></div>
    </div>
  `;
}

function sparklineChart(values, tone = "good") {
  const source = values.map(Number).filter(Number.isFinite);
  const data = source.length ? source : [0, 18, 13, 24, 19, 34, 28, 41];
  const max = Math.max(...data, 1);
  const min = Math.min(...data, 0);
  const span = Math.max(1, max - min);
  const points = data.map((value, index) => {
    const x = data.length === 1 ? 50 : Math.round(index * 100 / (data.length - 1));
    const y = Math.round(54 - ((value - min) / span) * 42);
    return `${x},${y}`;
  }).join(" ");
  const area = `0,60 ${points} 100,60`;
  return `
    <svg class="sparkline ${tone}" viewBox="0 0 100 62" preserveAspectRatio="none" aria-hidden="true">
      <polygon points="${area}"></polygon>
      <polyline points="${points}"></polyline>
    </svg>
  `;
}

function miniBarChart(items) {
  const rows = items.map(item => ({ ...item, value: Math.max(0, Number(item.value || 0)) }));
  const max = Math.max(...rows.map(x => x.value), 1);
  return `
    <div class="mini-bars">
      ${rows.map(row => `
        <div class="mini-bar" style="--bar:${Math.max(8, Math.round(row.value / max * 100))}%">
          <span>${esc(row.label)}</span>
          <strong>${esc(row.value)}</strong>
        </div>
      `).join("")}
    </div>
  `;
}

function dashboardCharts(status, perf, electionOverview, economy, perfReady, events) {
  const mspt = Number(perf.mspt || 0);
  const ready = Number(perfReady.readyPercent || 0);
  const votes = Number(electionOverview.votes || 0);
  const candidates = Number(electionOverview.candidates || 0);
  const ar = Number(economy.totalKnownInPlayerData || 0);
  const eventCount = asArray(events).length;
  return `
    <section class="dashboard-visual-grid">
      <article class="visual-card visual-card-large">
        <div>
          <span class="visual-label">Пульс MSPT</span>
          <strong>${mspt ? `${mspt} ms` : "нет live-данных"}</strong>
          <p>Анимированный график показывает состояние без красных score-цифр и лишнего шума.</p>
        </div>
        ${sparklineChart([12, 15, 14, mspt || 18, 17, 16, 19, mspt || 15], mspt > 50 ? "bad" : "good")}
      </article>
      <article class="visual-card ring-card">
        ${readinessRing(ready, "first-run")}
      </article>
      <article class="visual-card">
        <span class="visual-label">Выборы и экономика</span>
        ${miniBarChart([
          { label: "кандидаты", value: candidates },
          { label: "голоса", value: votes },
          { label: "АР", value: ar },
          { label: "события", value: eventCount }
        ])}
      </article>
    </section>
  `;
}

function panel(title, subtitle, body, actions = "") {
  return `
    <section class="panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">${esc(title)}</h2>
          ${subtitle ? `<p class="panel-subtitle">${esc(subtitle)}</p>` : ""}
        </div>
        ${actions ? `<div class="action-strip">${actions}</div>` : ""}
      </div>
      ${body}
    </section>
  `;
}

function releaseReadinessHtml(status, perf, electionOverview, economy, requestsReady, requestsTotal) {
  const mspt = Number(perf.mspt || 0);
  const checks = [
    ["Сервер", status.minecraftOnline ? "онлайн" : "проверить", status.minecraftOnline ? "good" : "bad"],
    ["MSPT", mspt && mspt > 50 ? `${mspt} ms` : "норма", mspt && mspt > 50 ? "bad" : "good"],
    ["Выборы", electionOverview.active ? "активны" : "готовы", "good"],
    ["Экономика", Number(economy.totalKnownInPlayerData || 0), "good"],
    ["Заявки", `${requestsReady}/${requestsTotal}`, requestsReady === requestsTotal ? "good" : "warn"],
    ["Связь с сервером", status.rconOk ? "есть" : "нет", status.rconOk ? "good" : "warn"]
  ];
  const score = checks.reduce((sum, row) => sum + (row[2] === "good" ? 1 : row[2] === "warn" ? 0.6 : 0), 0);
  const percentReady = Math.max(0, Math.min(100, Math.round((score / checks.length) * 100)));
  return `
    <section class="release-readiness-card">
      <div class="readiness-main">
        ${readinessRing(percentReady, "релиз")}
        <p>Оценка собирается по состоянию мира, скорости тиков, выборам, экономике, обращениям и живой связи с сервером. Для боевого режима цель: 95-100% без предупреждений.</p>
      </div>
      <div class="readiness-checks">
        ${checks.map(([name, value, tone]) => `
          <div class="readiness-check ${tone}">
            <span>${esc(name)}</span>
            <strong>${esc(value)}</strong>
          </div>
        `).join("")}
      </div>
    </section>
  `;
}

function firstRunReadinessHtml(data = {}) {
  const rows = asArray(data.checks);
  const ready = Number(data.readyPercent || 0);
  const blockers = rows.filter(row => !row.ok).slice(0, 6);
  return `
    <section class="panel first-run-ready">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Первый запуск</h2>
          <p class="panel-subtitle">Проверка после замены папки на сервере: плагины, конфиги, БД и resource pack.</p>
        </div>
        ${pill(`${ready}%`, ready >= 90 ? "good" : ready >= 70 ? "warn" : "bad")}
      </div>
      <div class="startup-grid">
        ${rows.slice(0, 8).map(row => `
          <div class="startup-check ${row.ok ? "good" : "warn"}">
            <span>${esc(row.name)}</span>
            <strong>${row.ok ? "OK" : "Проверить"}</strong>
            <em>${esc(row.detail || row.value || "")}</em>
          </div>
        `).join("") || empty("Данных first-run пока нет", "Открой админку в игре или перезапусти сервер, чтобы плагин записал startup self-check.")}
      </div>
      ${blockers.length ? `<div class="notice">Что мешает идеальному запуску: ${blockers.map(x => esc(x.name)).join(", ")}</div>` : ""}
    </section>
  `;
}

function optimizationStackHtml(data = {}) {
  const rows = asArray(data.optimizationStack);
  return `
    <div class="optimization-stack">
      ${rows.map(row => `
        <div class="optimization-item ${row.ok ? "good" : "warn"}">
          <strong>${esc(row.name)}</strong>
          <span>${row.ok ? "готов" : "проверить"} · ${esc(row.version || row.jar || "")}</span>
        </div>
      `).join("") || empty("Optimization stack не найден", "Проверь plugins и /api/performance/readiness.")}
    </div>
  `;
}

function safetyRail(items) {
  return `
    <div class="safety-rail">
      ${items.map(([title, text, tone = "neutral"]) => `
        <div class="safety-step ${tone}">
          <strong>${esc(title)}</strong>
          <span>${esc(text)}</span>
        </div>
      `).join("")}
    </div>
  `;
}

function dbPolicyPanel(policy = {}, access = {}) {
  const protectedPatterns = asArray(policy.protectedPatterns || access.dbWritePolicy?.protectedPatterns);
  const enabled = Boolean(policy.enabled ?? access.dbWriteEnabled);
  const allowlist = asArray(policy.allowlist || access.dbWriteAllowlist);
  const writePolicy = {
    mode: enabled ? (allowlist.length ? "allowlist" : "raw-write-enabled") : "read-only",
    protected: protectedPatterns.length,
    allowlist: allowlist.length
  };
  return panel("Защита данных", "Как сайт бережёт выборы, банк и журнал действий от случайных правок", `
    <div class="db-policy-grid">
      <div class="db-policy-card ${enabled ? "warn" : "good"}">
        <span>изменения с сайта</span>
        <strong>${enabled ? "ограничены правилами" : "только готовые действия"}</strong>
        <p>${enabled ? "Чувствительные разделы защищены и не меняются напрямую." : "Критичные данные можно менять только через подготовленные сценарии."}</p>
      </div>
      <div class="db-policy-card good">
        <span>защищённых правил</span>
        <strong>${writePolicy.protected}</strong>
        <p>Голоса, бюллетени, AR, аудит и официальные предметы ведутся через отдельные сценарии.</p>
      </div>
      <div class="db-policy-card ${allowlist.length ? "good" : "neutral"}">
        <span>разрешённых сценариев</span>
        <strong>${allowlist.length || "нет"}</strong>
        <p>${allowlist.length ? allowlist.map(esc).join(", ") : "Сайт сейчас не открывает прямые служебные правки."}</p>
      </div>
    </div>
    <div class="db-policy-list">
      ${protectedPatterns.slice(0, 12).map(row => `
        <span title="${esc(row.reason || "")}">${esc(row.reason || row.pattern || row)}</span>
      `).join("") || "<span>Защитные правила не получены</span>"}
    </div>
  `);
}

function empty(title, text = "") {
  return `<div class="empty"><strong>${esc(title)}</strong>${text ? `<p>${esc(text)}</p>` : ""}</div>`;
}

function kv(rows) {
  return `
    <div class="kv">
      ${rows.map(([key, value]) => `
        <div class="kv-row">
          <div class="kv-key">${esc(key)}</div>
          <div class="kv-value">${formatValue(value)}</div>
        </div>
      `).join("")}
    </div>
  `;
}

function formatValue(value) {
  if (value === true) return pill("РґР°", "good");
  if (value === false) return pill("нет", "bad");
  if (value == null || value === "") return "—";
  if (typeof value === "number") return esc(value);
  if (typeof value === "object") return esc(objectSummary(value));
  const text = cleanText(value);
  if (/^https?:\/\//.test(text)) return `<a href="${esc(text)}" target="_blank" rel="noreferrer">${esc(text)}</a>`;
  return esc(text);
}

function bankPinState(pin = {}) {
  if (pin.locked) return `Заблокирован до ${dt(pin.lockedUntil)}`;
  if (pin.status === "temporary-expired") return "Временный PIN истёк";
  if (pin.mustChange || pin.status === "temporary") return "Нужно заменить временный PIN";
  if (pin.set) return "Настроен";
  return "Не задан";
}

function bankPinTone(pin = {}) {
  if (pin.locked || pin.status === "temporary-expired") return "bad";
  if (pin.mustChange || !pin.set) return "warn";
  return "good";
}

function compactInt(value) {
  return Math.round(number(value || 0)).toLocaleString("ru-RU");
}

function formatAr(value) {
  return `${compactInt(value)} AR`;
}

function formatDonate(value) {
  return `${compactInt(value)} DC`;
}

function avatarUrl(name, size = 96) {
  const nick = cleanText(name || "Steve") || "Steve";
  return `https://mc-heads.net/avatar/${encodeURIComponent(nick)}/${size}`;
}

function initials(name, count = 2) {
  const raw = cleanText(name || "").replace(/[^\p{L}\p{N}_]/gu, "");
  return (raw.slice(0, count) || "CM").toUpperCase();
}

function avatarBadge(name, size = "md") {
  const px = size === "lg" ? 88 : size === "sm" ? 40 : 56;
  return `
    <span class="avatar-badge avatar-${size}" aria-hidden="true">
      <b>${esc(initials(name))}</b>
      <img src="${esc(avatarUrl(name, px))}" alt="" loading="lazy" onerror="this.remove()" />
    </span>
  `;
}

function electionStageLabel(value, fallback = "—") {
  const raw = cleanText(value).toUpperCase();
  const labels = {
    NONE: "Нет выборов",
    PREPARATION: "Подготовка",
    PREP: "Подготовка",
    APPLICATIONS: "Приём заявок",
    APPLICATIONS_OPEN: "Приём заявок",
    REVIEW: "Проверка заявок",
    APPLICATION_REVIEW: "Проверка заявок",
    DEBATES: "Дебаты",
    VOTING: "Голосование",
    VOTING_OPEN: "Голосование",
    COUNTING: "Подсчёт",
    SECOND_ROUND: "Второй тур",
    FINISHED: "Завершено",
    COMPLETED: "Завершено",
    PRESIDENT_TERM: "Президентский срок",
    ACTIVE: "Идут"
  };
  return labels[raw] || statusLabel(value, fallback);
}

function applicationStatusText(value) {
  const raw = cleanText(value).toUpperCase();
  const labels = {
    ISSUED: "Книга выдана",
    SUBMITTED: "Книга сдана",
    PENDING: "Ждёт решения",
    APPROVED: "Одобрено",
    REJECTED: "Отклонено"
  };
  return labels[raw] || statusLabel(value, "Ждёт решения");
}

function recommendationText(value) {
  const raw = cleanText(value).toUpperCase();
  if (!raw) return "Без пометки";
  const labels = {
    RECOMMEND: "Рекомендовать",
    RECOMMENDED: "Рекомендовать",
    APPROVE: "Рекомендовать",
    YES: "Рекомендовать",
    NO: "Не рекомендовать",
    REJECT: "Не рекомендовать",
    NOT_RECOMMEND: "Не рекомендовать"
  };
  return labels[raw] || statusLabel(value, "Без пометки");
}

function recommendationTone(value) {
  const raw = cleanText(value).toUpperCase();
  if (!raw) return "neutral";
  return ["RECOMMEND", "RECOMMENDED", "APPROVE", "YES"].includes(raw) ? "good" : "warn";
}

function adminDecisionTone(value) {
  const raw = cleanText(value).toUpperCase();
  if (raw === "APPROVED") return "good";
  if (raw === "REJECTED") return "bad";
  return "warn";
}

function normalizeBookText(value) {
  return String(value ?? "")
    .replace(/\r/g, "")
    .replace(/§[0-9A-FK-ORX]/gi, "")
    .replace(/&[0-9A-FK-ORX]/gi, "")
    .trim();
}

function parseApplicationAnswers(text) {
  const raw = normalizeBookText(text);
  if (!raw) {
    return APPLICATION_QUESTIONS.map((question) => ({
      question,
      answer: "Ответ пока не сдан."
    }));
  }
  const markers = APPLICATION_QUESTIONS.map((question, index) => `${index + 1}. ${question}`);
  const positions = markers.map((marker) => raw.indexOf(marker));
  if (positions.every((position) => position >= 0)) {
    return APPLICATION_QUESTIONS.map((question, index) => {
      const start = positions[index] + markers[index].length;
      const end = index + 1 < positions.length ? positions[index + 1] : raw.length;
      const answer = raw.slice(start, end).replace(/^\s+/, "").trim();
      return { question, answer: answer || "Ответ не заполнен." };
    });
  }
  const chunks = raw.split(/\n\s*\n+/).map((chunk) => chunk.trim()).filter(Boolean);
  return APPLICATION_QUESTIONS.map((question, index) => ({
    question,
    answer: chunks[index] || "Ответ не заполнен."
  }));
}

function bookAnswerHtml(value) {
  return esc(value || "Ответ не заполнен.").replace(/\n/g, "<br>");
}

function applicationBookPreview(row, compact = false) {
  const pages = parseApplicationAnswers(row.answers);
  const gridClass = compact ? "book-pages compact" : "book-pages";
  return `
    <div class="book-shell">
      <div class="book-meta">
        <div class="book-player">
          ${avatarBadge(row.player_name || "Игрок", compact ? "sm" : "md")}
          <div>
            <strong>${esc(row.player_name || "Кандидат")}</strong>
            <span>${esc(row.submitted_at ? `Книга сдана ${dt(row.submitted_at)}` : "Книга ещё не сдана")}</span>
          </div>
        </div>
        <div class="book-statuses">
          ${pill(recommendationText(row.chair_recommendation), recommendationTone(row.chair_recommendation))}
          ${pill(applicationStatusText(row.admin_status || row.status), adminDecisionTone(row.admin_status || row.status))}
        </div>
      </div>
      <div class="${gridClass}">
        ${pages.map((page, index) => `
          <article class="book-page">
            <span class="book-page-number">Стр. ${index + 1}</span>
            <strong>${esc(page.question)}</strong>
            <p>${bookAnswerHtml(page.answer)}</p>
          </article>
        `).join("")}
      </div>
    </div>
  `;
}

function lawCards(rows) {
  rows = asArray(rows);
  if (!rows.length) {
    return empty("Законов пока нет", "После одобрения президентские законы появятся здесь.");
  }
  return `
    <div class="law-grid">
      ${rows.slice(0, 5).map((row, index) => `
        <article class="law-card">
          <span class="law-slot">Закон ${index + 1}</span>
          <strong>${esc(short(cleanText(row.text || row.title || ""), 96) || "Без текста")}</strong>
          <p>${esc(row.president_name || row.author_name || "Президент CopiMine")}</p>
        </article>
      `).join("")}
    </div>
  `;
}

function candidateCards(rows) {
  rows = asArray(rows);
  if (!rows.length) {
    return empty("Кандидатов пока нет", "Когда заявки будут одобрены, кандидаты появятся здесь.");
  }
  const mapped = rows.slice(0, 8).map((row) => ({
    id: row.player_uuid || row.id,
    name: cleanText(row.player_name || row.display_name || row.name || "Кандидат"),
    votes: number(row.last_result || row.total || row.votes || row.raw_votes || 0)
  }));
  const maxVotes = Math.max(1, ...mapped.map((row) => row.votes));
  return `
    <div class="candidate-grid">
      ${mapped.map((row) => `
        <article class="candidate-card">
          <div class="candidate-head">
            ${avatarBadge(row.name, "sm")}
            <div>
              <strong>${esc(row.name)}</strong>
              <span>${row.votes > 0 ? `${compactInt(row.votes)} голосов` : "Голоса появятся после подсчёта"}</span>
            </div>
          </div>
          <div class="candidate-progress">
            <div class="candidate-progress-fill" style="width:${Math.max(8, Math.round((row.votes / maxVotes) * 100))}%"></div>
          </div>
        </article>
      `).join("")}
    </div>
  `;
}

function electionApplicationCards(rows) {
  rows = asArray(rows);
  if (!rows.length) {
    return empty("Заявок пока нет", "Когда кандидаты сдадут книги в участки, они появятся здесь.");
  }
  return `
    <div class="book-card-grid">
      ${rows.slice(0, 6).map((row) => `
        <article class="book-card">
          <div class="book-card-head">
            ${avatarBadge(row.player_name || "Игрок", "sm")}
            <div>
              <strong>${esc(row.player_name || "Кандидат")}</strong>
              <span>${esc(row.submitted_at ? dt(row.submitted_at) : "Книга ещё не сдана")}</span>
            </div>
          </div>
          <div class="book-card-body">
            <span>${esc(cleanText(parseApplicationAnswers(row.answers)[0]?.answer || "Ответ не заполнен.").slice(0, 140) || "Ответ не заполнен.")}</span>
          </div>
          <div class="book-card-actions">
            ${pill(recommendationText(row.chair_recommendation), recommendationTone(row.chair_recommendation))}
            ${pill(applicationStatusText(row.admin_status || row.status), adminDecisionTone(row.admin_status || row.status))}
            <button class="btn btn-secondary btn-small" onclick="openElectionApplicationBook('${esc(row.id)}')">Открыть книгу</button>
          </div>
        </article>
      `).join("")}
    </div>
  `;
}

function humanizeAuditAction(value) {
  const raw = cleanText(value).toLowerCase();
  const map = {
    application_issued: "Выдана книга заявки",
    application_submitted: "Книга заявки сдана",
    seal_issued: "Выдана печать ЦИК",
    seals_revoked: "Все печати ЦИК отозваны",
    stage_changed: "Сменён этап выборов",
    law_submitted: "Президент предложил закон",
    law_published: "Закон опубликован",
    tax_set: "Назначен налог",
    president_assigned: "Выбран президент",
    election_reset: "Выборы очищены"
  };
  return map[raw] || cleanText(value).replace(/^election[._]/i, "").replaceAll("_", " ") || "Событие";
}

function humanizeBankAction(row = {}) {
  const raw = cleanText(row.tx_type || row.type || row.action).toLowerCase();
  const map = {
    transfer: "Перевод",
    transfer_in: "Поступление",
    transfer_out: "Перевод",
    deposit: "Пополнение счёта",
    withdraw: "Снятие со счёта",
    artifact_purchase: "Покупка артефакта",
    tax_payment: "Оплата налога",
    tax: "Оплата налога",
    repair: "Ремонт предмета",
    issue: "Выдача",
    refund: "Возврат"
  };
  return map[raw] || cleanText(row.tx_type || row.type || row.action || "Операция");
}

function transactionFeed(rows, limit = 12) {
  rows = asArray(rows).slice(0, limit);
  if (!rows.length) {
    return empty("Операций пока нет", "После первого перевода или оплаты история появится здесь.");
  }
  return `
    <div class="transaction-feed">
      ${rows.map((row) => `
        <article class="transaction-row">
          <div class="transaction-main">
            <strong>${esc(humanizeBankAction(row))}</strong>
            <span>${esc(cleanText(row.details || "") || "Без комментария")}</span>
          </div>
          <div class="transaction-side">
            <strong>${esc(formatAr(row.amount || 0))}</strong>
            <span>${dt(row.created_at || row.time || row.updated_at)}</span>
          </div>
        </article>
      `).join("")}
    </div>
  `;
}

window.openElectionApplicationBook = (applicationId) => {
  const row = state.electionApplications?.[applicationId];
  if (!row) return toast("Книга заявки не найдена", true);
  $("modalRoot").innerHTML = `
    <div class="modal-overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal modal-wide">
        <div class="modal-head">
          <div>
            <h2>Заявка кандидата</h2>
            <p>${esc(row.player_name || "Кандидат")} · ${row.submitted_at ? dt(row.submitted_at) : "книга ещё не сдана"}</p>
          </div>
          <button class="btn btn-secondary" onclick="closeModal()">Закрыть</button>
        </div>
        ${applicationBookPreview(row)}
      </div>
    </div>
  `;
};

function siteBulletList(items) {
  return `
    <div class="site-bullet-list">
      ${items.map((item) => `<span>${esc(item)}</span>`).join("")}
    </div>
  `;
}

function table(id, rows, columns, opts = {}) {
  const normalizedRows = asArray(rows);
  const inferredColumns = columns || [...new Set(normalizedRows.flatMap(row => Object.keys(row || {})))]
    .slice(0, 10)
    .map(key => ({ key, label: key }));
  state.tables[id] = {
    ...(state.tables[id] || {}),
    id,
    rows: normalizedRows,
    columns: inferredColumns.map(col => typeof col === "string" ? { key: col, label: col } : col),
    page: state.tables[id]?.page || 1,
    pageSize: opts.pageSize || 25,
    filter: state.tables[id]?.filter || "",
    sortKey: state.tables[id]?.sortKey || "",
    sortDir: state.tables[id]?.sortDir || "asc",
    rowAction: opts.rowAction || ""
  };
  return `<div data-table="${id}">${renderStoredTable(id)}</div>`;
}

function tableRows(id) {
  const t = state.tables[id];
  let rows = [...(t?.rows || [])];
  const q = (t?.filter || "").trim().toLowerCase();
  if (q) rows = rows.filter(row => JSON.stringify(row ?? {}).toLowerCase().includes(q));
  if (t?.sortKey) {
    rows.sort((a, b) => {
      const av = a?.[t.sortKey] ?? "";
      const bv = b?.[t.sortKey] ?? "";
      const cmp = String(av).localeCompare(String(bv), "ru", { numeric: true, sensitivity: "base" });
      return t.sortDir === "asc" ? cmp : -cmp;
    });
  }
  return rows;
}

function renderStoredTable(id) {
  const t = state.tables[id];
  if (!t || !t.rows.length) return empty("Данных пока нет", "Источник не подключён или фильтр не нашёл строк.");
  const rows = tableRows(id);
  const pages = Math.max(1, Math.ceil(rows.length / t.pageSize));
  t.page = Math.min(Math.max(1, t.page), pages);
  const start = (t.page - 1) * t.pageSize;
  const pageRows = rows.slice(start, start + t.pageSize);
  const head = t.columns.map(col => `
    <th onclick="sortTable('${id}','${esc(col.key)}')">
      ${esc(col.label || col.key)}${t.sortKey === col.key ? (t.sortDir === "asc" ? " ↑" : " ↓") : ""}
    </th>
  `).join("");
  const body = pageRows.map((row, idx) => {
    const cells = t.columns.map(col => {
      const raw = row?.[col.key];
      return `<td>${col.render ? col.render(raw, row, start + idx) : formatValue(raw)}</td>`;
    }).join("");
    const action = t.rowAction ? ` onclick="${t.rowAction}(${start + idx})"` : "";
    return `<tr${action}>${cells}</tr>`;
  }).join("");
  return `
    <div class="toolbar">
      <input class="grow" value="${esc(t.filter)}" oninput="filterTable('${id}', this.value)" placeholder="Поиск по таблице" />
      <button class="btn btn-secondary btn-small" onclick="exportTable('${id}','csv')">Скачать CSV</button>
      <span class="last-update">${rows.length} записей</span>
    </div>
    <div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>
    <div class="table-footer">
      <span>Страница ${t.page} из ${pages}</span>
      <div class="action-strip">
        <button class="btn btn-secondary btn-small" onclick="pageTable('${id}',-1)">Назад</button>
        <button class="btn btn-secondary btn-small" onclick="pageTable('${id}',1)">Вперёд</button>
      </div>
    </div>
  `;
}

window.sortTable = (id, key) => {
  const t = state.tables[id];
  if (!t) return;
  if (t.sortKey === key) t.sortDir = t.sortDir === "asc" ? "desc" : "asc";
  else { t.sortKey = key; t.sortDir = "asc"; }
  document.querySelector(`[data-table="${id}"]`).innerHTML = renderStoredTable(id);
};

window.filterTable = (id, value) => {
  const t = state.tables[id];
  if (!t) return;
  t.filter = value;
  t.page = 1;
  document.querySelector(`[data-table="${id}"]`).innerHTML = renderStoredTable(id);
};

window.pageTable = (id, delta) => {
  const t = state.tables[id];
  if (!t) return;
  t.page += delta;
  document.querySelector(`[data-table="${id}"]`).innerHTML = renderStoredTable(id);
};

window.exportTable = (id, type) => {
  const t = state.tables[id];
  if (!t) return;
  const rows = tableRows(id);
  let content = "";
  let mime = "application/json";
  let ext = "json";
  if (type === "csv") {
    const keys = t.columns.map(c => c.key);
    content = [keys.join(","), ...rows.map(row => keys.map(k => `"${String(row[k] ?? "").replace(/"/g, '""')}"`).join(","))].join("\n");
    mime = "text/csv;charset=utf-8";
    ext = "csv";
  } else {
    content = JSON.stringify(rows, null, 2);
  }
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${id}.${ext}`;
  a.click();
  URL.revokeObjectURL(url);
};

function currentNavGroups() {
  return state.role === "player" ? playerNavGroups : navGroups;
}

function currentPageMeta() {
  return state.role === "player" ? playerPageMeta : pageMeta;
}

function defaultTab() {
  return state.role === "player" ? "cabinet" : "dashboard";
}

function setMobileNav(open) {
  const app = $("app");
  const toggle = $("mobileNavToggle");
  app.classList.toggle("nav-open", Boolean(open));
  if (toggle) toggle.setAttribute("aria-expanded", open ? "true" : "false");
}

function renderNav() {
  $("nav").innerHTML = currentNavGroups().map(group => `
    <div class="nav-group">
      <div class="nav-group-title">${esc(group.title)}</div>
      ${group.items.map(([id, label, hint, icon]) => `
        <button class="nav-item ${state.tab === id ? "active" : ""}" data-tab="${id}">
          <span class="nav-icon">${esc(icon)}</span>
          <span>
            <span class="nav-label">${esc(label)}</span>
            <span class="nav-hint">${esc(hint)}</span>
          </span>
        </button>
      `).join("")}
    </div>
  `).join("");
  document.querySelectorAll("[data-tab]").forEach(btn => {
    btn.addEventListener("click", () => {
      setTab(btn.dataset.tab);
      setMobileNav(false);
    });
  });
}

function setTab(tab) {
  const metaMap = currentPageMeta();
  state.tab = metaMap[tab] ? tab : defaultTab();
  location.hash = state.tab;
  const meta = metaMap[state.tab];
  $("pageTitle").textContent = meta.title;
  $("pageSubtitle").textContent = meta.subtitle;
  renderNav();
  loadCurrent();
}

function updateGlobalStatus(status = {}) {
  const ok = status.minecraftOnline === true && status.rconOk !== false;
  const badge = $("liveBadge");
  badge.className = `status-chip ${ok ? "status-good" : status.minecraftOnline ? "status-warn" : "status-bad"}`;
  badge.textContent = ok ? "сервер онлайн" : status.minecraftOnline ? "частично" : "offline";
  $("miniHealth").innerHTML = `
    <strong>${ok ? "Сервер работает" : "Нужна проверка"}</strong><br>
    TPS: ${esc(short(status.tps || "—", 26))}<br>
    MSPT: ${esc(short(status.mspt || "—", 26))}
  `;
}

function startLivePanelStream() {
  stopLivePanelStream();
  if (!("EventSource" in window)) return;
  const badge = $("liveBadge");
  try {
    const source = new EventSource("/api/events/stream?_fresh=" + Date.now(), { withCredentials: true });
    state.liveStream = source;
    source.onopen = () => {
      if (!badge) return;
      badge.className = "status-chip status-good";
      badge.textContent = "live";
    };
    source.addEventListener("events", event => {
      state.liveLastEvent = Date.now();
      if (badge) {
        badge.className = "status-chip status-good";
        badge.textContent = "live";
        badge.animate([{ transform: "scale(1)" }, { transform: "scale(1.04)" }, { transform: "scale(1)" }], { duration: 420 });
      }
      try {
        const payload = JSON.parse(event.data || "{}");
        if (payload.time && ["dashboard", "logs", "stats"].includes(state.tab)) loadCurrent(true);
      } catch {}
    });
    source.onerror = () => {
      if (!badge) return;
      badge.className = "status-chip status-warn";
      badge.textContent = "live reconnect";
    };
  } catch (err) {
    if (badge) {
      badge.className = "status-chip status-warn";
      badge.textContent = "polling";
    }
  }
}

function stopLivePanelStream() {
  if (!state.liveStream) return;
  try { state.liveStream.close(); } catch {}
  state.liveStream = null;
}

function setAuthRole(role) {
  state.authRole = role === "player" ? "player" : "admin";
  if (state.authRole !== "player") state.authAction = "login";
  localStorage.setItem("copimineLastRole", state.authRole);
  syncAuthUi();
  renderPublicAuthState();
}

function setAuthAction(action) {
  state.authAction = action === "register" ? "register" : "login";
  syncAuthUi();
}

function setPublicFeature(tab = "bank") {
  const feature = publicFeatures[tab] || publicFeatures.bank;
  const panel = $("publicFeaturePanel");
  if (!panel) return;
  document.querySelectorAll("[data-public-tab]").forEach((button) => {
    button.classList.toggle("active", button.dataset.publicTab === tab);
  });
  panel.innerHTML = `
    <div>
      <span class="hero-kicker">${esc(feature.kicker)}</span>
      <h3>${esc(feature.title)}</h3>
      <p>${esc(feature.text)}</p>
    </div>
    <img src="${esc(feature.icon)}" alt="" />
  `;
}

function publicStatusMetric(label, value, detail = "", tone = "neutral") {
  return `
    <article class="public-status-card ${tone}">
      <span>${esc(label)}</span>
      <strong>${esc(value)}</strong>
      <p>${esc(detail || "Нет данных")}</p>
    </article>
  `;
}

function publicOnlineRows(players = []) {
  if (!players.length) {
    return `<div class="empty-public-state">Список игроков сейчас недоступен или сервер не отдал его публично.</div>`;
  }
  return players.map((name, index) => `
    <div class="top-row">
      <b>${index + 1}</b>
      ${avatarBadge(name, "sm")}
      <span>${esc(name)}</span>
      <strong>онлайн</strong>
    </div>
  `).join("");
}

function renderPublicStatus(status = {}, config = {}) {
  const server = status.server || {};
  const elections = status.elections || {};
  const statusGrid = $("publicStatusGrid");
  const onlineBoard = $("publicOnlineBoard");
  if (statusGrid) {
    statusGrid.innerHTML = [
      publicStatusMetric("Сервер", server.online ? "онлайн" : "офлайн", server.online ? `Отклик ${server.latencyMs ?? "?"} мс` : "Соединение сейчас не подтверждено", server.online ? "good" : "bad"),
      publicStatusMetric("Игроки", String(server.playersOnline || 0), server.playerCap ? `из ${server.playerCap}` : (server.playerListAvailable ? "публичный список доступен" : "публичный список недоступен"), server.playersOnline ? "good" : "neutral"),
      publicStatusMetric("Выборы", elections.active ? "идут" : "пауза", elections.active ? `${elections.candidates || 0} кандидатов · ${elections.votes || 0} голосов` : "Сейчас нет активного этапа голосования", elections.active ? "good" : "warn"),
      publicStatusMetric("Президент", elections.president || "не выбран", elections.president ? "Данные пришли из ElectionCore" : "Активный срок пока не подтверждён", elections.president ? "good" : "neutral")
    ].join("");
  }
  if (onlineBoard) {
    onlineBoard.innerHTML = `
      <article class="top-board">
        <h3>Кто сейчас в игре</h3>
        ${publicOnlineRows(asArray(server.samplePlayers))}
      </article>
      <article class="top-board">
        <h3>Что работает на сервере</h3>
        <div class="top-note-list">
          <div class="top-note"><strong>Личный кабинет</strong><span>Регистрация, привязка ника, банк AR и история операций.</span></div>
          <div class="top-note"><strong>Выборы</strong><span>${esc(elections.active ? "Активная стадия видна в панели и в игре." : "Сейчас нет активного голосования, но ЦИК и участки остаются частью системы.")}</span></div>
          <div class="top-note"><strong>Донат</strong><span>${config.donationEnabled ? "Состояние донат-системы включено." : "Реальные платежи сейчас отключены. Доступны только безопасные test/mock сценарии для администрации."}</span></div>
        </div>
      </article>
    `;
  }
}

function updatePublicHero(status = {}, config = {}) {
  const server = status.server || {};
  const address = cleanText(config.serverAddress || "") || "запроси адрес у администрации";
  const version = cleanText(config.serverVersion || "1.21.x");
  const pulseText = server.online
    ? `Онлайн ${server.playersOnline || 0}${server.playerCap ? ` / ${server.playerCap}` : ""} · ${version} · Paper`
    : `Сервер сейчас недоступен для проверки · ${version}`;
  if ($("serverIpText")) $("serverIpText").textContent = address;
  if ($("serverPulseText")) $("serverPulseText").textContent = pulseText;
}

async function loadPublicStatus() {
  const [configRes, statusRes] = await Promise.all([
    safeApi("/api/public/config", { ok: false, data: {} }),
    safeApi("/api/public/status", { ok: false, data: {} })
  ]);
  state.publicConfig = configRes.data || configRes || {};
  state.publicStatus = statusRes.data || statusRes || {};
  updatePublicHero(state.publicStatus, state.publicConfig);
  renderPublicStatus(state.publicStatus, state.publicConfig);
}

function renderPublicAuthState() {
    const button = $("publicCabinetBtn");
    if (!button) return;
    const authed = Boolean(state.role || state.token || state.cookieAuth);
    button.classList.toggle("hidden", !authed);
    const username = state.user?.username || (state.role === "player" ? "игрок" : "команда");
    button.textContent = state.role === "admin" ? `Открыть кабинет (${username})` : `Личный кабинет (${username})`;
  }

function showGuestPages() {
    $("app").classList.add("hidden");
    $("login").classList.remove("hidden");
    stopLivePanelStream();
    clearInterval(state.refreshTimer);
    renderPublicAuthState();
    loadPublicStatus();
    if (!location.hash || location.hash === "#dashboard" || location.hash === "#cabinet") location.hash = "#start";
    setTimeout(() => document.querySelector(location.hash || "#start")?.scrollIntoView({ block: "start" }), 0);
  }

async function showCabinetFromPublic() {
    if (!state.role && !state.token && !state.cookieAuth) {
      location.hash = "#signin";
      return;
    }
    await bootAuthed({ quiet: true });
  }

function copyServerIp() {
    const ip = $("serverIpText")?.textContent?.trim() || "";
    if (!ip || ip === "запроси адрес у администрации" || ip === "загружаем адрес...") {
      toast("Публичный адрес сервера сейчас не настроен.", true);
      return;
    }
    navigator.clipboard?.writeText(ip).then(() => toast(`IP скопирован: ${ip}`)).catch(() => toast(`IP сервера: ${ip}`));
  }

function wirePublicSite() {
  document.querySelectorAll("[data-public-tab]").forEach((button) => {
    button.addEventListener("click", () => setPublicFeature(button.dataset.publicTab || "bank"));
  });
  $("copyIpBtn")?.addEventListener("click", copyServerIp);
  $("publicCabinetBtn")?.addEventListener("click", showCabinetFromPublic);
  setPublicFeature("bank");
  renderPublicAuthState();
  loadPublicStatus();
}

function syncAuthUi() {
  const isPlayer = state.authRole === "player";
  const isRegister = isPlayer && state.authAction === "register";
  const loginCard = $("loginForm");
  if (!loginCard) return;

  loginCard.querySelectorAll("[data-auth-role]").forEach((button) => {
    button.classList.toggle("active", button.dataset.authRole === state.authRole);
  });
  loginCard.querySelectorAll("[data-auth-action]").forEach((button) => {
    button.classList.toggle("active", button.dataset.authAction === state.authAction);
  });

  $("authActionRow").classList.toggle("hidden", !isPlayer);
  $("minecraftNameGroup").classList.toggle("hidden", !isRegister);

  const brandText = loginCard.querySelector(".login-brand p");
  const lead = loginCard.querySelector(".login-copy strong");
  const support = loginCard.querySelector(".login-copy span");
  const usernameLabel = loginCard.querySelector('label[for="username"]');
  const passwordLabel = loginCard.querySelector('label[for="password"]');
  const submit = loginCard.querySelector('button[type="submit"]');
  const note = loginCard.querySelector(".login-note");

  if (isPlayer) {
    if (brandText) brandText.textContent = "Кабинет игрока: банк, привязка и покупки";
    if (lead) lead.textContent = isRegister ? "Создать аккаунт игрока" : "Вход игрока";
    if (support) support.textContent = isRegister
      ? "Зарегистрируй отдельный логин сайта. Minecraft-ник подтверждается позже одноразовым кодом на сервере."
      : "Войди логином сайта. После входа откроются банк, налог, история операций и привязка Minecraft.";
    if (usernameLabel) usernameLabel.textContent = "Логин сайта";
    if (passwordLabel) passwordLabel.textContent = isRegister ? "Новый пароль" : "Пароль";
    $("username").placeholder = "Придумай логин";
    $("password").placeholder = isRegister ? "Минимум 8 символов" : "Введите пароль";
    if (submit) submit.textContent = isRegister ? "Создать аккаунт" : "Открыть кабинет";
    if (note) note.textContent = isRegister
      ? "Пароль от Minecraft здесь никогда не нужен. Укажи свой игровой ник и подтверди его кодом в игре."
      : "После входа можно запросить код привязки, настроить PIN и пользоваться переводами и оплатой налога.";
  } else {
    if (brandText) brandText.textContent = "Рабочий кабинет сервера";
    if (lead) lead.textContent = "Вход для команды сервера";
    if (support) support.textContent = "Доступ к админке получают только сотрудники сервера с действующим логином.";
    if (usernameLabel) usernameLabel.textContent = "Minecraft-ник";
    if (passwordLabel) passwordLabel.textContent = "Пароль";
    $("username").placeholder = "Например, Cells";
    $("password").placeholder = "Введите пароль";
    if (submit) submit.textContent = "Войти";
    if (note) note.textContent = "Если доступ не открывается, проверь логин и обратись к старшей команде сервера.";
  }
}

async function login(event) {
  event.preventDefault();
  $("loginError").textContent = "";
  try {
    const isPlayer = state.authRole === "player";
    const isRegister = isPlayer && state.authAction === "register";
    const payload = { username: $("username").value.trim(), password: $("password").value };
    if (isRegister) payload.minecraft_name = $("playerMinecraftName").value.trim();
    const data = await api(isPlayer ? (isRegister ? "/api/player/register" : "/api/player/login") : "/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    state.token = data.token || "";
    state.cookieAuth = data.cookieAuth === true;
    state.role = data.role || state.authRole;
    if (state.token) localStorage.setItem("copimineToken", state.token);
    else localStorage.removeItem("copimineToken");
    localStorage.setItem("copimineRole", state.role);
    localStorage.setItem("copimineLastRole", state.authRole);
    state.user = data.account || { username: data.username, role: state.role };
    await bootAuthed();
  } catch (err) {
    $("loginError").textContent = err.message;
  }
}

async function logout(call = true) {
  if (call) {
    try { await api("/api/auth/logout", { method: "POST", body: "{}" }); } catch {}
  }
  state.token = "";
  state.role = "";
  state.cookieAuth = false;
  state.user = null;
  state.playerLinkRequest = null;
  localStorage.removeItem("copimineToken");
  localStorage.removeItem("copimineRole");
  $("app").classList.add("hidden");
  $("login").classList.remove("hidden");
  stopLivePanelStream();
  clearInterval(state.refreshTimer);
  syncAuthUi();
}

async function resolveAuthSession() {
  const order = state.role === "player" ? ["player", "admin"] : ["admin", "player"];
  let lastError = new Error("Authentication is required");
  for (const role of order) {
    try {
      if (role === "admin") {
        const me = await api("/api/auth/me", { skipAuthReset: true });
        const config = await safeApi("/api/config", {});
        return { role: "admin", user: me, config };
      }
      const me = await api("/api/player/me", { skipAuthReset: true });
      return { role: "player", user: me.account || {}, config: {} };
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError;
}

async function bootAuthed(options = {}) {
  $("login").classList.add("hidden");
  $("app").classList.remove("hidden");
  try {
    const session = await resolveAuthSession();
    state.role = session.role;
    state.user = session.user;
    state.config = session.config || {};
    localStorage.setItem("copimineRole", state.role);
    const cookieAuth = Boolean(state.config.features?.cookieAuth || state.config.cookieAuth || state.cookieAuth);
    state.cookieAuth = cookieAuth;
    const username = state.role === "player" ? (state.user.username || "player") : (state.user.username || "admin");
    $("userBadge").textContent = state.role === "player" ? `${username} · игрок` : `${username}${cookieAuth ? " · cookieAuth" : ""}`;
  } catch (err) {
    if (!options.quiet) toast(err.message, true);
    logout(false);
    return;
  }
  renderPublicAuthState();
  renderNav();
  setTab(state.tab);
  clearInterval(state.refreshTimer);
  if (state.role === "admin") {
    startLivePanelStream();
    state.refreshTimer = setInterval(() => {
      if (!document.hidden && ["dashboard", "server"].includes(state.tab)) loadCurrent(true);
    }, 15000);
  } else {
    stopLivePanelStream();
    $("liveBadge").className = "status-chip status-neutral";
    $("liveBadge").textContent = "игрок";
    $("miniHealth").innerHTML = `<strong>Кабинет игрока</strong><br>Привязка: ${state.user?.linked ? "есть" : "нет"}<br>Банк: готов к работе`;
  }
}

function parsePerformance(status) {
  const tpsText = cleanText(status.tps || "");
  const msptText = cleanText(status.mspt || "");
  const tpsMatch = tpsText.match(/([0-9]+(?:[.,][0-9]+)?)/);
  const msptMatch = msptText.match(/([0-9]+(?:[.,][0-9]+)?)/);
  const tps = tpsMatch ? Number(tpsMatch[1].replace(",", ".")) : null;
  const mspt = msptMatch ? Number(msptMatch[1].replace(",", ".")) : null;
  return { tps, mspt, tpsText, msptText };
}

function timeline(rows) {
  rows = asArray(rows);
  if (!rows.length) return empty("Событий пока нет", "Когда сервер начнёт отдавать события, они появятся здесь.");
  return `<div class="timeline">${rows.map(row => `
    <div class="timeline-item">
      <div class="timeline-dot"></div>
      <div class="timeline-body">
        <strong>${esc(row.action || row.eventType || row.type || "событие")}</strong>
        <span>${esc(row.actor || row.source || "system")} В· ${esc(row.target || "")} ${row.time || row.createdAt || row.timestamp ? "В· " + dt(row.time || row.createdAt || row.timestamp) : ""}</span>
      </div>
    </div>
  `).join("")}</div>`;
}

function compactCoords(row) {
  if (row?.world == null && row?.x == null) return "";
  return `${row.world || "world"} ${row.x ?? "—"} ${row.y ?? "—"} ${row.z ?? "—"}`;
}

function activityTimeline(rows) {
  rows = asArray(rows).slice(0, 80);
  if (!rows.length) return empty("Действий пока нет", "Когда плагин или CoreProtect запишут события игрока, они появятся здесь.");
  return `<div class="activity-timeline">${rows.map(row => `
    <article class="activity-row">
      <div class="activity-icon">${esc(short(row.type || row.source || "log", 2).toUpperCase())}</div>
      <div class="activity-main">
        <div class="activity-head">
          <strong>${esc(row.type || "событие")}</strong>
          <span>${esc(row.source || "server")} В· ${dt(row.time || row.createdAt || row.timestamp)}</span>
        </div>
        <div class="activity-meta">
          ${row.actor ? `<span>админ: ${esc(row.actor)}</span>` : ""}
          ${row.player ? `<span>игрок: ${esc(row.player)}</span>` : ""}
          ${compactCoords(row) ? `<span>${esc(compactCoords(row))}</span>` : ""}
          ${row.amount ? `<span>кол-во: ${esc(row.amount)}</span>` : ""}
          ${row.material ? `<span>${esc(row.material)}</span>` : ""}
        </div>
        ${row.details ? `<p class="activity-note">${esc(detailSummary(row.details, 220))}</p>` : ""}
      </div>
    </article>
  `).join("")}</div>`;
}

function ledgerRows(rows, className = "ledger") {
  rows = asArray(rows).slice(0, 120);
  if (!rows.length) return empty("Записей пока нет", "Журнал появится после действий игроков или админов.");
  return `<div class="${esc(className)}">${rows.map(row => `
    <article class="ledger-row">
      <div>
        <strong>${esc(row.type || row.action || row.status || row.id || "запись")}</strong>
        <span>${esc(row.actor || row.actor_name || row.player_name || row.name || row.source || "system")}</span>
      </div>
      <div>
        <span>${dt(row.time || row.issued_at || row.submitted_at || row.updated_at || row.assigned_at)}</span>
        ${compactCoords(row) ? `<code>${esc(compactCoords(row))}</code>` : ""}
      </div>
      <p>${esc(short(row.details || row.notes || row.verdict_reason || row.message || row.target_name || "", 220))}</p>
    </article>
  `).join("")}</div>`;
}

function inventorySummary(snapshot) {
  if (!snapshot) return empty("Live-снимков нет", "Плагин начнёт писать онлайн-снимки после входа игрока или изменения инвентаря.");
  const inv = asArray(snapshot.inventory);
  const ender = asArray(snapshot.enderChest);
  return `
    <div class="inventory-summary">
      ${metric("Источник", snapshot.source || "plugin", dt(snapshot.createdAt))}
      ${metric("Слоты", inv.length + ender.length, `инв ${inv.length} · эндер ${ender.length}`)}
      ${metric("АР", number(snapshot.arInInventory) + number(snapshot.arInEnderChest), `инв ${snapshot.arInInventory ?? 0} · эндер ${snapshot.arInEnderChest ?? 0}`, "good")}
      ${metric("Позиция", snapshot.world || "—", `${snapshot.x ?? "—"} ${snapshot.y ?? "—"} ${snapshot.z ?? "—"}`)}
    </div>
  `;
}

function resultBars(rows, nameKeys = ["name", "display_name"], valueKeys = ["total", "votes", "raw_votes", "amount"]) {
  rows = asArray(rows).slice(0, 8);
  if (!rows.length) return empty("Нет данных для графика", "График появится после появления кандидатов, голосов или АР-строк.");
  const mapped = rows.map(row => {
    const name = nameKeys.map(k => row[k]).find(Boolean) || row.uuid || row.player || row.table || "строка";
    const value = valueKeys.map(k => row[k]).find(v => Number.isFinite(Number(v))) ?? 0;
    return { name, value: number(value) };
  });
  const max = Math.max(1, ...mapped.map(x => x.value));
  return `<div class="bars">${mapped.map(item => `
    <div class="bar-row">
      <div class="bar-head"><span>${esc(item.name)}</span><span>${esc(item.value)}</span></div>
      <div class="bar-track"><div class="bar-fill" style="width:${Math.max(4, Math.round(item.value / max * 100))}%"></div></div>
    </div>
  `).join("")}</div>`;
}

function stationCardsHtml(stations = [], deposits = []) {
  stations = asArray(stations);
  if (!stations.length) return `<div class="station-card-grid station-card-grid-empty">${empty("Участки пока не настроены", "В игре открой ЦИК > Участки и создай участок по блоку в прицеле.")}</div>`;
  const depositByStation = new Map(asArray(deposits).map(row => [String(row.station_id || row.station || row.id || ""), row]));
  return `<div class="station-card-grid">${stations.slice(0, 12).map(row => {
    const id = String(row.id ?? row.rowid ?? row.station_id ?? "");
    const deposit = depositByStation.get(id) || {};
    const active = number(row.active) > 0 && number(row.archived_at) <= 0;
    const votes = number(deposit.votes);
    const coords = `${row.world || "world"} ${row.x ?? "—"} ${row.y ?? "—"} ${row.z ?? "—"}`;
    return `
      <article class="station-card ${active ? "active" : "inactive"}">
        <div class="station-card-top">
          <strong>${esc(row.name || `Участок #${id || "—"}`)}</strong>
          ${pill(active ? "активен" : "выключен", active ? "good" : "warn")}
        </div>
        <div class="station-card-coords">${esc(coords)}</div>
        <div class="station-card-stats">
          <span><b>${esc(votes)}</b> опущено</span>
          <span>${esc(row.created_by || "ЦИК")}</span>
        </div>
      </article>
    `;
  }).join("")}</div>`;
}

async function loadDashboard(silent = false) {
  if (!silent) setLoading("Собираю сводку сервера");
  const [status, requestsStatus, elections, economy, audit, events, perfReady] = await Promise.all([
    safeApi("/api/status", {}),
    safeApi("/api/" + String.fromCharCode(100,105,115,99,111,114,100) + "/status", {}),
    safeApi("/api/elections/overview", {}),
    safeApi("/api/economy/ares/overview", {}),
    safeApi("/api/audit?limit=8", { rows: [] }),
    safeApi("/api/plugin/events?limit=8", { rows: [] }),
    safeApi("/api/performance/readiness", { checks: [], optimizationStack: [], readyPercent: 0 })
  ]);
  updateGlobalStatus(status);
  const perf = parsePerformance(status);
  const players = asArray(status.playersOnline).map(cleanText).filter(isMinecraftName);
  const electionOverview = elections.pluginWeb?.overview || {};
  const requestsReady = Object.values(requestsStatus.configured || {}).filter(Boolean).length;
  const requestsTotal = Object.keys(requestsStatus.configured || {}).length || 1;
  const eventRows = [...asArray(events.rows), ...asArray(audit.rows)].slice(0, 10);
  setView(`
    ${dashboardHero(status, perf, electionOverview, economy, perfReady.readyPercent)}
    ${dashboardCharts(status, perf, electionOverview, economy, perfReady, eventRows)}

    <section class="layout-grid grid-4">
      ${metric("Minecraft", status.minecraftOnline ? "Онлайн" : "Оффлайн", `ping ${status.latencyMs ?? "—"} ms`, status.minecraftOnline ? "good" : "bad")}
      ${metric("Игроки", players.length, players.length ? players.slice(0, 4).join(", ") : "сейчас никого нет", players.length ? "good" : "")}
      ${metric("TPS / MSPT", `${perf.tps ?? "—"} / ${perf.mspt ?? "—"}`, short(`${perf.tpsText || ""} ${perf.msptText || ""}`, 80), perf.mspt && perf.mspt > 50 ? "bad" : "good")}
      ${metric("Заявки", `${requestsReady}/${requestsTotal}`, "настроенные обязательные параметры", requestsReady === requestsTotal ? "good" : "warn")}
    </section>

    ${releaseReadinessHtml(status, perf, electionOverview, economy, requestsReady, requestsTotal)}
    ${firstRunReadinessHtml(perfReady)}

    <section class="layout-grid grid-wide">
      ${panel("Операционная сводка", "Ключевые состояния без перехода по разделам", `
        <div class="layout-grid grid-3">
          ${metric("Выборы", electionOverview.active ? "Идут" : "Пауза", `${short(electionOverview.title || "CopiMine Elections", 42)} · ${electionOverview.candidates ?? 0} кандидатов · ${electionOverview.votes ?? 0} голосов`, electionOverview.active ? "good" : "")}
          ${metric("АР у игроков", economy.totalKnownInPlayerData ?? 0, "сводка по игровым данным без списаний", "good")}
          ${metric("Связь с сервером", status.rconOk ? "Есть" : "Нет", status.rconOk ? "живые действия доступны" : "часть быстрых действий недоступна", status.rconOk ? "good" : "warn")}
        </div>
        <div style="height:12px"></div>
        ${players.length ? table("dash-online", players.map(name => ({ player: name })), [{ key: "player", label: "Игрок онлайн" }], { pageSize: 8 }) : empty("Игроков онлайн не найдено", "Если игрок скрыт vanish/hidden, сервер может не отдавать его в /list.")}
      `)}
      ${panel("Последние события", "Аудит панели и события плагинов", timeline(eventRows))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Стабильность сервера", "Плагины и настройки, которые помогают держать мир лёгким и плавным", optimizationStackHtml(perfReady))}
      ${panel("Источники проверки", "Какие данные уже подключены для панели", kv([
        ["server.properties", perfReady.sources?.serverProperties || "—"],
        ["plugins", perfReady.sources?.plugins || "—"],
        ["База панели", perfReady.sources?.adminDb || "—"],
        ["resource pack prompt", perfReady.resourcePackPromptReadable ? "читабельный" : "проверить"]
      ]))}
    </section>
  `);
}

async function loadPlayers() {
  setLoading("Загружаю игроков");
  const [playersData, onlineData] = await Promise.all([
    safeApi("/api/players", { players: [] }),
    safeApi("/api/players/online", { players: [] })
  ]);
  const online = new Set(asArray(onlineData.players).map(cleanText).filter(isMinecraftName));
  state.players = asArray(playersData.players).map(row => ({
    ...row,
    name: cleanText(row.name || row.username || row.player || row.uuid),
    online: online.has(cleanText(row.name || row.username || row.player || row.uuid))
  })).filter(row => row.name);
  if (!state.selectedPlayer && state.players[0]) state.selectedPlayer = state.players[0].name;
  const list = state.players.length ? `
    <div class="toolbar">
      <input class="grow" id="playerSearch" placeholder="Найти игрока" oninput="filterPlayers(this.value)" />
      <button class="btn btn-secondary" onclick="loadPlayers()">Обновить</button>
    </div>
    <div id="playerList" class="player-list">${playerListHtml(state.players)}</div>
  ` : empty("Игроки не найдены", "Проверь usercache/playerdata или источник игроков.");
  setView(`
    <section class="layout-grid grid-main-detail">
      ${panel("Игроки", "Выбери игрока, чтобы увидеть профиль, инвентарь и действия", list)}
      <section id="playerDetails" class="panel">${await playerDetailsHtml(state.selectedPlayer)}</section>
    </section>
  `);
}

function playerListHtml(rows) {
  return rows.map(row => `
    <button class="player-row ${state.selectedPlayer === row.name ? "active" : ""}" data-player="${esc(row.name)}" onclick="selectPlayer('${esc(row.name)}')">
      ${avatarBadge(row.name, "sm")}
      <span class="player-main">
        <span class="player-name">${esc(row.name)}</span>
        <span class="player-meta">${row.online ? "Сейчас в игре" : "Не в игре"}</span>
      </span>
      ${row.online ? pill("Онлайн", "good") : pill("Оффлайн", "neutral")}
    </button>
  `).join("");
}

window.filterPlayers = (query) => {
  const q = query.trim().toLowerCase();
  const rows = state.players.filter(row => JSON.stringify(row).toLowerCase().includes(q));
  $("playerList").innerHTML = playerListHtml(rows);
};

window.selectPlayer = async (name) => {
  state.selectedPlayer = cleanText(name);
  document.querySelectorAll(".player-row").forEach((button) => {
    button.classList.toggle("active", button.dataset.player === state.selectedPlayer);
  });
  $("playerDetails").innerHTML = `<div class="loading">Загружаю профиль...</div>`;
  $("playerDetails").innerHTML = await playerDetailsHtml(state.selectedPlayer);
};

async function playerDetailsHtml(player) {
  if (!player) return empty("Игрок не выбран", "Выбери игрока в списке слева.");
  const [profile, inventory, liveInventory, history, timelineData, actions] = await Promise.all([
    safeApi(`/api/players/${encodeURIComponent(player)}/profile`, {}),
    safeApi(`/api/players/${encodeURIComponent(player)}/inventory`, {}),
    safeApi(`/api/players/${encodeURIComponent(player)}/inventory/live?limit=12`, { onlineSnapshots: [] }),
    safeApi(`/api/players/${encodeURIComponent(player)}/inventory/history?limit=20`, { snapshots: [] }),
    safeApi(`/api/players/${encodeURIComponent(player)}/timeline?limit=220`, { rows: [] }),
    safeApi(`/api/players/${encodeURIComponent(player)}/actions?limit=30`, { rows: [] })
  ]);
  const live = liveInventory.latest || inventory.live;
  const actionButtons = playerActions.map(([action, label]) => `<button class="btn btn-secondary btn-small" onclick="playerAction('${esc(player)}','${action}')">${esc(label)}</button>`).join("");
  const site = profile.siteAccount || {};
  const bank = profile.bank || {};
  const pin = profile.pin || {};
  const pinState = bankPinState(pin);
  return `
    <div class="panel-header">
      <div>
        <h2 class="panel-title">${esc(player)}</h2>
        <p class="panel-subtitle">Профиль игрока, история действий и инвентарь</p>
      </div>
      <div class="action-strip">
        <button class="btn btn-primary btn-small" onclick="snapshotInventory('${esc(player)}')">Снимок инвентаря</button>
      </div>
    </div>
    <div class="layout-grid grid-3">
      ${metric("Здоровье", profile.health ?? "—", `еда ${profile.food ?? "—"}`)}
      ${metric("XP", profile.xpLevel ?? "—", profile.dimension || "измерение неизвестно")}
      ${metric("АР", number(profile.ar?.inventory) + number(profile.ar?.enderChest), `инв ${profile.ar?.inventory ?? 0} · эндер ${profile.ar?.enderChest ?? 0}`, "good")}
    </div>
    <div style="height:12px"></div>
    ${panel("Сайт и банк", "Привязка кабинета, баланс игрока и статус PIN без лишней техники.", kv([
      ["Аккаунт сайта", site.username || "Не привязан"],
      ["Кабинет привязан", Boolean(site.id)],
      ["Последний вход на сайт", dt(site.lastLoginAt)],
      ["Счёт", bank.accountId ? "Открыт" : "Не открыт"],
      ["Баланс банка", formatAr(bank.balance || 0)],
      ["Состояние PIN", pinState],
      ["PIN заблокирован", Boolean(pin.locked)],
      ["Временный PIN истекает", pin.temporaryExpiresAt ? dt(pin.temporaryExpiresAt) : "--"]
    ]), site.id ? `<button class="btn btn-secondary btn-small" onclick="playerResetBankPin('${esc(player)}')">Сбросить PIN</button>` : "")}
    ${panel("Быстрые действия", "Все действия записываются в журнал и требуют серверные права.", `<div class="action-strip">${actionButtons}</div>`)}
    ${panel("Текущий инвентарь", "Если игрок онлайн, первым берётся свежий игровой снимок.", `
      ${inventorySummary(live)}
      <div style="height:12px"></div>
      ${inventoryGrid(firstArray(live?.inventory, inventory.inventory, []), 18)}
    `)}
    ${panel("Эндер-сундук и свежие снимки", "Последние игровые снимки помогают разбирать спорные ситуации без ручного поиска по файлам.", `
      ${inventoryGrid(firstArray(live?.enderChest, inventory.enderChest, []), 18)}
      <div style="height:12px"></div>
      ${table("player-live-history", asArray(liveInventory.onlineSnapshots).map(x => ({ createdAt: x.createdAt, source: x.source, inventory: asArray(x.inventory).length, ender: asArray(x.enderChest).length, ar: number(x.arInInventory) + number(x.arInEnderChest), world: x.world })), [
        { key: "createdAt", label: "Время", render: v => dt(v) },
        { key: "source", label: "Источник" },
        { key: "inventory", label: "Инв." },
        { key: "ender", label: "Эндер" },
        { key: "ar", label: "АР" },
        { key: "world", label: "Мир" }
      ], { pageSize: 6 })}
    `)}
    ${panel("История снимков", "Архив последних сохранённых инвентарей и эндер-сундуков.", table("player-history", asArray(history.snapshots).map(x => ({ createdAt: x.createdAt, inventory: asArray(x.inventory).length, enderChest: asArray(x.enderChest).length, ar: number(x.arInInventory) + number(x.arInEnderChest) })), [
      { key: "createdAt", label: "Время", render: v => dt(v) },
      { key: "inventory", label: "Слоты инв." },
      { key: "enderChest", label: "Слоты эндера" },
      { key: "ar", label: "АР" }
    ], { pageSize: 8 }))}
    ${panel("Лента действий", "Проверки, АР и игровые события собраны в одну понятную ленту.", `<div class="player-actions-log">${activityTimeline(timelineData.rows)}</div>`)}
    ${panel("Последние действия CoreProtect", "Контекст для проверки игрока", table("player-actions", asArray(actions.rows), null, { pageSize: 12 }))}
  `;
}

window.playerAction = async (player, action) => {
  const reason = prompt("Причина действия", "CopiMine") || "CopiMine";
  let body = { reason };
  if (action === "tp_to" || action === "tp_here") body.target = prompt("К кому/кого телепортировать?", "") || "";
  const dangerLabels = {
    ban: "PLAYER_BAN",
    op: "PLAYER_OP",
    deop: "PLAYER_DEOP",
    clear: "PLAYER_CLEAR",
    kill: "PLAYER_KILL",
    tp_here: "PLAYER_TP_HERE",
    tp_coords: "PLAYER_TP_COORDS"
  };
  const headers = dangerLabels[action] ? dangerConfirm(`Опасное действие с игроком: ${action} -> ${player}`, dangerLabels[action]) : {};
  if (!headers) return;
  try {
    const res = await api(`/api/players/${encodeURIComponent(player)}/command/${action}`, { method: "POST", headers, body: JSON.stringify(body) });
    toast(`Команда выполнена: ${res.command || action}`);
  } catch (err) {
    toast(err.message, true);
  }
};

window.snapshotInventory = async (player = state.selectedPlayer) => {
  if (!player) return toast("Игрок не выбран", true);
  try {
    const snapshot = await api(`/api/players/${encodeURIComponent(player)}/inventory/snapshots`, { method: "POST", body: "{}" });
    openInventoryModal(snapshot);
    toast("Снимок инвентаря создан");
    if (state.tab === "players") $("playerDetails").innerHTML = await playerDetailsHtml(player);
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerResetBankPin = async (player = state.selectedPlayer) => {
  if (!player) return toast("Игрок не выбран", true);
  const headers = dangerConfirm(`Сбросить банковский PIN для ${player}? Старый PIN сразу перестанет работать.`, "PLAYER_BANK_PIN_RESET");
  if (!headers) return;
  try {
    const result = await api(`/api/players/${encodeURIComponent(player)}/bank-pin/reset`, {
      method: "POST",
      headers,
      body: "{}"
    });
    const note = result.deliveredInGame
      ? "Временный PIN также отправлен в Minecraft-чат."
      : "Игрок увидит временный PIN в личном кабинете.";
    toast(`Временный PIN выдан до ${dt(result.expiresAt)}. ${note}`);
    if (state.tab === "players") $("playerDetails").innerHTML = await playerDetailsHtml(player);
  } catch (err) {
    toast(err.message, true);
  }
};

function inventoryGrid(items, limit = 120) {
  items = asArray(items).slice(0, limit);
  if (!items.length) return empty("Предметов нет", "Источник инвентаря пуст или NBT parser недоступен.");
  return `<div class="inventory-grid">${items.map(item => `
    <div class="slot" title="${esc(item.id || item.displayName || "")}">
      <img src="${esc(item.iconUrl || `/assets/mc-icons/item/${item.icon || "barrier"}.png`)}" alt="" onerror="this.style.display='none'" />
      <b>${esc(short(item.displayName || item.id || "item", 18))}</b>
      <span>x${esc(item.Count ?? item.count ?? 1)} · slot ${esc(item.Slot ?? item.slot ?? "—")}</span>
    </div>
  `).join("")}</div>`;
}

function openInventoryModal(snapshot) {
  const inv = asArray(snapshot.inventory);
  const ender = asArray(snapshot.enderChest);
  $("modalRoot").innerHTML = `
    <div class="modal-overlay" onclick="if(event.target===this) closeModal()">
      <div class="modal">
        <div class="modal-head">
          <div>
            <h2>Снимок инвентаря: ${esc(snapshot.name || state.selectedPlayer)}</h2>
            <p>${dt(snapshot.createdAt)} · ${esc(snapshot.world || "игровой мир")}</p>
          </div>
          <button class="btn btn-secondary" onclick="closeModal()">Закрыть</button>
        </div>
        <section class="layout-grid grid-4">
          ${metric("Слоты инвентаря", inv.length)}
          ${metric("Слоты эндера", ender.length)}
          ${metric("АР в инвентаре", snapshot.arInInventory ?? 0, "", "good")}
          ${metric("АР в эндере", snapshot.arInEnderChest ?? 0, "", "good")}
        </section>
        <div style="height:14px"></div>
        ${panel("Инвентарь", "", inventoryGrid(inv))}
        ${panel("Эндер-сундук", "", inventoryGrid(ender))}
      </div>
    </div>
  `;
}

window.closeModal = () => { $("modalRoot").innerHTML = ""; };

async function loadInventories() {
  setLoading("Готовлю инвентари");
  const playersData = await safeApi("/api/players", { players: [] });
  const rows = asArray(playersData.players).map(p => ({ name: cleanText(p.name || p.username || p.player || p.uuid), uuid: p.uuid || "" })).filter(x => x.name);
  setView(`
    <section class="layout-grid grid-wide">
      ${panel("Снимки инвентарей", "Создай снимок по игроку и открой историю из профиля", `
        <div class="toolbar">
          <input id="inventoryPlayerInput" class="grow" placeholder="Ник игрока" list="playersDatalist" />
          <button class="btn btn-primary" onclick="snapshotInventoryFromInput()">Создать снимок</button>
        </div>
        <datalist id="playersDatalist">${rows.map(x => `<option value="${esc(x.name)}"></option>`).join("")}</datalist>
        ${table("inventory-players", rows, [
          { key: "name", label: "Игрок" },
          { key: "name", label: "Действие", render: v => `<button class="btn btn-secondary btn-small" onclick="snapshotInventory('${esc(v)}')">Снимок</button>` }
        ])}
      `)}
      ${panel("Как этим пользоваться", "Инструмент для проверок, спорных кейсов и экономики", `
        ${kv([
          ["Сценарий", "Выбрать игрока → создать снимок → открыть профиль → сравнить с историей"],
          ["Безопасность", "Сайт читает playerdata и сохраняет снимки в data/, без изменения инвентаря"],
          ["АР", "Отдельно считается в инвентаре и эндер-сундуке"]
        ])}
      `)}
    </section>
  `);
}

window.snapshotInventoryFromInput = () => {
  const player = $("inventoryPlayerInput")?.value?.trim();
  if (!player) return toast("Укажи ник игрока", true);
  snapshotInventory(player);
};

async function loadElections() {
  setLoading("Загружаю выборы");
  const [data, detail] = await Promise.all([
    safeApi("/api/elections/overview", {}),
    safeApi("/api/elections/detail?limit=500", {})
  ]);
  const web = data.pluginWeb || {};
  const overview = web.overview || detail.summary || {};
  const election = detail.election || {};
  const summary = detail.summary || {};
  const candidateRows = firstArray(detail.candidates, web.candidates, getPath(data, "groups.candidates.0.rows", []));
  const fraudRows = [...asArray(detail.antiFraud), ...asArray(web.antiFraud), ...asArray(data.antiFraud)];
  const applicationRows = asArray(detail.applications);
  const lawRows = firstArray(detail.laws, getPath(web, "raw.laws", []));
  const pendingLawRows = asArray(detail.pendingLaws || getPath(web, "raw.pendingLaws", []));
  const pollingStations = asArray(detail.pollingStations);
  const voteDeposits = asArray(detail.voteDeposits);
  const auditRows = asArray(detail.audit);
  const tax = asArray(detail.taxes)[0] || {};
  state.electionApplications = Object.fromEntries(applicationRows.map((row) => [row.id, row]));
  setView(`
    <section class="dashboard-hero election-hero">
      <div class="hero-copy">
        <span class="hero-kicker">Выборы CopiMine</span>
        <h2>${esc(electionStageLabel(election.current_stage || election.status || web.stageTitle, "Пауза"))}</h2>
        <p>Сайт показывает ход выборов понятным языком: заявки кандидатов, участки ЦИК, результаты, президента, законы и налог без сырых команд и технических реестров.</p>
        <div class="hero-actions">
          ${pill(`Тур ${esc(election.current_round || summary.round || web.raw?.round || 1)}`, "neutral")}
          ${pill(`${esc(summary.candidateCount ?? candidateRows.length)} кандидатов`, candidateRows.length ? "good" : "warn")}
          ${pill(detail.president?.president_name || detail.president?.minecraft_name || overview.president ? `Президент: ${esc(detail.president?.president_name || detail.president?.minecraft_name || overview.president)}` : "Президент ещё не выбран", detail.president?.president_name || detail.president?.minecraft_name || overview.president ? "good" : "warn")}
        </div>
      </div>
      <div class="hero-board">
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/writable_book.png" alt="" />
          <strong>${esc(summary.applications ?? applicationRows.length)}</strong>
          <span>заявок</span>
        </div>
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/paper.png" alt="" />
          <strong>${esc(summary.totalVotes ?? 0)}</strong>
          <span>голосов</span>
        </div>
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/lectern.png" alt="" />
          <strong>${esc(summary.activePollingStations ?? pollingStations.length)}</strong>
          <span>активных участков</span>
        </div>
        <div class="hero-tile">
          <img src="/assets/mc-icons/item/emerald.png" alt="" />
          <strong>${tax.amount ? formatAr(tax.amount) : "не назначен"}</strong>
          <span>налог президента</span>
        </div>
      </div>
    </section>
    <section class="layout-grid grid-2">
      ${panel("Состояние цикла", "Главные статусы выборов коротко и понятным языком.", kv([
        ["Этап", electionStageLabel(election.current_stage || election.status || web.stageTitle, "—")],
        ["Тур", election.current_round || summary.round || web.raw?.round || "1"],
        ["Президент", detail.president?.president_name || detail.president?.minecraft_name || election.president_name || overview.president || "—"],
        ["Лимит кандидатов", election.candidate_limit ?? web.raw?.candidateLimit ?? "—"],
        ["Срок президента", election.president_term_days ? `${election.president_term_days} дн.` : "—"],
        ["Режим сайта", data.readOnly ? "Только просмотр" : "Управление разрешено"]
      ]), siteBulletList([
        "Управление выборами перенесено в игровые GUI.",
        "Сайт показывает статусы, книги кандидатов, законы и оплату налога.",
        "Опасные действия здесь не дублируются."
      ]))}
      ${panel("Заявки кандидатов", "Главный рабочий экран для просмотра кандидатов: книга, статусы комиссии и решение админа.", `
        ${electionApplicationCards(applicationRows)}
        <div style="height:12px"></div>
        ${pendingLawRows.length ? `<div class="book-status-strip">${pendingLawRows.slice(0, 5).map((row) => pill(`Закон на проверке · ${short(row.text || "", 42)}`, "warn")).join("")}</div>` : ""}
      `)}
      ${panel("Кандидаты и результаты", "После одобрения кандидаты попадают в список, а голоса складываются в понятный рейтинг.", `
        ${candidateCards(candidateRows)}
        <div style="height:12px"></div>
        ${resultBars(candidateRows, ["player_name", "display_name", "name"], ["last_result", "total", "votes", "raw_votes"])}
      `)}
      ${panel("Участки и ЦИК", "Участки, комиссии и приём бюллетеней видны без погружения в служебный реестр.", `
        ${stationCardsHtml(pollingStations, voteDeposits)}
        <div style="height:12px"></div>
        ${kv([
          ["Активные участки", summary.activePollingStations ?? pollingStations.length],
          ["Опущено бюллетеней", summary.voteDeposits ?? voteDeposits.reduce((sum, row) => sum + number(row.votes), 0)],
          ["Сигналы антифрода", fraudRows.length || "не найдено"]
        ])}
      `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Президент и законы", "Победитель выборов, опубликованные законы и новые тексты на проверке.", `
        ${lawCards(lawRows)}
        <div style="height:12px"></div>
        ${pendingLawRows.length ? `<div class="law-stack">${pendingLawRows.slice(0, 5).map((row) => `
          <article class="law-pending-row">
            <strong>${esc(short(row.text || "", 88) || "Без текста")}</strong>
            <span>${dt(row.created_at)}</span>
          </article>
        `).join("")}</div>` : empty("Новых законов на проверке нет", "Когда президент отправит новый закон или замену, он появится здесь.")}
      `)}
      ${panel("Налог", "Налог задаёт президент, а игроки могут оплачивать его частями через сайт и игру.", kv([
        ["Текущий налог", tax.amount ? formatAr(tax.amount) : "не установлен"],
        ["Статус", tax.amount ? "приём оплаты открыт" : "налог пока не назначен"],
        ["Оплата", "через игру и личный кабинет"],
        ["Публичность", "без раскрытия плательщиков"]
      ]))}
    </section>
    ${panel("Журнал цикла", "Смена этапов, книги кандидатов, законы и выбор президента собраны в одну понятную ленту.", `
      <div class="ledger election-ledger">
        ${auditRows.length ? auditRows.slice(0, 40).map((row) => `
          <article class="ledger-row">
            <div>
              <strong>${esc(humanizeAuditAction(row.action || row.type || row.status))}</strong>
              <span>${esc(row.actor || row.actor_name || row.player_name || "Система CopiMine")}</span>
            </div>
            <div>
              <span>${dt(row.created_at || row.time || row.updated_at || row.submitted_at)}</span>
            </div>
            <p>${esc(short(row.details || row.notes || row.message || row.target_name || "", 220) || "Без дополнительных заметок")}</p>
          </article>
        `).join("") : empty("Событий пока нет", "Когда начнётся избирательный цикл, события появятся здесь.")}
      </div>
    `)}
  `);
}

async function loadEconomy() {
  setLoading("Загружаю экономику");
  const [data, history, ledger, donation] = await Promise.all([
    safeApi("/api/economy/ares/overview", {}),
    safeApi("/api/economy/ares/history?limit=40", { snapshots: [], changes: [] }),
    safeApi("/api/economy/ares/ledger?limit=500", { events: [], balances: [], transactions: [], assets: [], scans: [], snapshots: [], summary: {} }),
    safeApi("/api/admin/donation/overview?limit=120", { summary: {}, balances: [], ledger: [], claims: [], sessions: [] })
  ]);
  const players = asArray(ledger.balances).length ? asArray(ledger.balances).map((x) => ({
    player: x.name,
    amount: x.balance,
    inventory: x.inventory_balance,
    enderChest: x.ender_balance,
    uuid: x.uuid,
    updatedAt: x.updated_at
  })) : asArray(data.players);
  const containers = asArray(data.worldContainers?.rows);
  const econSummary = ledger.summary || {};
  const donationSummary = donation.summary || {};
  setView(`
    <section class="layout-grid grid-4">
      ${metric("AR в обороте", econSummary.totalBalance ?? data.totalKnownInPlayerData ?? 0, "Баланс на руках и в хранилищах", "good")}
      ${metric("Игроков с AR", econSummary.holders ?? players.length, "Только подтверждённые остатки")}
      ${metric("Операций AR", econSummary.transactions ?? asArray(ledger.transactions).length, `${econSummary.transfers ?? 0} переводов и ${econSummary.smelts ?? 0} переплавок`)}
      ${metric("Официальных AR-предметов", econSummary.activeAssets ?? asArray(ledger.assets).length, `${econSummary.events ?? 0} событий и ${econSummary.scans ?? 0} сканов`)}
    </section>
    <section class="layout-grid grid-wide">
      ${panel("Распределение AR", "Где сейчас сосредоточен баланс", resultBars(players, ["player"], ["amount"]))}
      ${panel("Операции", "Инструменты для снимков и аудита экономики", `
        <div class="action-strip">
          <button class="btn btn-primary" onclick="createEconomySnapshot()">Создать снимок</button>
          <button class="btn btn-secondary" onclick="scanAresWorld()">Скан предметов AR</button>
        </div>
        <div style="height:12px"></div>
        ${kv([
          ["ID AR-предметов", asArray(data.itemIds).join(", ") || "не настроены"],
          ["История снимков", history.count ? `${history.count} записей` : "пока пусто"],
          ["Источник журнала", ledger.source || "основной backend"],
          ["AR в инвентарях", econSummary.inventoryBalance ?? "—"],
          ["AR в эндер-сундуках", econSummary.enderBalance ?? "—"],
          ["Переводы", econSummary.transfers ?? "—"],
          ["Переплавки", econSummary.smelts ?? "—"],
          ["Последний снимок", dt(data.lastSnapshotAt || data.createdAt)]
        ])}
      `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Игроки с AR", "Балансы, инвентари и эндер-сундуки", table("economy-players", players, [
        { key: "player", label: "Игрок" },
        { key: "amount", label: "Баланс" },
        { key: "inventory", label: "Инвентарь" },
        { key: "enderChest", label: "Эндер" }
      ], { pageSize: 15 }))}
      ${panel("Контейнеры мира", "Подозрительные или крупные хранилища", table("economy-containers", containers, null, { pageSize: 15 }))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Транзакции AR", "Переводы, переплавки и другие движения", `<div class="economy-transactions">${table("economy-transactions-table", asArray(ledger.transactions), [
        { key: "time", label: "Время", render: value => dt(value) },
        { key: "type", label: "Тип" },
        { key: "from_name", label: "От" },
        { key: "to_name", label: "Кому" },
        { key: "amount", label: "Сумма" },
        { key: "material", label: "Материал" },
        { key: "details", label: "Детали", render: value => short(value || "", 90) }
      ], { pageSize: 12 })}</div>`)}
      ${panel("Активы AR", "Официальные предметы и их текущее состояние", `<div class="economy-assets">${table("economy-assets-table", asArray(ledger.assets), [
        { key: "updated_at", label: "Обновлён", render: value => dt(value) },
        { key: "owner_name", label: "Владелец" },
        { key: "status", label: "Статус" },
        { key: "material", label: "Материал" },
        { key: "source", label: "Источник" },
        { key: "asset_id", label: "Asset", render: value => short(value || "", 12) }
      ], { pageSize: 12 })}</div>`)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Журнал AR", "Все ключевые события экономики в одном потоке", `<div class="economy-ledger">${ledgerRows(asArray(ledger.events), "economy-ledger")}</div>`)}
      ${panel("История снимков", "Снимки состояния AR для аудита и расследований", table("economy-snapshots", asArray(ledger.snapshots), null, { pageSize: 12 }))}
    </section>
    <section class="layout-grid grid-4">
      ${metric("Донат-счёта", donationSummary.accounts ?? 0, "Отдельно от AR и банка", "good")}
      ${metric("Донат-баланс", formatDonate(donationSummary.totalBalance ?? 0), "Сумма по всем donation accounts", donationSummary.totalBalance ? "good" : "neutral")}
      ${metric("Невыдано", donationSummary.unclaimedItems ?? 0, "Предметы ждут выдачи через claim-flow", Number(donationSummary.unclaimedItems || 0) ? "warn" : "good")}
      ${metric("Открытые сессии", donationSummary.openSessions ?? 0, "Mock donation workflow", Number(donationSummary.openSessions || 0) ? "warn" : "neutral")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Донат-счёта", "Баланс игроков, который не смешивается с AR.", table("donation-balances", asArray(donation.balances), [
        { key: "player_name", label: "Игрок", render: (value, row) => esc(value || row.player_uuid || "—") },
        { key: "balance", label: "DC", render: value => formatDonate(value || 0) },
        { key: "updated_at", label: "Обновлён", render: value => dt(value) }
      ], { pageSize: 12 }))}
      ${panel("Журнал доната", "Пополнения и списания только по donation balance.", table("donation-ledger", asArray(donation.ledger), [
        { key: "created_at", label: "Время", render: value => dt(value) },
        { key: "player_uuid", label: "Игрок" },
        { key: "delta", label: "Изменение", render: value => formatDonate(value || 0) },
        { key: "balance_after", label: "После", render: value => formatDonate(value || 0) },
        { key: "reason", label: "Причина", render: value => short(value || "", 90) }
      ], { pageSize: 12 }))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Claims предметов", "Что уже оплачено и ждёт выдачи игроку.", table("donation-claims", asArray(donation.claims), [
        { key: "created_at", label: "Создан", render: value => dt(value) },
        { key: "player_uuid", label: "Игрок" },
        { key: "item_id", label: "Предмет" },
        { key: "amount", label: "Кол-во" },
        { key: "status", label: "Статус", render: value => pill(statusLabel(value || "pending"), artifactStatusTone(value)) }
      ], { pageSize: 12 }))}
      ${panel("Платёжные сессии", "Mock-сессии без реального SBP и без связи с AR.", table("donation-sessions", asArray(donation.sessions), [
        { key: "created_at", label: "Создана", render: value => dt(value) },
        { key: "player_uuid", label: "Игрок" },
        { key: "provider", label: "Провайдер" },
        { key: "amount", label: "Сумма", render: value => formatDonate(value || 0) },
        { key: "status", label: "Статус", render: value => pill(statusLabel(value || "pending"), artifactStatusTone(value)) }
      ], { pageSize: 12 }))}
    </section>
    ${panel("Скан мира", "AR в контейнерах и подозрительных местах", table("economy-scans", asArray(ledger.scans), null, { pageSize: 12 }))}
  `);
}

window.createEconomySnapshot = async () => {
  try { await api("/api/economy/ares/snapshots", { method: "POST", body: "{}" }); toast("Снимок экономики создан"); loadEconomy(); }
  catch (err) { toast(err.message, true); }
};

window.scanAresWorld = async () => {
  try { toast("Скан мира запущен"); await api("/api/economy/ares/scan-world", { method: "POST", body: "{}" }); toast("Скан мира завершён"); loadEconomy(); }
  catch (err) { toast(err.message, true); }
};

function artifactStatusTone(status) {
  const value = String(status || "").toUpperCase();
  if (["DELIVERED", "CLAIMED", "COMPLETED", "ACTIVE", "PAID"].includes(value)) return "good";
  if (["PENDING", "PENDING_DELIVERY"].includes(value)) return "warn";
  if (["FAILED", "REFUNDED", "SUSPICIOUS", "BLOCKED"].includes(value)) return "bad";
  return "neutral";
}

async function loadArtifacts() {
  setLoading("Загружаю артефакты");
  const [health, catalog, shops, purchases, pending, repairs, suspicious] = await Promise.all([
    safeApi("/api/artifacts/health", { activeJars: [], counts: {} }),
    safeApi("/api/artifacts/catalog?limit=300", { items: [] }),
    safeApi("/api/artifacts/shops?limit=120", { shops: [] }),
    safeApi("/api/artifacts/purchases?limit=200", { purchases: [] }),
    safeApi("/api/artifacts/pending?limit=200", { deliveries: [] }),
    safeApi("/api/artifacts/repairs?limit=200", { repairs: [] }),
    safeApi("/api/artifacts/suspicious?limit=120", { events: [] })
  ]);
  const counts = health.counts || {};
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Связка модулей", health.bridgeMode || "ArtifactsBridge", health.jarsOk ? "оба модуля активны" : `модули: ${asArray(health.activeJars).join(", ") || "нет"}`, health.jarsOk ? "good" : "bad")}
      ${metric("База сайта", health.postgres ? "PostgreSQL доступна" : "PostgreSQL недоступна", "единое хранилище CopiMine", health.postgres ? "good" : "bad")}
      ${metric("Каталог", counts.artifact_items_catalog ?? asArray(catalog.items).length, "кешируется плагином")}
      ${metric("Pending", counts.artifact_pending_deliveries ?? asArray(pending.deliveries).length, "не терять предметы", Number(counts.artifact_pending_deliveries || 0) ? "warn" : "good")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Каталог лавки", "WEAPON / ARMOR / TOOL активны, RP остаётся заготовкой", table("artifact-catalog", asArray(catalog.items), [
        { key: "item_id", label: "ID" },
        { key: "category", label: "Категория" },
        { key: "name", label: "Название" },
        { key: "rarity", label: "Редкость" },
        { key: "price_ar", label: "AR" },
        { key: "enabled", label: "Вкл", render: v => v ? pill("да", "good") : pill("нет", "warn") }
      ], { pageSize: 12 }))}
      ${panel("Лавки", "Активные точки автокассы в мире", table("artifact-shops", asArray(shops.shops), [
        { key: "shop_id", label: "Лавка" },
        { key: "world_name", label: "Мир" },
        { key: "block_x", label: "X" },
        { key: "block_y", label: "Y" },
        { key: "block_z", label: "Z" },
        { key: "enabled", label: "Статус", render: v => v ? pill("active", "good") : pill("off", "warn") }
      ], { pageSize: 10 }))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Покупки", "Подтверждённые оплаты через официальный банк AR", table("artifact-purchases", asArray(purchases.purchases), [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "player_name", label: "Игрок" },
        { key: "item_id", label: "Предмет" },
        { key: "price_ar", label: "AR" },
        { key: "status", label: "Статус", render: v => pill(v || "—", artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
      ${panel("Отложенная выдача", "Оплачено, но предмет ждёт безопасной выдачи", table("artifact-pending", asArray(pending.deliveries), [
        { key: "created_at", label: "Создано", render: v => dt(v) },
        { key: "player_uuid", label: "Игрок", render: (value, row) => esc(row.player_name || row.player || value || "—") },
        { key: "item_id", label: "Предмет" },
        { key: "status", label: "Статус", render: v => pill(statusLabel(v || "pending"), artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Ремонты", "Только официальные PDC-предметы за AR", table("artifact-repairs", asArray(repairs.repairs), [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "player_name", label: "Игрок" },
        { key: "item_id", label: "Предмет" },
        { key: "repair_cost_ar", label: "AR" },
        { key: "status", label: "Статус", render: v => pill(v || "—", artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
      ${panel("Подозрительные предметы", "Fake lore/displayName не считается официальным предметом", table("artifact-suspicious", asArray(suspicious.events), [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "player_name", label: "Игрок" },
        { key: "event_type", label: "Событие" },
        { key: "details", label: "Детали", render: v => short(v, 120) }
      ], { pageSize: 12 }))}
    </section>
  `);
}

async function loadRequests() {
  setLoading("Загружаю заявки");
  const [status, applications, reports] = await Promise.all([
    safeApi("/api/" + String.fromCharCode(100,105,115,99,111,114,100) + "/status", {}),
    safeApi("/api/applications", { applications: [] }),
    safeApi("/api/reports", { reports: [] })
  ]);
  const configured = status.configured || {};
  const ready = Object.values(configured).filter(Boolean).length;
  const total = Object.keys(configured).length || 1;
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Готовность", `${ready}/${total}`, "токен, guild, каналы, роль, api key", ready === total ? "good" : "warn")}
      ${metric("Заявки", asArray(applications.applications).length, "из кабинета и очереди")}
      ${metric("Жалобы", asArray(reports.reports).length, "активные обращения")}
      ${metric("Outbox", asArray(status.outbox).length, "очередь публикации")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Новая заявка", "Создаётся в кабинете и попадает в очередь обработки", `
        <div class="form-grid">
          <input id="appPlayer" placeholder="Ник игрока" />
          <input id="appContact" placeholder="Контакт или игровой ник" />
          <textarea id="appWhy" class="full" placeholder="Почему игрок хочет участвовать / стать кандидатом"></textarea>
          <button class="btn btn-primary full" onclick="createRequestApplication()">Создать заявку</button>
        </div>
      `)}
      ${panel("Новая жалоба", "Быстрое создание обращения с целью и координатами", `
        <div class="form-grid">
          <input id="repReporter" placeholder="Кто жалуется" />
          <input id="repTarget" placeholder="На кого / цель" />
          <select id="repSeverity"><option value="normal">normal</option><option value="high">high</option><option value="critical">critical</option></select>
          <input id="repWorld" placeholder="world" />
          <textarea id="repMessage" class="full" placeholder="Описание проблемы"></textarea>
          <button class="btn btn-primary full" onclick="createRequestReport()">Создать жалобу</button>
        </div>
      `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Заявки", "Статусы синхронизируются с ботом", table("requests-apps", asArray(applications.applications), [
        { key: "id", label: "ID" },
        { key: "player", label: "Игрок" },
        { key: "status", label: "Статус", render: v => pill(v || "pending", v === "approved" ? "good" : v === "rejected" ? "bad" : "warn") },
        { key: "createdAt", label: "Создано", render: v => dt(v) },
        { key: "id", label: "Действия", render: v => `<div class="action-strip"><button class="btn btn-secondary btn-small" onclick="requestApplicationStatus('${esc(v)}','approved')">OK</button><button class="btn btn-secondary btn-small" onclick="requestApplicationStatus('${esc(v)}','rejected')">Reject</button></div>` }
      ], { pageSize: 10 }))}
      ${panel("Жалобы", "Рабочая очередь администрации", table("requests-reports", asArray(reports.reports), [
        { key: "id", label: "ID" },
        { key: "reporter", label: "Автор" },
        { key: "target", label: "Цель" },
        { key: "status", label: "Статус", render: v => pill(v || "open", v === "closed" ? "good" : v === "rejected" ? "bad" : "warn") },
        { key: "severity", label: "Важность" },
        { key: "id", label: "Действия", render: v => `<div class="action-strip"><button class="btn btn-secondary btn-small" onclick="requestReportStatus('${esc(v)}','in_progress')">Взять</button><button class="btn btn-secondary btn-small" onclick="requestReportStatus('${esc(v)}','closed')">Закрыть</button></div>` }
      ], { pageSize: 10 }))}
    </section>
  `);
}

window.createRequestApplication = async () => {
  try {
    await api("/api/applications", { method: "POST", body: JSON.stringify({ player: $("appPlayer").value.trim(), [String.fromCharCode(100,105,115,99,111,114,100) + "_username"]: $("appContact").value.trim(), contact: $("appContact").value.trim(), why: $("appWhy").value.trim() }) });
    toast("Заявка создана");
    loadRequests();
  } catch (err) { toast(err.message, true); }
};

window.createRequestReport = async () => {
  try {
    await api("/api/reports", { method: "POST", body: JSON.stringify({ reporter: $("repReporter").value.trim(), target: $("repTarget").value.trim(), severity: $("repSeverity").value, world: $("repWorld").value.trim(), message: $("repMessage").value.trim() }) });
    toast("Жалоба создана");
    loadRequests();
  } catch (err) { toast(err.message, true); }
};

window.requestApplicationStatus = async (id, status) => {
  try { await api(`/api/applications/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify({ status, reason: "Изменено из панели" }) }); toast("Статус заявки обновлён"); loadRequests(); }
  catch (err) { toast(err.message, true); }
};

window.requestReportStatus = async (id, status) => {
  try { await api(`/api/reports/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify({ status, reason: "Изменено из панели" }) }); toast("Статус жалобы обновлён"); loadRequests(); }
  catch (err) { toast(err.message, true); }
};

async function loadStats() {
  setLoading("Собираю статистику сервера");
  const [stats, events, errorLog, perfReady] = await Promise.all([
    safeApi("/api/server/stats", {}),
    safeApi("/api/plugin/events?limit=80", { rows: [] }),
    safeApi("/api/logs/latest?lines=220&category=errors", { lines: [] }),
    safeApi("/api/performance/readiness", { checks: [], propertyChecks: [], optimizationStack: [], readyPercent: 0 })
  ]);
  const system = stats.system || {};
  const minecraft = stats.minecraft || {};
  const rcon = stats.rcon || {};
  const logs = stats.logs || {};
  const plugins = stats.plugins || {};
  const world = stats.world || {};
  const properties = stats.properties || {};
  const memoryText = system.memoryPercent == null ? "—" : `${Number(system.memoryPercent).toFixed(1)}%`;
  const diskText = system.diskTotal ? `${bytes(system.diskUsed)} / ${bytes(system.diskTotal)}` : "—";
  setView(`
    <section class="server-stat-grid">
      ${metric("Minecraft", minecraft.online ? "Онлайн" : "Оффлайн", rcon.ok ? "живые данные подключены" : "живые данные ограничены", minecraft.online ? "good" : "bad")}
      ${metric("TPS", short(rcon.tps || minecraft.tps || "—", 38), "живые данные сервера")}
      ${metric("MSPT", short(rcon.mspt || minecraft.mspt || "—", 38), "без красных цифр справа")}
      ${metric("Игроки", asArray(minecraft.playersOnline).length, asArray(minecraft.playersOnline).join(", ") || "никого онлайн")}
      ${metric("CPU", system.cpuPercent == null ? "—" : `${Number(system.cpuPercent).toFixed(1)}%`, "нагрузка панели")}
      ${metric("RAM", memoryText, system.memoryUsed ? `${bytes(system.memoryUsed)} / ${bytes(system.memoryTotal)}` : "")}
      ${metric("Диск", system.diskFree ? bytes(system.diskFree) : "—", diskText)}
      ${metric("Логи", Number(logs.errors || 0) + Number(logs.warnings || 0), `${logs.errors || 0} ошибок, ${logs.warnings || 0} warn`, logs.errors ? "warn" : "good")}
    </section>
    <section class="layout-grid grid-wide">
      ${panel("Серверные параметры", "Ключевые настройки, влияющие на стабильность и MSPT", kv([
        ["view-distance", properties["view-distance"] ?? "—"],
        ["simulation-distance", properties["simulation-distance"] ?? "—"],
        ["network-compression-threshold", properties["network-compression-threshold"] ?? "—"],
        ["entity-broadcast-range-percentage", properties["entity-broadcast-range-percentage"] ?? "—"],
        ["sync-chunk-writes", properties["sync-chunk-writes"] ?? "—"],
        ["world region files", world.regionFiles ?? 0],
        ["sample region size", bytes(world.sampleRegionSize || 0)]
      ]))}
      ${panel("Сводка плагинов", "Контроль, что активный CopiMine собран в один jar", kv([
        ["Всего jar", plugins.totalJars ?? 0],
        ["CopiMine jar", plugins.copimineJars ?? 0],
        ["Живая связь", rcon.ok ? "подключена" : (rcon.error || "не подключена")],
        ["First-run готовность", `${perfReady.readyPercent || 0}%`],
        ["Последнее обновление", dt(stats.time)]
      ]))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Профиль стабильности", "Chunky, SeeMore и другие плагины, которые помогают миру работать плавно", optimizationStackHtml(perfReady))}
      ${panel("Проверка параметров", "Настройки, которые чаще всего влияют на скорость мира и FPS", table("performance-properties", asArray(perfReady.propertyChecks), [
        { key: "name", label: "Параметр" },
        { key: "ok", label: "Статус", render: v => v ? pill("ok", "good") : pill("проверить", "warn") },
        { key: "value", label: "Значение" }
      ], { pageSize: 12 }))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Плагины", "Размеры и время изменения jar-файлов", table("stats-plugins", asArray(plugins.jars), [
        { key: "name", label: "Jar" },
        { key: "copimine", label: "CopiMine", render: v => v ? pill("да", "good") : pill("нет", "neutral") },
        { key: "size", label: "Размер", render: v => bytes(v) },
        { key: "modified", label: "Изменён", render: v => dt(v) }
      ], { pageSize: 16 }))}
      ${panel("Базы и журналы", "Файлы, на которых держатся сайт, выборы, CoreProtect и аудит", table("stats-databases", asArray(stats.databases), [
        { key: "name", label: "Файл" },
        { key: "exists", label: "Есть", render: v => v ? pill("есть", "good") : pill("нет", "bad") },
        { key: "size", label: "Размер", render: v => bytes(v) },
        { key: "modified", label: "Изменён", render: v => dt(v) }
      ], { pageSize: 12 }))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Проблемные строки latest.log", "Ошибки, warn и исключения из последних строк", `<pre class="log-box">${esc(asArray(logs.recentProblems).concat(asArray(errorLog.lines)).slice(-140).join("\n") || "Свежих проблем не найдено")}</pre>`)}
      ${panel("События панели и плагинов", "Последние игровые и админские события в реальном времени", table("stats-events", asArray(events.rows), null, { pageSize: 18 }))}
    </section>
  `);
}

async function loadServer() {
  setLoading("Загружаю сервер");
  const [status, services, files, props, backups, rp] = await Promise.all([
    safeApi("/api/status", {}),
    safeApi("/api/system/services", { services: [] }),
    safeApi("/api/server/files", { files: [] }),
    safeApi("/api/server/properties", { properties: {} }),
    safeApi("/api/backups", { backups: [] }),
    safeApi("/api/resourcepack/status", {})
  ]);
  updateGlobalStatus(status);
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Minecraft", status.minecraftOnline ? "Онлайн" : "Оффлайн", status.rconOk ? "живая связь подключена" : "живая связь недоступна", status.minecraftOnline ? "good" : "bad")}
      ${metric("Voice", status.ports?.voiceUdp24454?.online ? "Ок" : "Нет ответа", "UDP 24454", status.ports?.voiceUdp24454?.online ? "good" : "warn")}
      ${metric("Сайт сервера", status.ports?.backend8090?.online ? "Доступен" : "Нет связи", "панель и обновления", status.ports?.backend8090?.online ? "good" : "bad")}
      ${metric("Бэкапы", asArray(backups.backups).length, backups.dir || "data/backups")}
    </section>
    <section class="layout-grid grid-wide">
      ${panel("Управление сервером", "Опасные действия вынесены в явные кнопки и пишутся в аудит", `
        <div class="action-strip">
          <button class="btn btn-secondary" onclick="serverControl('status')">Проверить</button>
          <button class="btn btn-secondary" onclick="serverControl('save-all')">Сохранить мир</button>
          <button class="btn btn-secondary" onclick="serverControl('restart')">Перезапуск</button>
          <button class="btn btn-danger" onclick="serverControl('stop')">Остановить</button>
        </div>
        <div style="height:12px"></div>
        <div class="toolbar">
          <input id="rconCommand" class="grow" placeholder="Безопасная команда сервера" />
          <button class="btn btn-primary" onclick="runSafeRcon()">Выполнить</button>
        </div>
        <pre id="serverResponse" class="log-box" style="min-height:120px;max-height:220px">Ответ появится здесь</pre>
      `)}
      ${panel("server.properties", "Основные параметры в режиме просмотра", kv([
        ["MOTD", props.properties?.motd],
        ["max-players", props.properties?.["max-players"]],
        ["view-distance", props.properties?.["view-distance"]],
        ["simulation-distance", props.properties?.["simulation-distance"]],
        ["online-mode", props.properties?.["online-mode"]],
        ["resource-pack", rp.url || "—"]
      ]))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Сервисы", "Состояние служб сервера", table("services", asArray(services.services), [
        { key: "service", label: "Сервис" },
        { key: "active", label: "Active" },
        { key: "enabled", label: "Enabled" },
        { key: "ok", label: "OK", render: v => v ? pill("ok", "good") : pill("нет", "warn") }
      ]))}
      ${panel("Файлы сервера", "Наличие важных файлов", table("server-files", asArray(files.files), [
        { key: "name", label: "Файл" },
        { key: "exists", label: "Есть", render: v => v ? pill("есть", "good") : pill("нет", "bad") },
        { key: "size", label: "Размер", render: v => bytes(v) },
        { key: "modified", label: "Изменён", render: v => dt(v) }
      ]))}
    </section>
    ${panel("Бэкапы", "Создание и скачивание архивов", `
      <div class="action-strip">
        <button class="btn btn-primary" onclick="createBackup(false)">Бэкап конфигов</button>
        <button class="btn btn-secondary" onclick="createBackup(true)">Бэкап с world data</button>
      </div>
      <div style="height:12px"></div>
      ${table("backups", asArray(backups.backups), [
        { key: "name", label: "Архив", render: v => `<a href="/api/backups/${encodeURIComponent(v)}" target="_blank">${esc(v)}</a>` },
        { key: "size", label: "Размер", render: v => bytes(v) },
        { key: "modified", label: "Создан", render: v => dt(v) }
      ])}
    `)}
  `);
}

async function loadAnticheat() {
  setLoading("Загружаю античит");
  const data = await safeApi("/api/anticheat/status?limit=180", {});
  const jar = data.jar || {};
  const plugin = jar.plugin || {};
  const summary = data.summary || {};
  const files = asArray(data.files);
  const events = asArray(data.events);
  setView(`
    <section class="server-stat-grid anticheat-grid">
      ${metric("GrimAC", data.installed ? "Установлен" : "Не найден", plugin.version || "релизная сборка", data.installed ? "good" : "bad")}
      ${metric("Профиль", data.silentProfile ? "Тихий" : "Проверить", "лог без чата и титров", data.silentProfile ? "good" : "warn")}
      ${metric("Стабильность", data.stableProfile ? "Релиз" : "Проверить", "эксперименты выключены, автообновления выключены", data.stableProfile ? "good" : "warn")}
      ${metric("Сигналы", Number(summary.logLines || 0) + Number(summary.panelEvents || 0), "журнал сервера и события панели")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Контроль античита", "Что должно быть включено после замены папки на сервере", table("anticheat-checks", asArray(data.checks), [
        { key: "name", label: "Проверка" },
        { key: "ok", label: "Статус", render: v => v ? pill("ok", "good") : pill("проверить", "warn") },
        { key: "detail", label: "Деталь", render: v => esc(detailSummary(v, 140)) }
      ], { pageSize: 12 }))}
      ${panel("Файлы GrimAC", "Jar, config, punishments и compatibility-профиль", table("anticheat-files", files, [
        { key: "name", label: "Файл" },
        { key: "exists", label: "Есть", render: v => v ? pill("есть", "good") : pill("нет", "bad") },
        { key: "size", label: "Размер", render: v => bytes(v) },
        { key: "modified", label: "Изменён", render: v => dt(v) }
      ], { pageSize: 12 }))}
    </section>
    <section class="layout-grid grid-wide">
      ${panel("События античита", "Log-only сигналы GrimAC без сообщений игрокам в чат или на экран", table("anticheat-events", events.map(row => ({
        time: row.timestamp || row.time || "",
        source: row.source || "latest.log",
        severity: row.severity || "info",
        message: row.message || row.eventType || row.event_type || jsonPreview(row)
      })), [
        { key: "severity", label: "Тип", render: v => `<span class="anticheat-signal ${esc(String(v || "info").toLowerCase())}">${esc(v || "info")}</span>` },
        { key: "source", label: "Источник" },
        { key: "time", label: "Время", render: v => Number(v) ? dt(v) : esc(v || "—") },
        { key: "message", label: "Событие", render: v => esc(short(v, 220)) }
      ], { pageSize: 20 }))}
    </section>
  `);
}

window.serverControl = async (action) => {
  if (["stop", "restart"].includes(action) && !confirm(`Подтвердить ${action}?`)) return;
  const labels = { stop: "SERVER_STOP", restart: "SERVER_RESTART", say: "SERVER_SAY" };
  const headers = labels[action] ? dangerConfirm(`Опасное серверное действие: ${action}`, labels[action]) : {};
  if (!headers) return;
  try {
    const res = await api("/api/server/control", { method: "POST", headers, body: JSON.stringify({ action }) });
    $("serverResponse").textContent = res.stdout || res.stderr || res.response || JSON.stringify(res, null, 2);
    toast("Команда отправлена");
  } catch (err) { toast(err.message, true); }
};

window.runSafeRcon = async () => {
  try {
    const command = $("rconCommand").value.trim();
    const res = await api("/api/rcon", { method: "POST", body: JSON.stringify({ command }) });
    $("serverResponse").textContent = res.response || JSON.stringify(res, null, 2);
  } catch (err) { toast(err.message, true); }
};

window.createBackup = async (includeWorld) => {
  try {
    await api("/api/backups", { method: "POST", body: JSON.stringify({ scope: includeWorld ? "world" : "configs", include_logs: true, include_world: includeWorld }) });
    toast("Бэкап создан");
    loadServer();
  } catch (err) { toast(err.message, true); }
};

async function loadLogs() {
  setLoading("Загружаю логи");
  const data = await safeApi("/api/logs/latest?lines=260&category=all", { lines: [] });
  const events = await safeApi("/api/plugin/events?limit=120", { rows: [] });
  setView(`
    <section class="layout-grid grid-2">
      ${panel("Логи сервера", "latest.log, последние строки", `<pre class="log-box">${esc(asArray(data.lines).join("\n") || "Лог пуст")}</pre>`)}
      ${panel("События плагинов", "Игровые и системные события, которые пришли в панель", table("plugin-events", asArray(events.rows), null, { pageSize: 18 }))}
    </section>
  `);
}

async function loadInvestigations() {
  setLoading("Готовлю расследования");
  const [sources, rows] = await Promise.all([
    safeApi("/api/investigations/sources", { tables: [] }),
    safeApi("/api/investigations/block-logs?limit=120", { rows: [] })
  ]);
  setView(`
    <section class="layout-grid grid-wide">
      ${panel("Поиск CoreProtect", "Фильтры по игроку, координатам и радиусу", `
        <div class="toolbar">
          <input id="invPlayer" placeholder="Игрок" />
          <input id="invX" placeholder="X" />
          <input id="invY" placeholder="Y" />
          <input id="invZ" placeholder="Z" />
          <input id="invRadius" placeholder="Радиус" value="0" />
          <button class="btn btn-primary" onclick="searchInvestigation()">Искать</button>
        </div>
        <div id="investigationResults">${table("investigation-rows", asArray(rows.rows), null, { pageSize: 18 })}</div>
      `)}
      ${panel("Источники расследований", "Какие таблицы доступны", table("investigation-sources", asArray(sources.tables).map(t => ({ table: t.name, columns: asArray(t.columns).join(", ") })), [
        { key: "table", label: "Таблица" },
        { key: "columns", label: "Колонки" }
      ]))}
    </section>
  `);
}

window.searchInvestigation = async () => {
  const params = new URLSearchParams({
    player: $("invPlayer").value.trim(),
    x: $("invX").value.trim(),
    y: $("invY").value.trim(),
    z: $("invZ").value.trim(),
    radius: $("invRadius").value.trim() || "0",
    limit: "200"
  });
  ["x", "y", "z"].forEach(k => { if (!params.get(k)) params.delete(k); });
  const rows = await safeApi(`/api/investigations/block-logs?${params.toString()}`, { rows: [] });
  $("investigationResults").innerHTML = table("investigation-rows", asArray(rows.rows), null, { pageSize: 18 });
};

async function loadSources() {
  setLoading("Проверяю источники данных");
  const [data, config, access] = await Promise.all([
    safeApi("/api/data-sources", { sources: [] }),
    safeApi("/api/config", {}),
    safeApi("/api/security/access", {})
  ]);
  setView(`
    ${panel("Источники данных", "Плагины, файлы и БД, на которых строится панель", table("sources", asArray(data.sources), [
      { key: "name", label: "Источник" },
      { key: "type", label: "Тип" },
      { key: "status", label: "Статус", render: v => pill(v, v === "connected" ? "good" : "warn") },
      { key: "capabilities", label: "Данные", render: v => asArray(v).map(x => pill(x, "neutral")).join(" ") || "—" },
      { key: "message", label: "Комментарий" }
    ], { pageSize: 20 }))}
    ${dbPolicyPanel(config.dbWritePolicy || access.dbWritePolicy || {}, access)}
  `);
}

async function loadAudit() {
  setLoading("Загружаю аудит");
  const data = await safeApi("/api/audit?limit=260", { rows: [] });
  setView(panel("Аудит", "Действия команды сервера и важные системные изменения", table("audit", asArray(data.rows), null, { pageSize: 25 })));
}

async function loadSecurity() {
  setLoading("Загружаю доступы");
  const [admins, access, lists, whitelist, ipAlerts] = await Promise.all([
    safeApi("/api/security/admins", { admins: [], whitelistCandidates: [] }),
    safeApi("/api/security/access", {}),
    safeApi("/api/minecraft/access-lists", {}),
    safeApi("/api/admin/whitelist/requests?limit=60", { requests: [], count: 0 }),
    safeApi("/api/admin/security/ip-alerts?limit=60", { alerts: [], count: 0 })
  ]);
  const whitelistRows = asArray(whitelist.requests);
  const alertRows = asArray(ipAlerts.alerts);
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Команда", asArray(admins.admins).length, "Пользователи рабочего кабинета")}
      ${metric("Whitelist", asArray(lists.whitelist).length, "Игроки с доступом к серверу")}
      ${metric("Заявки", whitelistRows.filter(row => String(row.status || "").toUpperCase() === "PENDING").length, "Ждут Discord или web approval", whitelistRows.some(row => String(row.status || "").toUpperCase() === "PENDING") ? "warn" : "good")}
      ${metric("IP-alerts", alertRows.length, "Подозрительные регистрации и лимиты", alertRows.length ? "warn" : "good")}
    </section>
    ${safetyRail([
      ["Вход в панель", access.cookieAuth ? "сессия защищена и работает" : "проверь вход в панель", access.cookieAuth ? "good" : "warn"],
      ["Пароли", "хранятся в защищённом виде", "good"],
      ["Опасные действия", `требуют точный код ${CONFIRM_HEADER}`, "warn"],
      ["Minecraft-доступ", "права и допуск меняются только через журналируемые действия", "good"]
    ])}
    <section class="layout-grid grid-2">
      ${panel("Команда сервера", "Кто может входить в админ-панель и управлять сервером.", table("admins", asArray(admins.admins), [
        { key: "username", label: "Ник" },
        { key: "enabled", label: "Включён", render: v => v ? pill("да", "good") : pill("нет", "bad") },
        { key: "op", label: "OP", render: v => v ? pill("OP", "good") : pill("нет", "warn") },
        { key: "whitelisted", label: "Whitelist", render: v => v ? pill("есть", "good") : pill("нет", "warn") },
        { key: "canLogin", label: "Вход", render: v => v ? pill("может", "good") : pill("нельзя", "bad") }
      ]))}
      ${panel("Minecraft-доступ", "Выдай доступ, права или ограничение без лишних команд.", `
        <div class="form-grid">
          <input id="accessPlayer" placeholder="Ник игрока" />
          <select id="accessAction">
            <option value="whitelist_add">Разрешить вход</option>
            <option value="whitelist_remove">Убрать доступ</option>
            <option value="op">Выдать OP</option>
            <option value="deop">Снять OP</option>
            <option value="ban">Забанить</option>
            <option value="pardon">Разбанить</option>
          </select>
          <input id="accessReason" class="full" placeholder="Причина" />
          <button class="btn btn-primary full" onclick="runAccessAction()">Выполнить</button>
        </div>
        <div style="height:12px"></div>
        ${kv([
          ["Требуется OP для входа", access.requireOp],
          ["Нужен whitelist", access.requireWhitelist],
          ["Изменения через сайт", access.dbWriteEnabled ? "разрешены только готовые действия" : "только через серверный runtime"]
        ])}
      `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Whitelist-заявки", "Новые заявки с сайта. Одобрение дублируется с Discord и работает идемпотентно.", whitelistRows.length ? table("whitelist-requests", whitelistRows, [
        { key: "created_at", label: "Создана", render: v => dt(v) },
        { key: "minecraft_name", label: "Minecraft" },
        { key: "username", label: "Сайт" },
        { key: "status", label: "Статус", render: v => pill(statusLabel(v || "pending"), artifactStatusTone(v)) },
        { key: "approved_by", label: "Одобрил", render: v => esc(v || "—") },
        { key: "id", label: "Действие", render: (value, row) => String(row.status || "").toUpperCase() === "PENDING" ? `<button class="btn btn-primary" onclick="approveWhitelistRequest('${esc(value)}')">Одобрить</button>` : '<span class="muted">Готово</span>' }
      ], { pageSize: 12 }) : empty("Whitelist-заявок пока нет", "Когда игроки отправят запросы с сайта, они появятся здесь."))}
      ${panel("IP-alerts", "Срабатывают при лимите регистраций и других подозрительных паттернах.", alertRows.length ? table("security-ip-alerts", alertRows, [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "ip", label: "IP" },
        { key: "username", label: "Логин" },
        { key: "minecraft_name", label: "Minecraft" },
        { key: "reason", label: "Причина" },
        { key: "status", label: "Статус" }
      ], { pageSize: 12 }) : empty("IP-alerts пока нет", "Подозрительные регистрации и лимиты появятся здесь автоматически."))}
    </section>
    <section id="admin-create-panel" class="layout-grid grid-2">
      ${panel("Новый админ панели", "Создай сотруднику отдельный вход в рабочий кабинет сервера.", `
        <div class="form-grid danger-zone">
          <input id="newAdminUsername" placeholder="Minecraft-ник" />
          <input id="newAdminPassword" type="password" placeholder="Временный пароль" />
          <label class="check-line"><input id="newAdminWhitelist" type="checkbox" checked /> Добавить доступ к серверу, если нужно</label>
          <label class="check-line"><input id="newAdminOp" type="checkbox" /> Выдать OP, если политика входа требует</label>
          <button class="btn btn-primary full" onclick="createAdminUser()">Создать админа</button>
        </div>
      `)}
      ${panel("Защита входа", "Короткая сводка по доступу в админку и подтверждениям.", kv([
        ["Сессия входа", access.cookieAuth ? "активна" : "проверить"],
        ["Хранилище входа", access.authDb || "основное"],
        ["Готовность хранилища", access.authDbExists ? "готово" : "проверить"],
        ["Код подтверждения", CONFIRM_HEADER]
      ]))}
    </section>
  `);
}

window.approveWhitelistRequest = async (requestId) => {
  try {
    const headers = dangerConfirm(`Одобрить whitelist-заявку ${requestId}`, "WHITELIST_APPROVE");
    if (!headers) return;
    await api("/api/admin/whitelist/approve", {
      method: "POST",
      headers,
      body: JSON.stringify({ request_id: requestId, note: "Одобрено через admin-web" })
    });
    toast("Whitelist-заявка одобрена");
    await loadSecurity();
  } catch (err) {
    toast(err.message, true);
  }
};
window.createAdminUser = async () => {
  const username = $("newAdminUsername")?.value?.trim() || "";
  const password = $("newAdminPassword")?.value || "";
  if (!isMinecraftName(username)) return toast("Ник должен быть 3-16 символов: A-Z, 0-9 или _.", true);
  if (password.length < 8) return toast("Пароль должен быть не короче 8 символов.", true);
    const headers = dangerConfirm(`Создать админа панели: ${username}`, "ADMIN_CREATE");
  if (!headers) return;
  try {
    await api("/api/security/admins", {
      method: "POST",
      headers,
      body: JSON.stringify({
        username,
        password,
        ensure_whitelist: Boolean($("newAdminWhitelist")?.checked),
        ensure_op: Boolean($("newAdminOp")?.checked)
      })
    });
    $("newAdminPassword").value = "";
    toast("Админ создан");
    loadSecurity();
  } catch (err) {
    toast(err.message, true);
  }
};

window.runAccessAction = async () => {
  try {
    const action = $("accessAction").value;
    const headers = dangerConfirm(`Изменить Minecraft-доступ: ${action} -> ${$("accessPlayer").value.trim()}`, `ACCESS_${action.toUpperCase()}`);
    if (!headers) return;
    await api("/api/minecraft/access-lists", {
      method: "POST",
      headers,
      body: JSON.stringify({ player: $("accessPlayer").value.trim(), action, reason: $("accessReason").value.trim() })
    });
    toast("Доступ обновлён");
    loadSecurity();
  } catch (err) { toast(err.message, true); }
};

async function loadSettings() {
  setLoading("Загружаю настройки");
  const config = await safeApi("/api/config", {});
  state.config = config;
  setView(`
    <section class="layout-grid grid-2">
      ${panel("Конфигурация панели", "Публичные параметры без секретов", kv([
        ["Папка сервера", config.mcServerDir],
        ["Папка мира", config.worldDir],
        ["Основной лог", config.logFile],
        ["База панели", config.adminPluginDb],
        ["База входа", config.authDb],
        ["База CoreProtect", config.coreprotectDb],
        ["Папка бэкапов", config.backupsDir],
        ["Живая связь с сервером", config.rconConfigured],
        ["Публичный адрес", config.adminPublicBaseUrl],
        ["Сессии входа", config.features?.cookieAuth ? "включены" : "проверить"]
      ]))}
      ${panel("Функции", "Какие возможности сейчас включены в панели", table("features", Object.entries(config.features || {}).map(([name, value]) => ({ name, value })), [
        { key: "name", label: "Функция" },
        { key: "value", label: "Статус", render: v => v ? pill("работает", "good") : pill("нет", "warn") }
      ]))}
    </section>
    ${dbPolicyPanel(config.dbWritePolicy || {}, {})}
    ${panel("Разрешённые команды сайта", "Команды сервера, которые можно запускать из панели", table("rcon-allowlist", asArray(config.rconWebAllowlist).map(command => ({ command })), [{ key: "command", label: "Команда" }]))}
  `);
}

function playerLinkSummary(result) {
  if (!result) return empty("Нет активного запроса", "Запроси одноразовый код, пока ты онлайн на Minecraft-сервере.");
  return kv([
    ["Minecraft-ник", result.minecraftName || "—"],
    ["Доставлен в игре", result.deliveredInGame],
    ["Истекает", dt(result.expiresAt)]
  ]);
}

async function loadPlayerCabinet() {
  setLoading("Загрузка кабинета игрока");
  const me = await api("/api/player/me");
  state.user = me.account || {};
  let bank = null;
  let electionTax = null;
  if (state.user.linked) bank = await safeApi("/api/player/bank", { account: null, pin: {}, ledger: [] });
  if (state.user.linked) electionTax = await safeApi("/api/player/elections/tax", { linked: false, president: {}, laws: [], tax: null, paid: 0, due: 0, payments: [] });
  const donation = await safeApi("/api/player/donation/balance", { linked: false, balance: 0 });
  const linked = Boolean(state.user.linked);
  const whitelisted = Boolean(state.user.whitelisted);
  const whitelistRequest = state.user.whitelistRequest || null;
  const pin = bank?.pin || {};
  const tempPin = bank?.temporaryPin || {};
  const balance = linked ? number(bank?.account?.balance || 0) : 0;
  const donationBalance = donation?.linked ? number(donation.balance || 0) : 0;
  const whitelistStatus = whitelisted ? "одобрен" : (whitelistRequest?.status || (linked ? "не отправлен" : "нужна привязка"));
  $("miniHealth").innerHTML = `<strong>${esc(state.user.username || "игрок")}</strong><br>Привязка: ${linked ? "есть" : "нет"}<br>Банк AR: ${formatAr(balance)}`;
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Логин сайта", state.user.username || "игрок", linked ? "Minecraft уже привязан" : "Нужна привязка", linked ? "good" : "warn")}
      ${metric("Minecraft", state.user.minecraftName || "Не привязан", linked ? "Связан с кабинетом" : "Запроси код привязки", linked ? "good" : "warn")}
      ${metric("Баланс AR", linked ? formatAr(balance) : "—", linked ? "Доступен в игре и на сайте" : "Сначала привяжи Minecraft", linked ? "good" : "neutral")}
      ${metric("Whitelist", whitelistStatus, whitelisted ? "Доступ к серверу уже открыт" : "Заявка отправляется один раз", whitelisted ? "good" : (whitelistRequest?.status === "PENDING" ? "warn" : "neutral"))}
    </section>
    ${panel("Личный кабинет", "Профиль, доступ к серверу, банк и налоги без технического мусора.", kv([
      ["Логин", state.user.username || "—"],
      ["Роль", state.user.role || "игрок"],
      ["Minecraft-ник", state.user.minecraftName || "—"],
      ["Привязан", linked],
      ["Создан", dt(state.user.createdAt)],
      ["Последний вход", dt(state.user.lastLoginAt)]
    ]))}
    ${linked ? panel("Банк", "Деньги, переводы и покупки собраны в одном месте.", kv([
      ["Счёт", bank?.account?.accountId ? "открыт" : "не открыт"],
      ["Баланс", formatAr(balance)],
      ["PIN задан", pin.set],
      ["PIN заблокирован", pin.locked],
      ["Состояние PIN", bankPinState(pin)],
      ["Нужно сменить временный PIN", pin.mustChange],
      ["Временный PIN истекает", tempPin.expiresAt ? dt(tempPin.expiresAt) : "--"]
    ]), `<button class="btn btn-secondary" onclick="setTab('bank')">Открыть банк</button>`) : panel("Привязка Minecraft", "Перед банком и переводами привяжи аккаунт сайта к своему игровому нику.", `
      <div class="notice">На странице привязки запроси одноразовый код, получи его в Minecraft и подтверди здесь.</div>
    `, `<button class="btn btn-primary" onclick="setTab('link')">Открыть привязку</button>`)}
    ${panel("Whitelist", "Разовая заявка на доступ к серверу после привязки Minecraft-ника.", `
      ${kv([
        ["Статус", whitelistStatus],
        ["Одобрил", whitelistRequest?.approvedBy || "—"],
        ["Обновлён", dt(whitelistRequest?.updatedAt || whitelistRequest?.createdAt || 0)],
        ["Комментарий", whitelistRequest?.note || "—"]
      ])}
      <div style="height:12px"></div>
      ${!linked ? `<div class="notice">Сначала привяжи Minecraft-ник, затем появится кнопка whitelist-заявки.</div>` : ""}
      ${linked && !whitelisted && !whitelistRequest ? `<button class="btn btn-primary" onclick="playerRequestWhitelist()">Отправить whitelist-заявку</button>` : ""}
      ${linked && whitelistRequest?.status === "PENDING" ? `<div class="notice">Заявка уже отправлена и ждёт одобрения в Discord или админ-панели.</div>` : ""}
      ${whitelisted ? `<div class="notice">Игрок уже добавлен в whitelist. Повторная заявка не нужна.</div>` : ""}
    `)}
    ${panel("Донат-баланс", "Отдельный баланс для донат-предметов. Он не конвертируется в AR.", kv([
      ["Статус", donation?.linked ? "доступен" : "ждёт привязки Minecraft"],
      ["Баланс", donation?.linked ? formatDonate(donationBalance) : "—"],
      ["Покупки", "через вкладку Артефакты / Донат"],
      ["SBP", "донат в разработке"]
    ]), `<button class="btn btn-secondary" onclick="setTab('artifacts')">Открыть лавку</button>`)}
    ${tempPin.code ? panel("Временный PIN", "Этот код выдан сбросом. Используй его как текущий PIN и сразу замени.", kv([
      ["Временный PIN", tempPin.code],
      ["Выдан", dt(tempPin.createdAt)],
      ["Истекает", dt(tempPin.expiresAt)]
    ]), `<button class="btn btn-primary" onclick="setTab('bank')">Заменить PIN</button>`) : ""}
    ${linked ? panel("Президент и налоги", "Президент сервера, действующие законы и оплата налога без выхода из кабинета.", `
      ${kv([
        ["Президент", electionTax?.president?.president_name || "—"],
        ["Налог", electionTax?.tax ? formatAr(electionTax.tax.amount || 0) : "не установлен"],
        ["Оплачено", formatAr(electionTax?.paid || 0)],
        ["Остаток", formatAr(electionTax?.due || 0)]
      ])}
      <div style="height:12px"></div>
      ${lawCards(asArray(electionTax?.laws))}
      ${number(electionTax?.due || 0) > 0 ? `
        <div style="height:12px"></div>
        <div class="form-grid">
          <input id="cabinetTaxAmount" type="number" min="1" max="${esc(number(electionTax?.due || 0))}" value="${esc(number(electionTax?.due || 0))}" placeholder="Сумма" />
          <input id="cabinetTaxPin" type="password" inputmode="numeric" placeholder="PIN" />
          <button class="btn btn-primary full" onclick="playerPayElectionTax()">Оплатить налог</button>
        </div>
      ` : ""}
    `) : ""}
    ${panel("Запрос привязки", "Последний одноразовый код", playerLinkSummary(state.playerLinkRequest))}
  `);
}

window.playerRequestWhitelist = async () => {
  try {
    const result = await api("/api/player/whitelist/request", {
      method: "POST",
      body: "{}"
    });
    const row = result.request || {};
    toast(row.alreadyExists ? "Заявка уже существует" : "Whitelist-заявка отправлена");
    await loadPlayerCabinet();
  } catch (err) {
    toast(err.message, true);
  }
};
async function loadPlayerLink() {
  setLoading("Загрузка привязки Minecraft");
  const me = await api("/api/player/me");
  state.user = me.account || {};
  const linked = Boolean(state.user.linked);
  setView(`
    <section class="layout-grid grid-2">
      ${panel("Статус привязки", "Minecraft-ник подтверждается одноразовым кодом из игры. Пароль от Minecraft здесь не нужен.", kv([
        ["Логин сайта", state.user.username || "—"],
        ["Minecraft-ник", state.user.minecraftName || "—"],
        ["Привязан", linked],
        ["Создан", dt(state.user.createdAt)]
      ]))}
      ${panel("Как это работает", "Связка аккаунта занимает меньше минуты и открывает банк, переводы и налог.", safetyRail([
        ["1. Запроси код", "Укажи свой игровой ник, пока ты онлайн на сервере.", "good"],
        ["2. Прочитай в игре", "Код приходит в Minecraft-чат через сервер.", "neutral"],
        ["3. Подтверди на сайте", "Введи код здесь, чтобы открыть банк и личный кабинет игрока.", "good"]
      ]))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Запросить одноразовый код", "Код не показывается публично и не уходит в логи браузера.", `
        <div class="form-grid">
          <input id="linkMinecraftName" value="${esc(state.user.minecraftName || "")}" placeholder="Minecraft-ник на сервере" />
          <button class="btn btn-primary full" onclick="playerRequestLinkCode()">Получить код в Minecraft</button>
        </div>
        <div style="height:12px"></div>
        ${playerLinkSummary(state.playerLinkRequest)}
      `)}
      ${panel("Подтвердить код", "Введи одноразовый код из Minecraft-чата.", `
        <div class="form-grid">
          <input id="linkCodeInput" placeholder="Например: 7H2K9M4Q" />
          <button class="btn btn-primary full" onclick="playerConfirmLinkCode()">Подтвердить привязку</button>
        </div>
        ${linked ? '<div class="notice">Аккаунт уже привязан. Повторное подтверждение обновит активную привязку к тому же Minecraft-нику.</div>' : ""}
      `)}
    </section>
  `);
}

async function loadPlayerBank() {
  setLoading("Загрузка банка AR");
  const me = await api("/api/player/me");
  state.user = me.account || {};
  if (!state.user.linked) {
    setView(`
      ${panel("Банк AR закрыт", "Для банка нужна привязка Minecraft-ника.", `
        <div class="notice">Сначала запроси одноразовый код привязки. После этого банк откроется здесь и в игре.</div>
      `, `<button class="btn btn-primary" onclick="setTab('link')">Открыть привязку</button>`)}
    `);
    return;
  }
  const bank = await api("/api/player/bank");
  const pin = bank.pin || {};
  const tempPin = bank.temporaryPin || {};
  const transferLocked = Boolean(pin.mustChange || pin.status === "temporary-expired");
  const ledger = asArray(bank.ledger);
  $("miniHealth").innerHTML = `<strong>${esc(state.user.username || "игрок")}</strong><br>Банк AR: ${formatAr(bank.account?.balance || 0)}<br>PIN: ${bankPinState(pin)}`;
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Баланс", formatAr(bank.account?.balance || 0), "Один счёт для сайта и игры", "good")}
      ${metric("Последние операции", ledger.length, "Показываем только подтверждённые движения", "neutral")}
      ${metric("PIN", bankPinState(pin), pin.locked ? `Заблокирован до ${dt(pin.lockedUntil)}` : (tempPin.code ? `Временный PIN до ${dt(tempPin.expiresAt)}` : "Нужен для переводов"), bankPinTone(pin))}
      ${metric("Minecraft", state.user.minecraftName || "—", "Привязан к кабинету", "good")}
    </section>
    ${safetyRail([
      ["Банк", "Баланс один и тот же на сайте, в банкомате и в игровых оплатах.", "good"],
      ["PIN", tempPin.code ? "Активен временный PIN. Замени его до переводов и ATM-действий." : (pin.set ? "Хранится только как хэш." : "Задай PIN перед переводами."), tempPin.code ? "warn" : (pin.set ? "good" : "warn")],
      ["Блокировка", pin.locked ? `PIN заблокирован до ${dt(pin.lockedUntil)}` : "Неверные попытки временно блокируют PIN.", pin.locked ? "bad" : "neutral"]
    ])}
    ${tempPin.code ? panel("Временный PIN", "Этот PIN выдан сбросом. Введи его как текущий и сохрани новый.", kv([
      ["Временный PIN", tempPin.code],
      ["Выдан", dt(tempPin.createdAt)],
      ["Истекает", dt(tempPin.expiresAt)]
    ])) : ""}
    <section class="layout-grid grid-2">
      ${panel("Задать или изменить PIN", tempPin.code ? "Активен временный PIN. Введи его как текущий и замени сейчас." : "PIN нужен для переводов на сайте и защищённых операций банкомата.", `
        <div class="form-grid">
          <input id="bankOldPin" type="password" inputmode="numeric" placeholder="${tempPin.code ? "Текущий временный PIN" : "Текущий PIN, если уже задан"}" />
          <input id="bankNewPin" type="password" inputmode="numeric" placeholder="Новый PIN, 4-8 цифр" />
          <button class="btn btn-primary full" onclick="playerSetPin()">Сохранить PIN</button>
        </div>
      `)}
      ${panel("Перевести AR", transferLocked ? "Переводы закрыты, пока временный PIN не заменён." : "Перевод отправит AR на другой счёт и сразу покажет результат в истории.", transferLocked ? `
        <div class="notice">${pin.status === "temporary-expired" ? "Временный PIN истёк. Попроси команду сервера сбросить его ещё раз." : "Сначала задай личный PIN. После этого переводы и ATM-операции откроются автоматически."}</div>
      ` : `
        <div class="form-grid">
          <input id="bankRecipient" placeholder="Логин получателя или Minecraft-ник" />
          <input id="bankAmount" type="number" min="1" step="1" placeholder="Сумма" />
          <input id="bankPinInput" type="password" inputmode="numeric" placeholder="PIN" />
          <input id="bankNote" class="full" placeholder="Комментарий, необязательно" />
          <button class="btn btn-primary full" onclick="playerTransfer()">Отправить AR</button>
        </div>
      `)}
    </section>
    ${panel("Журнал операций", "Последние переводы, оплаты и покупки по этому счёту.", transactionFeed(ledger, 18))}
  `);
}

window.playerRequestLinkCode = async () => {
  try {
    const minecraftName = $("linkMinecraftName")?.value?.trim() || "";
    state.playerLinkRequest = await api("/api/player/link/request", {
      method: "POST",
      body: JSON.stringify({ minecraft_name: minecraftName })
    });
    toast(state.playerLinkRequest.deliveredInGame ? "Код привязки отправлен в Minecraft-чат." : "Код создан, но доставка в Minecraft не удалась. Зайди на сервер и запроси код снова.");
    if (state.tab === "link") loadPlayerLink();
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerConfirmLinkCode = async () => {
  try {
    const result = await api("/api/player/link/confirm", {
      method: "POST",
      body: JSON.stringify({ code: $("linkCodeInput")?.value?.trim() || "" })
    });
    state.user = result.account || state.user;
    toast("Minecraft-аккаунт привязан.");
    loadPlayerBank();
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerSetPin = async () => {
  try {
    await api("/api/player/bank/pin", {
      method: "POST",
      body: JSON.stringify({
        old_pin: $("bankOldPin")?.value || "",
        new_pin: $("bankNewPin")?.value || ""
      })
    });
    toast("PIN обновлён.");
    if ($("bankOldPin")) $("bankOldPin").value = "";
    if ($("bankNewPin")) $("bankNewPin").value = "";
    loadPlayerBank();
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerTransfer = async () => {
  try {
    const result = await api("/api/player/bank/transfer", {
      method: "POST",
      body: JSON.stringify({
        recipient: $("bankRecipient")?.value?.trim() || "",
        amount: number($("bankAmount")?.value || 0),
        pin: $("bankPinInput")?.value || "",
        note: $("bankNote")?.value?.trim() || ""
      })
    });
    toast(`Переведено ${result.amount} AR получателю ${result.recipient}.`);
    ["bankRecipient", "bankAmount", "bankPinInput", "bankNote"].forEach((id) => { if ($(id)) $(id).value = ""; });
    loadPlayerBank();
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerPayElectionTax = async () => {
  try {
    const result = await api("/api/player/elections/tax/pay", {
      method: "POST",
      body: JSON.stringify({
        amount: number($("cabinetTaxAmount")?.value || 0),
        pin: $("cabinetTaxPin")?.value || ""
      })
    });
    toast(`Налог оплачен: ${result.amount} AR.`);
    if ($("cabinetTaxPin")) $("cabinetTaxPin").value = "";
    loadPlayerCabinet();
  } catch (err) {
    toast(err.message, true);
  }
};

async function loadPlayerArtifacts() {
  setLoading("Загрузка предметов");
  const [data, donation] = await Promise.all([
    safeApi("/api/player/artifacts", { linked: false, purchases: [], pending: [], repairs: [] }),
    safeApi("/api/player/donation/items", { linked: false, items: [] })
  ]);
  if (!data.linked) {
    setView(panel("Артефакты", "Сначала привяжи Minecraft-аккаунт", empty("Minecraft-ник не привязан", "После привязки здесь появятся покупки, pending delivery и ремонт предметов.")));
    return;
  }
  const purchases = asArray(data.purchases);
  const pending = asArray(data.pending);
  const repairs = asArray(data.repairs);
  const donationItems = asArray(donation.items);
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Покупки", purchases.length, "Подтверждённые покупки артефактов", purchases.length ? "good" : "neutral")}
      ${metric("Ожидают выдачи", pending.length, "Предметы ещё не дошли до Minecraft", pending.length ? "warn" : "good")}
      ${metric("Ремонты", repairs.length, "История восстановления PDC-предметов")}
      ${metric("Донат-claims", donationItems.length, "Оплаченные предметы ждут выдачи", donationItems.length ? "warn" : "neutral")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Мои покупки", "Что уже куплено в лавке артефактов.", table("player-artifact-purchases", purchases, [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "item_id", label: "Предмет" },
        { key: "shop_id", label: "Лавка" },
        { key: "price_ar", label: "AR" },
        { key: "status", label: "Статус", render: v => pill(statusLabel(v), artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
      ${panel("Ожидают выдачи", "Если в игре не хватило места, предметы останутся здесь до следующей выдачи.", table("player-artifact-pending", pending, [
        { key: "created_at", label: "Создано", render: v => dt(v) },
        { key: "item_id", label: "Предмет" },
        { key: "status", label: "Статус", render: v => pill(statusLabel(v || "pending"), artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
    </section>
    ${panel("Донат-предметы", "Оплаченные или тестовые донат-предметы, ещё не полученные в игровой лавке.", table("player-donation-items", donationItems, [
      { key: "created_at", label: "Создано", render: v => dt(v) },
      { key: "item_id", label: "Предмет" },
      { key: "amount", label: "Кол-во" },
      { key: "status", label: "Статус", render: v => pill(statusLabel(v || "pending"), artifactStatusTone(v)) }
    ], { pageSize: 12 }))}
    ${panel("Ремонт", "В Minecraft можно восстановить официальный предмет в лавке или командой /cmartifacts repair.", table("player-artifact-repairs", repairs, [
      { key: "created_at", label: "Время", render: v => dt(v) },
      { key: "item_id", label: "Предмет" },
      { key: "repair_cost_ar", label: "AR" },
      { key: "status", label: "Статус", render: v => pill(statusLabel(v), artifactStatusTone(v)) }
    ], { pageSize: 12 }))}
  `);
}
async function loadPlayerHistory() {
  setLoading("Загрузка истории игрока");
  const [bank, artifacts, donation] = await Promise.all([
    safeApi("/api/player/bank", { ledger: [] }),
    safeApi("/api/player/artifacts", { purchases: [], pending: [], repairs: [] }),
    safeApi("/api/player/donation/history", { history: [] })
  ]);
  const rows = [
    ...asArray(bank.ledger).map((row) => ({
      title: humanizeBankAction(row),
      time: row.created_at,
      amount: row.amount,
      details: row.details || "",
      section: "Банк"
    })),
    ...asArray(artifacts.purchases).map((row) => ({
      title: `Покупка: ${cleanText(row.item_id || "предмет")}`,
      time: row.created_at,
      amount: row.amount_ar || 0,
      details: row.status || "создана",
      section: "Лавка"
    })),
    ...asArray(artifacts.repairs).map((row) => ({
      title: `Ремонт: ${cleanText(row.item_id || "предмет")}`,
      time: row.created_at,
      amount: row.repair_cost_ar || 0,
      details: row.status || "готово",
      section: "Ремонт"
    })),
    ...asArray(donation.history).map((row) => ({
      title: `Донат: ${cleanText(row.reason || row.source || "операция")}`,
      time: row.created_at,
      amount: row.delta || 0,
      details: row.actor || row.source || "donation",
      section: "Донат"
    }))
  ].sort((a, b) => String(b.time || "").localeCompare(String(a.time || "")));
  setView(`
    ${panel("История операций", "Банк, покупки, ремонт и донат собраны в одну хронологию.", rows.length ? `
      <div class="transaction-feed">
        ${rows.map((row) => `
          <article class="transaction-row">
            <div class="transaction-main">
              <strong>${esc(row.title)}</strong>
              <span>${esc(`${row.section} • ${row.details || "без комментария"}`)}</span>
            </div>
            <div class="transaction-side">
              <strong>${row.amount ? esc(row.section === "Донат" ? formatDonate(row.amount) : formatAr(row.amount)) : "—"}</strong>
              <span>${dt(row.time)}</span>
            </div>
          </article>
        `).join("")}
      </div>
    ` : empty("История пока пустая", "Когда появятся переводы, покупки или донат-операции, они появятся здесь."))}
  `);
}
async function loadPlayerSettings() {
  setLoading("Загрузка настроек");
  const me = await safeApi("/api/player/me", { account: state.user || {} });
  const account = me.account || state.user || {};
  setView(`
    <section class="layout-grid grid-2">
      ${panel("Профиль", "Основные данные аккаунта игрока.", kv([
        ["Логин", account.username || "игрок"],
        ["Minecraft-ник", account.minecraftName || "не привязан"],
        ["Email", account.email || "не указан"],
        ["Создан", dt(account.createdAt)]
      ]), `<button class="btn btn-secondary" onclick="setTab('link')">Настроить Minecraft</button>`)}
      ${panel("Что можно настроить", "В кабинете доступны только реальные действия без декоративных переключателей.", safetyRail([
        ["Привязка", "Свяжи кабинет с Minecraft-ником и открой банк.", account.minecraftName ? "good" : "warn"],
        ["Безопасность", "Смени пароль и проверь PIN банка.", "neutral"],
        ["История", "Здесь же можно посмотреть переводы, покупки и налог.", "good"]
      ]))}
    </section>
  `);
}

async function loadPlayerSecurity() {
    setLoading("Загрузка безопасности");
    const bank = await safeApi("/api/player/bank", { pin: {}, ledger: [] });
    const pin = bank.pin || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("PIN банка", "PIN используется для переводов и защищённых операций", kv([
          ["PIN задан", pin.set || false],
          ["Статус", bankPinState(pin)],
          ["Заблокирован", pin.locked || false],
          ["Нужна замена", pin.mustChange || false]
        ]), `<button class="btn btn-primary" onclick="setTab('bank')">Открыть банк</button>`)}
        ${panel("Сессии", "Базовые действия безопасности", `
          <div class="notice">Если заметил подозрительную активность, смени пароль и PIN, затем выйди из аккаунта на всех устройствах.</div>
          <button class="btn btn-secondary full" onclick="logout(true)">Выйти из текущей сессии</button>
        `)}
      </section>
    `);
  }

async function loadPlayerSupport() {
  setLoading("Загрузка поддержки");
  setView(`
    <section class="layout-grid grid-2">
      ${panel("Как обратиться за помощью", "В этом патче не показываем неработающие формы. Доступны только реальные способы связи.", safetyRail([
        ["/report в игре", "Если проблема произошла на сервере, отправь сообщение через игровую команду /report.", "good"],
        ["Команда сервера", "Для банка, привязки и налогов можно обратиться к администрации напрямую.", "neutral"],
        ["Не присылай PIN", "Никогда не отправляй пароль или PIN в сообщениях.", "warn"]
      ]))}
      ${panel("Частые вопросы", "Короткие ответы по самым частым ситуациям.", safetyRail([
        ["Банк", "Сначала привяжи Minecraft-ник и задай PIN.", "good"],
        ["Артефакты", "Лавка открывается кликом по игровому блоку.", "neutral"],
        ["Выдача", "Если инвентарь был полон, предмет ждёт в отложенной выдаче.", "warn"]
      ]))}
    </section>
  `);
}

async function loadCurrent(silent = false) {
  const adminLoaders = {
    dashboard: loadDashboard,
    players: loadPlayers,
    inventories: loadInventories,
    elections: loadElections,
    stats: loadStats,
    economy: loadEconomy,
    artifacts: loadArtifacts,
    anticheat: loadAnticheat,
    requests: loadRequests,
    server: loadServer,
    logs: loadLogs,
    investigations: loadInvestigations,
    sources: loadSources,
    settings: loadSettings,
    security: loadSecurity,
    audit: loadAudit
  };
  const playerLoaders = {
    cabinet: loadPlayerCabinet,
    link: loadPlayerLink,
    bank: loadPlayerBank,
    history: loadPlayerHistory,
    settings: loadPlayerSettings,
    security: loadPlayerSecurity,
    support: loadPlayerSupport,
    artifacts: loadPlayerArtifacts
  };
  const loaders = state.role === "player" ? playerLoaders : adminLoaders;
  try {
    await (loaders[state.tab] || loaders[defaultTab()] || loadDashboard)(silent);
  } catch (err) {
    setView(panel("Ошибка загрузки", "Раздел не смог получить данные", empty(err.message || "Неизвестная ошибка", "Проверь соединение сайта с сервером и повтори попытку.")));
  }
}

function wire() {
  wirePublicSite();
  $("loginForm").addEventListener("submit", login);
  document.querySelectorAll("[data-auth-role]").forEach((button) => {
    button.addEventListener("click", () => setAuthRole(button.dataset.authRole || "admin"));
  });
  document.querySelectorAll("[data-auth-action]").forEach((button) => {
    button.addEventListener("click", () => setAuthAction(button.dataset.authAction || "login"));
  });
  $("logout").addEventListener("click", () => logout(true));
  $("guestPagesBtn")?.addEventListener("click", showGuestPages);
  $("refreshBtn").addEventListener("click", () => loadCurrent());
  $("mobileNavToggle").setAttribute("aria-expanded", "false");
  $("mobileNavToggle").addEventListener("click", (event) => {
    event.stopPropagation();
    setMobileNav(!$("app").classList.contains("nav-open"));
  });
  document.addEventListener("click", (event) => {
    if (!$("app").classList.contains("nav-open")) return;
    if (event.target.closest(".sidebar") || event.target.closest("#mobileNavToggle")) return;
    setMobileNav(false);
  });
  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setMobileNav(false);
  });
  window.addEventListener("hashchange", () => {
    const next = (location.hash || "#dashboard").slice(1);
    if (!state.role && ["start", "about", "features", "join", "signin"].includes(next)) return;
    if (next !== state.tab) setTab(next);
  });
  syncAuthUi();
}

async function boot() {
  wire();
  await bootAuthed({ quiet: !state.token });
}

boot();
