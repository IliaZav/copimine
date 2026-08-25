import { loadPatchIndexForNews } from "./patch-data.js?v=20260825siteui16";
import { renderNewsList } from "./patch-render.js?v=20260825siteui18";

export async function initNewsPage() {
  renderNewsList(await loadPatchIndexForNews());
}
