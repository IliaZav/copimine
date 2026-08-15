import { loadPatchDetail } from "./patch-data.js?v=20260815launchernews1";
import { renderPatchDetail } from "./patch-render.js?v=20260815launchernews1";

function resolveSlug() {
  const fromBody = String(document.body?.dataset.patchSlug || "").trim();
  if (fromBody) return fromBody;
  const match = String(window.location.pathname || "").match(/\/news\/([^/]+)\.html$/i);
  return match ? match[1] : "";
}

export async function initPatchDetailPage() {
  renderPatchDetail(await loadPatchDetail(resolveSlug()));
}
