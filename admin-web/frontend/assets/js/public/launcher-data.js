const FEED_TIMEOUT_MS = 4500;

function isSafeRelative(path, prefix) {
  return typeof path === "string"
    && path.startsWith(prefix)
    && !path.includes("..")
    && !/[\u0000-\u001f]/.test(path);
}

export function isSafeNewsPath(path) {
  return isSafeRelative(path, "/news/") && path.endsWith(".html");
}

async function fetchJson(path) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), FEED_TIMEOUT_MS);
  try {
    const response = await fetch(path, { cache: "no-cache", headers: { Accept: "application/json" }, signal: controller.signal });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const payload = await response.json();
    if (!payload || typeof payload !== "object") throw new Error("payload is not an object");
    return payload;
  } finally {
    window.clearTimeout(timer);
  }
}

function parseDate(value) {
  const date = new Date(String(value || ""));
  return Number.isNaN(date.getTime()) ? null : date;
}

export function parseLauncherMetadata(payload) {
  if (!payload || payload.schemaVersion !== 1 || payload.channel !== "stable") return null;
  const version = String(payload.version || "");
  const downloadUrl = String(payload.downloadUrl || "");
  const releaseNotesUrl = String(payload.releaseNotesUrl || "");
  if (!/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(version)) return null;
  if (!/^\d+$/.test(String(payload.minimumWindowsBuild || ""))) return null;
  if (payload.architecture !== "x64" || !String(payload.filename || "").toLowerCase().endsWith(".exe")) return null;
  if (!isSafeRelative(downloadUrl, "/downloads/launcher/") || !downloadUrl.endsWith(".exe")) return null;
  if (!isSafeNewsPath(releaseNotesUrl)) return null;
  if (!Number.isSafeInteger(payload.sizeBytes) || payload.sizeBytes <= 0) return null;
  if (!/^[0-9a-f]{64}$/.test(String(payload.sha256 || ""))) return null;
  let msi = null;
  const hasMsi = payload.msiDownloadUrl !== undefined || payload.msiFilename !== undefined;
  if (hasMsi) {
    const msiFilename = String(payload.msiFilename || "");
    const msiDownloadUrl = String(payload.msiDownloadUrl || "");
    if (!/^[A-Za-z0-9._-]+\.msi$/i.test(msiFilename)
      || !isSafeRelative(msiDownloadUrl, "/downloads/launcher/")
      || !msiDownloadUrl.endsWith(".msi")
      || !Number.isSafeInteger(payload.msiSizeBytes)
      || payload.msiSizeBytes <= 0
      || !/^[0-9a-f]{64}$/.test(String(payload.msiSha256 || ""))
      || payload.msiInstallLocation !== "choose") return null;
    msi = {
      filename: msiFilename,
      downloadUrl: msiDownloadUrl,
      sizeBytes: payload.msiSizeBytes,
      sha256: String(payload.msiSha256),
      installLocation: "choose",
    };
  }
  const publishedAt = parseDate(payload.publishedAt);
  if (!publishedAt) return null;
  return { ...payload, version, downloadUrl, releaseNotesUrl, publishedAt, msi };
}

export function parsePatchIndex(payload) {
  if (!payload || payload.schemaVersion !== 1 || !Array.isArray(payload.patches)) return [];
  return payload.patches.map((raw) => {
    if (!raw || typeof raw !== "object") return null;
    const detailUrl = String(raw.detailUrl || "");
    const publishedAt = parseDate(raw.publishedAt);
    const summary = Array.isArray(raw.summary) ? raw.summary.filter((value) => typeof value === "string").slice(0, 3) : [];
    if (!raw.id || !raw.version || !raw.title || !publishedAt || !isSafeNewsPath(detailUrl) || summary.length === 0) return null;
    const thumbnailUrl = typeof raw.thumbnailUrl === "string" && isSafeRelative(raw.thumbnailUrl, "/assets/") ? raw.thumbnailUrl : null;
    return { ...raw, id: String(raw.id), version: String(raw.version), title: String(raw.title), detailUrl, publishedAt, summary, thumbnailUrl };
  }).filter(Boolean).slice(0, 3);
}

export async function loadLauncherMetadata() {
  try {
    const apiPayload = await fetchJson("/api/public/launcher");
    const apiData = apiPayload?.data || apiPayload;
    const fromApi = parseLauncherMetadata(apiData?.installer);
    if (fromApi) return fromApi;
  } catch (error) {
    console.warn("CopiMine Launcher API metadata unavailable", error?.message || "unknown error");
  }
  try {
    return parseLauncherMetadata(await fetchJson("/assets/public-data/launcher/latest.json"));
  } catch (error) {
    console.warn("CopiMine Launcher static metadata unavailable", error?.message || "unknown error");
    return null;
  }
}

export async function loadPatchIndex() {
  try {
    const apiPayload = await fetchJson("/api/public/news");
    const apiPatches = Array.isArray(apiPayload?.patches) ? apiPayload.patches : apiPayload?.data?.patches;
    const fromApi = parsePatchIndex({ schemaVersion: 1, patches: Array.isArray(apiPatches) ? apiPatches.map((patch) => ({
      ...patch,
      id: patch.id || `launcher-${patch.slug}`,
      detailUrl: patch.detailUrl || `/news/${patch.slug}.html`,
    })) : [] });
    if (fromApi.length) return fromApi;
  } catch (error) {
    console.warn("CopiMine patch API unavailable", error?.message || "unknown error");
  }
  try {
    return parsePatchIndex(await fetchJson("/assets/public-data/patches/index.json"));
  } catch (error) {
    console.warn("CopiMine patch static feed unavailable", error?.message || "unknown error");
    return [];
  }
}
