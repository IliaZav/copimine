import { loadPatchIndexForNews } from "./patch-data.js?v=20260815launchernews1";
import { renderNewsList } from "./patch-render.js?v=20260815launchernews1";

export async function initNewsPage() {
  renderNewsList(await loadPatchIndexForNews());
}
