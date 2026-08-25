import { initThemeToggle } from "../theme/theme-toggle.js?v=20260825siteui8";
import { initPublicNav } from "./public-nav.js?v=20260825siteui8";

initPublicNav();
initThemeToggle();

document.querySelector('[data-action="reload-page"]')?.addEventListener("click", () => {
  window.location.reload();
});
