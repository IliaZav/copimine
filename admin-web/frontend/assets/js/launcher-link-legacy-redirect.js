import { launcherBindingHrefFromSearch } from "./shared/app-routes.js?v=20260828launcherlink3";

// Older backend workers may still return /cabinet/link.html while they are
// being replaced. Keep those one-time requests on the same standalone page
// without exposing the cabinet shell or duplicating query validation here.
const target = launcherBindingHrefFromSearch(window.location.search || "");
if (target && window.location.pathname === "/cabinet/link.html") {
  window.location.replace(target);
}
