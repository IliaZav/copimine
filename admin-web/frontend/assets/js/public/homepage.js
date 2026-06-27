import { loadPublicHomepageData, loadPublicTreasuryFallback } from "./site-data.js";
import { createHomepageRenderer } from "./site-render.js";

const renderer = createHomepageRenderer();
let homepageLoaded = false;

function bindCopyIpButton() {
  const button = document.getElementById("copyIpBtn");
  if (!button || button.dataset.bound === "true") return;
  button.dataset.bound = "true";
  button.addEventListener("click", async () => {
    const ip = document.getElementById("serverIpText")?.textContent?.trim() || "";
    if (!ip || ip === "Адрес сервера пока не указан" || ip === "загружаем адрес...") return;
    try {
      await navigator.clipboard.writeText(ip);
      button.textContent = "IP скопирован";
      window.setTimeout(() => {
        button.textContent = "Скопировать IP";
      }, 1400);
    } catch (_error) {
      button.textContent = ip;
    }
  });
}

async function loadHomepage() {
  if (homepageLoaded) return;
  homepageLoaded = true;
  bindCopyIpButton();
  try {
    const payload = await loadPublicHomepageData();
    renderer.renderServerHero(payload.config, payload.status, payload.modpack);
    renderer.renderStatus(payload.status, payload.config);
    renderer.renderOnline(payload.status.server || {});
    renderer.renderBudget(payload.budget || {});
    renderer.renderPresidentCard(payload.president || payload.budget || {});
    renderer.renderHistory(payload.history.items || []);
  } catch (_error) {
    try {
      const { budgetPayload, historyPayload } = await loadPublicTreasuryFallback();
      if (budgetPayload?.ok && budgetPayload.data) renderer.renderBudget(budgetPayload.data);
      if (historyPayload?.ok && historyPayload.data) renderer.renderHistory(historyPayload.data.items || []);
    } catch (_fallbackError) {
      renderer.renderUnavailableState();
    }
  }
}

window.addEventListener("copimine:public-status", (event) => {
  renderer.renderStatusPayload(event.detail || {});
});

window.addEventListener("copimine:auth-state", (event) => {
  renderer.renderAuthState(event.detail || {});
});

window.setTimeout(loadHomepage, 120);
