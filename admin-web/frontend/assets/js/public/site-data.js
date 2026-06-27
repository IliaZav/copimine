const PUBLIC_FETCH_TIMEOUT_MS = 8000;

async function fetchJson(path, fallback = {}) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), PUBLIC_FETCH_TIMEOUT_MS);
  try {
    const response = await fetch(path, {
      credentials: "same-origin",
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

export async function loadPublicAuthState() {
  const [adminMe, playerMe] = await Promise.all([
    fetchJson("/api/auth/me", {}),
    fetchJson("/api/player/me", {}),
  ]);
  if (adminMe && typeof adminMe === "object" && adminMe.role) {
    return {
      role: String(adminMe.role || ""),
      cookieAuth: true,
      fullAccess: Boolean(adminMe.fullAccess),
      owner: Boolean(adminMe.owner),
    };
  }
  if (playerMe && typeof playerMe === "object" && playerMe.account) {
    return {
      role: "player",
      cookieAuth: true,
      linked: Boolean(playerMe.account.linked),
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
