import { bindHomepageEvents, loadPublicPage } from "./homepage.js";
import { initThemeToggle } from "../theme/theme-toggle.js";
import { initPublicNav } from "./public-nav.js";

initPublicNav();
initThemeToggle();
bindHomepageEvents();

if (document.querySelector(".public-site")) {
  window.setTimeout(() => {
    const kind = String(document.body?.dataset.pageKind || "").trim().toLowerCase() || "public-home";
    void loadPublicPage(kind);
  }, 120);
}
