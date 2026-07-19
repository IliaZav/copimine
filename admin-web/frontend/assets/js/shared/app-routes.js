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
  return query ? `${href}?${query}` : href;
}

export function authLandingHref(flow = "signin") {
  return String(flow || "").toLowerCase() === "register" ? "/register.html" : "/signin.html";
}

export function routeFromHref(pathname = window.location.pathname) {
  const lower = String(pathname || "").trim().toLowerCase();
  const match = Object.entries(APP_ROUTE_FILES).find(([, href]) => lower.endsWith(href.toLowerCase()));
  return match ? match[0] : "";
}
