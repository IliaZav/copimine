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

function normalizePatch(raw, safe) {
  const source = raw?.news || raw?.patch || raw;
  if (!source || typeof source !== "object") return null;
  const slug = safeSlug(source.slug || safe);
  if (!slug) return null;
  const sections = Object.fromEntries(Object.entries(source.sections || {}).map(([key, values]) => [
    key,
    Array.isArray(values) ? values.map((value, index) => ({
      id: `${key}-${index + 1}`,
      title: key,
      changes: [{ kind: "changed", text: String(value || "") }],
    })) : [],
  ]));
  return {
    schemaVersion: 1,
    id: String(source.id || `launcher-${slug}`),
    slug,
    version: String(source.version || ""),
    title: String(source.title || "Обновление"),
    publishedAt: String(source.publishedAt || source.updatedAt || ""),
    summary: Array.isArray(source.summary) ? source.summary.filter((value) => typeof value === "string").slice(0, 3) : [],
    sections,
    items: Array.isArray(source.items) ? source.items : [],
    detailUrl: `/news/${slug}.html`,
  };
}

export async function loadPatchDetail(slug) {
  const safe = safeSlug(slug);
  if (!safe) return null;
  try {
    const apiPayload = await fetchJson(`/api/public/news/${safe}`);
    const fromApi = normalizePatch(apiPayload, safe);
    if (fromApi) return fromApi;
  } catch (error) {
    console.warn("CopiMine patch API detail unavailable", error?.message || "unknown error");
  }
  try {
    const payload = await fetchJson(`/assets/public-data/patches/${safe}.json`);
    return payload && payload.schemaVersion === 1 && payload.slug === safe ? payload : null;
  } catch (error) {
    console.warn("CopiMine patch static detail unavailable", error?.message || "unknown error");
    return null;
  }
}

export async function loadPatchIndexForNews() {
  try {
    const apiPayload = await fetchJson("/api/public/news");
    const apiNews = Array.isArray(apiPayload?.news) ? apiPayload.news : apiPayload?.data?.news;
    if (Array.isArray(apiNews) && apiNews.length) {
      return apiNews.map((patch) => ({
        ...patch,
        id: String(patch.id || `launcher-${patch.slug}`),
        detailUrl: `/news/${patch.slug}.html`,
      })).filter((patch) => patch.slug && patch.detailUrl.startsWith("/news/")).slice(0, 50);
    }
  } catch (error) {
    console.warn("CopiMine patch API index unavailable", error?.message || "unknown error");
  }
  try {
    const payload = await fetchJson("/assets/public-data/patches/index.json");
    if (!payload || payload.schemaVersion !== 1 || !Array.isArray(payload.patches)) return [];
    return payload.patches.filter((patch) => patch && typeof patch.detailUrl === "string" && patch.detailUrl.startsWith("/news/")).slice(0, 50);
  } catch (error) {
    console.warn("CopiMine patch static index unavailable", error?.message || "unknown error");
    return [];
  }
}
