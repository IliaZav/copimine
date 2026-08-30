const PUBLIC_FETCH_TIMEOUT_MS = 8000;

async function fetchJson(path, fallback = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), PUBLIC_FETCH_TIMEOUT_MS);
  const rawPath = String(path || "");
  const freshPath = rawPath.startsWith("/api/")
    ? `${rawPath}${rawPath.includes("?") ? "&" : "?"}_fresh=${Date.now()}`
    : rawPath;
  try {
    const response = await fetch(freshPath, {
      credentials: "include",
      cache: "no-store",
      headers: {
        Accept: "application/json",
        "Cache-Control": "no-cache",
      },
      signal: controller.signal,
    });
    if (!response.ok) {
      return fallback;
    }
    const payload = await response.json();
    return payload && typeof payload === "object" ? payload : fallback;
  } catch (_error) {
    return fallback;
  } finally {
    window.clearTimeout(timeout);
  }
}

async function fetchConfigPayload() {
  return fetchJson("/api/public/config", { ok: false, data: {} });
}

async function fetchStatusPayload() {
  return fetchJson("/api/public/status", { ok: false, data: {} });
}

async function fetchElectionsPayload() {
  return fetchJson("/api/public/elections", { ok: false, data: {} });
}

async function fetchModpackPayload() {
  return fetchJson("/api/public/modpack", { ok: false, data: {} });
}

async function fetchStaticModpackSnapshot() {
  return fetchJson("/assets/public-data/modpack_snapshot.json", {});
}

async function fetchBudgetPayload() {
  return fetchJson("/api/public/president-budget", { ok: false, data: {} });
}

async function fetchBudgetHistoryPayload(limit = 6) {
  return fetchJson(`/api/public/president-budget/history?limit=${Number(limit) || 6}`, { ok: false, data: {} });
}

async function fetchPresidentPayload() {
  return fetchJson("/api/public/president", { ok: false, data: {} });
}

async function fetchArCatalogPayload() {
  return fetchJson("/api/public/shop/ar-items", { ok: false, data: { items: [], _unavailable: true } });
}

async function fetchDonationCatalogPayload() {
  return fetchJson("/api/public/shop/donation-items", { ok: false, data: { items: [], _unavailable: true } });
}

async function fetchCmsPayload() {
  return fetchJson("/api/public/cms", { items: [], sections: [] });
}

async function fetchEventsPayload() {
  const apiPayload = await fetchJson("/api/public/events", { ok: false, data: {} });
  if (apiPayload?.ok === true && Array.isArray(apiPayload?.data?.events)) {
    return apiPayload.data;
  }
  return fetchJson("/assets/public-data/events.json", { schemaVersion: 1, events: [] });
}

export async function loadPublicEventsPageData() {
  return fetchEventsPayload();
}

export async function loadPublicHomePageData() {
  const [configPayload, statusPayload, modpackPayload, staticModpack, cmsPayload] = await Promise.all([
    fetchConfigPayload(),
    fetchStatusPayload(),
    fetchModpackPayload(),
    fetchStaticModpackSnapshot(),
    fetchCmsPayload(),
  ]);

  const apiModpack = modpackPayload?.data || {};
  const resolvedModpack = apiModpack && (apiModpack.available || apiModpack.filename || apiModpack.manifest)
    ? apiModpack
    : (staticModpack || {});

  return {
    config: configPayload?.data || {},
    status: statusPayload?.data || {},
    modpack: resolvedModpack,
    cms: cmsPayload || { items: [], sections: [] },
  };
}

export async function loadPublicServerPageData() {
  const [
    configPayload,
    statusPayload,
    budgetPayload,
    historyPayload,
    presidentPayload,
    cmsPayload,
  ] = await Promise.all([
    fetchConfigPayload(),
    fetchStatusPayload(),
    fetchBudgetPayload(),
    fetchBudgetHistoryPayload(6),
    fetchPresidentPayload(),
    fetchCmsPayload(),
  ]);

  return {
    config: configPayload?.data || {},
    status: statusPayload?.data || {},
    budget: budgetPayload?.data || {},
    history: historyPayload?.data || {},
    president: presidentPayload?.data || {},
    cms: cmsPayload || { items: [], sections: [] },
  };
}

export async function loadPublicElectionsPageData() {
  const [electionsPayload, statusPayload, configPayload, cmsPayload] = await Promise.all([
    fetchElectionsPayload(),
    fetchStatusPayload(),
    fetchConfigPayload(),
    fetchCmsPayload(),
  ]);
  const electionData = electionsPayload?.data && typeof electionsPayload.data === "object"
    ? electionsPayload.data
    : {};
  return {
    elections: { ...electionData, _unavailable: electionsPayload?.ok !== true },
    status: statusPayload?.data || {},
    config: configPayload?.data || {},
    cms: cmsPayload || { items: [], sections: [] },
  };
}

