const SITE_LABEL = "\u041d\u0430 \u0441\u0430\u0439\u0442";
const PANEL_LABEL = "\u041f\u0430\u043d\u0435\u043b\u044c";
const CABINET_LABEL = "\u041a\u0430\u0431\u0438\u043d\u0435\u0442";

function syncGuestSiteButton() {
  const guestSite = document.getElementById("guestPagesBtn");
  if (!guestSite) return;
  if (guestSite.hidden) guestSite.hidden = false;
  if (guestSite.textContent !== SITE_LABEL) {
    guestSite.textContent = SITE_LABEL;
  }
}

function syncCabinetHeader(auth = {}) {
  const authed = Boolean(auth.role || auth.cookieAuth);
  const role = String(auth.role || "");
  const username = String(auth.username || "").trim();

  const signin = document.getElementById("publicSigninLink");
  const register = document.getElementById("publicRegisterLink");
  const cabinet = document.getElementById("publicCabinetBtn");
  const logout = document.getElementById("publicLogoutBtn");

  if (signin) signin.classList.toggle("hidden", authed);
  if (register) register.classList.toggle("hidden", authed);
  if (cabinet) {
    cabinet.classList.toggle("hidden", !authed);
    cabinet.textContent = authed
      ? (role && role !== "player" ? `${PANEL_LABEL}${username ? ` (${username})` : ""}` : `${CABINET_LABEL}${username ? ` (${username})` : ""}`)
      : CABINET_LABEL;
  }
  if (logout) logout.classList.toggle("hidden", !authed);
  syncGuestSiteButton();
}

function hideBootStageIfReady() {
  const app = document.getElementById("app");
  const boot = document.getElementById("bootStage");
  const nav = document.getElementById("nav");
  if (!app || !boot) return;
  const appVisible = !app.hidden;
  const navReady = !nav || nav.childElementCount > 0;
  if (appVisible && navReady) {
    boot.classList.add("hidden");
  }
}

function bindCabinetHeaderActions() {
  const logout = document.getElementById("publicLogoutBtn");
  const cabinet = document.getElementById("publicCabinetBtn");

  if (logout && logout.dataset.bound !== "true") {
    logout.dataset.bound = "true";
    logout.addEventListener("click", () => {
      const mainLogout = document.getElementById("logout");
      if (mainLogout instanceof HTMLButtonElement) {
        mainLogout.click();
      }
    });
  }

  if (cabinet && cabinet.dataset.bound !== "true") {
    cabinet.dataset.bound = "true";
    cabinet.addEventListener("click", () => {
      const href = roleHomeHref();
      if (href) window.location.href = href;
    });
  }
}

function roleHomeHref() {
  const hash = String(window.location.hash || "").replace(/^#/, "").trim().toLowerCase();
  if (!hash) return window.location.pathname;
  return `${window.location.pathname}#${hash}`;
}

window.addEventListener("copimine:auth-state", (event) => {
  syncCabinetHeader(event.detail || {});
  hideBootStageIfReady();
});

window.addEventListener("load", () => {
  bindCabinetHeaderActions();
  syncCabinetHeader({});
  hideBootStageIfReady();

  const observer = new MutationObserver(() => {
    bindCabinetHeaderActions();
    hideBootStageIfReady();
    syncGuestSiteButton();
  });

  const app = document.getElementById("app");
  if (app) {
    observer.observe(app, { attributes: true, childList: true, subtree: true });
  }
});
