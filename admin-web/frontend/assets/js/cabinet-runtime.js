import { getStoredUiState, removeStoredUiState, setStoredUiState } from "./shared/browser-state.js";
import { fragmentFromHtml, makeElement, replaceChildrenSafe } from "./shared/dom.js";
import { createAdminCmsPages } from "./admin/cms-pages.js";
import { createAdminCommercePages } from "./admin/commerce-pages.js";
import { createAdminNarcoticsRecipePages } from "./admin/narcotics-recipe-pages.js";
import { createPluginRegistryPages } from "./admin/plugin-registry-pages.js";
import { createPlayerAccountPages } from "./player/account-pages.js";
import { createPlayerArtifactPages } from "./player/artifact-pages.js";
import { createPlayerDonationPages } from "./player/donation-pages.js";
import { createPlayerTreasuryPages } from "./player/treasury-pages.js";
import { appRouteHref, authLandingHref, defaultAppRouteForRole, normalizeAppRoute, routeFromHref } from "./shared/app-routes.js";

const $ = (id) => document.getElementById(id);

function setClickAction(node, action) {
  if (node && action) node.dataset.click = action;
  return node;
}

function setAttributes(node, attrs = {}) {
  if (!node) return node;
  Object.entries(attrs).forEach(([key, value]) => {
    if (value === null || value === undefined || value === false) return;
    if (value === true) {
      node.setAttribute(key, "");
      return;
    }
    node.setAttribute(key, String(value));
  });
  return node;
}

function appendChildren(node, children = []) {
  if (!node) return node;
  const normalized = Array.isArray(children) ? children : [children];
  normalized.filter(Boolean).forEach((child) => node.append(child));
  return node;
}

function makeButton(label, className, action, attrs = {}) {
  const button = makeElement("button", className, label);
  setAttributes(button, { type: "button", ...attrs });
  return setClickAction(button, action);
}

function buildModalOverlay() {
  return setClickAction(makeElement("div", "modal-overlay"), "if(event.target===this) closeModal()");
}

function buildModalShell(title, subtitle = "", options = {}) {
  const modal = makeElement("div", options.wide ? "modal modal-wide" : "modal");
  const head = makeElement("div", "modal-head");
  const copy = makeElement("div");
  copy.append(makeElement("h2", "", title));
  if (subtitle) copy.append(makeElement("p", "", subtitle));
  head.append(copy);
  if (options.closeLabel) {
    head.append(makeButton(options.closeLabel, "btn btn-secondary", "closeModal()"));
  }
  modal.append(head);
  return modal;
}

function parseHashRoute(hashValue) {
  const raw = String(hashValue || "").replace(/^#/, "");
  const [tabRaw = "", queryRaw = ""] = raw.split("?", 2);
  return {
    tab: tabRaw || "dashboard",
    params: new URLSearchParams(queryRaw || "")
  };
}

function currentPageKind() {
  return String(document.body?.dataset.pageKind || "").trim().toLowerCase() || "signin";
}

function currentAuthFlow() {
  return String(document.body?.dataset.authFlow || "").trim().toLowerCase() || "login";
}

function isCabinetPage() {
  return currentPageKind() === "cabinet";
}

function isAuthLandingPage() {
  const kind = currentPageKind();
  return kind === "signin" || kind === "register";
}

function isRegisterPage() {
  return currentAuthFlow() === "register" || currentPageKind() === "register";
}

function parseInitialRouteState() {
  const fromHash = parseHashRoute(location.hash);
  const bodyRoute = normalizeAppRoute(document.body?.dataset.appRoute || routeFromHref(location.pathname), fromHash.tab || "dashboard");
  const params = new URLSearchParams(window.location.search || "");
  if (!params.has("session") && fromHash.params.get("session")) {
    params.set("session", fromHash.params.get("session"));
  }
  if (!params.has("item") && fromHash.params.get("item")) {
    params.set("item", fromHash.params.get("item"));
  }
  return {
    tab: bodyRoute,
    params,
  };
}

const initialRouteState = parseInitialRouteState();

const state = {
  token: "",
  role: "",
  fullAccess: false,
  owner: false,
  authRole: "",
  authAction: isRegisterPage() ? "register" : "login",
  cookieAuth: false,
  tab: initialRouteState.tab || "dashboard",
  user: null,
  config: null,
  selectedPlayer: "",
  players: [],
  tables: {},
  refreshTimer: null,
  refreshPromise: null,
  liveStream: null,
  liveLastEvent: 0,
  playerLinkRequest: null,
  publicStatus: null,
  publicConfig: null,
  modalResolver: null,
  donationSessionId: initialRouteState.params.get("session") || getStoredUiState("copimineDonationSessionId", "") || "",
  donationFocusItemId: String(initialRouteState.params.get("item") || "").trim().toLowerCase(),
  donationBusy: false,
  playerBankScope: getStoredUiState("copiminePlayerBankScope", "PERSONAL") || "PERSONAL",
  adminSearchQuery: "",
  adminSearchTarget: "",
  adminSearchNeedle: "",
  adminSearchHighlightTimer: null
};

const PUBLIC_GUEST_HASH_ROUTES = new Set([
  "start",
  "features",
  "rules",
  "help",
  "servers",
  "join",
  "signin",
  "mods",
  "shops",
  "tops",
  "presidentBudgetShowcase",
  "treasuryHistorySection",
  "cabinet-zones"
]);

const dataClickHandlers = Object.create(null);
const dataInputHandlers = Object.create(null);
const fromWindow = (name) => (...args) => window[name]?.(...args);

const CONFIRM_HEADER = "X-Copimine-Confirm";
const CSRF_COOKIE = "cm_csrf";
const CSRF_HEADER = "X-CSRF-Token";

document.addEventListener("error", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLImageElement)) return;
  if (target.closest(".avatar-badge")) {
    target.remove();
    return;
  }
  target.style.display = "none";
}, true);

const publicFeatures = {
  bank: {
    kicker: "Банк AR",
    title: "Банк, переводы и покупки",
    text: "Один счёт для банка, сайта и игровых оплат.",
    icon: "/assets/mc-icons/item/diamond_ore.png"
  },
  elections: {
    kicker: "Выборы",
    title: "ЦИК, бюллетени и результаты",
    text: "Заявки, участки, бюллетени и подсчёт проходят через игровые интерфейсы.",
    icon: "/assets/mc-icons/item/writable_book.png"
  },
  president: {
    kicker: "Президент",
    title: "Законы, обращения и казна",
    text: "Президент ведёт срок, законы и казну.",
    icon: "/assets/mc-icons/item/nether_star.png"
  },
  artifacts: {
    kicker: "Артефакты",
    title: "Официальные предметы за AR",
    text: "AR-лавка хранит покупки, ремонт и историю предметов.",
    icon: "/assets/mc-icons/item/netherite_sword.png"
  },
  donation: {
    kicker: "Донат",
    title: "Donation-баланс и выдача в игре",
    text: "Пополнение идёт в отдельный donation-баланс, выдача проходит в игре.",
    icon: "/assets/mc-icons/item/nether_star.png"
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
      ["artifacts", "Лавки артефактов", "Каталог, покупки и выдача", "А"],
      ["elections", "Выборы", "ЦИК, президент и результаты", "В"]
    ]
  },
  {
    title: "Контроль",
    items: [
      ["requests", "Заявки", "Обращения и жалобы", "З"],
      ["inventories", "Инвентари", "Снимки и сравнение", "С"],
      ["investigations", "Расследования", "CoreProtect и проверка действий", "Р"],
      ["anticheat", "Античит", "GrimAC и нарушения", "А"],
      ["logs", "Логи", "Сервер и события", "Л"],
      ["audit", "Аудит", "Действия команды", "Д"]
    ]
  },
  {
    title: "Система",
    items: [
      ["server", "Службы", "Связь с миром и сервисами", "S"],
      ["admins", "Админы", "Аккаунты команды и доступ", "А"],
      ["security", "Безопасность", "Права, сессии и доступ", "Б"],
      ["sources", "Источники", "Плагины, файлы и реестр", "И"],
      ["narcotics-recipes", "Рецепты", "Котёл и ингредиенты", "Р"],
      ["cms", "CMS", "Тексты, баннеры и страницы", "C"],
      ["settings", "Настройки", "Конфигурация панели", "Н"]
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
      ["cabinet", "Кабинет", "Сводка аккаунта", "К"],
      ["bank", "Банк AR", "Баланс, PIN и переводы", "Б"],
      ["donation-balance", "Донат-баланс", "Пополнения и история", "D"],
      ["donation-shop", "Донат-лавка", "Покупка предметов", "Л"],
      ["donation-items", "Мои предметы", "Выдача и активные покупки", "П"],
      ["history", "История", "Банк, лавка и операции", "И"],
      ["settings", "Аккаунт", "Профиль и интерфейс", "А"],
      ["security", "Безопасность", "Пароль, PIN и сессии", "Б"],
      ["support", "Репорты", "Обращения и жалобы", "Р"],
      ["artifacts", "Артефакты", "Покупки и выдача", "А"],
      ["link", "Minecraft", "Привязка аккаунта", "M"]
    ]
  }
];

const playerPageMeta = Object.fromEntries(
  playerNavGroups.flatMap(group => group.items.map(([id, title, subtitle]) => [id, { title, subtitle }]))
);

const adminSearchAliases = {
  dashboard: "главная обзор статус tps mspt онлайн сервер состояние",
  players: "игрок игроки ник бан кик варн эффекты наркотики очистить баланс",
  economy: "банк ar ар ары баланс казна перевод банкомат atm pin пин",
  artifacts: "лавка лавки артефакты магазин предметы покупка выдача ремонт удалить блок витрина",
  elections: "выборы цик председатель председ предмедатель президент бюллетень участок законы мандат кандидат кандидаты книга заявка",
  requests: "заявки обращения жалобы книга рассмотреть одобрить отклонить discord дискорд",
  "narcotics-recipes": "рецепты наркотики нарко котел котёл ингредиенты варка фета кола гирион сбп жужево смесь",
  cms: "cms контент новости баннеры правила faq картинки страницы",
  admins: "админы команда доступ регистрация роли младший owner",
  security: "безопасность csrf сессии whitelist вайтлист доступ ip",
  server: "сервер ресурспак модпак nginx сервисы rcon",
  sources: "плагины конфиги yaml настройки registry реестр",
  logs: "логи журнал ошибки события",
  anticheat: "античит grimac читы нарушения",
  settings: "настройки пароль тема аккаунт"
};

const adminSearchSectionItems = [
  { id: "economy", target: "economy-treasury", title: "Казна и счета", subtitle: "Казна, банковые счета и переводы", group: "Банк и AR", haystack: "казна счета ar переводы treasury account bank", focusNeedle: "Казна" },
  { id: "economy", target: "economy-treasury-pin", title: "PIN казны", subtitle: "Настройка PIN казны", group: "Банк и AR", haystack: "pin казны treasury pin казна пароль банка", focusNeedle: "PIN казны" },
  { id: "artifacts", target: "artifacts-shop", title: "Лавка артефактов", subtitle: "Каталог, покупки и выдача", group: "Лавки", haystack: "лавка артефакты магазин покупки выдача витрина", focusNeedle: "Лавки артефактов" },
  { id: "elections", target: "elections-cik", title: "Участки и ЦИК", subtitle: "Участки, председатели и бюллетени", group: "Выборы", haystack: "цик участки председатели бюллетени seals chairs stations", focusNeedle: "Участки" },
  { id: "elections", target: "elections-laws", title: "Законы и указы", subtitle: "Законы, указы и мандат", group: "Выборы", haystack: "законы указы мандат decrees petitions", focusNeedle: "Законы" },
  { id: "requests", target: "requests-applications", title: "Заявки игроков", subtitle: "Заявки, жалобы и очередь", group: "Контроль", haystack: "заявки жалобы репорты обращения очередь discord", focusNeedle: "Заявки" },
  { id: "players", target: "players-profile", title: "Профиль игрока", subtitle: "Профиль, инвентарь и действия", group: "Игроки", haystack: "игрок профиль инвентарь действия эффекты timeline", focusNeedle: "Игроки" },
  { id: "security", target: "security-access", title: "Доступ и сессии", subtitle: "Доступ, сессии и защита", group: "Безопасность", haystack: "доступ сессии csrf ip security auth", focusNeedle: "Доступ" },
  { id: "narcotics-recipes", target: "recipes-editor", title: "Редактор рецептов", subtitle: "Котёл, ингредиенты и результат", group: "Наркотики", haystack: "рецепты котёл варка ингредиенты жужево editor", focusNeedle: "Рецепты" },
  { id: "cms", target: "cms-content", title: "CMS и баннеры", subtitle: "Тексты, баннеры и страницы", group: "CMS", haystack: "cms баннеры тексты страницы новости faq", focusNeedle: "CMS" },
  { id: "settings", target: "settings-site", title: "Настройки сайта", subtitle: "Публичные параметры и конфиги", group: "Система", haystack: "настройки сайт конфиг resourcepack modpack", focusNeedle: "Настройки" },
];

function fuzzyContains(text, query) {
  const normalized = cleanText(text).toLowerCase().replace(/ё/g, "е");
  const needle = cleanText(query).toLowerCase().replace(/ё/g, "е");
  if (!needle) return true;
  if (normalized.includes(needle)) return true;
  const compact = normalized.replace(/[^a-zа-я0-9]/g, "");
  const compactNeedle = needle.replace(/[^a-zа-я0-9]/g, "");
  if (compact.includes(compactNeedle)) return true;
  if (compactNeedle.length < 4) return false;
  let hits = 0;
  for (const part of compactNeedle.match(/.{1,3}/g) || []) {
    if (compact.includes(part)) hits++;
  }
  return hits >= Math.max(1, Math.ceil(compactNeedle.length / 6));
}

function adminSearchItems() {
  const tabs = navGroups.flatMap(group => group.items.map(([id, title, subtitle]) => ({
    id,
    target: "",
    focusNeedle: title,
    title,
    subtitle,
    group: group.title,
    haystack: `${title} ${subtitle} ${group.title} ${adminSearchAliases[id] || ""}`
  })));
  return [...tabs, ...adminSearchSectionItems];
}

const juniorNavGroups = [
  {
    title: "Контроль",
    items: [
      ["dashboard", "Обзор", "Сводка сервера", "О"],
      ["players", "Игроки", "Профили и история без опасных действий", ""],
      ["stats", "Статистика", "TPS, MSPT и ресурсы", "С"],
      ["inventories", "Инвентари", "Снимки и сравнение", "С"],
      ["investigations", "Расследования", "CoreProtect", "Р"],
      ["anticheat", "Античит", "GrimAC и нарушения", "А"],
      ["logs", "Логи", "Сервер и события", "Л"],
      ["audit", "Аудит", "Действия команды", "А"]
    ]
  }
];

if (Array.isArray(juniorNavGroups?.[0]?.items) && !juniorNavGroups[0].items.some(([id]) => id === "admins")) {
  juniorNavGroups[0].items.splice(2, 0, ["admins", "Админы", "Состав команды и ограниченный обзор", "А"]);
}

const juniorPageMeta = Object.fromEntries(
  juniorNavGroups.flatMap(group => group.items.map(([id, title, subtitle]) => [id, { title, subtitle }]))
);

function isPlayerRole(role = state.role) {
  return role === "player";
}

function isJuniorAdminRole(role = state.role) {
  return role === "junior_admin";
}

function isPanelAdminRole(role = state.role) {
  return Boolean(role) && role !== "player";
}

function hasFullAdminAccess(role = state.role) {
  return role === "admin" || role === "owner";
}

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

function playerChoiceRows(rows) {
  return asArray(rows)
    .map((row) => cleanText(row.name || row.username || row.player || row.minecraft_name || row.minecraftName || row.uuid))
    .filter(isMinecraftName)
    .filter((name, index, all) => all.indexOf(name) === index)
    .sort((a, b) => a.localeCompare(b, "ru"));
}

function playerDatalistHtml(id, rows) {
  const options = playerChoiceRows(rows).map((name) => `<option value="${esc(name)}"></option>`).join("");
  return `<datalist id="${esc(id)}">${options}</datalist>`;
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
    created: "Создана",
    paid: "Оплачено",
    cancelled: "Отменено",
    expired: "Истекла",
    claim_pending: "Ждёт выдачи",
    unclaimed: "Можно забрать",
    reserved: "Резервируется",
    delivering: "Выдаётся",
    delivery_review: "Проверка",
    lost_reclaimable: "Можно вернуть",
    replaced_after_loss: "Заменён после потери",
    broken: "Сломан",
    consumed: "Израсходован",
    deleted_as_invalid: "Недействителен",
    finished: "Завершены",
    closed: "Закрыты",
    not_connected: "Нет связи",
    disconnected: "Нет связи",
    missing: "Нет данных",
    none: "Пауза"
  };
  return labels[raw] || (raw ? cleanText(value).replaceAll("_", " ") : fallback);
}

function donationSessionKey(session) {
  return String(session?.session_id || session?.id || "");
}

