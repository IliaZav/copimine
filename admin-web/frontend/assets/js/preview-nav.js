import { initThemeToggle } from "./theme/theme-toggle.js?v=20260718r1";

function initPreviewNavigation() {
  initThemeToggle();
  const sidebar = document.querySelector(".preview-sidebar");
  const topbar = document.querySelector(".preview-topbar");
  if (!(sidebar instanceof HTMLElement) || !(topbar instanceof HTMLElement)) return;

  sidebar.id ||= "previewNavigation";

  const toggle = document.createElement("button");
  toggle.type = "button";
  toggle.className = "preview-nav-toggle";
  toggle.setAttribute("aria-controls", sidebar.id);
  toggle.setAttribute("aria-expanded", "false");
  toggle.setAttribute("aria-label", "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e");
  toggle.innerHTML = '<span class="preview-nav-toggle-icon" aria-hidden="true"></span>';

  const backdrop = document.createElement("button");
  backdrop.type = "button";
  backdrop.className = "preview-nav-backdrop";
  backdrop.setAttribute("aria-label", "\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e \u043f\u043e \u0444\u043e\u043d\u0443");
  backdrop.tabIndex = -1;

  topbar.prepend(toggle);
  document.body.append(backdrop);

  function setOpen(open) {
    const next = Boolean(open && window.innerWidth <= 800);
    document.body.classList.toggle("preview-nav-open", next);
    toggle.setAttribute("aria-expanded", next ? "true" : "false");
    toggle.setAttribute("aria-label", next ? "\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e" : "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e");
    backdrop.tabIndex = next ? 0 : -1;
  }

  toggle.addEventListener("click", () => setOpen(!document.body.classList.contains("preview-nav-open")));
  backdrop.addEventListener("click", () => {
    setOpen(false);
    toggle.focus({ preventScroll: true });
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && document.body.classList.contains("preview-nav-open")) {
      setOpen(false);
      toggle.focus({ preventScroll: true });
    }
  });
  window.addEventListener("resize", () => setOpen(document.body.classList.contains("preview-nav-open")));
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initPreviewNavigation, { once: true });
} else {
  initPreviewNavigation();
}
