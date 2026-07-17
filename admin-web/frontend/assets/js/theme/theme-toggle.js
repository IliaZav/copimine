function themeApi() {
  return window.CopiMineTheme || {
    getTheme: () => document.documentElement.dataset.theme || "light",
    setTheme: (theme) => {
      document.documentElement.dataset.theme = theme === "dark" ? "dark" : "light";
      return document.documentElement.dataset.theme;
    },
    toggleTheme: () => {
      const next = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      document.documentElement.dataset.theme = next;
      return next;
    },
  };
}

function currentThemeLabel(theme) {
  return theme === "dark"
    ? "\u0442\u0451\u043c\u043d\u0430\u044f"
    : "\u0441\u0432\u0435\u0442\u043b\u0430\u044f";
}

function currentThemeLabelDisplay(theme) {
  return theme === "dark"
    ? "\u0422\u0451\u043c\u043d\u0430\u044f"
    : "\u0421\u0432\u0435\u0442\u043b\u0430\u044f";
}

function ensureButtonContent(button) {
  if (!(button instanceof HTMLButtonElement)) return null;
  [...button.childNodes].forEach((node) => {
    if (node.nodeType === Node.TEXT_NODE) node.remove();
  });
  let icon = button.querySelector(".theme-toggle-icon");
  if (!(icon instanceof HTMLElement)) {
    icon = document.createElement("span");
    icon.className = "theme-toggle-icon";
    icon.setAttribute("aria-hidden", "true");
    button.append(icon);
  }
  let label = button.querySelector(".theme-toggle-label");
  if (!(label instanceof HTMLElement)) {
    label = document.createElement("span");
    label.className = "theme-toggle-label";
    button.append(label);
  }
  return { icon, label };
}

function syncThemeButtons() {
  const current = themeApi().getTheme();
  document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
    const isButton = button instanceof HTMLButtonElement;
    const currentLabel = currentThemeLabelDisplay(current);
    const compact = button.getAttribute("data-theme-toggle-compact") === "true";
    button.setAttribute("data-theme-current", current);
    button.setAttribute("role", "switch");
    button.setAttribute("aria-checked", current === "dark" ? "true" : "false");
    button.setAttribute(
      "aria-label",
      `\u041f\u0435\u0440\u0435\u043a\u043b\u044e\u0447\u0438\u0442\u044c \u0442\u0435\u043c\u0443. \u0421\u0435\u0439\u0447\u0430\u0441 \u0432\u043a\u043b\u044e\u0447\u0435\u043d\u0430 ${currentThemeLabel(current)} \u0442\u0435\u043c\u0430.`,
    );
    button.setAttribute("title", currentLabel);
    if (isButton) {
      button.classList.add("ui-switch");
      const content = ensureButtonContent(button);
      if (content) {
        content.icon.textContent = "";
        content.label.textContent = compact ? "\u0422\u0435\u043c\u0430" : currentLabel;
      }
    } else {
      button.textContent = currentLabel;
    }
  });
}

function onToggleClick(event) {
  const target = event.target instanceof Element ? event.target.closest("[data-theme-toggle]") : null;
  if (!target) return;
  event.preventDefault();
  themeApi().toggleTheme();
  syncThemeButtons();
}

export function initThemeToggle() {
  if (document.documentElement.dataset.themeToggleBound === "true") {
    syncThemeButtons();
    return;
  }
  document.documentElement.dataset.themeToggleBound = "true";
  document.addEventListener("click", onToggleClick);
  window.addEventListener("copimine:theme-change", syncThemeButtons);
  syncThemeButtons();
}
