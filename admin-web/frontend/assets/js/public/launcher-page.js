import { loadLauncherMetadata, loadPatchIndex } from "./launcher-data.js?v=20260815launchernews2";
import { bindLauncherLightbox, renderLauncherMetadata, renderLauncherNews } from "./launcher-render.js?v=20260825designpass2";

export async function initLauncherPage() {
  renderLauncherMetadata(null);
  bindLauncherLightbox();
  const [metadata, patches] = await Promise.all([loadLauncherMetadata(), loadPatchIndex()]);
  renderLauncherMetadata(metadata);
  renderLauncherNews(patches);
}
