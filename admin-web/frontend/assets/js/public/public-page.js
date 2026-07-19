import { bindHomepageEvents, loadPublicPage } from "./homepage.js";
import { initCartPage } from "./cart-page.js";
import { initThemeToggle } from "../theme/theme-toggle.js?v=20260719r7";
import { initPublicNav } from "./public-nav.js?v=20260719r7";

initPublicNav();
initThemeToggle();

const pageKind = String(document.body?.dataset.pageKind || "").trim().toLowerCase();

if (pageKind === "public-cart") {
  void initCartPage();
} else if (document.querySelector(".public-site")) {
  bindHomepageEvents();
  window.setTimeout(() => {
    const kind = pageKind || "public-home";
    void loadPublicPage(kind);
  }, 120);
}
