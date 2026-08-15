const PATCH_TIMEOUT_MS = 4500;

function safeSlug(slug) {
  return /^[a-z0-9][a-z0-9-]{1,119}$/.test(slug) ? slug : "";
}

async function fetchJson(path) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), PATCH_TIMEOUT_MS);
  try {
    const response = await fetch(path, { cache: "no-cache", headers: { Accept: "application/json" }, signal: controller.signal });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } finally {
    window.clearTimeout(timer);
  }
}

export async function loadPatchDetail(slug) {
  const safe = safeSlug(slug);
  if (!safe) return null;
  try {
    const payload = await fetchJson(`/assets/public-data/patches/${safe}.json`);
    return payload && payload.schemaVersion === 1 && payload.slug === safe ? payload : null;
  } catch (error) {
    console.warn("CopiMine patch detail unavailable", error?.message || "unknown error");
    return null;
  }
}

export async function loadPatchIndexForNews() {
  try {
    const payload = await fetchJson("/assets/public-data/patches/index.json");
    if (!payload || payload.schemaVersion !== 1 || !Array.isArray(payload.patches)) return [];
    return payload.patches.filter((patch) => patch && typeof patch.detailUrl === "string" && patch.detailUrl.startsWith("/news/")).slice(0, 50);
  } catch (error) {
    console.warn("CopiMine patch index unavailable", error?.message || "unknown error");
    return [];
  }
}
