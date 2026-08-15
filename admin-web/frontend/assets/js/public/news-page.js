import { loadPatchIndexForNews } from "./patch-data.js?v=20260815launchernews2";
import { renderNewsList } from "./patch-render.js?v=20260815launchernews2";

export async function initNewsPage() {
  renderNewsList(await loadPatchIndexForNews());
}
