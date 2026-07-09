(function () {
  const KEY = "copimine.theme";
  const THEMES = new Set(["light", "dark"]);

  function readStoredTheme() {
    try {
      const value = window.localStorage.getItem(KEY);
      return THEMES.has(value) ? value : "";
    } catch (_error) {
      return "";
    }
  }

  function preferredTheme() {
    const stored = readStoredTheme();
    if (stored) return stored;
    return "light";
  }

  function applyTheme(theme, persist) {
    const next = THEMES.has(theme) ? theme : "light";
    const root = document.documentElement;
    root.dataset.theme = next;
    root.style.colorScheme = next === "dark" ? "dark" : "light";
    const meta = document.querySelector('meta[name="color-scheme"]');
    if (meta) meta.setAttribute("content", "light dark");
    if (persist) {
      try {
        window.localStorage.setItem(KEY, next);
      } catch (_error) {
        // Ignore storage failures. Theme still applies for this session.
      }
    }
    if (window.CopiMineTheme) {
      window.CopiMineTheme.current = next;
    }
  }

  const api = {
    key: KEY,
    current: preferredTheme(),
    getTheme() {
      return document.documentElement.dataset.theme || this.current || "light";
    },
    setTheme(theme) {
      applyTheme(theme, true);
      this.current = document.documentElement.dataset.theme || "light";
      window.dispatchEvent(new CustomEvent("copimine:theme-change", { detail: { theme: this.current } }));
      return this.current;
    },
    toggleTheme() {
      return this.setTheme(this.getTheme() === "dark" ? "light" : "dark");
    },
  };

  window.CopiMineTheme = api;
  applyTheme(api.current, false);
})();
