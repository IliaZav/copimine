export const APP_ROUTE_FILES = Object.freeze({
  dashboard: "/cabinet/dashboard.html",
  players: "/cabinet/players.html",
  stats: "/cabinet/stats.html",
  economy: "/cabinet/economy.html",
  // These sections share the existing cabinet shells but still need stable
  // route identities so a reload/back button never falls back to overview.
  shops: "/cabinet/artifacts.html?route=shops",
  artifacts: "/cabinet/artifacts.html",
  elections: "/cabinet/elections.html",
  requests: "/cabinet/requests.html",
  inventories: "/cabinet/inventories.html",
  investigations: "/cabinet/investigations.html",
  anticheat: "/cabinet/anticheat.html",
  logs: "/cabinet/logs.html",
  audit: "/cabinet/audit.html",
  server: "/cabinet/server.html",
  admins: "/cabinet/admins.html",
  security: "/cabinet/security.html",
  sources: "/cabinet/sources.html",
  settings: "/cabinet/settings.html",
  "narcotics-recipes": "/cabinet/settings.html?route=narcotics-recipes",
  launcher: "/cabinet/settings.html?route=launcher",
  news: "/cabinet/settings.html?route=news",
  events: "/cabinet/settings.html?route=events",
  cms: "/cabinet/settings.html?route=cms",
  cabinet: "/cabinet/cabinet.html",
  balance: "/cabinet/balance.html",
  bank: "/cabinet/bank.html",
  transfer: "/cabinet/transfer.html",
  "donation-balance": "/cabinet/donation-balance.html",
  "donation-shop": "/cabinet/donation-shop.html",
  "donation-items": "/cabinet/donation-items.html",
  purchases: "/cabinet/purchases.html",
  history: "/cabinet/history.html",
  support: "/cabinet/support.html",
  link: "/cabinet/link.html",
});

export const ROLE_HOME_ROUTES = Object.freeze({
  player: "balance",
  junior_admin: "dashboard",
  admin: "dashboard",
  owner: "dashboard",
});

const LAUNCHER_CHALLENGE_PATTERN = /^[A-Za-z0-9_-]{16,96}$/;
const LAUNCHER_CODE_PATTERN = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/;
const MINECRAFT_NAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/;
const LAUNCHER_BINDING_PATH = "/launcher-link.html";
const LEGACY_LAUNCHER_BINDING_PATH = "/cabinet/link.html";

function launcherBindingQuery(search = "") {
  const source = new URLSearchParams(String(search || "").replace(/^\?/, ""));
  const challenge = source.get("launcher_challenge") || "";
  const code = source.get("launcher_code") || "";
  const nick = source.get("launcher_nick") || "";
  if (!LAUNCHER_CHALLENGE_PATTERN.test(challenge) || !LAUNCHER_CODE_PATTERN.test(code) || !MINECRAFT_NAME_PATTERN.test(nick)) {
    return "";
  }
  const safe = new URLSearchParams();
  safe.set("launcher_challenge", challenge);
  safe.set("launcher_code", code);
  safe.set("launcher_nick", nick);
  return safe.toString();
}

export function launcherBindingHrefFromSearch(search = "") {
  const query = launcherBindingQuery(search);
  return query ? `${LAUNCHER_BINDING_PATH}?${query}` : "";
}

export function launcherReturnHrefFromAuthSearch(search = "") {
  const source = new URLSearchParams(String(search || "").replace(/^\?/, ""));
  const raw = source.get("return") || "";
  if (!raw) return "";
  try {
    const target = new URL(raw, window.location.origin);
    const safePath = target.pathname === LAUNCHER_BINDING_PATH
      || target.pathname === LEGACY_LAUNCHER_BINDING_PATH;
    if (target.origin !== window.location.origin || !safePath) return "";
    return launcherBindingHrefFromSearch(target.search);
  } catch {
    return "";
  }
}

export function normalizeAppRoute(route, fallback = "dashboard") {
  const value = String(route || "").trim().toLowerCase();
  return APP_ROUTE_FILES[value] ? value : fallback;
}

export function defaultAppRouteForRole(role = "") {
  return ROLE_HOME_ROUTES[String(role || "").trim().toLowerCase()] || "dashboard";
}

export function appRouteHref(route, params = {}) {
  const normalized = normalizeAppRoute(route, "dashboard");
  const href = APP_ROUTE_FILES[normalized] || APP_ROUTE_FILES.dashboard;
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === null || value === undefined || value === "") return;
    search.set(String(key), String(value));
  });
  const query = search.toString();
  if (!query) return href;
  const separator = href.includes("?") ? "&" : "?";
  return `${href}${separator}${query}`;
}

export function authLandingHref(flow = "signin", returnHref = "") {
  const base = String(flow || "").toLowerCase() === "register" ? "/register.html" : "/signin.html";
  const safeReturn = launcherBindingHrefFromSearch(new URL(returnHref || "", window.location.origin).search);
  return safeReturn ? `${base}?return=${encodeURIComponent(safeReturn)}` : base;
}

export function routeFromHref(pathname = window.location.pathname) {
  const lower = String(pathname || "").trim().toLowerCase();
  const match = Object.entries(APP_ROUTE_FILES).find(([, href]) => lower.endsWith(href.toLowerCase()));
  return match ? match[0] : "";
}