function sourceLabel(value) {
  if (!value || typeof value !== "object") return cleanText(value || "-");
  const name = value.name || value.path || "источник";
  const state = value.exists === false ? "не найден" : value.exists === true ? "найден" : "не проверен";
  const size = value.size ? ` · ${bytes(value.size)}` : "";
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

function getCookie(name) {
  const prefix = `${name}=`;
  return document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

function isUnsafeMethod(method) {
  return ["POST", "PUT", "PATCH", "DELETE"].includes(String(method || "GET").toUpperCase());
}

function splitDataClickArgs(raw) {
  const args = [];
  let current = "";
  let quote = "";
  for (let i = 0; i < raw.length; i += 1) {
    const ch = raw[i];
    if (quote) {
      current += ch;
      if (ch === "\\" && i + 1 < raw.length) {
        current += raw[i + 1];
        i += 1;
        continue;
      }
      if (ch === quote) quote = "";
      continue;
    }
    if (ch === "'" || ch === "\"") {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === ",") {
      args.push(current.trim());
      current = "";
      continue;
    }
    current += ch;
  }
  if (current.trim()) args.push(current.trim());
  return args;
}

function parseDataClickArg(token) {
  if (token === "true") return true;
  if (token === "false") return false;
  if (token === "null") return null;
  if (/^-?\d+(?:\.\d+)?$/.test(token)) return Number(token);
  if ((token.startsWith("'") && token.endsWith("'")) || (token.startsWith("\"") && token.endsWith("\""))) {
    return token
      .slice(1, -1)
      .replace(/\\\\/g, "\\")
      .replace(/\\'/g, "'")
      .replace(/\\"/g, "\"");
  }
  return token;
}

function parseDataClickInvocation(expression) {
  const raw = String(expression || "").trim();
  if (!raw) return null;
  if (raw === "if(event.target===this) closeModal()") {
    return { special: "closeModalOnOverlay" };
  }
  const match = raw.match(/^([A-Za-z0-9_$.]+)\((.*)\)$/);
  if (!match) return null;
  return {
    fn: match[1],
    args: splitDataClickArgs(match[2]).map(parseDataClickArg)
  };
}

function wireDataClickDelegation() {
  document.addEventListener("click", (event) => {
    const node = event.target.closest("[data-click]");
    if (!node) return;
    const parsed = parseDataClickInvocation(node.getAttribute("data-click"));
    if (!parsed) return;
    if (parsed.special === "closeModalOnOverlay") {
      if (event.target === node && typeof window.closeModal === "function") window.closeModal();
      return;
    }
    const handler = dataClickHandlers[parsed.fn] || window[parsed.fn];
    if (typeof handler !== "function") {
      console.warn("Unknown data-click handler", parsed.fn);
      return;
    }
    handler(...parsed.args);
  });
}

function handleDataInputEvent(event) {
  const node = event.target.closest("[data-input]");
  if (!node) return;
  const action = node.getAttribute("data-input");
  if (action === "filterTable") {
    const handler = dataInputHandlers.filterTable || window.filterTable;
    handler?.(node.getAttribute("data-input-id") || "", node.value || "");
    return;
  }
  if (action === "filterPlayers") {
    const handler = dataInputHandlers.filterPlayers || window.filterPlayers;
    handler?.(node.value || "");
    return;
  }
  const handler = dataInputHandlers[action] || window[action];
  if (typeof handler === "function") handler(node.value || "", node);
}

function wireDataInputDelegation() {
  document.addEventListener("input", handleDataInputEvent);
  document.addEventListener("change", handleDataInputEvent);
}

async function refreshCsrfCookie() {
  try {
    await fetch(`/api/auth/csrf?_fresh=${Date.now()}`, {
      method: "GET",
      cache: "no-store",
      credentials: "include"
    });
  } catch {}
}

async function tryRefreshSession() {
  if (state.refreshPromise) return state.refreshPromise;
  state.refreshPromise = (async () => {
    const endpoints = state.role === "player"
      ? ["/api/player/refresh", "/api/auth/refresh"]
      : ["/api/auth/refresh", "/api/player/refresh"];
    const csrf = getCookie(CSRF_COOKIE);
    for (const endpoint of endpoints) {
      try {
        const headers = { "Content-Type": "application/json" };
        if (csrf) headers[CSRF_HEADER] = csrf;
        const res = await fetch(`${endpoint}?_fresh=${Date.now()}`, {
          method: "POST",
          cache: "no-store",
          credentials: "include",
          headers,
          body: "{}"
        });
        if (!res.ok) continue;
        const data = await res.json();
        state.cookieAuth = data.cookieAuth === true;
        state.role = data.role || state.role;
        state.fullAccess = Boolean(data.fullAccess ?? (state.role === "admin" || state.role === "owner"));
        state.owner = Boolean(data.owner ?? state.role === "owner");
        state.authRole = state.role === "player" ? "player" : "admin";
        if (data.account) state.user = data.account;
        if (data.username && !data.account) state.user = { ...(state.user || {}), username: data.username, role: data.role || state.role };
        return true;
      } catch {}
    }
    return false;
  })();
  try {
    return await state.refreshPromise;
  } finally {
    state.refreshPromise = null;
  }
}

function toast(message, bad = false) {
  const root = $("toast");
  const el = document.createElement("div");
  el.className = `toast ${bad ? "bad" : "ok"}`;
  el.textContent = message;
  root.appendChild(el);
  setTimeout(() => el.remove(), 5200);
}

function randomActionKey(prefix = "cm") {
  if (globalThis.crypto?.randomUUID) return `${prefix}-${globalThis.crypto.randomUUID()}`;
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function copyText(value, okMessage = "Скопировано") {
  const text = String(value || "").trim();
  if (!text) return;
  try {
    await navigator.clipboard?.writeText(text);
    toast(okMessage);
  } catch {
    toast(text);
  }
}

function authHeaders(extra = {}) {
  const headers = { ...extra };
  if (!headers["Content-Type"] && !(extra instanceof FormData)) headers["Content-Type"] = "application/json";
  return headers;
}

async function api(url, opts = {}) {
  const { skipAuthReset = false, retryOn401 = true, ...fetchOpts } = opts;
  const method = String(fetchOpts.method || "GET").toUpperCase();
  const headers = authHeaders(fetchOpts.headers || {});
  if (fetchOpts.body instanceof FormData) delete headers["Content-Type"];
  if (isUnsafeMethod(method)) {
    const csrf = getCookie(CSRF_COOKIE);
    if (csrf) headers[CSRF_HEADER] = csrf;
  }
  const finalUrl = url.startsWith("/api/") ? `${url}${url.includes("?") ? "&" : "?"}_fresh=${Date.now()}` : url;
  const res = await fetch(finalUrl, {
    ...fetchOpts,
    method,
    cache: "no-store",
    credentials: "include",
    headers
  });
  const text = await res.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch { data = { detail: text }; }
  if (!res.ok) {
    if (res.status === 401 && (state.role || state.cookieAuth) && !skipAuthReset && retryOn401) {
      const refreshed = await tryRefreshSession();
      if (refreshed) return api(url, { ...opts, skipAuthReset: true, retryOn401: false });
      logout(false);
    }
    throw new Error(data.detail || data.error || `HTTP ${res.status}`);
  }
  return data;
}

function resolveModal(result = null) {
  const resolver = state.modalResolver;
  state.modalResolver = null;
  replaceChildrenSafe($("modalRoot"), []);
  if (typeof resolver === "function") resolver(result);
}

window.closeModal = () => resolveModal(null);
window.modalConfirmCancel = () => resolveModal(false);
window.modalConfirmAccept = () => resolveModal(true);

async function dangerConfirm(message, label = "CONFIRM") {
  if (state.modalResolver) resolveModal(false);
  const overlay = buildModalOverlay();
  const modal = buildModalShell("Подтверди действие", String(message || ""), { closeLabel: "Отмена" });
  modal.append(makeElement("div", "notice", "Это действие пишет запись в аудит и выполняется только после явного подтверждения."));
  const actions = makeElement("div", "action-strip");
  appendChildren(actions, [
    makeButton("Отмена", "btn btn-secondary", "modalConfirmCancel()"),
    makeButton("Подтвердить", "btn btn-danger", "modalConfirmAccept()"),
  ]);
  modal.append(actions);
  overlay.append(modal);
  replaceChildrenSafe($("modalRoot"), [overlay]);
  const confirmed = await new Promise((resolve) => {
    state.modalResolver = resolve;
  });
  if (!confirmed) {
    toast("Действие отменено.", true);
    return null;
  }
  return { [CONFIRM_HEADER]: label };
}

async function safeApi(url, fallback = {}) {
  try { return await api(url); }
  catch (err) { return { ...fallback, error: err.message }; }
}

function setLoading(title = "Загрузка данных") {
  const view = $("view");
  if (!view) return;
  replaceChildrenSafe(view, [makeElement("div", "loading", `${title}...`)]);
}

function setBootState(mode = "loading") {
  const body = document.body;
  if (body) body.dataset.bootState = mode;
  const boot = $("bootStage");
  const app = $("app");
  const ready = mode === "ready";
  if (boot) boot.classList.toggle("hidden", ready);
  if (app) {
    app.hidden = !ready;
    app.classList.toggle("hidden", !ready);
  }
}

function applyDynamicViewStyles(root = $("view")) {
  if (!root) return;
  root.querySelectorAll(".readiness-ring[data-ring-offset][data-ring-value]").forEach((el) => {
    const offset = number(el.dataset.ringOffset, 0);
    const value = number(el.dataset.ringValue, 0);
    el.style.setProperty("--ring-offset", String(offset));
    el.style.setProperty("--ring-value", String(value));
  });
  root.querySelectorAll(".mini-bar[data-bar]").forEach((el) => {
    const width = Math.max(0, Math.min(100, number(el.dataset.bar, 0)));
    el.style.setProperty("--bar", `${width}%`);
  });
  root.querySelectorAll(".candidate-progress-fill[data-width], .bar-fill[data-width]").forEach((el) => {
    const width = Math.max(0, Math.min(100, number(el.dataset.width, 0)));
    el.style.width = `${width}%`;
  });
}

function setView(content) {
  const root = $("view");
  if (!root) return;
  if (content instanceof Node) {
    replaceChildrenSafe(root, [content]);
  } else if (Array.isArray(content)) {
    replaceChildrenSafe(root, content.filter(Boolean));
  } else {
    replaceChildrenSafe(root, [fragmentFromHtml(content)]);
  }
  applyDynamicViewStyles(root);
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
        <p>TPS, MSPT, выборы, банк AR и артефакты.</p>
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
          <img src="/assets/mc-icons/item/diamond.png" alt="" />
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
    <div class="readiness-ring" data-ring-offset="${offset}" data-ring-value="${value}">
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
        <div class="mini-bar" data-bar="${Math.max(8, Math.round(row.value / max * 100))}">
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
        <p>Сводка показывает мир, тики, выборы, экономику, обращения и связь с сервером. Если есть предупреждение, оно требует проверки перед релизом.</p>
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
          <p class="panel-subtitle">Проверка после обновления сервера: плагины, конфиги, база и ресурспак.</p>
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
  return panel("Защита данных", "Правила изменения данных через сайт.", `
    <div class="db-policy-grid">
      <div class="db-policy-card ${enabled ? "warn" : "good"}">
        <span>изменения с сайта</span>
        <strong>${enabled ? "ограничены правилами" : "только готовые действия"}</strong>
        <p>${enabled ? "Прямое изменение закрыто. Работают только разрешённые действия." : "Доступны только готовые действия."}</p>
      </div>
      <div class="db-policy-card good">
        <span>защищённых правил</span>
        <strong>${writePolicy.protected}</strong>
        <p>Выборы, AR и официальные предметы меняются только через отдельные сценарии.</p>
      </div>
      <div class="db-policy-card ${allowlist.length ? "good" : "neutral"}">
        <span>разрешённых сценариев</span>
        <strong>${allowlist.length || "нет"}</strong>
        <p>${allowlist.length ? allowlist.map(esc).join(", ") : "Прямые служебные правки закрыты."}</p>
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
  if (value === true) return pill("да", "good");
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
      <img src="${esc(avatarUrl(name, px))}" alt="" loading="lazy" />
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
    return empty("Законов пока нет", "Одобренных законов нет.");
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
    return empty("Кандидатов пока нет", "Одобренных кандидатов нет.");
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
              <span>${row.votes > 0 ? `${compactInt(row.votes)} голосов` : "Подсчёт ещё не завершён"}</span>
            </div>
          </div>
          <div class="candidate-progress">
            <div class="candidate-progress-fill" data-width="${Math.max(8, Math.round((row.votes / maxVotes) * 100))}"></div>
          </div>
        </article>
      `).join("")}
    </div>
  `;
}

function electionApplicationCards(rows) {
  rows = asArray(rows);
  if (!rows.length) {
    return empty("Заявок пока нет", "Поданных заявок нет.");
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
            <button class="btn btn-secondary btn-small" data-click="openElectionApplicationBook('${esc(row.id)}')">Открыть книгу</button>
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
    tax_set: "Обновлена казна",
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
    tax_payment: "Платёж в казну",
    tax: "Платёж в казну",
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

function electionApplicationBookScene(row, pageIndex = 0) {
  const pages = parseApplicationAnswers(row.answers);
  const current = Math.max(0, Math.min(Number(pageIndex) || 0, Math.max(0, pages.length - 1)));
  const page = pages[current] || { question: "Заявка", answer: "Текст заявки не найден." };
  const status = cleanText(row.admin_status || row.status || "PENDING").toUpperCase();
  const canReview = !["APPROVED", "REJECTED"].includes(status);
  const id = esc(row.id);
  return `
    <div class="mc-book-stage">
      <div class="mc-book">
        <div class="mc-book-page-count">Страница ${current + 1} из ${Math.max(1, pages.length)}</div>
        <div class="mc-book-title">${esc(row.player_name || "Кандидат")}</div>
        <div class="mc-book-question">${esc(page.question)}</div>
        <div class="mc-book-text">${bookAnswerHtml(page.answer)}</div>
        <button class="mc-book-arrow mc-book-arrow-left" ${current <= 0 ? "disabled" : ""} data-click="openElectionApplicationBook('${id}',${current - 1})" aria-label="Предыдущая страница"></button>
        <button class="mc-book-arrow mc-book-arrow-right" ${current >= pages.length - 1 ? "disabled" : ""} data-click="openElectionApplicationBook('${id}',${current + 1})" aria-label="Следующая страница"></button>
      </div>
      <div class="mc-book-status">
        ${pill(recommendationText(row.chair_recommendation), recommendationTone(row.chair_recommendation))}
        ${pill(applicationStatusText(row.admin_status || row.status), adminDecisionTone(row.admin_status || row.status))}
      </div>
      <div class="mc-book-actions">
        <button class="mc-book-button" ${canReview ? "" : "disabled"} data-click="reviewElectionApplication('${id}','approved')">Одобрить</button>
        <button class="mc-book-button mc-book-close" data-click="closeModal()">Закрыть</button>
        <button class="mc-book-button" ${canReview ? "" : "disabled"} data-click="reviewElectionApplication('${id}','rejected')">Отклонить</button>
      </div>
    </div>
  `;
}

window.openElectionApplicationBook = (applicationId, pageIndex = 0) => {
  const row = state.electionApplications?.[applicationId];
  if (!row) return toast("Книга заявки не найдена", true);
  const overlay = buildModalOverlay();
  overlay.classList.add("mc-book-overlay");
  const modal = buildModalShell("", "", { wide: true });
  modal.classList.add("mc-book-modal");
  const preview = document.createElement("div");
  preview.append(fragmentFromHtml(electionApplicationBookScene(row, pageIndex)));
  modal.append(preview);
  overlay.append(modal);
  replaceChildrenSafe($("modalRoot"), [overlay]);
};

window.reviewElectionApplication = async (applicationId, decision) => {
  const label = decision === "approved" ? "одобрить" : "отклонить";
  try {
    const headers = await dangerConfirm(`Нужно ${label} заявку кандидата?`, `ELECTION_APPLICATION_${decision.toUpperCase()}`);
    await api(`/api/elections/applications/${encodeURIComponent(applicationId)}/review`, {
      method: "POST",
      headers,
      body: JSON.stringify({ decision }),
    });
    toast(decision === "approved" ? "Заявка одобрена" : "Заявка отклонена");
    await loadElections();
    window.closeModal();
  } catch (err) {
    toast(err.message, true);
  }
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
    <th data-click="sortTable('${id}','${esc(col.key)}')">
      ${esc(col.label || col.key)}${t.sortKey === col.key ? (t.sortDir === "asc" ? " ↑" : " ↓") : ""}
    </th>
  `).join("");
  const body = pageRows.map((row, idx) => {
    const cells = t.columns.map(col => {
      const raw = row?.[col.key];
      return `<td>${col.render ? col.render(raw, row, start + idx) : formatValue(raw)}</td>`;
    }).join("");
    const action = t.rowAction ? ` data-click="${t.rowAction}(${start + idx})"` : "";
    return `<tr${action}>${cells}</tr>`;
  }).join("");
  return `
    <div class="toolbar">
      <input class="grow" value="${esc(t.filter)}" data-input="filterTable" data-input-id="${esc(id)}" placeholder="Поиск по таблице" />
      <button class="btn btn-secondary btn-small" data-click="exportTable('${id}','csv')">Скачать CSV</button>
      <span class="last-update">${rows.length} записей</span>
    </div>
    <div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>
    <div class="table-footer">
      <span>Страница ${t.page} из ${pages}</span>
      <div class="action-strip">
        <button class="btn btn-secondary btn-small" data-click="pageTable('${id}',-1)">Назад</button>
        <button class="btn btn-secondary btn-small" data-click="pageTable('${id}',1)">Вперёд</button>
      </div>
    </div>
  `;
}

function rerenderStoredTable(id) {
  const root = document.querySelector(`[data-table="${id}"]`);
  if (!root) return;
  replaceChildrenSafe(root, [fragmentFromHtml(renderStoredTable(id))]);
}

window.sortTable = (id, key) => {
  const t = state.tables[id];
  if (!t) return;
  if (t.sortKey === key) t.sortDir = t.sortDir === "asc" ? "desc" : "asc";
  else { t.sortKey = key; t.sortDir = "asc"; }
  rerenderStoredTable(id);
};

window.filterTable = (id, value) => {
  const t = state.tables[id];
  if (!t) return;
  t.filter = value;
  t.page = 1;
  rerenderStoredTable(id);
};

window.filterPlayers = (value) => {
  const query = cleanText(value).toLowerCase();
  document.querySelectorAll(".player-row[data-player]").forEach((node) => {
    const name = cleanText(node.dataset.player || "").toLowerCase();
    node.classList.toggle("hidden", Boolean(query) && !name.includes(query));
  });
};

window.pageTable = (id, delta) => {
  const t = state.tables[id];
  if (!t) return;
  t.page += delta;
  rerenderStoredTable(id);
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
  if (isPlayerRole()) return playerNavGroups;
  if (isJuniorAdminRole()) return juniorNavGroups;
  return navGroups;
}

function currentPageMeta() {
  if (isPlayerRole()) return playerPageMeta;
  if (isJuniorAdminRole()) return juniorPageMeta;
  return pageMeta;
}

function defaultTab() {
  return isPlayerRole() ? "cabinet" : "dashboard";
}

function setMobileNav(open) {
  const app = $("app");
  const toggle = $("mobileNavToggle");
  app.classList.toggle("nav-open", Boolean(open));
  if (toggle) toggle.setAttribute("aria-expanded", open ? "true" : "false");
}

function syncWorkspaceMode() {
  const role = state.role || "guest";
  const tab = state.tab || defaultTab();
  const body = document.body;
  const app = $("app");
  document.documentElement.dataset.copimineRole = role;
  document.documentElement.dataset.copimineTab = tab;
  body.dataset.copimineRole = role;
  body.dataset.copimineTab = tab;
  body.classList.toggle("player-mode", isPlayerRole());
  body.classList.toggle("junior-admin-mode", isJuniorAdminRole());
  body.classList.toggle("panel-admin-mode", isPanelAdminRole() && !isJuniorAdminRole());
  if (app) {
    app.dataset.copimineRole = role;
    app.dataset.copimineTab = tab;
  }
  renderAdminSearchDock();
}

function setMiniHealthSummary(title, lines = []) {
  const root = $("miniHealth");
  if (!root) return;
  const nodes = [makeElement("strong", "", title)];
  lines.filter((line) => String(line || "").trim()).forEach((line) => {
    nodes.push(document.createElement("br"));
    nodes.push(document.createTextNode(String(line)));
  });
  replaceChildrenSafe(root, nodes);
}

function buildNavButton([id, label, hint, icon]) {
  const button = makeElement("button", `nav-item ${state.tab === id ? "active" : ""}`);
  button.type = "button";
  button.dataset.tab = id;
  const iconNode = makeElement("span", "nav-icon", icon);
  const copy = makeElement("span");
  copy.append(
    makeElement("span", "nav-label", label),
    makeElement("span", "nav-hint", hint),
  );
  button.append(iconNode, copy);
  button.addEventListener("click", () => {
    setTab(id);
    setMobileNav(false);
  });
  return button;
}

function renderNav() {
  const navRoot = $("nav");
  if (!navRoot) return;
  const groups = currentNavGroups().map((group) => {
    const shell = makeElement("div", "nav-group");
    shell.append(makeElement("div", "nav-group-title", group.title));
    group.items.forEach((item) => {
      shell.append(buildNavButton(item));
    });
    return shell;
  });
  replaceChildrenSafe(navRoot, groups);
}

function openAdminSearchResult(tab, target = "", needle = "") {
  state.adminSearchTarget = String(target || "");
  state.adminSearchNeedle = String(needle || "");
  setTab(tab);
}

function clearAdminSearchHighlight() {
  if (state.adminSearchHighlightTimer) {
    clearTimeout(state.adminSearchHighlightTimer);
    state.adminSearchHighlightTimer = null;
  }
  document.querySelectorAll(".search-hit").forEach((node) => node.classList.remove("search-hit"));
}

function findAdminSearchFocusNode() {
  const root = $("view");
  if (!root) return null;
  const needle = cleanText(state.adminSearchNeedle || "").toLowerCase();
  if (!needle) return null;
  const candidates = Array.from(root.querySelectorAll(".panel, .metric-card, .notice, .table-shell, .inventory-panel, .inventory-card"));
  return candidates.find((node) => cleanText(node.textContent || "").toLowerCase().includes(needle)) || null;
}

function applyPendingAdminSearchFocus() {
  if (!state.adminSearchNeedle) return;
  requestAnimationFrame(() => {
    const node = findAdminSearchFocusNode();
    if (!node) return;
    clearAdminSearchHighlight();
    node.scrollIntoView({ behavior: "smooth", block: "center" });
    node.classList.add("search-hit");
    state.adminSearchHighlightTimer = window.setTimeout(() => {
      node.classList.remove("search-hit");
      state.adminSearchHighlightTimer = null;
    }, 3000);
    state.adminSearchTarget = "";
    state.adminSearchNeedle = "";
  });
}

function renderAdminSearchDock() {
  let dock = $("adminSearchDock");
  if (!isPanelAdminRole() || isJuniorAdminRole()) {
    dock?.remove();
    return;
  }
  if (!dock) {
    dock = makeElement("aside", "admin-search-dock");
    dock.id = "adminSearchDock";
  }
  const workspace = document.querySelector(".workspace");
  const view = $("view");
  if (workspace && dock.parentElement !== workspace) {
    workspace.insertBefore(dock, view || null);
  }
  const query = state.adminSearchQuery || "";
  const results = adminSearchItems().filter(item => fuzzyContains(item.haystack, query)).slice(0, 7);
  const label = makeElement("label", "admin-search-box");
  label.append(makeElement("span", "", "Поиск по админке"));
  const input = makeElement("input");
  setAttributes(input, {
    id: "adminGlobalSearch",
    "data-input": "adminGlobalSearch",
    value: query,
    placeholder: "банк, рецепты, игроки...",
    autocomplete: "off"
  });
  label.append(input);
  const list = makeElement("div", "admin-search-results");
  if (results.length) {
    results.forEach((item) => {
      const button = makeElement("button", state.tab === item.id ? "active" : "");
      button.type = "button";
      button.addEventListener("click", () => openAdminSearchResult(item.id, item.target || "", item.focusNeedle || item.title || ""));
      button.append(makeElement("strong", "", item.title));
      button.append(makeElement("span", "", `${item.group} · ${item.subtitle}`));
      list.append(button);
    });
  } else {
    list.append(makeElement("p", "", "Ничего не найдено."));
  }
  replaceChildrenSafe(dock, [label, list]);
}

function tabNavigationParams(tab) {
  const params = {};
  if (["donation-balance", "donation-shop", "donation-items"].includes(tab) && state.donationSessionId) {
    params.session = state.donationSessionId;
  }
  if (tab === "donation-shop" && state.donationFocusItemId) {
    params.item = state.donationFocusItemId;
  }
  return params;
}

async function setTab(tab) {
  const metaMap = currentPageMeta();
  const routeTab = metaMap[tab] ? tab : defaultTab();
  const currentRoute = normalizeAppRoute(document.body?.dataset.appRoute || routeFromHref(location.pathname), state.tab || defaultTab());
  if (routeTab !== currentRoute) {
    window.location.href = appRouteHref(routeTab, tabNavigationParams(routeTab));
    return;
  }
  state.tab = routeTab;
  if (state.tab !== "donation-shop") state.donationFocusItemId = "";
  const meta = metaMap[state.tab];
  $("pageTitle").textContent = meta.title;
  $("pageSubtitle").textContent = meta.subtitle;
  syncWorkspaceMode();
  renderNav();
  renderAdminSearchDock();
  await loadCurrent().finally(applyPendingAdminSearchFocus);
}

function updateGlobalStatus(status = {}) {
  const ok = status.minecraftOnline === true && status.rconOk !== false;
  const badge = $("liveBadge");
  badge.className = `status-chip ${ok ? "status-good" : status.minecraftOnline ? "status-warn" : "status-bad"}`;
  badge.textContent = ok ? "сервер онлайн" : status.minecraftOnline ? "частично" : "offline";
  setMiniHealthSummary(ok ? "Сервер стабилен" : "Требует проверки", [
    `TPS: ${short(status.tps || "—", 26)}`,
    `MSPT: ${short(status.mspt || "—", 26)}`,
  ]);
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
  const copy = makeElement("div");
  copy.append(
    makeElement("span", "hero-kicker", feature.kicker),
    makeElement("h3", "", feature.title),
    makeElement("p", "", feature.text),
  );
  const art = makeElement("img");
  art.src = feature.icon;
  art.alt = "";
  replaceChildrenSafe(panel, [copy, art]);
}

function publicStatusMetric(label, value, detail = "", tone = "neutral") {
  const card = makeElement("article", `public-status-card ${tone}`);
  card.append(
    makeElement("span", "", label),
    makeElement("strong", "", value),
    makeElement("p", "", detail || "Нет данных"),
  );
  return card;
}

function buildAvatarBadgeNode(name, size = "sm") {
  const px = size === "lg" ? 88 : size === "sm" ? 40 : 56;
  const badge = makeElement("span", `avatar-badge avatar-${size}`);
  badge.setAttribute("aria-hidden", "true");
  const initialsNode = makeElement("b", "", initials(name));
  const image = makeElement("img");
  image.src = avatarUrl(name, px);
  image.alt = "";
  image.loading = "lazy";
  badge.append(initialsNode, image);
  return badge;
}

function publicOnlineRows(players = []) {
  if (!players.length) {
    return [makeElement("div", "empty-public-state", "Список игроков скрыт.")];
  }
  return players.map((name, index) => {
    const row = makeElement("div", "top-row");
    row.append(
      makeElement("b", "", String(index + 1)),
      buildAvatarBadgeNode(name, "sm"),
      makeElement("span", "", String(name)),
      makeElement("strong", "", "онлайн"),
    );
    return row;
  });
}

function buildTopNote(title, text) {
  const row = makeElement("div", "top-note");
  row.append(
    makeElement("strong", "", title),
    makeElement("span", "", text),
  );
  return row;
}

function buildTopBoard(title, children = []) {
  const card = makeElement("article", "top-board");
  card.append(makeElement("h3", "", title));
  children.forEach((child) => card.append(child));
  return card;
}

function renderPublicStatus(status = {}, config = {}) {
  const server = status.server || {};
  const elections = status.elections || {};
  const treasury = status.treasury || {};
  const statusGrid = $("publicStatusGrid");
  const onlineBoard = $("publicOnlineBoard");
  if (statusGrid) {
    replaceChildrenSafe(statusGrid, [
      publicStatusMetric("", server.online ? "" : "", server.online ? ` ${server.latencyMs ?? "?"} ` : "   ", server.online ? "good" : "bad"),
      publicStatusMetric("Игроки", String(server.playersOnline || 0), server.playerCap ? `из ${server.playerCap}` : (server.playerListAvailable ? "список открыт" : "список скрыт"), server.playersOnline ? "good" : "neutral"),
      publicStatusMetric("Выборы", elections.active ? "идут" : "пауза", elections.active ? `${elections.candidates || 0} кандидатов · ${elections.votes || 0} голосов` : "Сейчас нет активного этапа голосования", elections.active ? "good" : "warn"),
      publicStatusMetric("Президент", elections.president || "не выбран", elections.president ? "Данные пришли из ElectionCore" : "Активный срок пока не подтверждён", elections.president ? "good" : "neutral"),
      publicStatusMetric("Казна", formatAr(treasury.balance || 0), treasury.ownerName ? `Ведёт ${treasury.ownerName}` : "Открытая казна сервера", Number(treasury.balance || 0) > 0 ? "good" : "neutral")
    ]);
  }
  if (onlineBoard) {
    const onlineRows = publicOnlineRows(asArray(server.samplePlayers));
    const runtimeNotes = makeElement("div", "top-note-list");
    runtimeNotes.append(
      buildTopNote("Кабинет", "Вход, привязка и банк AR."),
      buildTopNote("Выборы", elections.active ? "Идёт активный этап." : "Активных выборов нет."),
      buildTopNote("Донат", config.donationEnabled ? "Донат открыт." : "Оплата временно закрыта."),
    );
    const treasuryNotes = makeElement("div", "top-note-list");
    treasuryNotes.append(
      buildTopNote("Баланс", formatAr(treasury.balance || 0)),
      buildTopNote("Доступ", "Президент и админы."),
      buildTopNote("Последние события", asArray(treasury.history).slice(0, 3).map((row) => row.label || row.type || "операция").join(" • ") || "Записей пока нет."),
    );
    replaceChildrenSafe(onlineBoard, [
      buildTopBoard("Кто сейчас в игре", onlineRows),
      buildTopBoard("Что работает на сервере", [runtimeNotes]),
      buildTopBoard("Президентская казна", [treasuryNotes]),
    ]);
  }
  window.dispatchEvent(new CustomEvent("copimine:public-status", {
    detail: {
      status,
      config,
      route: "legacy-public-status"
    }
  }));
}

function updatePublicHero(status = {}, config = {}) {
  const server = status.server || {};
  const address = cleanText(config.serverAddress || "") || "запроси адрес у администрации";
  const version = cleanText(config.serverVersion || "1.21.x");
  const pulseText = server.online
    ? `Онлайн ${server.playersOnline || 0}${server.playerCap ? ` / ${server.playerCap}` : ""} · ${version} · Paper`
    : `Сервер не ответил · ${version}`;
  if ($("serverIpText")) $("serverIpText").textContent = address;
  if ($("serverPulseText")) $("serverPulseText").textContent = pulseText;
}

async function loadPublicStatus() {
  const [configRes, statusRes, modpackRes] = await Promise.all([
    safeApi("/api/public/config", { ok: false, data: {} }),
    safeApi("/api/public/status", { ok: false, data: {} }),
    safeApi("/api/public/modpack", { ok: false, data: {} })
  ]);
  state.publicConfig = configRes.data || configRes || {};
  state.publicStatus = statusRes.data || statusRes || {};
  state.publicModpack = modpackRes.data || modpackRes || {};
  updatePublicHero(state.publicStatus, state.publicConfig);
  updatePublicModpack(state.publicModpack);
  renderPublicStatus(state.publicStatus, state.publicConfig);
}

function updatePublicModpack(modpack = {}) {
  const button = $("downloadModsBtn");
  if (!button) return;
  if (modpack.available) {
    button.classList.remove("hidden");
    button.classList.remove("btn-disabled");
    button.href = modpack.downloadUrl || "/downloads/CopiMineMods.zip";
    const sizeMb = modpack.size ? `${(Number(modpack.size) / (1024 * 1024)).toFixed(2)} МБ` : "архив готов";
    button.textContent = `Скачать моды (${sizeMb})`;
    button.removeAttribute("aria-disabled");
    return;
  }
  button.classList.remove("hidden");
  button.classList.add("btn-disabled");
  button.href = "mods.html";
  button.textContent = "Архив недоступен";
  button.setAttribute("aria-disabled", "true");
}

function renderPublicAuthState() {
    const button = $("publicCabinetBtn");
    if (!button) return;
    const authed = Boolean(state.role || state.cookieAuth);
    button.classList.toggle("hidden", !authed);
    const username = state.user?.username || (isPlayerRole() ? "игрок" : "команда");
    button.textContent = isPanelAdminRole() ? `Открыть кабинет (${username})` : `Личный кабинет (${username})`;
    window.dispatchEvent(new CustomEvent("copimine:auth-state", {
      detail: {
        role: state.role,
        cookieAuth: state.cookieAuth,
        username,
      },
    }));
  }

function roleHomeHref(role = state.role) {
    return appRouteHref(defaultAppRouteForRole(role || ""));
  }

function redirectToRoleHome(replace = true) {
    const target = roleHomeHref(state.role || "player");
    if (replace) {
      window.location.replace(target);
      return;
    }
    window.location.href = target;
  }

function showGuestPages() {
    window.location.href = "index.html";
  }

function syncTopbarActions() {
  const guestButton = $("guestPagesBtn");
  if (!guestButton) return;
  const playerMode = isPlayerRole();
  guestButton.hidden = playerMode;
  guestButton.textContent = playerMode ? "" : "Сайт";
}

async function showCabinetFromPublic() {
    if (!state.role && !state.cookieAuth) {
      window.location.href = authLandingHref("signin");
      return;
    }
    redirectToRoleHome(false);
  }

function copyServerIp() {
    const ip = $("serverIpText")?.textContent?.trim() || "";
    if (!ip || ip === "запроси адрес у администрации" || ip === "загружаем адрес...") {
      toast("Адрес сервера не задан.", true);
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

function syncAuthUiLegacyUnused() {
  const isRegister = isRegisterPage();
  const loginCard = $("loginForm");
  if (!loginCard) return;

  $("minecraftNameGroup")?.classList.toggle("hidden", !isRegister);

  const brandText = loginCard.querySelector(".login-brand p");
  const lead = loginCard.querySelector(".login-copy strong");
  const support = loginCard.querySelector(".login-copy span");
  const usernameLabel = loginCard.querySelector('label[for="username"]');
  const passwordLabel = loginCard.querySelector('label[for="password"]');
  const submit = loginCard.querySelector('button[type="submit"]');
  const note = loginCard.querySelector(".login-note");

  if (true) {
    if (brandText) brandText.textContent = "Личный кабинет CopiMine";
    if (lead) lead.textContent = isRegister ? "Создать аккаунт" : "Вход";
    if (support) support.textContent = isRegister
      ? "Зарегистрируй отдельный логин сайта. Minecraft-ник подтверждается позже одноразовым кодом на сервере."
      : "Войдите логином сайта. После входа будут доступны онлайн-банк, удалённые покупки и история операций.";
    if (usernameLabel) usernameLabel.textContent = "Логин сайта";
    if (passwordLabel) passwordLabel.textContent = isRegister ? "Новый пароль" : "Пароль";
    $("username").placeholder = "Придумай логин";
    $("password").placeholder = isRegister ? "Минимум 8 символов" : "Введите пароль";
    if (submit) submit.textContent = isRegister ? "Создать аккаунт" : "Открыть кабинет";
    if (note) note.textContent = isRegister
      ? "Пароль от Minecraft здесь никогда не нужен. Укажи свой игровой ник и подтверди его кодом в игре."
      : "После входа можно привязать игровой аккаунт, настроить PIN и пользоваться переводами.";
  } else {
    if (brandText) brandText.textContent = "Рабочий кабинет сервера";
    if (lead) lead.textContent = "Вход";
    if (support) support.textContent = "Доступ к админке получают только сотрудники сервера с действующим логином.";
    if (usernameLabel) usernameLabel.textContent = "Minecraft-ник";
    if (passwordLabel) passwordLabel.textContent = "Пароль";
    $("username").placeholder = "Например, Cells";
    $("password").placeholder = "Введите пароль";
    if (submit) submit.textContent = "Войти";
    if (note) note.textContent = "Если доступ не открывается, проверь логин и обратись к старшей команде сервера.";
  }
  if (brandText) brandText.textContent = isRegister ? "Новый кабинет" : "Вход в CopiMine";
  if (lead) lead.textContent = isRegister ? "Регистрация" : "Вход";
  if (support) support.textContent = isRegister
    ? "Создайте аккаунт сайта."
    : "Введите логин и пароль, чтобы открыть свой кабинет.";
  if (usernameLabel) usernameLabel.textContent = "Логин сайта";
  if (passwordLabel) passwordLabel.textContent = isRegister ? "Новый пароль" : "Пароль";
  if ($("username")) $("username").placeholder = isRegister ? "Придумай логин" : "Введи логин";
  if ($("password")) $("password").placeholder = isRegister ? "Минимум 8 символов" : "Введи пароль";
  if (submit) submit.textContent = isRegister ? "Создать кабинет" : "Войти";
  if (note) note.textContent = isRegister
    ? "Создайте аккаунт сайта."
    : "После входа откроется кабинет этого аккаунта.";
}

function syncAuthUi() {
  const isRegister = isRegisterPage();
  const form = $("loginForm");
  if (!form) return;

  $("minecraftNameGroup")?.classList.toggle("hidden", !isRegister);

  const authCard = form.closest(".auth-card");
  const lead = authCard?.querySelector(".auth-card-copy h1");
  const kicker = authCard?.querySelector(".auth-kicker");
  const usernameLabel = form.querySelector('label[for="username"]');
  const passwordLabel = form.querySelector('label[for="password"]');
  const minecraftLabel = form.querySelector('label[for="playerMinecraftName"]');
  const submit = form.querySelector('button[type="submit"]');
  const authLink = document.querySelector(".auth-links a");

  if (kicker) kicker.textContent = "CopiMine";
  if (lead) lead.textContent = isRegister ? "Регистрация" : "Вход";
  if (usernameLabel) usernameLabel.textContent = "Логин";
  if (passwordLabel) passwordLabel.textContent = "Пароль";
  if (minecraftLabel) minecraftLabel.textContent = "Minecraft-ник";
  if ($("username")) $("username").placeholder = isRegister ? "Придумайте логин" : "Введите логин";
  if ($("password")) $("password").placeholder = isRegister ? "Минимум 8 символов" : "Введите пароль";
  if ($("playerMinecraftName")) $("playerMinecraftName").placeholder = "Например, Cells";
  if (submit) submit.textContent = isRegister ? "Создать аккаунт" : "Войти";
  if (authLink) {
    authLink.textContent = isRegister ? "Уже есть аккаунт? Войти" : "Нет аккаунта? Регистрация";
    authLink.setAttribute("href", isRegister ? "/signin.html" : "/register.html");
  }
}

async function login(event) {
  event.preventDefault();
  if ($("loginError")) $("loginError").textContent = "";
  try {
    const isRegister = isRegisterPage();
    const payload = { username: $("username").value.trim(), password: $("password").value };
    if (isRegister) payload.minecraft_name = $("playerMinecraftName").value.trim();
    const data = await api(isRegister ? "/api/player/register" : "/api/session/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    state.token = "";
    state.cookieAuth = data.cookieAuth === true;
    state.role = data.role || "player";
    state.authRole = state.role;
    state.user = data.account || { username: data.username, role: state.role };
    redirectToRoleHome(true);
  } catch (err) {
    if ($("loginError")) $("loginError").textContent = err.message;
  }
}

async function logout(call = true) {
  if (call) {
    try { await api("/api/auth/logout", { method: "POST", body: "{}" }); } catch {}
  }
  state.token = "";
  state.role = "";
  state.fullAccess = false;
  state.owner = false;
  state.cookieAuth = false;
  state.user = null;
  state.playerLinkRequest = null;
  state.donationSessionId = "";
  state.donationFocusItemId = "";
  state.donationBusy = false;
  state.playerBankScope = "PERSONAL";
  removeStoredUiState("copimineDonationSessionId");
  removeStoredUiState("copiminePlayerBankScope");
  $("app")?.classList.add("hidden");
  $("login")?.classList.remove("hidden");
  syncWorkspaceMode();
  stopLivePanelStream();
  clearInterval(state.refreshTimer);
  syncAuthUi();
  if (isCabinetPage()) {
    window.location.replace(authLandingHref("signin"));
  }
}

async function resolveAuthSession() {
  const me = await api("/api/session/me", { skipAuthReset: true });
  if (me.kind === "panel") {
    const config = await safeApi("/api/config", {});
    return {
      role: me.role || "admin",
      user: { username: me.username || "", role: me.role || "admin" },
      config,
      fullAccess: Boolean(me.fullAccess),
      owner: Boolean(me.owner),
    };
  }
  return {
    role: "player",
    user: me.account || { username: me.username || "", role: "player" },
    config: {},
    fullAccess: false,
    owner: false,
  };
}

async function bootAuthed(options = {}) {
  $("login")?.classList.add("hidden");
  setBootState("loading");
  try {
    const session = await resolveAuthSession();
    state.role = session.role;
    state.fullAccess = Boolean(session.fullAccess);
    state.owner = Boolean(session.owner);
    state.user = session.user;
    state.config = session.config || {};
    const cookieAuth = Boolean(state.config.features?.cookieAuth || state.config.cookieAuth || state.cookieAuth);
    state.cookieAuth = cookieAuth;
    state.authRole = state.role;
    if (isAuthLandingPage()) {
      renderPublicAuthState();
      redirectToRoleHome(true);
      return;
    }
    const username = isPlayerRole() ? (state.user.username || "player") : (state.user.username || "admin");
    $("userBadge").textContent = isPlayerRole()
      ? `${username} · игрок`
      : `${username}${isJuniorAdminRole() ? " · младший админ" : (state.owner ? " · владелец" : " · админ")}`;
  } catch (err) {
    if (!options.quiet) toast(err.message, true);
    if (isCabinetPage()) {
      window.location.replace(authLandingHref("signin"));
      return;
    }
    logout(false);
    return;
  }
  syncWorkspaceMode();
  syncTopbarActions();
  renderPublicAuthState();
  renderNav();
  await setTab(state.tab);
  setBootState("ready");
  clearInterval(state.refreshTimer);
  if (isPanelAdminRole()) {
    startLivePanelStream();
    state.refreshTimer = setInterval(() => {
      if (!document.hidden && ["dashboard", "server"].includes(state.tab)) loadCurrent(true);
    }, 15000);
  } else {
    stopLivePanelStream();
    $("liveBadge").className = "status-chip status-neutral";
    $("liveBadge").textContent = "игрок";
    setMiniHealthSummary("Личный кабинет", [
      `Привязка: ${state.user?.linked ? "есть" : "нет"}`,
      "Доступ: банк и кабинет",
    ]);
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
  if (!rows.length) return empty("Событий пока нет", "Сервер ещё не отдал события.");
  return `<div class="timeline">${rows.map(row => `
    <div class="timeline-item">
      <div class="timeline-dot"></div>
      <div class="timeline-body">
        <strong>${esc(row.action || row.eventType || row.type || "событие")}</strong>
        <span>${esc(row.actor || row.source || "system")} · ${esc(row.target || "")} ${row.time || row.createdAt || row.timestamp ? "· " + dt(row.time || row.createdAt || row.timestamp) : ""}</span>
      </div>
    </div>
  `).join("")}</div>`;
}

function compactCoords(row) {
  if (row?.world == null && row?.x == null) return "";
  return `${row.world || "world"} ${row.x ?? ""} ${row.y ?? ""} ${row.z ?? ""}`;
}

function activityTimeline(rows) {
  rows = asArray(rows).slice(0, 80);
  if (!rows.length) return empty("Действий пока нет", "Записей по игроку нет.");
  return `<div class="activity-timeline">${rows.map(row => `
    <article class="activity-row">
      <div class="activity-icon">${esc(short(row.type || row.source || "log", 2).toUpperCase())}</div>
      <div class="activity-main">
        <div class="activity-head">
          <strong>${esc(row.type || "событие")}</strong>
          <span>${esc(row.source || "server")} · ${dt(row.time || row.createdAt || row.timestamp)}</span>
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
  if (!rows.length) return empty("Записей пока нет", "История ещё пустая.");
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
      ${metric("", number(snapshot.arInInventory) + number(snapshot.arInEnderChest), ` ${snapshot.arInInventory ?? 0}   ${snapshot.arInEnderChest ?? 0}`, "good")}
      ${metric("", snapshot.world || "", `${snapshot.x ?? ""} ${snapshot.y ?? ""} ${snapshot.z ?? ""}`)}
    </div>
  `;
}

function resultBars(rows, nameKeys = ["name", "display_name"], valueKeys = ["total", "votes", "raw_votes", "amount"]) {
  rows = asArray(rows).slice(0, 8);
  if (!rows.length) return empty("Нет данных для графика", "Когда накопятся данные, график появится здесь.");
  const mapped = rows.map(row => {
    const name = nameKeys.map(k => row[k]).find(Boolean) || row.uuid || row.player || row.table || "строка";
    const value = valueKeys.map(k => row[k]).find(v => Number.isFinite(Number(v))) ?? 0;
    return { name, value: number(value) };
  });
  const max = Math.max(1, ...mapped.map(x => x.value));
  return `<div class="bars">${mapped.map(item => `
    <div class="bar-row">
      <div class="bar-head"><span>${esc(item.name)}</span><span>${esc(item.value)}</span></div>
      <div class="bar-track"><div class="bar-fill" data-width="${Math.max(4, Math.round(item.value / max * 100))}"></div></div>
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
    const coords = `${row.world || "world"} ${row.x ?? ""} ${row.y ?? ""} ${row.z ?? ""}`;
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
  if (!silent) setLoading("Обновляем сводку сервера");
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
      ${metric("Minecraft", status.minecraftOnline ? "" : "", `ping ${status.latencyMs ?? ""} ms`, status.minecraftOnline ? "good" : "bad")}
      ${metric("Игроки", players.length, players.length ? players.slice(0, 4).join(", ") : "сейчас никого нет", players.length ? "good" : "")}
      ${metric("TPS / MSPT", `${perf.tps ?? ""} / ${perf.mspt ?? ""}`, short(`${perf.tpsText || ""} ${perf.msptText || ""}`, 80), perf.mspt && perf.mspt > 50 ? "bad" : "good")}
      ${metric("Заявки", `${requestsReady}/${requestsTotal}`, "настроенные обязательные параметры", requestsReady === requestsTotal ? "good" : "warn")}
    </section>

    ${releaseReadinessHtml(status, perf, electionOverview, economy, requestsReady, requestsTotal)}
    ${firstRunReadinessHtml(perfReady)}

    <section class="layout-grid grid-wide">
      ${panel("Сводка по серверу", "Главные показатели и состояние служб на одной странице", `
        <div class="layout-grid grid-3">
          ${metric("", electionOverview.active ? "" : "", `${short(electionOverview.title || "CopiMine Elections", 42)}  ${electionOverview.candidates ?? 0}   ${electionOverview.votes ?? 0} `, electionOverview.active ? "good" : "")}
          ${metric("  ", economy.totalKnownInPlayerData ?? 0, "     ", "good")}
          ${metric("Связь с сервером", status.rconOk ? "Есть" : "Нет", status.rconOk ? "RCON отвечает" : "RCON не отвечает", status.rconOk ? "good" : "warn")}
        </div>
        <div class="spacer-12"></div>
        ${players.length ? table("dash-online", players.map(name => ({ player: name })), [{ key: "player", label: "Игрок онлайн" }], { pageSize: 8 }) : empty("Игроков онлайн не найдено", "Если игрок скрыт vanish/hidden, сервер может не отдавать его в /list.")}
      `)}
      ${panel("Последние события", "Аудит панели и события плагинов", timeline(eventRows))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Стабильность сервера", "Плагины и настройки, которые помогают держать мир лёгким и плавным", optimizationStackHtml(perfReady))}
      ${panel("Источники данных", "Файлы, сервисы и таблицы панели.", kv([
        ["server.properties", perfReady.sources?.serverProperties || "—"],
        ["plugins", perfReady.sources?.plugins || "—"],
        ["База панели", perfReady.sources?.adminDb || "—"],
        ["подсказка ресурспака", perfReady.resourcePackPromptReadable ? "нормальная" : "проверить"]
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
      <input class="grow" id="playerSearch" placeholder="Найти игрока" data-input="filterPlayers" />
      <button class="btn btn-secondary" data-click="loadPlayers()">Обновить</button>
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
    <button class="player-row ${state.selectedPlayer === row.name ? "active" : ""}" data-player="${esc(row.name)}" data-click="selectPlayer('${esc(row.name)}')">
      ${avatarBadge(row.name, "sm")}
      <span class="player-main">
        <span class="player-name">${esc(row.name)}</span>
        <span class="player-meta">${row.online ? "Сейчас в игре" : "Не в игре"}</span>
      </span>
      ${row.online ? pill("Онлайн", "good") : pill("Оффлайн", "neutral")}
    </button>
  `).join("");
}

function renderPlayerList(rows) {
  const root = $("playerList");
  if (!root) return;
  replaceChildrenSafe(root, [fragmentFromHtml(playerListHtml(rows))]);
}

window.filterPlayers = (query) => {
  const q = query.trim().toLowerCase();
  const rows = state.players.filter(row => JSON.stringify(row).toLowerCase().includes(q));
  renderPlayerList(rows);
};

window.selectPlayer = async (name) => {
  state.selectedPlayer = cleanText(name);
  document.querySelectorAll(".player-row").forEach((button) => {
    button.classList.toggle("active", button.dataset.player === state.selectedPlayer);
  });
  const detailsRoot = $("playerDetails");
  if (detailsRoot) {
    replaceChildrenSafe(detailsRoot, [makeElement("div", "loading", "Загружаю профиль...")]);
    replaceChildrenSafe(detailsRoot, [fragmentFromHtml(await playerDetailsHtml(state.selectedPlayer))]);
    return;
  }
  const fallbackRoot = $("playerDetails");
  if (!fallbackRoot) return;
  replaceChildrenSafe(fallbackRoot, [makeElement("div", "loading", "Загружаю профиль...")]);
  replaceChildrenSafe(fallbackRoot, [fragmentFromHtml(await playerDetailsHtml(state.selectedPlayer))]);
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
  const actionOptions = playerActions.map(([action, label]) => `<option value="${esc(action)}">${esc(label)}</option>`).join("");
  const actionButtons = playerActions.map(([action, label]) => `<button class="btn btn-secondary btn-small" data-click="playerAction('${esc(player)}','${action}')">${esc(label)}</button>`).join("");
  const quickActions = hasFullAdminAccess()
    ? `
      <div class="field-grid compact admin-player-action-form">
        <label class="field-stack" for="playerActionSelect">
          <span>Действие</span>
          <select id="playerActionSelect">${actionOptions}</select>
        </label>
        <label class="field-stack" for="playerActionReason">
          <span>Причина</span>
          <input id="playerActionReason" value="CopiMine" autocomplete="off" />
        </label>
        <label class="field-stack" for="playerActionTarget">
          <span>Цель для телепорта</span>
          <input id="playerActionTarget" list="playersDatalist" placeholder="Ник игрока" autocomplete="off" />
        </label>
        <div class="field-stack">
          <span>&nbsp;</span>
          <button class="btn btn-primary" data-click="playerActionFromPanel('${esc(player)}')">Выполнить</button>
        </div>
      </div>
      <div class="action-strip">${actionButtons}</div>
    `
    : `<div class="notice">Младший админ может просматривать профиль, инвентарь и историю, но не выполнять опасные действия.</div>`;
  const site = profile.siteAccount || {};
  const bank = profile.bank || {};
  const pin = profile.pin || {};
  const pinState = bankPinState(pin);
  const canManagePins = hasFullAdminAccess();
  const pinButtons = site.id && canManagePins
    ? `
      <div class="action-strip">
        <button class="btn btn-secondary btn-small" data-click="playerResetBankPin('${esc(player)}')">Сбросить PIN</button>
        <button class="btn btn-secondary btn-small" data-click="playerRandomizeBankPin('${esc(player)}')">Случайный PIN</button>
      </div>
      <div class="toolbar compact">
        <input id="playerAdminPinInput" inputmode="numeric" autocomplete="off" placeholder="Новый PIN, 4-8 цифр" />
        <button class="btn btn-secondary btn-small" data-click="playerSetBankPinAdmin('${esc(player)}')">Задать PIN</button>
      </div>
    `
    : (site.id && !canManagePins ? `<div class="notice">Младший админ видит статус PIN, но не может раскрывать, сбрасывать или задавать его.</div>` : "");
  return `
    <div class="panel-header">
      <div>
        <h2 class="panel-title">${esc(player)}</h2>
        <p class="panel-subtitle">Профиль игрока, история действий и инвентарь</p>
      </div>
      <div class="action-strip">
        <button class="btn btn-primary btn-small" data-click="snapshotInventory('${esc(player)}')">Снимок инвентаря</button>
      </div>
    </div>
    <div class="layout-grid grid-3">
      ${metric("", profile.health ?? "", ` ${profile.food ?? ""}`)}
      ${metric("XP", profile.xpLevel ?? "", profile.dimension || " ")}
      ${metric("", number(profile.ar?.inventory) + number(profile.ar?.enderChest), ` ${profile.ar?.inventory ?? 0}   ${profile.ar?.enderChest ?? 0}`, "good")}
    </div>
    <div class="spacer-12"></div>
    ${panel("Кабинет и банк", "Привязка, баланс и PIN.", kv([
      ["Аккаунт сайта", site.username || "Не привязан"],
      ["Кабинет привязан", Boolean(site.id)],
      ["Последний вход на сайт", dt(site.lastLoginAt)],
      ["Счёт", bank.accountId ? "Открыт" : "Не открыт"],
      ["Баланс банка", formatAr(bank.balance || 0)],
      ["Состояние PIN", pinState],
      ["Текущий PIN", canManagePins ? (pin.visiblePin || "Скрыт / не задан") : "Скрыт"],
      ["PIN заблокирован", Boolean(pin.locked)],
      ["Временный PIN истекает", pin.temporaryExpiresAt ? dt(pin.temporaryExpiresAt) : "--"]
    ]), pinButtons)}
    ${panel("Быстрые действия", "Все действия записываются в журнал и требуют серверные права.", quickActions)}
    ${panel("Текущий инвентарь", "Если игрок онлайн, первым берётся свежий игровой снимок.", `
      ${inventorySummary(live)}
      <div class="spacer-12"></div>
      ${inventoryGrid(firstArray(live?.inventory, inventory.inventory, []), 18)}
    `)}
    ${panel("Эндер-сундук и свежие снимки", "Последние игровые снимки помогают разбирать спорные ситуации без ручного поиска по файлам.", `
      ${inventoryGrid(firstArray(live?.enderChest, inventory.enderChest, []), 18)}
      <div class="spacer-12"></div>
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
    ${panel("Лента действий", "Проверки, AR и игровые события по времени.", `<div class="player-actions-log">${activityTimeline(timelineData.rows)}</div>`)}
    ${panel("Последние действия CoreProtect", "События по игроку.", table("player-actions", asArray(actions.rows), null, { pageSize: 12 }))}
  `;
}

window.playerAction = async (player, action) => {
  const reason = $("playerActionReason")?.value?.trim() || "CopiMine";
  let body = { reason };
  if (action === "tp_to" || action === "tp_here") {
    body.target = $("playerActionTarget")?.value?.trim() || "";
    if (!body.target) return toast("Укажи цель телепорта.", true);
  }
  const dangerLabels = {
    ban: "PLAYER_BAN",
    op: "PLAYER_OP",
    deop: "PLAYER_DEOP",
    clear: "PLAYER_CLEAR",
    kill: "PLAYER_KILL",
    tp_here: "PLAYER_TP_HERE",
    tp_coords: "PLAYER_TP_COORDS"
  };
  const headers = dangerLabels[action] ? await dangerConfirm(`Опасное действие с игроком: ${action} -> ${player}`, dangerLabels[action]) : {};
  if (!headers) return;
  try {
    const res = await api(`/api/players/${encodeURIComponent(player)}/command/${action}`, { method: "POST", headers, body: JSON.stringify(body) });
    toast(`Команда выполнена: ${res.command || action}`);
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerActionFromPanel = async (player = state.selectedPlayer) => {
  const action = $("playerActionSelect")?.value || "";
  if (!action) return toast("Выбери действие.", true);
  return window.playerAction(player, action);
};

window.snapshotInventory = async (player = state.selectedPlayer) => {
  if (!player) return toast("Игрок не выбран", true);
  try {
    const snapshot = await api(`/api/players/${encodeURIComponent(player)}/inventory/snapshots`, { method: "POST", body: "{}" });
    openInventoryModal(snapshot);
    toast("Снимок инвентаря создан");
    if (state.tab === "players") replaceChildrenSafe($("playerDetails"), [fragmentFromHtml(await playerDetailsHtml(player))]);
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerResetBankPin = async (player = state.selectedPlayer) => {
  if (!player) return toast("Игрок не выбран", true);
  const headers = await dangerConfirm(`Сбросить банковский PIN для ${player}? Старый PIN сразу перестанет работать.`, "PLAYER_BANK_PIN_RESET");
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
    if (state.tab === "players") replaceChildrenSafe($("playerDetails"), [fragmentFromHtml(await playerDetailsHtml(player))]);
  } catch (err) {
    toast(err.message, true);
  }
};

function inventoryGrid(items, limit = 120) {
  items = asArray(items).slice(0, limit);
  if (!items.length) return empty("Предметов нет", "Источник инвентаря пуст или NBT parser недоступен.");
  return `<div class="inventory-grid">${items.map(item => `
    <div class="slot" title="${esc(item.id || item.displayName || "")}">
      <img src="${esc(item.iconUrl || `/assets/mc-icons/item/${item.icon || "barrier"}.png`)}" alt="" />
      <b>${esc(short(item.displayName || item.id || "item", 18))}</b>
      <span>x${esc(item.Count ?? item.count ?? 1)}  slot ${esc(item.Slot ?? item.slot ?? "")}</span>
    </div>
  `).join("")}</div>`;
}

function openInventoryModal(snapshot) {
  const inv = asArray(snapshot.inventory);
  const ender = asArray(snapshot.enderChest);
  {
    const overlay = buildModalOverlay();
    const subtitle = `${dt(snapshot.createdAt)} · ${cleanText(snapshot.world || "игровой мир")}`;
    const modal = buildModalShell(`Снимок инвентаря: ${cleanText(snapshot.name || state.selectedPlayer || "игрок")}`, subtitle, { closeLabel: "Закрыть" });
    const summary = document.createElement("section");
    summary.className = "layout-grid grid-4";
    summary.append(fragmentFromHtml([
      metric("Слоты инвентаря", inv.length),
      metric("Слоты эндера", ender.length),
      metric("AR в инвентаре", snapshot.arInInventory ?? 0, "", "good"),
      metric("AR в эндере", snapshot.arInEnderChest ?? 0, "", "good"),
    ].join("")));
    modal.append(summary);
    modal.append(makeElement("div", "spacer-14"));
    const inventoryPanel = document.createElement("div");
    inventoryPanel.append(fragmentFromHtml(panel("Инвентарь", "", inventoryGrid(inv))));
    const enderPanel = document.createElement("div");
    enderPanel.append(fragmentFromHtml(panel("Эндер-сундук", "", inventoryGrid(ender))));
    modal.append(inventoryPanel);
    modal.append(enderPanel);
    overlay.append(modal);
    replaceChildrenSafe($("modalRoot"), [overlay]);
    return;
  }
  /* legacy modal fallback removed
    <div class="modal-overlay" data-click="if(event.target===this) closeModal()">
      <div class="modal">
        <div class="modal-head">
          <div>
            <h2>Снимок инвентаря: ${esc(snapshot.name || state.selectedPlayer)}</h2>
            <p>${dt(snapshot.createdAt)} · ${esc(snapshot.world || "игровой мир")}</p>
          </div>
          <button class="btn btn-secondary" data-click="closeModal()">Закрыть</button>
        </div>
        <section class="layout-grid grid-4">
          ${metric("Слоты инвентаря", inv.length)}
          ${metric("Слоты эндера", ender.length)}
          ${metric("  ", snapshot.arInInventory ?? 0, "", "good")}
          ${metric("  ", snapshot.arInEnderChest ?? 0, "", "good")}
        </section>
        <div class="spacer-14"></div>
        ${panel("Инвентарь", "", inventoryGrid(inv))}
        ${panel("Эндер-сундук", "", inventoryGrid(ender))}
      </div>
    </div>
  */
}

async function loadInventories() {
  setLoading("Готовлю инвентари");
  const playersData = await safeApi("/api/players", { players: [] });
  const rows = asArray(playersData.players).map(p => ({ name: cleanText(p.name || p.username || p.player || p.uuid), uuid: p.uuid || "" })).filter(x => x.name);
  setView(`
    <section class="layout-grid grid-wide">
      ${panel("Снимки инвентарей", "Создай снимок по игроку и открой историю из профиля", `
        <div class="toolbar">
          <input id="inventoryPlayerInput" class="grow" placeholder="Ник игрока" list="playersDatalist" />
          <button class="btn btn-primary" data-click="snapshotInventoryFromInput()">Создать снимок</button>
        </div>
        <datalist id="playersDatalist">${rows.map(x => `<option value="${esc(x.name)}"></option>`).join("")}</datalist>
        ${table("inventory-players", rows, [
          { key: "name", label: "Игрок" },
          { key: "name", label: "Действие", render: v => `<button class="btn btn-secondary btn-small" data-click="snapshotInventory('${esc(v)}')">Снимок</button>` }
        ])}
      `)}
      ${panel("Проверка инвентаря", "Снимки, история и AR.", `
        ${kv([
          ["Сценарий", "Выбрать игрока → создать снимок → открыть профиль → сравнить с историей"],
          ["Только чтение", "Читает playerdata и сохраняет снимки в data/"],
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
  const treasuryBudget = Number(detail.treasury?.balance || detail.presidentBudget?.balance_ar || 0);
  state.electionApplications = Object.fromEntries(applicationRows.map((row) => [row.id, row]));
  setView(`
    <section class="dashboard-hero election-hero">
      <div class="hero-copy">
        <span class="hero-kicker">Выборы CopiMine</span>
        <h2>${esc(electionStageLabel(election.current_stage || election.status || web.stageTitle, "Пауза"))}</h2>
        <p>Стадия, кандидаты, участки, законы и открытая казна.</p>
        <div class="hero-actions">
          ${pill(`Тур ${esc(election.current_round || summary.round || web.raw?.round || 1)}`, "neutral")}
          ${pill(`${esc(summary.candidateCount ?? candidateRows.length)} `, candidateRows.length ? "good" : "warn")}
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
          <img src="/assets/mc-icons/item/diamond.png" alt="" />
          <strong>${formatAr(treasuryBudget)}</strong>
          <span>открытая казна</span>
        </div>
      </div>
    </section>
    <section class="layout-grid grid-2">
      ${panel("Состояние цикла", "Этап, тур и президент.", kv([
        ["Этап", electionStageLabel(election.current_stage || election.status || web.stageTitle, "—")],
        ["Тур", election.current_round || summary.round || web.raw?.round || "1"],
        ["Президент", detail.president?.president_name || detail.president?.minecraft_name || election.president_name || overview.president || "—"],
        [" ", election.candidate_limit ?? web.raw?.candidateLimit ?? ""],
        ["Срок президента", election.president_term_days ? `${election.president_term_days} дн.` : "—"],
        ["Режим сайта", data.readOnly ? "Только просмотр" : "Управление разрешено"]
      ]), siteBulletList([
        "Сводка по выборам.",
        "Кандидаты, законы и казна.",
        "Смена этапов только в игре."
      ]))}
      ${panel("Пульт цикла", "Этапы запускаются только в игре.", `
        <div class="book-status-strip">
          ${pill("Только просмотр", "warn")}
          ${pill("Игровой GUI", "neutral")}
          ${pill("Аудит включён", "good")}
        </div>
        <div class="spacer-12"></div>
        ${siteBulletList([
          "Управление этапами через /cadm.",
          "На сайте: обзор, книги, законы и казна.",
          "Аварийных кнопок здесь нет."
        ])}
      `)}
      ${panel("Заявки кандидатов", "Книги, статусы комиссии и решение админа.", `
        ${electionApplicationCards(applicationRows)}
        <div class="spacer-12"></div>
        ${pendingLawRows.length ? `<div class="book-status-strip">${pendingLawRows.slice(0, 5).map((row) => pill(`Закон на проверке · ${short(row.text || "", 42)}`, "warn")).join("")}</div>` : ""}
      `)}
      ${panel("Кандидаты и результаты", "Список кандидатов и результаты туров.", `
        ${candidateCards(candidateRows)}
        <div class="spacer-12"></div>
        ${resultBars(candidateRows, ["player_name", "display_name", "name"], ["last_result", "total", "votes", "raw_votes"])}
      `)}
      ${panel("Участки и ЦИК", "Участки, комиссии и приём бюллетеней.", `
        ${stationCardsHtml(pollingStations, voteDeposits)}
        <div class="spacer-12"></div>
        ${kv([
          [" ", summary.activePollingStations ?? pollingStations.length],
          [" ", summary.voteDeposits ?? voteDeposits.reduce((sum, row) => sum + number(row.votes), 0)],
          ["Сигналы антифрода", fraudRows.length || "не найдено"]
        ])}
      `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Президент и законы", "Действующий срок и законы.", `
        ${lawCards(lawRows)}
        <div class="spacer-12"></div>
        ${pendingLawRows.length ? `<div class="law-stack">${pendingLawRows.slice(0, 5).map((row) => `
          <article class="law-pending-row">
            <strong>${esc(short(row.text || "", 88) || "Без текста")}</strong>
            <span>${dt(row.created_at)}</span>
          </article>
        `).join("")}</div>` : empty("Новых законов на проверке нет", "Когда президент отправит новый закон или замену, он появится здесь.")}
      `)}
      ${panel("Казна", "Баланс, владелец и история.", kv([
        ["Баланс", formatAr(treasuryBudget)],
        ["Владелец", detail.treasury?.ownerName || overview.president || "не указан"],
        ["Источник", "игровая экономика и витрины"],
        ["Публичность", "только открытые записи"]
      ]))}
    </section>
    ${panel("Журнал цикла", "Этапы, книги кандидатов, законы и президент.", `
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
        `).join("") : empty("Событий пока нет", "Избирательных событий ещё не было.")}
      </div>
    `)}
  `);
}

async function loadEconomy() {
  return getAdminCommercePages().loadEconomy();
}

window.createEconomySnapshot = async () => getAdminCommercePages().createEconomySnapshot();

window.scanAresWorld = async () => getAdminCommercePages().scanAresWorld();

window.adminArAddBalance = async () => getAdminCommercePages().adminArAddBalance();

window.adminDonationAddBalance = async () => getAdminCommercePages().adminDonationAddBalance();

window.playerRandomizeBankPin = async (player = state.selectedPlayer) => {
  if (!player) return toast("Игрок не выбран", true);
  const headers = await dangerConfirm(`Назначить новый случайный постоянный PIN для ${player}?`, "PLAYER_BANK_PIN_RANDOMIZE");
  if (!headers) return;
  try {
    const result = await api(`/api/players/${encodeURIComponent(player)}/bank-pin/randomize`, {
      method: "POST",
      headers,
      body: "{}"
    });
    toast(`Новый PIN для ${player}: ${result.pin}`);
    if (state.tab === "players") replaceChildrenSafe($("playerDetails"), [fragmentFromHtml(await playerDetailsHtml(player))]);
  } catch (err) {
    toast(err.message, true);
  }
};

window.playerSetBankPinAdmin = async (player = state.selectedPlayer) => {
  if (!player) return toast("Игрок не выбран", true);
  const pin = $("playerAdminPinInput")?.value?.trim() || "";
  if (!pin.trim()) return;
  if (!/^\d{4,8}$/.test(pin)) return toast("PIN должен состоять из 4-8 цифр.", true);
  const headers = await dangerConfirm(`Задать новый PIN для ${player}? Старый PIN перестанет работать сразу.`, "PLAYER_BANK_PIN_SET");
  if (!headers) return;
  try {
    const result = await api(`/api/players/${encodeURIComponent(player)}/bank-pin/set`, {
      method: "POST",
      headers,
      body: JSON.stringify({ new_pin: pin.trim() })
    });
    toast(`PIN для ${player} обновлён: ${result.pin}`);
    if ($("playerAdminPinInput")) $("playerAdminPinInput").value = "";
    if (state.tab === "players") replaceChildrenSafe($("playerDetails"), [fragmentFromHtml(await playerDetailsHtml(player))]);
  } catch (err) {
    toast(err.message, true);
  }
};

window.adminDonationTestPurchase = async () => getAdminCommercePages().adminDonationTestPurchase();

window.adminDonationMarkPaid = async (sessionId) => getAdminCommercePages().adminDonationMarkPaid(sessionId);

window.adminDonationCancelSession = async (sessionId) => getAdminCommercePages().adminDonationCancelSession(sessionId);

window.adminSetTreasuryPin = async () => getAdminCommercePages().adminSetTreasuryPin();

function artifactStatusTone(status) {
  const value = String(status || "").toUpperCase();
  if (["DELIVERED", "CLAIMED", "COMPLETED", "ACTIVE", "PAID"].includes(value)) return "good";
  if (["PENDING", "PENDING_DELIVERY", "UNCLAIMED", "RESERVED", "DELIVERING", "DELIVERY_REVIEW", "LOST_RECLAIMABLE", "CLAIM_PENDING", "CREATED"].includes(value)) return "warn";
  if (["FAILED", "REFUNDED", "SUSPICIOUS", "BLOCKED", "CANCELLED", "BROKEN", "CONSUMED", "DELETED_AS_INVALID", "REPLACED_AFTER_LOSS", "EXPIRED"].includes(value)) return "bad";
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
    <section class="layout-grid grid-4" data-artifact-health="Bridge PostgreSQL">
      ${metric("Лавка", health.jarsOk ? "работает" : "проверить", health.jarsOk ? "каталог и выдача подключены" : `активно: ${asArray(health.activeJars).join(", ") || "нет данных"}`, health.jarsOk ? "good" : "bad")}
      ${metric("Данные", health.postgres ? "подключены" : "недоступны", "покупки, выдачи и ремонты", health.postgres ? "good" : "bad")}
      ${metric("Каталог", counts.artifact_items_catalog ?? asArray(catalog.items).length, "предметов")}
      ${metric("Выдача", counts.artifact_pending_deliveries ?? asArray(pending.deliveries).length, "ожидают игрока", Number(counts.artifact_pending_deliveries || 0) ? "warn" : "good")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Каталог лавки", "Предметы, которые игроки покупают за AR.", table("artifact-catalog", asArray(catalog.items), [
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
      ${panel("Отложенная выдача", "Оплаченные выдачи, ещё не забранные игроками.", table("artifact-pending", asArray(pending.deliveries), [
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
      ${panel("Подозрительные предметы", "Предметы без официальной записи в базе.", table("artifact-suspicious", asArray(suspicious.events), [
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
  const [status, applications, reports, playersData] = await Promise.all([
    safeApi("/api/" + String.fromCharCode(100,105,115,99,111,114,100) + "/status", {}),
    safeApi("/api/applications", { applications: [] }),
    safeApi("/api/reports", { reports: [] }),
    safeApi("/api/players", { players: [] })
  ]);
  const configured = status.configured || {};
  const ready = Object.values(configured).filter(Boolean).length;
  const total = Object.keys(configured).length || 1;
  const requestPlayers = asArray(playersData.players);
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Связь", `${ready}/${total}`, "бот, каналы и роль", ready === total ? "good" : "warn")}
      ${metric("Заявки", asArray(applications.applications).length, "ожидают обработки")}
      ${metric("Жалобы", asArray(reports.reports).length, "активные обращения")}
      ${metric("Публикации", asArray(status.outbox).length, "сообщения для Discord")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Новая заявка", "Создайте обращение вручную, если игрок написал вне сайта.", `
        <div class="form-grid">
          <input id="appPlayer" placeholder="Ник игрока" list="requestPlayersDatalist" />
          <input id="appContact" placeholder="Контакт или игровой ник" list="requestPlayersDatalist" />
          <textarea id="appWhy" class="full" placeholder="Почему игрок хочет участвовать / стать кандидатом"></textarea>
          <button class="btn btn-primary full" data-click="createRequestApplication()">Создать заявку</button>
        </div>
        ${playerDatalistHtml("requestPlayersDatalist", requestPlayers)}
      `)}
      ${panel("Новая жалоба", "Запишите жалобу с игроком, целью и местом события.", `
        <div class="form-grid">
          <input id="repReporter" placeholder="Кто жалуется" list="requestPlayersDatalist" />
          <input id="repTarget" placeholder="На кого / цель" list="requestPlayersDatalist" />
          <select id="repSeverity"><option value="normal">normal</option><option value="high">high</option><option value="critical">critical</option></select>
          <input id="repWorld" placeholder="world" />
          <textarea id="repMessage" class="full" placeholder="Описание проблемы"></textarea>
          <button class="btn btn-primary full" data-click="createRequestReport()">Создать жалобу</button>
        </div>
      `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Заявки", "Очередь обращений от игроков.", table("requests-apps", asArray(applications.applications), [
        { key: "id", label: "ID" },
        { key: "player", label: "Игрок" },
        { key: "status", label: "Статус", render: v => pill(v || "pending", v === "approved" ? "good" : v === "rejected" ? "bad" : "warn") },
        { key: "createdAt", label: "Создано", render: v => dt(v) },
        { key: "id", label: "Действия", render: v => `<div class="action-strip"><button class="btn btn-secondary btn-small" data-click="requestApplicationStatus('${esc(v)}','approved')">Одобрить</button><button class="btn btn-secondary btn-small" data-click="requestApplicationStatus('${esc(v)}','rejected')">Отклонить</button></div>` }
      ], { pageSize: 10 }))}
      ${panel("Жалобы", "Рабочая очередь администрации", table("requests-reports", asArray(reports.reports), [
        { key: "id", label: "ID" },
        { key: "reporter", label: "Автор" },
        { key: "target", label: "Цель" },
        { key: "status", label: "Статус", render: v => pill(v || "open", v === "closed" ? "good" : v === "rejected" ? "bad" : "warn") },
        { key: "severity", label: "Важность" },
        { key: "id", label: "Действия", render: v => `<div class="action-strip"><button class="btn btn-secondary btn-small" data-click="requestReportStatus('${esc(v)}','in_progress')">Взять</button><button class="btn btn-secondary btn-small" data-click="requestReportStatus('${esc(v)}','closed')">Закрыть</button></div>` }
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
        ["view-distance", properties["view-distance"] ?? ""],
        ["simulation-distance", properties["simulation-distance"] ?? ""],
        ["network-compression-threshold", properties["network-compression-threshold"] ?? ""],
        ["entity-broadcast-range-percentage", properties["entity-broadcast-range-percentage"] ?? ""],
        ["sync-chunk-writes", properties["sync-chunk-writes"] ?? ""],
        ["world region files", world.regionFiles ?? 0],
        ["sample region size", bytes(world.sampleRegionSize || 0)]
      ]))}
      ${panel("Сводка плагинов", "Контроль, что активный CopiMine собран в один jar", kv([
        [" jar", plugins.totalJars ?? 0],
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
      ${metric("Панель сайта", status.ports?.backend8090?.online ? "Доступен" : "Нет связи", "backend 8090", status.ports?.backend8090?.online ? "good" : "bad")}
      ${metric("Бэкапы", asArray(backups.backups).length, backups.dir || "data/backups")}
    </section>
    <section class="layout-grid grid-wide">
      ${panel("Управление сервером", "Проверка, сохранение и перезагрузка.", `
        <div class="action-strip">
          <button class="btn btn-secondary" data-click="serverControl('status')">Проверить</button>
          <button class="btn btn-secondary" data-click="serverControl('save-all')">Сохранить мир</button>
          <button class="btn btn-secondary" data-click="serverControl('restart')">Перезапуск</button>
          <button class="btn btn-danger" data-click="serverControl('stop')">Остановить</button>
        </div>
        <div class="spacer-12"></div>
        <div class="toolbar">
          <input id="rconCommand" class="grow" placeholder="Безопасная команда сервера" />
          <button class="btn btn-primary" data-click="runSafeRcon()">Выполнить</button>
        </div>
        <pre id="serverResponse" class="log-box log-box-tall">Ответ появится здесь</pre>
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
        <button class="btn btn-primary" data-click="createBackup(false)">Бэкап конфигов</button>
        <button class="btn btn-secondary" data-click="createBackup(true)">Бэкап с world data</button>
      </div>
      <div class="spacer-12"></div>
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
      ${metric("Чат", data.silentProfile ? "тихо" : "проверить", "без лишних сообщений игрокам", data.silentProfile ? "good" : "warn")}
      ${metric("Настройки", data.stableProfile ? "спокойные" : "проверить", "без автонаказаний и резких действий", data.stableProfile ? "good" : "warn")}
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
  const headers = labels[action] ? await dangerConfirm(`Опасное серверное действие: ${action}`, labels[action]) : {};
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
      ${panel("События плагинов", "Игровые и системные события панели.", table("plugin-events", asArray(events.rows), null, { pageSize: 18 }))}
    </section>
  `);
}

async function loadInvestigations() {
  setLoading("Готовлю расследования");
  const [sources, rows, playersData] = await Promise.all([
    safeApi("/api/investigations/sources", { tables: [] }),
    safeApi("/api/investigations/block-logs?limit=120", { rows: [] }),
    safeApi("/api/players", { players: [] })
  ]);
  const investigationPlayers = asArray(playersData.players);
  setView(`
    <section class="layout-grid grid-wide">
      ${panel("Поиск CoreProtect", "Фильтры по игроку, координатам и радиусу", `
        <div class="toolbar">
          <input id="invPlayer" placeholder="Игрок" list="investigationPlayersDatalist" />
          <input id="invX" placeholder="X" />
          <input id="invY" placeholder="Y" />
          <input id="invZ" placeholder="Z" />
          <input id="invRadius" placeholder="Радиус" value="0" />
          <button class="btn btn-primary" data-click="searchInvestigation()">Искать</button>
        </div>
        ${playerDatalistHtml("investigationPlayersDatalist", investigationPlayers)}
        <div id="investigationResults">${table("investigation-rows", asArray(rows.rows), null, { pageSize: 18 })}</div>
      `)}
      ${panel("Источники расследований", "Таблицы и колонки для поиска.", table("investigation-sources", asArray(sources.tables).map(t => ({ table: t.name, columns: asArray(t.columns).join(", ") })), [
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
  const resultsRoot = $("investigationResults");
  if (!resultsRoot) return;
  replaceChildrenSafe(resultsRoot, [fragmentFromHtml(table("investigation-rows", asArray(rows.rows), null, { pageSize: 18 }))]);
};

async function loadSources() {
  return getPluginRegistryPages().loadSources();
}

window.pluginRegistrySelect = async (pluginId) => getPluginRegistryPages().pluginRegistrySelect(pluginId);

window.pluginRegistryValidate = async (pluginId) => getPluginRegistryPages().pluginRegistryValidate(pluginId);

window.pluginRegistryBackup = async (pluginId) => getPluginRegistryPages().pluginRegistryBackup(pluginId);

window.pluginRegistryApply = async (pluginId) => getPluginRegistryPages().pluginRegistryApply(pluginId);

window.pluginRegistryReload = async (pluginId) => getPluginRegistryPages().pluginRegistryReload(pluginId);

async function loadAudit() {
  setLoading("Загружаю аудит");
  const data = await safeApi("/api/audit?limit=260", { rows: [] });
  setView(panel("Аудит", "Действия команды сервера и важные системные изменения", table("audit", asArray(data.rows), null, { pageSize: 25 })));
}

async function loadSecurity() {
  setLoading("Загружаю доступы");
  const [access, lists, whitelist, ipAlerts, playersData] = await Promise.all([
    safeApi("/api/security/access", {}),
    safeApi("/api/minecraft/access-lists", {}),
    safeApi("/api/admin/whitelist/requests?limit=60", { requests: [], count: 0 }),
    safeApi("/api/admin/security/ip-alerts?limit=60", { alerts: [], count: 0 }),
    safeApi("/api/players", { players: [] })
  ]);
  const whitelistRows = asArray(whitelist.requests);
  const alertRows = asArray(ipAlerts.alerts);
  const securityPlayers = asArray(playersData.players);
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Whitelist", asArray(lists.whitelist).length, "Игроки с доступом к серверу")}
      ${metric("Заявки", whitelistRows.filter(row => String(row.status || "").toUpperCase() === "PENDING").length, "Ждут Discord или web approval", whitelistRows.some(row => String(row.status || "").toUpperCase() === "PENDING") ? "warn" : "good")}
      ${metric("IP-alerts", alertRows.length, "Подозрительные регистрации и лимиты", alertRows.length ? "warn" : "good")}
      ${metric("Подтверждения", CONFIRM_HEADER, "Нужны для опасных действий", "neutral")}
    </section>
    ${safetyRail([
      ["Вход в панель", access.cookieAuth ? "сессия активна" : "проверь вход в панель", access.cookieAuth ? "good" : "warn"],
      ["Пароли", "хранятся как хэши", "good"],
      ["Опасные действия", `требуют точный код ${CONFIRM_HEADER}`, "warn"],
      ["Minecraft-доступ", "права и допуск меняются только через журналируемые действия", "good"]
    ])}
    <section class="layout-grid grid-2">
      ${panel("Команда панели", "Состав админов и отдельная регистрация новых аккаунтов перенесены во вкладку «Админы».", `
        <div class="stack gap-12">
          <p class="muted">Здесь остаётся только безопасность входа, whitelist и контроль Minecraft-доступа.</p>
          ${isJuniorAdminRole()
            ? '<div class="notice">Младший админ не может создавать или менять админ-аккаунты.</div>'
            : '<button class="btn btn-secondary full" data-click="openAdminsTab()">Открыть вкладку «Админы»</button>'}
        </div>
      `)}
      ${panel("Minecraft-доступ", "Выдай доступ, права или ограничение без лишних команд.", `
        <div class="form-grid">
          <input id="accessPlayer" placeholder="Ник игрока" list="securityPlayersDatalist" />
          <select id="accessAction">
            <option value="whitelist_add">Разрешить вход</option>
            <option value="whitelist_remove">Убрать доступ</option>
            <option value="op">Выдать OP</option>
            <option value="deop">Снять OP</option>
            <option value="ban">Забанить</option>
            <option value="pardon">Разбанить</option>
          </select>
          <input id="accessReason" class="full" placeholder="Причина" />
          <button class="btn btn-primary full" data-click="runAccessAction()">Выполнить</button>
        </div>
        ${playerDatalistHtml("securityPlayersDatalist", securityPlayers)}
        <div class="spacer-12"></div>
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
        { key: "id", label: "Действие", render: (value, row) => String(row.status || "").toUpperCase() === "PENDING" ? `<button class="btn btn-primary" data-click="approveWhitelistRequest('${esc(value)}')">Одобрить</button>` : '<span class="muted">Готово</span>' }
      ], { pageSize: 12 }) : empty("Whitelist-заявок пока нет", "Новых заявок нет."))}
      ${panel("IP-alerts", "Срабатывают при лимите регистраций и других подозрительных паттернах.", alertRows.length ? table("security-ip-alerts", alertRows, [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "ip", label: "IP" },
        { key: "username", label: "Логин" },
        { key: "minecraft_name", label: "Minecraft" },
        { key: "reason", label: "Причина" },
        { key: "status", label: "Статус" }
      ], { pageSize: 12 }) : empty("IP-alerts пока нет", "Подозрительных регистраций нет."))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Защита входа", "Сессия, хранилище и код подтверждения.", kv([
        ["Сессия входа", access.cookieAuth ? "активна" : "проверить"],
        ["Хранилище входа", access.authDb || "основное"],
        ["Состояние хранилища", access.authDbExists ? "готово" : "проверить"],
        ["Код подтверждения", CONFIRM_HEADER]
      ]))}
      ${panel("Права ролей", "Кто что может делать в админке.", safetyRail([
        ["Младший админ", "может работать в панели без опасных действий и без управления админами", "neutral"],
        ["Полный админ", "может создавать новые admin/junior_admin аккаунты и управлять рабочими разделами", "good"],
        ["Владелец", "дополнительно меняет owner-аккаунты и owner-only настройки", "warn"]
      ]))}
    </section>
  `);
}

async function loadAdmins() {
  setLoading("Загружаю команду панели");
  const [admins, access] = await Promise.all([
    safeApi("/api/security/admins", { admins: [], whitelistCandidates: [] }),
    safeApi("/api/security/access", {})
  ]);
  const rows = asArray(admins.admins);
  const activeRows = rows.filter(row => row.enabled);
  const fullAdmins = activeRows.filter(row => String(row.role || "").toLowerCase() === "admin" || String(row.role || "").toLowerCase() === "owner");
  const juniorAdmins = activeRows.filter(row => String(row.role || "").toLowerCase() === "junior_admin");
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Команда", rows.length, "Все аккаунты админ-панели")}
      ${metric("Активные", activeRows.length, "Могут войти прямо сейчас", activeRows.length ? "good" : "warn")}
      ${metric("Полные админы", fullAdmins.length, "admin и owner", fullAdmins.length ? "good" : "neutral")}
      ${metric("Младшие", juniorAdmins.length, "junior_admin", juniorAdmins.length ? "neutral" : "good")}
    </section>
    ${safetyRail([
      ["Создание", "Новый аккаунт получает отдельный логин в панель и не делит пароль с существующими админами.", "good"],
      ["Ограничения", "Owner-аккаунты, отключение и правка существующих админов остаются только у владельца панели.", "warn"],
      ["Minecraft-доступ", "По желанию сразу открывается whitelist и OP для рабочего входа в игру.", "good"],
      ["Подтверждение", `Создание требует код ${CONFIRM_HEADER}.`, "warn"]
    ])}
    <section class="layout-grid grid-2">
      ${panel("Администрация", "Кто уже имеет доступ к рабочему кабинету.", table("admins", rows, [
        { key: "username", label: "Ник" },
        { key: "role", label: "Роль", render: value => pill(value === "junior_admin" ? "младший админ" : (value || "admin"), value === "junior_admin" ? "neutral" : "good") },
        { key: "enabled", label: "Включён", render: v => v ? pill("да", "good") : pill("нет", "bad") },
        { key: "op", label: "OP", render: v => v ? pill("OP", "good") : pill("нет", "warn") },
        { key: "whitelisted", label: "Whitelist", render: v => v ? pill("есть", "good") : pill("нет", "warn") },
        { key: "canLogin", label: "Вход", render: v => v ? pill("может", "good") : pill("нельзя", "bad") }
      ], { pageSize: 14 }))}
      ${isJuniorAdminRole()
        ? panel("Регистрация админов", "Для младшего админа эта вкладка только обзорная.", `
            <div class="empty-state compact">
              <strong>Только просмотр</strong>
              <span>Создавать новые admin/junior_admin аккаунты могут только полный админ или владелец панели.</span>
            </div>
          `)
        : panel("Новый админ панели", "Создай сотруднику отдельный вход в рабочий кабинет сервера.", `
            <div class="form-grid danger-zone admin-create-panel">
              <input id="newAdminUsername" placeholder="Minecraft-ник" />
              <input id="newAdminPassword" type="password" placeholder="Временный пароль" />
              <select id="newAdminRole">
                <option value="admin">Полный админ</option>
                <option value="junior_admin">Младший админ</option>
              </select>
              <label class="check-line"><input id="newAdminWhitelist" type="checkbox" checked /> Сразу открыть доступ к серверу</label>
              <label class="check-line"><input id="newAdminOp" type="checkbox" /> Сразу выдать OP, если это нужно</label>
              <button class="btn btn-primary full" data-click="createAdminUser()">Создать админа</button>
            </div>
            <div class="notice">Этот экран создаёт только admin и junior_admin. Owner-аккаунты и изменение существующих учёток остаются у владельца панели.</div>
          `)}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Защита входа", "Сессия, хранилище и код подтверждения.", kv([
        ["Сессия входа", access.cookieAuth ? "активна" : "проверить"],
        ["Хранилище входа", access.authDb || "основное"],
        ["Состояние хранилища", access.authDbExists ? "готово" : "проверить"],
        ["Код подтверждения", CONFIRM_HEADER]
      ]))}
      ${panel("Что дальше", "Как работать с новыми аккаунтами после регистрации.", safetyRail([
        ["1. Создай учётку", "Выдай временный пароль и при необходимости сразу добавь whitelist/OP.", "neutral"],
        ["2. Передай доступ сотруднику", "После первого входа он сможет сменить пароль и работать только в разрешённых разделах.", "good"],
        ["3. Изменения ролей", "Отключение и глубокое редактирование текущих админов остаётся у владельца панели.", "warn"]
      ]))}
    </section>
  `);
}

window.approveWhitelistRequest = async (requestId) => {
  try {
    const headers = await dangerConfirm(`Одобрить whitelist-заявку ${requestId}`, "WHITELIST_APPROVE");
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
  const headers = await dangerConfirm(`Создать админа панели: ${username}`, "ADMIN_CREATE");
  if (!headers) return;
  try {
    await api("/api/security/admins", {
      method: "POST",
      headers,
      body: JSON.stringify({
        username,
        password,
        role: $("newAdminRole")?.value || "admin",
        ensure_whitelist: Boolean($("newAdminWhitelist")?.checked),
        ensure_op: Boolean($("newAdminOp")?.checked)
      })
    });
    $("newAdminUsername").value = "";
    $("newAdminPassword").value = "";
    toast("Админ создан");
    loadAdmins();
  } catch (err) {
    toast(err.message, true);
  }
};

window.openAdminsTab = () => setTab("admins");

window.runAccessAction = async () => {
  try {
    const action = $("accessAction").value;
    const headers = await dangerConfirm(`Изменить Minecraft-доступ: ${action} -> ${$("accessPlayer").value.trim()}`, `ACCESS_${action.toUpperCase()}`);
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
      ${panel("Конфигурация панели", "Параметры панели без секретов", kv([
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
  const [bank, donation, donationHistory] = await Promise.all([
    state.user.linked ? safeApi("/api/player/bank", { account: null, pin: {}, ledger: [] }) : Promise.resolve(null),
    safeApi("/api/player/donation/balance", { linked: false, balance: 0 }),
    safeApi("/api/player/donation/history", { history: [] })
  ]);
  const linked = Boolean(state.user.linked);
  const whitelisted = Boolean(state.user.whitelisted);
  const whitelistRequest = state.user.whitelistRequest || null;
  const balance = linked ? number(bank?.account?.balance || 0) : 0;
  const donationBalance = donation?.linked ? number(donation.balance || 0) : 0;
  const whitelistStatus = whitelisted ? "одобрен" : (whitelistRequest?.status || (linked ? "не отправлен" : "нужна привязка"));
  const historyRows = [
    ...asArray(bank?.ledger).map((row) => ({
      title: humanizeBankAction(row),
      section: "Банк AR",
      details: cleanText(row.details || row.note || ""),
      amount: formatAr(row.amount || 0),
      time: row.created_at || row.time || row.updated_at || 0
    })),
    ...asArray(donationHistory.history).map((row) => ({
      title: `Донат: ${cleanText(row.reason || row.source || "операция")}`,
      section: "Донат",
      details: cleanText(row.reason || row.source || ""),
      amount: formatDonate(row.delta || row.amount || 0),
      time: row.created_at || row.time || row.updated_at || 0
    }))
  ].sort((a, b) => Number(b.time || 0) - Number(a.time || 0)).slice(0, 14);
  setMiniHealthSummary(state.user.username || "игрок", [
    `Привязка: ${linked ? "есть" : "нет"}`,
    `Банк AR: ${formatAr(balance)}`,
  ]);
  setView(`
    <section class="layout-grid grid-2 account-ledger-head">
      ${metric("Банк AR", linked ? formatAr(balance) : "нужна привязка", linked ? "Игровой и сайт-счёт" : "Открой вкладку Minecraft", linked ? "good" : "warn")}
      ${metric("Донат-баланс", donation?.linked ? formatDonate(donationBalance) : "нужна привязка", donation?.linked ? "Покупки и выдачи" : "Привяжи Minecraft", donation?.linked ? "good" : "warn")}
    </section>
    ${panel("История платежей", "", historyRows.length ? `
      <div class="transaction-feed">
        ${historyRows.map((row) => `
          <article class="transaction-row">
            <div class="transaction-main">
              <strong>${esc(row.title)}</strong>
              <span>${esc([row.section, row.details || "без комментария"].filter(Boolean).join(" · "))}</span>
            </div>
            <div class="transaction-side">
              <strong>${esc(row.amount)}</strong>
              <span>${dt(row.time)}</span>
            </div>
          </article>
        `).join("")}
      </div>
    ` : empty("История пока пустая", "Операции появятся после первого перевода, покупки или пополнения."))}
    ${panel("Действия", "", `
      <div class="action-strip account-actions">
        <button class="btn btn-primary" data-click="setTab('bank')">Открыть банк</button>
        <button class="btn btn-secondary" data-click="setTab('donation-shop')">Донат-лавка</button>
        <button class="btn btn-secondary" data-click="setTab('link')">Minecraft</button>
        <button class="btn btn-secondary" data-click="setTab('settings')">Аккаунт</button>
        ${linked && !whitelisted && !whitelistRequest ? `<button class="btn btn-secondary" data-click="playerRequestWhitelist()">Whitelist</button>` : ""}
      </div>
      <div class="spacer-12"></div>
      ${kv([
        ["Логин", state.user.username || "—"],
        ["Minecraft", state.user.minecraftName || "не привязан"],
        ["Whitelist", whitelistStatus],
        ["Последний вход", dt(state.user.lastLoginAt)]
      ])}
    `)}
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
      ${panel("Статус привязки", "Minecraft-ник подтверждается кодом из игры.", kv([
        ["Логин сайта", state.user.username || "—"],
        ["Minecraft-ник", state.user.minecraftName || "—"],
        ["Привязан", linked],
        ["Создан", dt(state.user.createdAt)]
      ]))}
      ${panel("Привязка", "Запроси код в игре и подтверди его здесь.", safetyRail([
        ["1. Запроси код", "Укажи свой игровой ник, пока ты онлайн на сервере.", "good"],
        ["2. Прочитай в игре", "Код приходит в Minecraft-чат через сервер.", "neutral"],
        ["3. Подтверди на сайте", "Введи код здесь, чтобы открыть банк и личный кабинет игрока.", "good"]
      ]))}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Запросить одноразовый код", "Код выдаётся только в игре.", `
        <div class="form-grid">
          <input id="linkMinecraftName" value="${esc(state.user.minecraftName || "")}" placeholder="Minecraft-ник на сервере" />
          <button class="btn btn-primary full" data-click="playerRequestLinkCode()">Получить код в Minecraft</button>
        </div>
        <div class="spacer-12"></div>
        ${playerLinkSummary(state.playerLinkRequest)}
      `)}
      ${panel("Подтвердить код", "Введи одноразовый код из Minecraft-чата.", `
        <div class="form-grid">
          <input id="linkCodeInput" placeholder="Например: 7H2K9M4Q" />
          <button class="btn btn-primary full" data-click="playerConfirmLinkCode()">Подтвердить привязку</button>
        </div>
        ${linked ? '<div class="notice">Аккаунт уже привязан. Повторное подтверждение обновит активную привязку к тому же Minecraft-нику.</div>' : ""}
      `)}
    </section>
  `);
}

async function loadPlayerBank() {
  return getPlayerTreasuryPages().loadPlayerBank();
  setLoading("Загрузка банка AR");
  const me = await api("/api/player/me");
  state.user = me.account || {};
  if (!state.user.linked) {
    setView(`
      ${panel("Банк AR закрыт", "Для банка нужна привязка Minecraft-ника.", `
        <div class="notice">Сначала запроси одноразовый код привязки. После этого банк откроется здесь и в игре.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`)}
    `);
    return;
  }
  const bank = await api("/api/player/bank");
  const accounts = asArray(bank.accounts);
  if (!accounts.some((x) => String(x.scope || "").toUpperCase() === state.playerBankScope)) {
    state.playerBankScope = "PERSONAL";
  }
  const selectedAccount = accounts.find((x) => String(x.scope || "").toUpperCase() === state.playerBankScope) || accounts[0] || {};
  const pin = bank.pin || {};
  const tempPin = bank.temporaryPin || {};
  const treasuryPin = bank.treasuryPin || {};
  const transferLocked = Boolean(pin.mustChange || pin.status === "temporary-expired");
  const ledger = asArray(selectedAccount.ledger || bank.ledger);
  const usingTreasury = String(selectedAccount.scope || "").toUpperCase() === "TREASURY";
  const selectedPinState = usingTreasury ? (treasuryPin.visiblePin ? "Настроен" : "Не задан") : bankPinState(pin);
  const accountTabs = bank.canAccessTreasury ? `
    <div class="segmented">
      <button class="btn ${state.playerBankScope === "PERSONAL" ? "btn-primary" : "btn-secondary"}" data-click="selectPlayerBankScope('PERSONAL')">Личный счёт</button>
      <button class="btn ${state.playerBankScope === "TREASURY" ? "btn-primary" : "btn-secondary"}" data-click="selectPlayerBankScope('TREASURY')">Казна</button>
    </div>
  ` : "";
  setMiniHealthSummary(state.user.username || "игрок", [
    `${selectedAccount.label || "Банк AR"}: ${formatAr(selectedAccount.balance || bank.account?.balance || 0)}`,
    `PIN: ${selectedPinState}`,
  ]);
  setView(`
    <section class="layout-grid grid-4">
      ${metric("Баланс", formatAr(selectedAccount.balance || bank.account?.balance || 0), usingTreasury ? "Казна" : "Личный счёт", "good")}
      ${metric("Последние операции", ledger.length, "Подтверждённые записи", "neutral")}
      ${metric("PIN", selectedPinState, usingTreasury ? (treasuryPin.visiblePin ? `PIN казны: ${treasuryPin.visiblePin}` : "Задай PIN для казны") : (pin.locked ? `Заблокирован до ${dt(pin.lockedUntil)}` : (tempPin.code ? `Временный PIN до ${dt(tempPin.expiresAt)}` : "Нужен для переводов")), usingTreasury ? (treasuryPin.visiblePin ? "good" : "warn") : bankPinTone(pin))}
      ${metric("Minecraft", state.user.minecraftName || "—", "Привязан", "good")}
    </section>
    ${accountTabs}
    ${safetyRail([
      ["Счёт", usingTreasury ? "Доступен президенту и админам" : "Сайт, банкомат и игровые оплаты", "good"],
      ["PIN", usingTreasury ? (treasuryPin.visiblePin ? `PIN казны: ${treasuryPin.visiblePin}` : "Задайте PIN казны") : (tempPin.code ? "Сейчас действует временный PIN" : (pin.set ? "PIN уже задан" : "Задайте PIN")), usingTreasury ? (treasuryPin.visiblePin ? "good" : "warn") : (tempPin.code ? "warn" : (pin.set ? "good" : "warn"))],
      ["Блокировка", usingTreasury ? "Лимит попыток действует и для казны" : (pin.locked ? `PIN заблокирован до ${dt(pin.lockedUntil)}` : "После ошибок PIN временно блокируется"), pin.locked && !usingTreasury ? "bad" : "neutral"]
    ])}
    ${!usingTreasury && tempPin.code ? panel("Временный PIN", "Сначала войди по нему, потом задай новый.", kv([
      ["Временный PIN", tempPin.code],
      ["Выдан", dt(tempPin.createdAt)],
      ["Истекает", dt(tempPin.expiresAt)]
    ])) : ""}
    <section class="layout-grid grid-2">
      ${panel(usingTreasury ? "PIN казны" : "Задать или изменить PIN", usingTreasury ? "PIN казны виден президенту и админам." : (tempPin.code ? "Сначала введи временный PIN." : "PIN нужен для переводов."), `
        <div class="form-grid">
          ${usingTreasury ? `<div class="notice full">PIN казны: <strong>${esc(treasuryPin.visiblePin || "не задан")}</strong></div>` : ""}
          <input id="bankOldPin" type="password" inputmode="numeric" placeholder="${usingTreasury ? "Текущий PIN казны" : (tempPin.code ? "Текущий временный PIN" : "Текущий PIN")}" />
          <input id="bankNewPin" type="password" inputmode="numeric" placeholder="Новый PIN, 4-8 цифр" />
          <button class="btn btn-primary full" data-click="playerSetPin()">Сохранить PIN</button>
        </div>
      `)}
      ${panel(usingTreasury ? "Перевести AR из казны" : "Перевести AR", !usingTreasury && transferLocked ? "Переводы закрыты, пока временный PIN не заменён." : (usingTreasury ? "Доступно президенту и админам." : "Перевод на другой счёт."), (!usingTreasury && transferLocked) ? `
        <div class="notice">${pin.status === "temporary-expired" ? "Временный PIN истёк. Попроси команду сервера сбросить его ещё раз." : "Сначала задай личный PIN. После этого переводы и ATM-операции откроются автоматически."}</div>
      ` : `
        <div class="form-grid">
          <input id="bankRecipient" placeholder="Логин получателя или Minecraft-ник" />
          <input id="bankAmount" type="number" min="1" step="1" placeholder="Сумма" />
          <input id="bankPinInput" type="password" inputmode="numeric" placeholder="PIN" />
          <input id="bankNote" class="full" placeholder="Комментарий, необязательно" />
          <button class="btn btn-primary full" data-click="playerTransfer()">Отправить AR</button>
        </div>
      `)}
    </section>
    ${panel("Журнал операций", "Последние движения счёта.", transactionFeed(ledger, 18))}
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
    getPlayerTreasuryPages().loadPlayerBank();
  } catch (err) {
    toast(err.message, true);
  }
};

window.legacyPlayerSetPinDeprecated = async () => {
  return getPlayerTreasuryPages().playerSetPin();
  try {
    await api("/api/player/bank/pin", {
      method: "POST",
      body: JSON.stringify({
        old_pin: $("bankOldPin")?.value || "",
        new_pin: $("bankNewPin")?.value || "",
        account_scope: state.playerBankScope || "PERSONAL"
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

window.legacyPlayerTransferDeprecated = async () => {
  return getPlayerTreasuryPages().playerTransfer();
  try {
    const result = await api("/api/player/bank/transfer", {
      method: "POST",
      body: JSON.stringify({
        recipient: $("bankRecipient")?.value?.trim() || "",
        amount: number($("bankAmount")?.value || 0),
        pin: $("bankPinInput")?.value || "",
        note: $("bankNote")?.value?.trim() || "",
        from_account: state.playerBankScope || "PERSONAL"
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
  toast("Эта механика отключена.", true);
};

window.playerPayElectionTax = async () => getPlayerTreasuryPages().playerPayElectionTax();

window.legacySelectPlayerBankScopeDeprecated = async (scope = "PERSONAL") => {
  return getPlayerTreasuryPages().selectPlayerBankScope(scope);
  state.playerBankScope = String(scope || "PERSONAL").toUpperCase();
  setStoredUiState("copiminePlayerBankScope", state.playerBankScope);
  if (state.tab === "bank") {
    await loadPlayerBank();
  }
};

let playerDonationPages;
let adminCommercePages;
let adminCmsPages;
let adminNarcoticsRecipePages;
let pluginRegistryPages;
let playerAccountPages;
let playerArtifactPages;
let playerTreasuryPages;

function getAdminCommercePages() {
  if (!adminCommercePages) {
    adminCommercePages = createAdminCommercePages({
      $,
      state,
      api,
      safeApi,
      setLoading,
      setView,
      panel,
      metric,
      kv,
      resultBars,
      formatAr,
      formatDonate,
      asArray,
      dt,
      table,
      pill,
      artifactStatusTone,
      statusLabel,
      esc,
      cleanText,
      short,
      ledgerRows,
      dangerConfirm,
      number,
      randomActionKey,
      toast,
    });
  }
  return adminCommercePages;
}

function getAdminCmsPages() {
  if (!adminCmsPages) {
    adminCmsPages = createAdminCmsPages({
      $,
      state,
      api,
      safeApi,
      setLoading,
      setView,
      panel,
      metric,
      table,
      pill,
      esc,
      cleanText,
      short,
      dt,
      asArray,
      dangerConfirm,
      toast,
    });
  }
  return adminCmsPages;
}

function getAdminNarcoticsRecipePages() {
  if (!adminNarcoticsRecipePages) {
    adminNarcoticsRecipePages = createAdminNarcoticsRecipePages({
      $,
      state,
      api,
      safeApi,
      setLoading,
      setView,
      panel,
      metric,
      esc,
      cleanText,
      dangerConfirm,
      toast,
    });
  }
  return adminNarcoticsRecipePages;
}

function getPluginRegistryPages() {
  if (!pluginRegistryPages) {
    pluginRegistryPages = createPluginRegistryPages({
      $,
      state,
      api,
      safeApi,
      setLoading,
      setView,
      panel,
      table,
      pill,
      kv,
      asArray,
      esc,
      cleanText,
      empty,
      dbPolicyPanel,
      dangerConfirm,
      toast,
    });
  }
  return pluginRegistryPages;
}

function getPlayerAccountPages() {
  if (!playerAccountPages) {
    playerAccountPages = createPlayerAccountPages({
      state,
      setLoading,
      setView,
      panel,
      kv,
      safetyRail,
      safeApi,
      api,
      dt,
      bankPinState,
    });
  }
  return playerAccountPages;
}

function getPlayerTreasuryPages() {
  if (!playerTreasuryPages) {
    playerTreasuryPages = createPlayerTreasuryPages({
      $,
      state,
      setLoading,
      setView,
      panel,
      kv,
      safetyRail,
      api,
      dt,
      metric,
      formatAr,
      asArray,
      bankPinState,
      esc,
      transactionFeed,
      number,
      toast,
      setMiniHealthSummary,
      setStoredUiState,
    });
  }
  return playerTreasuryPages;
}

function getPlayerArtifactPages() {
  if (!playerArtifactPages) {
    playerArtifactPages = createPlayerArtifactPages({
      $,
      state,
      setLoading,
      api,
      safeApi,
      setView,
      panel,
      metric,
      table,
      pill,
      esc,
      cleanText,
      short,
      dt,
      asArray,
      empty,
      formatAr,
      number,
      statusLabel,
      artifactStatusTone,
      randomActionKey,
      toast,
    });
  }
  return playerArtifactPages;
}

function getPlayerDonationPages() {
  if (!playerDonationPages) {
    playerDonationPages = createPlayerDonationPages({
      state,
      setLoading,
      api,
      safeApi,
      setView,
      panel,
      metric,
      kv,
      dt,
      esc,
      short,
      formatDonate,
      asArray,
      cleanText,
      table,
      safetyRail,
      pill,
      number,
      donationSessionKey,
      statusLabel,
      artifactStatusTone,
      randomActionKey,
      setStoredUiState,
      removeStoredUiState,
      copyText,
      toast,
    });
  }
  return playerDonationPages;
}

async function loadPlayerDonationBalance() {
  return getPlayerDonationPages().loadPlayerDonationBalance();
}

async function loadPlayerDonationShop() {
  return getPlayerDonationPages().loadPlayerDonationShop();
}

async function loadPlayerDonationItems() {
  return getPlayerDonationPages().loadPlayerDonationItems();
}

async function legacyLoadPlayerBankDeprecated() {
  return getPlayerTreasuryPages().loadPlayerBank();
}

window.playerCreateDonationSession = async (amount) => getPlayerDonationPages().playerCreateDonationSession(amount);
window.playerRefreshDonationSession = async () => getPlayerDonationPages().playerRefreshDonationSession();
window.playerForgetDonationSession = () => getPlayerDonationPages().playerForgetDonationSession();
window.playerCopyDonationSessionCode = async () => getPlayerDonationPages().playerCopyDonationSessionCode();
window.playerCopyDonationPaymentUrl = async () => getPlayerDonationPages().playerCopyDonationPaymentUrl();
window.playerBuyDonationItem = async (itemId, displayName = "предмет", price = 0) =>
  getPlayerDonationPages().playerBuyDonationItem(itemId, displayName, price);
window.playerSetPin = async () => getPlayerTreasuryPages().playerSetPin();
window.playerTransfer = async () => getPlayerTreasuryPages().playerTransfer();
window.selectPlayerBankScope = async (scope = "PERSONAL") => getPlayerTreasuryPages().selectPlayerBankScope(scope);

async function legacyLoadPlayerArtifacts() {
  setLoading("Загрузка предметов");
  const [data, catalogPayload, bank] = await Promise.all([
    safeApi("/api/player/artifacts", { linked: false, purchases: [], pending: [], repairs: [] }),
    safeApi("/api/player/shop/ar-items", { items: [] }),
    safeApi("/api/player/bank", { account: { balance: 0 }, pin: { set: false }, linked: false })
  ]);
  if (!data.linked) {
    setView(panel("Артефакты", "Сначала привяжи Minecraft-аккаунт", empty("Minecraft-ник не привязан", "После привязки здесь будут покупки, выдача и ремонт предметов.")));
    return;
  }
  const catalog = asArray(catalogPayload.items);
  const purchases = asArray(data.purchases);
  const pending = asArray(data.pending);
  const repairs = asArray(data.repairs);
  const catalogRows = catalog.map((row) => ({
    ...row,
    limit_text: row.per_player_limit > 0 ? `${number(row.per_player_limit)} на игрока` : "без лимита",
    cooldown_text: row.cooldown_seconds ? `${number(row.cooldown_seconds)} сек.` : "—",
    action_html: row.enabled
      ? `<button class="btn btn-primary btn-small" data-click='playerBuyArItem(${JSON.stringify(String(row.item_id || ""))}, ${JSON.stringify(cleanText(row.display_name || row.name || row.item_id || "предмет"))}, ${number(row.price_ar || 0)})'>Купить</button>`
      : `<span class="btn btn-secondary btn-small disabled">Недоступно</span>`,
  }));
  const catalogMetric = metric("AR-каталог", catalog.length, "Цены и лимиты каталога", catalog.length ? "good" : "neutral");
  setView(`
    <section class="layout-grid grid-4">
      ${catalogMetric}
      ${metric("Баланс AR", formatAr(bank.account?.balance || 0), bank.pin?.set ? "PIN настроен" : "Нужно задать PIN", bank.pin?.set ? "good" : "warn")}
      ${metric("Покупки", purchases.length, "Подтверждённые покупки", purchases.length ? "good" : "neutral")}
      ${metric("Ожидают выдачи", pending.length, "Предметы ждут выдачи", pending.length ? "warn" : "good")}
      ${metric("Ремонты", repairs.length, "История ремонтов")}
    </section>
    <section class="layout-grid grid-2">
      ${panel("Мои покупки", "Купленные предметы.", table("player-artifact-purchases", purchases, [
        { key: "created_at", label: "Время", render: v => dt(v) },
        { key: "item_id", label: "Предмет" },
        { key: "shop_id", label: "Лавка" },
        { key: "price_ar", label: "AR" },
        { key: "status", label: "Статус", render: v => pill(statusLabel(v), artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
      ${panel("Ожидают выдачи", "Предметы ждут следующей выдачи.", table("player-artifact-pending", pending, [
        { key: "created_at", label: "Создано", render: v => dt(v) },
        { key: "item_id", label: "Предмет" },
        { key: "status", label: "Статус", render: v => pill(statusLabel(v || "pending"), artifactStatusTone(v)) }
      ], { pageSize: 12 }))}
    </section>
    ${panel("Каталог AR-лавки", "Текущие цены и лимиты.", table("player-artifact-catalog", catalogRows, [
      { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
      { key: "price_ar", label: "AR" },
      { key: "effect_description", label: "Эффект", render: (value) => short(value || "", 110) || "Без отдельного описания" },
      { key: "cooldown_text", label: "Кулдаун" },
      { key: "limit_text", label: "Лимит" },
      { key: "action_html", label: "Действие", render: (value) => value },
    ], { pageSize: 10 }), `<div class="notice">После покупки предмет появится в очереди выдачи. В игре забери его командой /cmartifacts claim.</div>`)}
    ${panel("Ремонт", "В Minecraft можно восстановить официальный предмет в лавке или командой /cmartifacts repair.", table("player-artifact-repairs", repairs, [
      { key: "created_at", label: "Время", render: v => dt(v) },
      { key: "item_id", label: "Предмет" },
      { key: "repair_cost_ar", label: "AR" },
      { key: "status", label: "Статус", render: v => pill(statusLabel(v), artifactStatusTone(v)) }
    ], { pageSize: 12 }), `<button class="btn btn-secondary" data-click="setTab('donation-items')">Открыть мои донат-предметы</button>`)}
  `);
}

async function loadPlayerArtifacts() {
  return getPlayerArtifactPages().loadPlayerArtifacts();
}

window.playerBuyArItem = async (...args) => getPlayerArtifactPages().playerBuyArItem(...args);
window.playerSelectArItem = (itemId) => getPlayerArtifactPages().playerSelectArItem(itemId);
window.playerArtifactSearch = (value) => getPlayerArtifactPages().playerArtifactSearch(value);

async function loadPlayerHistory() {
  setLoading("Загрузка истории игрока");
  const [bank, artifacts, donation, owned] = await Promise.all([
    safeApi("/api/player/bank", { ledger: [] }),
    safeApi("/api/player/artifacts", { purchases: [], pending: [], repairs: [] }),
    safeApi("/api/player/donation/history", { history: [] }),
    safeApi("/api/player/shop/owned", { linked: false, claims: [], instances: [], summary: {} })
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
      details: cleanText(row.reason || "donation"),
      section: "Донат"
    })),
    ...asArray(owned.claims).map((row) => ({
      title: `Выдача: ${cleanText(row.display_name || row.item_id || "донат-предмет")}`,
      time: row.updated_at || row.created_at || row.purchase_created_at,
      amount: row.price_donation || 0,
      details: [statusLabel(row.status || "pending"), cleanText(row.purchase_source || "donation_shop")].filter(Boolean).join(" · "),
      section: "Выдачи"
    })),
    ...asArray(owned.instances).map((row) => ({
      title: `Экземпляр: ${cleanText(row.display_name || row.item_id || "донат-предмет")}`,
      time: row.updated_at || row.created_at,
      amount: 0,
      details: statusLabel(row.status || "pending"),
      section: "Инстансы"
    }))
  ].sort((a, b) => String(b.time || "").localeCompare(String(a.time || "")));
  setView(`
    ${panel("История операций", "Все движения по счёту и покупкам по времени.", rows.length ? `
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
    ` : empty("История пока пустая", "Операций ещё не было."))}
  `);
}
async function loadPlayerSettings() {
  return getPlayerAccountPages().loadPlayerSettings();
}

async function loadPlayerSecurity() {
  return getPlayerAccountPages().loadPlayerSecurity();
}

async function loadPlayerSupport() {
  return getPlayerAccountPages().loadPlayerSupport();
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
    "narcotics-recipes": () => getAdminNarcoticsRecipePages().loadRecipes(),
    cms: () => getAdminCmsPages().loadCms(),
    settings: loadSettings,
    admins: loadAdmins,
    security: loadSecurity,
    audit: loadAudit
  };
  const playerLoaders = {
    cabinet: loadPlayerCabinet,
    link: loadPlayerLink,
    bank: () => getPlayerTreasuryPages().loadPlayerBank(),
    "donation-balance": loadPlayerDonationBalance,
    "donation-shop": loadPlayerDonationShop,
    "donation-items": loadPlayerDonationItems,
    history: loadPlayerHistory,
    settings: loadPlayerSettings,
    security: loadPlayerSecurity,
    support: loadPlayerSupport,
    artifacts: loadPlayerArtifacts
  };
  const loaders = isPlayerRole() ? playerLoaders : adminLoaders;
  try {
    await (loaders[state.tab] || loaders[defaultTab()] || loadDashboard)(silent);
  } catch (err) {
    setView(panel("Ошибка загрузки", "Раздел не смог получить данные", empty(err.message || "Неизвестная ошибка", "Проверь соединение сайта с сервером и повтори попытку.")));
  }
}

function wire() {
  wireDataClickDelegation();
  wireDataInputDelegation();
  wirePublicSite();
  $("loginForm")?.addEventListener("submit", login);
  $("logout")?.addEventListener("click", () => {
    if (!confirm("Выйти из кабинета CopiMine?")) return;
    logout(true);
  });
  syncTopbarActions();
  $("guestPagesBtn")?.addEventListener("click", showGuestPages);
  $("refreshBtn")?.addEventListener("click", () => loadCurrent());
  if ($("mobileNavToggle")) {
    $("mobileNavToggle").setAttribute("aria-expanded", "false");
    $("mobileNavToggle").addEventListener("click", (event) => {
      event.stopPropagation();
      setMobileNav(!$("app")?.classList.contains("nav-open"));
    });
  }
  document.addEventListener("click", (event) => {
    if (!$("app")?.classList.contains("nav-open")) return;
    if (event.target.closest(".sidebar") || event.target.closest("#mobileNavToggle")) return;
    setMobileNav(false);
  });
  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") setMobileNav(false);
  });
  window.addEventListener("hashchange", () => {
    const route = parseHashRoute(location.hash);
    const next = route.tab;
    const sessionId = String(route.params.get("session") || "").trim();
    const itemId = String(route.params.get("item") || "").trim().toLowerCase();
    if (sessionId) {
      state.donationSessionId = sessionId;
      setStoredUiState("copimineDonationSessionId", sessionId);
    }
    state.donationFocusItemId = itemId;
    if (!state.role && PUBLIC_GUEST_HASH_ROUTES.has(next)) return;
    if (next !== state.tab) {
      setTab(next);
      return;
    }
    if (["donation-balance", "donation-shop", "donation-items"].includes(state.tab)) {
      loadCurrent(true);
    }
  });
  syncAuthUi();
}

async function boot() {
  wire();
  setBootState("loading");
  await refreshCsrfCookie();
  await bootAuthed({ quiet: true });
}

Object.assign(dataClickHandlers, {
  adminArAddBalance: fromWindow("adminArAddBalance"),
  adminDonationAddBalance: fromWindow("adminDonationAddBalance"),
  adminDonationCancelSession: fromWindow("adminDonationCancelSession"),
  adminDonationMarkPaid: fromWindow("adminDonationMarkPaid"),
  adminDonationTestPurchase: fromWindow("adminDonationTestPurchase"),
  adminCmsDisable: (...args) => getAdminCmsPages().adminCmsDisable(...args),
  adminCmsEdit: (...args) => getAdminCmsPages().adminCmsEdit(...args),
  adminCmsNew: () => getAdminCmsPages().adminCmsNew(),
  adminCmsSave: () => getAdminCmsPages().adminCmsSave(),
  adminRecipeAdd: (...args) => getAdminNarcoticsRecipePages().adminRecipeAdd(...args),
  adminRecipeClear: () => getAdminNarcoticsRecipePages().adminRecipeClear(),
  adminRecipeRemove: (...args) => getAdminNarcoticsRecipePages().adminRecipeRemove(...args),
  adminRecipeSave: () => getAdminNarcoticsRecipePages().adminRecipeSave(),
  adminRecipeTab: (...args) => getAdminNarcoticsRecipePages().adminRecipeTab(...args),
  adminRecipeTogglePicker: () => getAdminNarcoticsRecipePages().adminRecipeTogglePicker(),
  adminSetTreasuryPin: fromWindow("adminSetTreasuryPin"),
  approveWhitelistRequest: fromWindow("approveWhitelistRequest"),
  closeModal: fromWindow("closeModal"),
  createAdminUser: fromWindow("createAdminUser"),
  createBackup: fromWindow("createBackup"),
  createEconomySnapshot: fromWindow("createEconomySnapshot"),
  createRequestApplication: fromWindow("createRequestApplication"),
  createRequestReport: fromWindow("createRequestReport"),
  exportTable: fromWindow("exportTable"),
  loadPlayers,
  logout,
  openElectionApplicationBook: fromWindow("openElectionApplicationBook"),
  pageTable: fromWindow("pageTable"),
  playerAction: fromWindow("playerAction"),
  playerActionFromPanel: fromWindow("playerActionFromPanel"),
  playerBuyArItem: fromWindow("playerBuyArItem"),
  playerBuyDonationItem: fromWindow("playerBuyDonationItem"),
  playerConfirmLinkCode: fromWindow("playerConfirmLinkCode"),
  playerCopyDonationPaymentUrl: fromWindow("playerCopyDonationPaymentUrl"),
  playerCopyDonationSessionCode: fromWindow("playerCopyDonationSessionCode"),
  playerCreateDonationSession: fromWindow("playerCreateDonationSession"),
  playerForgetDonationSession: fromWindow("playerForgetDonationSession"),
  playerPayElectionTax: fromWindow("playerPayElectionTax"),
  playerRandomizeBankPin: fromWindow("playerRandomizeBankPin"),
  playerRefreshDonationSession: fromWindow("playerRefreshDonationSession"),
  playerRequestLinkCode: fromWindow("playerRequestLinkCode"),
  playerRequestWhitelist: fromWindow("playerRequestWhitelist"),
  playerSelectArItem: fromWindow("playerSelectArItem"),
  playerResetBankPin: fromWindow("playerResetBankPin"),
  playerSetBankPinAdmin: fromWindow("playerSetBankPinAdmin"),
  playerSetPin: fromWindow("playerSetPin"),
  playerTransfer: fromWindow("playerTransfer"),
  pluginRegistryApply: fromWindow("pluginRegistryApply"),
  pluginRegistryBackup: fromWindow("pluginRegistryBackup"),
  pluginRegistryReload: fromWindow("pluginRegistryReload"),
  pluginRegistrySelect: fromWindow("pluginRegistrySelect"),
  pluginRegistryValidate: fromWindow("pluginRegistryValidate"),
  requestApplicationStatus: fromWindow("requestApplicationStatus"),
  requestReportStatus: fromWindow("requestReportStatus"),
  reviewElectionApplication: fromWindow("reviewElectionApplication"),
  runAccessAction: fromWindow("runAccessAction"),
  runSafeRcon: fromWindow("runSafeRcon"),
  scanAresWorld: fromWindow("scanAresWorld"),
  searchInvestigation: fromWindow("searchInvestigation"),
  selectPlayer: fromWindow("selectPlayer"),
  selectPlayerBankScope: fromWindow("selectPlayerBankScope"),
  serverControl: fromWindow("serverControl"),
  setTab,
  snapshotInventory: fromWindow("snapshotInventory"),
  snapshotInventoryFromInput: fromWindow("snapshotInventoryFromInput"),
  sortTable: fromWindow("sortTable"),
});

Object.assign(dataInputHandlers, {
  adminCmsPickAsset: (value) => getAdminCmsPages().adminCmsPickAsset(value),
  adminCmsSelect: () => getAdminCmsPages().adminCmsSelect(),
  adminGlobalSearch: (value) => {
    state.adminSearchQuery = String(value || "");
    renderAdminSearchDock();
  },
  adminRecipeSearch: (value) => getAdminNarcoticsRecipePages().adminRecipeSearch(value),
  adminRecipeSelect: (value) => getAdminNarcoticsRecipePages().adminRecipeSelect(value),
  filterPlayers: fromWindow("filterPlayers"),
  filterTable: fromWindow("filterTable"),
  playerArtifactSearch: fromWindow("playerArtifactSearch"),
  playerSelectArItem: fromWindow("playerSelectArItem"),
});

boot();
