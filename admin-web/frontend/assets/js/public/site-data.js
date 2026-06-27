async function fetchJson(path, fallback = {}) {
  try {
    const response = await fetch(path, {
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
    });
    if (!response.ok) {
      return fallback;
    }
    return await response.json();
  } catch (_error) {
    return fallback;
  }
}

export async function loadPublicHomepageData() {
  const [
    configPayload,
    statusPayload,
    modpackPayload,
    budgetPayload,
    historyPayload,
    presidentPayload,
  ] = await Promise.all([
    fetchJson("/api/public/config", { ok: false, data: {} }),
    fetchJson("/api/public/status", { ok: false, data: {} }),
    fetchJson("/api/public/modpack", { ok: false, data: {} }),
    fetchJson("/api/public/president-budget", { ok: false, data: {} }),
    fetchJson("/api/public/president-budget/history?limit=6", { ok: false, data: {} }),
    fetchJson("/api/public/president", { ok: false, data: {} }),
  ]);

  return {
    config: configPayload?.data || {},
    status: statusPayload?.data || {},
    modpack: modpackPayload?.data || {},
    budget: budgetPayload?.data || {},
    history: historyPayload?.data || {},
    president: presidentPayload?.data || {},
  };
}

export async function loadPublicTreasuryFallback() {
  const { budget, history } = await loadPublicHomepageData();
  return {
    budgetPayload: { ok: true, data: budget },
    historyPayload: { ok: true, data: history },
  };
}
