import {
  loadPublicAuthState,
  loadPublicHomepageData,
  loadPublicModsPageData,
  loadPublicServerPageData,
  loadPublicShopsPageData,
  loadPublicTreasuryFallback,
} from "./site-data.js";
import { createHomepageRenderer } from "./site-render.js";
import { createSuccessfulLoadRegistry } from "../shared/successful-load-registry.js";

const renderer = createHomepageRenderer();
const pageLoads = createSuccessfulLoadRegistry();
let homepageEventsBound = false;

function bindCopyIpButton() {
  const button = document.getElementById("copyIpBtn");
  if (!button || button.dataset.bound === "true") return;
  button.dataset.bound = "true";
  button.addEventListener("click", async () => {
    const ipNode = document.getElementById("serverIpText");
    const ip = ipNode?.dataset.serverAddress?.trim() || ipNode?.textContent?.trim() || "copimine.ru";
    if (!ip) return;
    try {
      await navigator.clipboard.writeText(ip);
      button.textContent = "Адрес скопирован";
      window.setTimeout(() => {
        button.textContent = `Скопировать ${ip}`;
      }, 1400);
    } catch (_error) {
      button.textContent = ip;
    }
  });
}

function resolvePublicPageKind() {
  const explicit = String(document.body?.dataset.pageKind || "").trim().toLowerCase();
  if (explicit) return explicit;
  const pathname = String(window.location.pathname || "").toLowerCase();
  if (pathname.endsWith("/server.html") || pathname.endsWith("server.html")) return "public-server";
  if (pathname.endsWith("/shops.html") || pathname.endsWith("shops.html")) return "public-shops";
  if (pathname.endsWith("/mods.html") || pathname.endsWith("mods.html")) return "public-mods";
  return "public-home";
}

async function loadPublicPageByKind(kind, authState) {
  switch (kind) {
    case "public-server": {
      const payload = await loadPublicServerPageData();
      renderer.renderServerHero(payload.config, payload.status, {});
      renderer.renderStatus(payload.status, payload.config);
      renderer.renderOnline(payload.status.server || {});
      renderer.renderBudget(payload.budget || {});
      renderer.renderPresidentCard(payload.president || payload.budget || {});
      renderer.renderHistory(payload.history.items || []);
      renderer.renderAuthState(authState);
      renderer.renderCms(payload.cms || {}, kind);
      return;
    }
    case "public-shops": {
      const payload = await loadPublicShopsPageData(authState);
      renderer.renderCommerce(payload.arCatalog || {}, payload.donationCatalog || {}, authState, payload.ownership || {});
      renderer.renderAuthState(authState);
      renderer.renderCms(payload.cms || {}, kind);
      return;
    }
    case "public-mods": {
      const payload = await loadPublicModsPageData();
      renderer.renderModpack(payload.modpack || {}, payload.config || {});
      renderer.renderAuthState(authState);
      renderer.renderCms(payload.cms || {}, kind);
      return;
    }
    case "public-home":
    default: {
      const payload = await loadPublicHomepageData(authState);
      renderer.renderServerHero(payload.config, payload.status, payload.modpack);
      renderer.renderStatus(payload.status, payload.config);
      renderer.renderBudget(payload.budget || {});
      renderer.renderPresidentCard(payload.president || payload.budget || {});
      renderer.renderHistory(payload.history.items || []);
      renderer.renderCommerce(payload.arCatalog || {}, payload.donationCatalog || {}, authState, payload.ownership || {});
      renderer.renderAuthState(authState);
      renderer.renderCms(payload.cms || {}, kind);
    }
  }
}

async function renderFallbackForKind(kind) {
  if (kind === "public-server") {
    try {
      const { budgetPayload, historyPayload } = await loadPublicTreasuryFallback();
      if (budgetPayload?.ok && budgetPayload.data) renderer.renderBudget(budgetPayload.data);
      if (historyPayload?.ok && historyPayload.data) renderer.renderHistory(historyPayload.data.items || []);
      return;
    } catch (_fallbackError) {
      renderer.renderUnavailableState();
      return;
    }
  }
  renderer.renderUnavailableState();
}

export async function loadPublicPage(kind = resolvePublicPageKind()) {
  try {
    await pageLoads.run(kind, async () => {
      bindCopyIpButton();
      const authState = await loadPublicAuthState();
      await loadPublicPageByKind(kind, authState);
    });
  } catch (_error) {
    await renderFallbackForKind(kind);
  }
}

export async function loadHomepage() {
  await loadPublicPage(resolvePublicPageKind());
}

export function bindHomepageEvents() {
  if (homepageEventsBound) return;
  homepageEventsBound = true;
  window.addEventListener("copimine:public-status", (event) => {
    renderer.renderStatusPayload(event.detail || {});
  });

  window.addEventListener("copimine:auth-state", (event) => {
    renderer.renderAuthState(event.detail || {});
  });
}