export async function loadPlayerShopOwnership() {
  const [artifacts, owned] = await Promise.all([
    fetchJson("/api/player/artifacts", { linked: false, purchases: [], pending: [], repairs: [] }),
    fetchJson("/api/player/shop/owned", { linked: false, claims: [], instances: [], summary: {} }),
  ]);
  return { artifacts, owned };
}

export async function loadPublicShopsPageData(authState = {}) {
  const shouldLoadOwnership = Boolean(authState?.cookieAuth && authState?.role === "player" && authState?.linked);
  const [arCatalogPayload, donationCatalogPayload, cmsPayload, ownership] = await Promise.all([
    fetchArCatalogPayload(),
    fetchDonationCatalogPayload(),
    fetchCmsPayload(),
    shouldLoadOwnership ? loadPlayerShopOwnership() : Promise.resolve({ artifacts: { purchases: [], pending: [] }, owned: { claims: [], instances: [] } }),
  ]);

  return {
    arCatalog: arCatalogPayload?.data || { items: [] },
    donationCatalog: donationCatalogPayload?.data || { items: [] },
    cms: cmsPayload || { items: [], sections: [] },
    ownership,
  };
}

export async function loadPublicModsPageData() {
  const [configPayload, modpackPayload, staticModpack, cmsPayload] = await Promise.all([
    fetchConfigPayload(),
    fetchModpackPayload(),
    fetchStaticModpackSnapshot(),
    fetchCmsPayload(),
  ]);

  const apiModpack = modpackPayload?.data || {};
  const resolvedModpack = apiModpack && (apiModpack.available || apiModpack.filename || apiModpack.manifest)
    ? apiModpack
    : (staticModpack || {});

  return {
    config: configPayload?.data || {},
    modpack: resolvedModpack,
    cms: cmsPayload || { items: [], sections: [] },
  };
}

export async function loadPublicHomepageData(authState = {}) {
  // The home page used to call the home, server and shops loaders together.
  // That repeated config/status/CMS requests and still fetched the retired
  // modpack endpoint even though the home UI only links to Launcher.
  const [
    configPayload,
    statusPayload,
    budgetPayload,
    historyPayload,
    presidentPayload,
    arCatalogPayload,
    donationCatalogPayload,
    cmsPayload,
  ] = await Promise.all([
    fetchConfigPayload(),
    fetchStatusPayload(),
    fetchBudgetPayload(),
    fetchBudgetHistoryPayload(6),
    fetchPresidentPayload(),
    fetchArCatalogPayload(),
    fetchDonationCatalogPayload(),
    fetchCmsPayload(),
  ]);

  const shouldLoadOwnership = Boolean(authState?.cookieAuth && authState?.role === "player" && authState?.linked);
  const ownership = shouldLoadOwnership
    ? await loadPlayerShopOwnership()
    : { artifacts: { purchases: [], pending: [] }, owned: { claims: [], instances: [] } };

  return {
    config: configPayload?.data || {},
    status: statusPayload?.data || {},
    modpack: {},
    budget: budgetPayload?.data || {},
    history: historyPayload?.data || {},
    president: presidentPayload?.data || {},
    arCatalog: arCatalogPayload?.data || { items: [] },
    donationCatalog: donationCatalogPayload?.data || { items: [] },
    ownership,
    cms: cmsPayload || { items: [], sections: [] },
  };
}

export async function loadPublicAuthState() {
  const session = await fetchJson("/api/session/me", {});
  if (session && typeof session === "object" && session.kind === "panel" && session.role) {
    return {
      role: String(session.role || ""),
      cookieAuth: true,
      fullAccess: Boolean(session.fullAccess),
      owner: Boolean(session.owner),
    };
  }
  if (session && typeof session === "object" && session.kind === "player") {
    return {
      role: "player",
      cookieAuth: true,
      linked: Boolean(session.account?.linked),
      accountId: String(session.account?.id || ""),
    };
  }
  return {
    role: "",
    cookieAuth: false,
  };
}

export async function loadPublicTreasuryFallback() {
  const [budgetPayload, historyPayload] = await Promise.all([
    fetchBudgetPayload(),
    fetchBudgetHistoryPayload(6),
  ]);
  return {
    budgetPayload,
    historyPayload,
  };
}
