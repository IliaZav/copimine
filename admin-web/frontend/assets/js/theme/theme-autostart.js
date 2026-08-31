import { initThemeToggle } from "./theme-toggle.js?v=20260831siteui26";

// Preview pages keep their theme control alive even if the optional drawer
// navigation module fails to load. The shared initializer is idempotent.
initThemeToggle();
