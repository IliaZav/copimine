import { loadPatchDetail } from "./patch-data.js?v=20260825siteui16";
import { renderPatchDetail } from "./patch-render.js?v=20260825siteui18";

function localizePatchDate() {
  const time = document.querySelector("[data-patch-detail] time[datetime]");
  if (!(time instanceof HTMLTimeElement)) return;
  const date = new Date(time.dateTime);
  if (Number.isNaN(date.getTime())) return;
  time.textContent = new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "long",
    timeStyle: "short",
  }).format(date);
}

function resolveSlug() {
  const fromBody = String(document.body?.dataset.patchSlug || "").trim();
  if (fromBody) return fromBody;
  const match = String(window.location.pathname || "").match(/\/news\/([^/]+)\.html$/i);
  return match ? match[1] : "";
}

export async function initPatchDetailPage() {
  localizePatchDate();
  renderPatchDetail(await loadPatchDetail(resolveSlug()));
}
