import { initThemeToggle } from "./theme/theme-toggle.js?v=20260825siteui16";
import { appRouteHref, normalizeAppRoute } from "./shared/app-routes.js?v=20260825siteui16";
import { initPublicNav } from "./public/public-nav.js?v=20260825siteui22";
import { initAuthPage, redirectLegacyAuthRoute } from "./auth/auth-page.js?v=20260825siteui16";

const LEGACY_PUBLIC_REDIRECTS = new Map([
  ["start", "index.html"],
  ["features", "index.html"],
  ["rules", "index.html"],
  ["help", "index.html"],
  ["servers", "server.html"],
  ["presidentbudgetshowcase", "server.html"],
  ["treasuryhistorysection", "server.html"],
  ["tops", "server.html"],
  ["elections", "elections.html"],
  ["shops", "shops.html"],
  ["mods", "launcher.html"],
  ["join", "launcher.html"],
  ["cabinet-zones", "signin.html"],
  ["register", "register.html"],
]);

let cabinetRuntimePromise = null;

function showCabinetRuntimeFailure(error) {
  document.body?.setAttribute("data-boot-state", "error");
  const boot = document.getElementById("bootStage");
  if (!boot) return;
  boot.setAttribute("aria-busy", "false");
  const message = document.createElement("div");
  message.className = "loading boot-error";
  message.setAttribute("role", "alert");
  message.textContent = "Кабинет не загрузился. Проверьте соединение и повторите попытку.";
  const detail = document.createElement("p");
  detail.textContent = "Если ошибка повторится, откройте сайт позже или сообщите в поддержку.";
  const retry = document.createElement("button");
  retry.type = "button";
  retry.className = "btn btn-primary";
  retry.textContent = "Повторить";
  retry.addEventListener("click", () => {
    boot.replaceChildren(message, detail, retry);
    boot.setAttribute("aria-busy", "true");
    document.body?.setAttribute("data-boot-state", "loading");
    requestCabinetRuntime();
  }, { once: true });
  boot.replaceChildren(message, detail, retry);
  console.error("CopiMine cabinet runtime failed to load", error);
}

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
  cabinetRuntimePromise = import("./cabinet-runtime.js?v=20260828launcherlink2")
    .then((module) => {
      document.documentElement.dataset.runtime = "ready";
      document.documentElement.dataset.cabinetRuntime = "modern";
      return module;
    })
    .catch((error) => {
      cabinetRuntimePromise = null;
      showCabinetRuntimeFailure(error);
      throw error;
    });
  return cabinetRuntimePromise;
}

function requestCabinetRuntime() {
  void loadCabinetRuntime().catch(() => undefined);
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
