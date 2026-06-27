import "./public/homepage.js";

const PUBLIC_HASH_ROUTES = new Set([
  "",
  "start",
  "servers",
  "tops",
  "features",
  "rules",
  "help",
  "join",
  "signin",
]);

let legacyRuntimePromise = null;
let legacyRuntimeReady = false;

function currentHashRoute(hashValue = window.location.hash) {
  return String(hashValue || "").replace(/^#/, "").split("?", 1)[0].trim().toLowerCase();
}

function needsLegacyRuntime(hashValue = window.location.hash) {
  return !PUBLIC_HASH_ROUTES.has(currentHashRoute(hashValue));
}

function loadLegacyRuntime() {
  if (legacyRuntimePromise) return legacyRuntimePromise;
  legacyRuntimePromise = import("./legacy/app-legacy.js")
    .then((module) => {
      legacyRuntimeReady = true;
      document.documentElement.dataset.legacyRuntime = "ready";
      return module;
    })
    .catch((error) => {
      legacyRuntimePromise = null;
      console.error("CopiMine legacy runtime failed to load", error);
      throw error;
    });
  return legacyRuntimePromise;
}

function requestLegacyRuntime() {
  void loadLegacyRuntime();
}

function primeLegacyRuntimeFromEvent(event) {
  const target = event.target instanceof Element ? event.target : null;
  if (!target) return;
  if (target.closest("#loginForm") || target.closest("#publicCabinetBtn")) {
    requestLegacyRuntime();
  }
}

async function handleLoginSubmit(event) {
  if (legacyRuntimeReady) return;
  const form = event.target instanceof HTMLFormElement ? event.target : null;
  if (!form || form.id !== "loginForm") return;
  event.preventDefault();
  await loadLegacyRuntime();
  window.requestAnimationFrame(() => form.requestSubmit());
}

window.addEventListener("hashchange", () => {
  if (needsLegacyRuntime()) {
    requestLegacyRuntime();
  }
});

window.addEventListener("copimine:legacy-runtime-request", requestLegacyRuntime);
document.addEventListener("pointerdown", primeLegacyRuntimeFromEvent, { passive: true, capture: true });
document.addEventListener("focusin", primeLegacyRuntimeFromEvent, { capture: true });
document.addEventListener(
  "submit",
  (event) => {
    void handleLoginSubmit(event);
  },
  true,
);

if (needsLegacyRuntime()) {
  requestLegacyRuntime();
}
