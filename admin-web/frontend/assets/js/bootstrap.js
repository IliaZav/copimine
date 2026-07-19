import { initThemeToggle } from "./theme/theme-toggle.js?v=20260720r11";
import { appRouteHref, normalizeAppRoute } from "./shared/app-routes.js";
import { initPublicNav } from "./public/public-nav.js";
import { initAuthPage, redirectLegacyAuthRoute } from "./auth/auth-page.js";

const LEGACY_PUBLIC_REDIRECTS = new Map([
  ["start", "index.html"],
  ["features", "index.html"],
  ["rules", "index.html"],
  ["help", "index.html"],
  ["servers", "server.html"],
  ["presidentbudgetshowcase", "server.html"],
  ["treasuryhistorysection", "server.html"],
  ["tops", "server.html"],
  ["shops", "shops.html"],
  ["mods", "mods.html"],
  ["join", "mods.html"],
  ["cabinet-zones", "signin.html"],
  ["register", "register.html"],
]);

let cabinetRuntimePromise = null;

function currentHashRoute(hashValue = window.location.hash) {
  return String(hashValue || "").replace(/^#/, "").split("?", 1)[0].trim().toLowerCase();
}

function pageKind() {
  return String(document.body?.dataset.pageKind || "").trim().toLowerCase();
}

function normalizeLegacyPublicHash() {
  const route = currentHashRoute();
  const redirectTarget = LEGACY_PUBLIC_REDIRECTS.get(route);
  if (!redirectTarget) return false;
  window.location.replace(redirectTarget);
  return true;
}

function normalizeAuthHashRoute() {
  const route = currentHashRoute();
  if (!route) return false;
  if (redirectLegacyAuthRoute(`#${route}`)) return true;
  const normalized = normalizeAppRoute(route, "");
  if (!normalized) return false;
  window.location.replace(appRouteHref(normalized));
  return true;
}

function loadCabinetRuntime() {
  if (cabinetRuntimePromise) return cabinetRuntimePromise;
  cabinetRuntimePromise = import("./cabinet-runtime.js?v=20260720r11")
    .then((module) => {
      document.documentElement.dataset.runtime = "ready";
      document.documentElement.dataset.cabinetRuntime = "modern";
      return module;
    })
    .catch((error) => {
      cabinetRuntimePromise = null;
      console.error("CopiMine cabinet runtime failed to load", error);
      throw error;
    });
  return cabinetRuntimePromise;
}

function requestCabinetRuntime() {
  void loadCabinetRuntime();
}

window.addEventListener("hashchange", () => {
  if (pageKind() === "cabinet") {
    if (normalizeAuthHashRoute()) return;
    requestCabinetRuntime();
    return;
  }
  if (pageKind() === "signin" || pageKind() === "register") {
    normalizeAuthHashRoute();
    return;
  }
  if (normalizeLegacyPublicHash()) return;
});

if (pageKind() === "cabinet") {
  if (!normalizeAuthHashRoute()) {
    requestCabinetRuntime();
  }
} else if (pageKind() === "signin" || pageKind() === "register") {
  if (!normalizeAuthHashRoute()) {
    void initAuthPage();
  }
} else {
  normalizeLegacyPublicHash();
}

initThemeToggle();
initPublicNav();
