function makeChangeList(changes) {
  const list = document.createElement("ul");
  (Array.isArray(changes) ? changes : []).forEach((change) => {
    const item = document.createElement("li");
    const badge = document.createElement("span");
    badge.className = `change-kind change-kind-${String(change.kind || "changed").replace(/[^a-z_]/g, "")}`;
    badge.textContent = String(change.kind || "changed");
    item.append(badge, document.createTextNode(String(change.text || "")));
    list.append(item);
  });
  return list;
}

export function renderNewsList(patches) {
  const mount = document.getElementById("newsList");
  if (!mount) return;
  const fragment = document.createDocumentFragment();
  if (!patches.length) {
    const empty = document.createElement("p");
    empty.className = "patch-empty";
    empty.textContent = "Обновления пока недоступны.";
    fragment.append(empty);
  }
  patches.forEach((patch) => {
    const card = document.createElement("article");
    card.className = "news-card";
    const body = document.createElement("div");
    body.className = "news-card-body";
    const meta = document.createElement("div");
    meta.className = "news-card-meta";
    const version = document.createElement("span");
    version.textContent = `v${String(patch.version || "")}`;
    meta.append(version);
    (Array.isArray(patch.badges) ? patch.badges.slice(0, 3) : []).forEach((value) => {
      const badge = document.createElement("span");
      badge.className = "news-card-badge";
      badge.textContent = String(value);
      meta.append(badge);
    });
    body.append(meta);
    const heading = document.createElement("h2");
    heading.textContent = String(patch.title || "Обновление");
    body.append(heading);
    body.append(makeChangeList((Array.isArray(patch.summary) ? patch.summary : []).map((text) => ({ kind: "", text }))));
    const link = document.createElement("a");
    link.href = String(patch.detailUrl || "#");
    link.textContent = "Открыть патчноут →";
    body.append(link);
    card.append(body);
    fragment.append(card);
  });
  mount.replaceChildren(fragment);
}

export function renderPatchDetail(detail) {
  const mount = document.getElementById("patchDetailDynamic");
  if (mount) mount.replaceChildren();
  if (!detail && mount) {
    const message = document.createElement("p");
    message.className = "patch-empty";
    message.textContent = "Патчноут недоступен.";
    mount.append(message);
    return;
  }
  if (!detail) return;
  const items = Array.isArray(detail.items) ? detail.items : [];
  const itemMount = document.getElementById("patch-items");
  if (itemMount) {
    items.forEach((item) => {
      const target = itemMount.querySelector(`[data-item-id="${CSS.escape(String(item.itemId || ""))}"]`);
      if (!target) return;
      const heading = target.querySelector("h3");
      if (heading) heading.textContent = String(item.displayName || item.itemId || "Предмет");
      const icon = target.querySelector(".patch-item-icon");
      if (icon && item.iconUrl && String(item.iconUrl).startsWith("/assets/patch-items/") && !String(item.iconUrl).includes("..")) {
        const image = document.createElement("img");
        image.src = item.iconUrl;
        image.alt = `Иконка предмета ${String(item.displayName || item.itemId || "")}`;
        image.loading = "lazy";
        icon.replaceChildren(image);
      }
    });
  }
}
