import { bindHomepageEvents, loadPublicPage } from "./homepage.js?v=20260825designpass3";
import { initCartPage } from "./cart-page.js?v=20260809publiccopy1";
import { initThemeToggle } from "../theme/theme-toggle.js?v=20260719r7";
import { initPublicNav } from "./public-nav.js?v=20260825siteui1";
import { initLauncherPage } from "./launcher-page.js?v=20260825designpass3";
import { initNewsPage } from "./news-page.js?v=20260815launchernews2";
import { initPatchDetailPage } from "./patch-detail-page.js?v=20260825designpass2";

initPublicNav();
initThemeToggle();

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
