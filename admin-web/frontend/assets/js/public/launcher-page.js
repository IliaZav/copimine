import { loadLauncherMetadata, loadPatchIndex } from "./launcher-data.js?v=20260815launchernews2";
import { bindLauncherLightbox, renderLauncherMetadata, renderLauncherNews } from "./launcher-render.js?v=20260825siteui19";

let launcherLoadInFlight = false;

function setLauncherLoadingState() {
  const state = document.getElementById("launcherDownloadStatus");
  if (!state) return;
  state.dataset.state = "loading";
  state.textContent = "Проверяем установщик…";
}

async function refreshLauncherPage() {
  if (launcherLoadInFlight) return;
  launcherLoadInFlight = true;
  const retryButton = document.getElementById("launcherRetryBtn");
  if (retryButton instanceof HTMLButtonElement) {
    retryButton.disabled = true;
    retryButton.textContent = "Проверяем…";
  }
  setLauncherLoadingState();
  try {
    const [metadata, patches] = await Promise.all([loadLauncherMetadata(), loadPatchIndex()]);
    renderLauncherMetadata(metadata);
    renderLauncherNews(patches);
  } finally {
    launcherLoadInFlight = false;
    if (retryButton instanceof HTMLButtonElement) {
      retryButton.disabled = false;
      retryButton.textContent = "Повторить";
    }
  }
}

function bindLauncherRetry() {
  const retryButton = document.getElementById("launcherRetryBtn");
  if (!(retryButton instanceof HTMLButtonElement) || retryButton.dataset.bound === "true") return;
  retryButton.dataset.bound = "true";
  retryButton.addEventListener("click", () => void refreshLauncherPage());
}

export async function initLauncherPage() {
  renderLauncherMetadata(null);
  bindLauncherLightbox();
  bindLauncherRetry();
  await refreshLauncherPage();
}
