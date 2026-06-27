import { bindHomepageEvents, loadPublicPage } from "./homepage.js";
import { initThemeToggle } from "../theme/theme-toggle.js";
import { initPublicNav } from "./public-nav.js";

function bindMirrorButton(sourceId, mirrorId) {
  const source = document.getElementById(sourceId);
  const mirror = document.getElementById(mirrorId);
  if (!source || !mirror || mirror.dataset.bound === "true") return;
  mirror.dataset.bound = "true";
  mirror.addEventListener("click", () => source.click());
}

bindMirrorButton("openArShopBtn", "openArShopBtnMirror");
bindMirrorButton("openDonationShopBtn", "openDonationShopBtnMirror");
initPublicNav();
initThemeToggle();
bindHomepageEvents();

if (document.querySelector(".public-site")) {
  window.setTimeout(() => {
    const kind = String(document.body?.dataset.pageKind || "").trim().toLowerCase() || "public-home";
    void loadPublicPage(kind);
  }, 120);
}
