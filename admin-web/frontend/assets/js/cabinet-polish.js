function syncCabinetHeader(auth = {}) {
  const authed = Boolean(auth.role || auth.cookieAuth);
  const role = String(auth.role || "");
  const username = String(auth.username || "").trim();

  const signin = document.getElementById("publicSigninLink");
  const register = document.getElementById("publicRegisterLink");
  const cabinet = document.getElementById("publicCabinetBtn");
  const logout = document.getElementById("publicLogoutBtn");
  const guestSite = document.getElementById("guestPagesBtn");

  if (signin) signin.classList.toggle("hidden", authed);
  if (register) register.classList.toggle("hidden", authed);
  if (cabinet) {
    cabinet.classList.toggle("hidden", !authed);
    cabinet.textContent = authed
      ? (role && role !== "player" ? `Панель${username ? ` (${username})` : ""}` : `Кабинет${username ? ` (${username})` : ""}`)
      : "Кабинет";
  }
  if (logout) logout.classList.toggle("hidden", !authed);
  if (guestSite) {
    guestSite.hidden = false;
    guestSite.textContent = "На сайт";
  }
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
    const guestSite = document.getElementById("guestPagesBtn");
    if (guestSite) {
      guestSite.hidden = false;
      guestSite.textContent = "На сайт";
    }
  });

  const app = document.getElementById("app");
  if (app) {
    observer.observe(app, { attributes: true, childList: true, subtree: true });
  }
});
