import { bindHomepageEvents, loadPublicPage } from "./homepage.js?v=20260825siteui17";
import { initCartPage } from "./cart-page.js?v=20260825siteui17";
import { initThemeToggle } from "../theme/theme-toggle.js?v=20260825siteui17";
import { initPublicNav } from "./public-nav.js?v=20260825siteui17";
import { initLauncherPage } from "./launcher-page.js?v=20260825siteui17";
import { initNewsPage } from "./news-page.js?v=20260825siteui17";
import { initPatchDetailPage } from "./patch-detail-page.js?v=20260825siteui17";
import { initPublicMotion } from "./public-motion.js?v=20260825siteui17";

initPublicNav();
initThemeToggle();
initPublicMotion();

const pageKind = String(document.body?.dataset.pageKind || "").trim().toLowerCase();

if (pageKind === "public-cart") {
  void initCartPage();
} else if (pageKind === "public-launcher") {
  void initLauncherPage();
} else if (pageKind === "public-news") {
  void initNewsPage();
} else if (pageKind === "public-patch") {
  void initPatchDetailPage();
} else if (document.querySelector(".public-site")) {
  bindHomepageEvents();
  window.setTimeout(() => {
    const kind = pageKind || "public-home";
    void loadPublicPage(kind);
  }, 120);
}
