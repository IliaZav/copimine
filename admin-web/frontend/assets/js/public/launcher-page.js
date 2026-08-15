import { loadLauncherMetadata, loadPatchIndex } from "./launcher-data.js?v=20260815launchernews1";
import { bindLauncherLightbox, renderLauncherMetadata, renderLauncherNews } from "./launcher-render.js?v=20260815launchernews1";

export async function initLauncherPage() {
  renderLauncherMetadata(null);
  bindLauncherLightbox();
  const [metadata, patches] = await Promise.all([loadLauncherMetadata(), loadPatchIndex()]);
  renderLauncherMetadata(metadata);
  renderLauncherNews(patches);
}
