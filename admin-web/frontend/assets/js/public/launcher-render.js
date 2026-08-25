function text(node, value) {
  if (node) node.textContent = String(value ?? "");
}

function formatBytes(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes <= 0) return "—";
  const units = ["Б", "КиБ", "МиБ", "ГиБ"];
  let size = bytes;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) { size /= 1024; index += 1; }
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`;
}

function formatDate(value) {
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : new Intl.DateTimeFormat("ru-RU", { dateStyle: "medium" }).format(date);
}

function createSummaryList(summary) {
  const list = document.createElement("ul");
  (Array.isArray(summary) ? summary : []).slice(0, 3).forEach((value) => {
    const item = document.createElement("li");
    item.textContent = value;
    list.append(item);
  });
  return list;
}

export function renderLauncherMetadata(metadata) {
  const folderButton = document.getElementById("launcherFolderBtn");
  const state = document.getElementById("launcherDownloadStatus");
  const fields = {
    version: document.getElementById("launcherVersion"),
    size: document.getElementById("launcherSize"),
    published: document.getElementById("launcherPublished"),
    platform: document.getElementById("launcherPlatform"),
    sha: document.getElementById("launcherSha256"),
  };
  text(fields.version, metadata?.version || "—");
  text(fields.size, metadata ? formatBytes(metadata.customInstaller?.sizeBytes ?? metadata.sizeBytes) : "—");
  text(fields.published, metadata ? formatDate(metadata.publishedAt) : "—");
  text(fields.platform, metadata ? `Windows ${metadata.minimumWindowsBuild}+ · ${metadata.architecture}` : "—");
  text(fields.sha, metadata?.customInstaller?.sha256 || metadata?.sha256 || "—");
  if (!state) return;
  if (folderButton instanceof HTMLAnchorElement && metadata?.customInstaller) {
    configureDownloadButton(folderButton, metadata.customInstaller);
  } else if (folderButton instanceof HTMLAnchorElement) {
    disableDownloadButton(folderButton);
  }
  if (metadata?.customInstaller) {
    state.dataset.state = "ready";
    text(state, `${metadata.customInstaller.filename} · выберите диск и папку в установщике.`);
  } else {
    state.dataset.state = "error";
    text(state, "Установщик временно недоступен.");
  }
}

function configureDownloadButton(button, installer) {
  if (!(button instanceof HTMLAnchorElement)) return;
  button.href = installer.downloadUrl;
  button.download = installer.filename;
  button.removeAttribute("aria-disabled");
  button.classList.remove("is-disabled");
}

function disableDownloadButton(button) {
  if (!(button instanceof HTMLAnchorElement)) return;
  button.removeAttribute("href");
  button.removeAttribute("download");
  button.setAttribute("aria-disabled", "true");
  button.classList.add("is-disabled");
}

export function renderLauncherNews(patches) {
  const mount = document.getElementById("launcherNewsGrid");
  if (!mount) return;
  const fragment = document.createDocumentFragment();
  const safePatches = Array.isArray(patches) ? patches.slice(0, 3) : [];
  if (safePatches.length === 0) {
    const empty = document.createElement("p");
    empty.className = "patch-empty";
    empty.textContent = "Последние обновления пока недоступны.";
    fragment.append(empty);
  }
  safePatches.forEach((patch) => {
    const card = document.createElement("article");
    card.className = "news-card";
    if (patch.thumbnailUrl) {
      const image = document.createElement("img");
      image.className = "news-card-media";
      image.loading = "lazy";
      image.src = patch.thumbnailUrl;
      image.alt = `Иконка предмета из обновления ${patch.version}`;
      card.append(image);
    }
    const body = document.createElement("div");
    body.className = "news-card-body";
    const meta = document.createElement("div");
    meta.className = "news-card-meta";
    const version = document.createElement("span");
    version.textContent = `v${patch.version}`;
    meta.append(version);
    (Array.isArray(patch.badges) ? patch.badges.slice(0, 3) : []).forEach((badge) => {
      const badgeNode = document.createElement("span");
      badgeNode.className = "news-card-badge";
      badgeNode.textContent = badge;
      meta.append(badgeNode);
    });
    body.append(meta);
    const heading = document.createElement("h3");
    heading.textContent = patch.title;
    body.append(heading);
    body.append(createSummaryList(patch.summary));
    const link = document.createElement("a");
    link.className = "launcher-news-link";
    link.href = patch.detailUrl;
    link.textContent = "Подробнее →";
    body.append(link);
    card.append(body);
    fragment.append(card);
  });
  mount.replaceChildren(fragment);
}

export function bindLauncherLightbox() {
  const fallbackSource = "/assets/launcher-screenshots/launcher-home.jpg";
  const dialog = document.getElementById("launcherLightbox");
  const image = dialog?.querySelector("img");
  const caption = dialog?.querySelector("figcaption");
  const closeButton = dialog?.querySelector(".launcher-lightbox-close");
  if (!(dialog instanceof HTMLDialogElement) || !(image instanceof HTMLImageElement) || !(caption instanceof HTMLElement)) return;
  document.querySelectorAll("[data-lightbox-src]").forEach((button) => {
    if (!(button instanceof HTMLButtonElement) || button.dataset.bound === "true") return;
    button.dataset.bound = "true";
    button.addEventListener("click", () => {
      const source = String(button.dataset.lightboxSrc || "");
      if (!source.startsWith("/assets/launcher-screenshots/") || source.includes("..")) return;
      image.src = source;
      image.alt = String(button.dataset.lightboxAlt || "Скриншот CopiMine Launcher");
      caption.textContent = image.alt;
      dialog.showModal();
    });
  });
  if (closeButton instanceof HTMLButtonElement) {
    closeButton.addEventListener("click", () => {
      if (dialog.open) dialog.close();
    });
  }
  dialog.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && dialog.open) {
      event.preventDefault();
      dialog.close();
    }
  });
  dialog.addEventListener("cancel", (event) => {
    if (dialog.open) {
      event.preventDefault();
      dialog.close();
    }
  });
  dialog.addEventListener("close", () => {
    image.src = fallbackSource;
    image.alt = "Предварительный просмотр скриншота CopiMine Launcher";
    caption.textContent = "";
  });
}
