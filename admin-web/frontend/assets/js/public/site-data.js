const PUBLIC_FETCH_TIMEOUT_MS = 8000;

async function fetchJson(path, fallback = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), PUBLIC_FETCH_TIMEOUT_MS);
  try {
    const response = await fetch(path, {
      credentials: "include",
      headers: {
        Accept: "application/json",
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
  return fetchJson("/api/public/shop/ar-items", { ok: false, data: { items: [] } });
}

async function fetchDonationCatalogPayload() {
  return fetchJson("/api/public/shop/donation-items", { ok: false, data: { items: [] } });
}

async function fetchCmsPayload() {
  return fetchJson("/api/public/cms", { items: [], sections: [] });
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
  const [home, server, shops] = await Promise.all([
    loadPublicHomePageData(),
    loadPublicServerPageData(),
    loadPublicShopsPageData(authState),
  ]);

  return {
    config: home.config,
    status: home.status,
    modpack: home.modpack,
    budget: server.budget,
    history: server.history,
    president: server.president,
    arCatalog: shops.arCatalog,
    donationCatalog: shops.donationCatalog,
    ownership: shops.ownership,
    cms: home.cms || shops.cms || { items: [], sections: [] },
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
  const { budget, history } = await loadPublicHomepageData();
  return {
    budgetPayload: { ok: true, data: budget },
    historyPayload: { ok: true, data: history },
  };
}
