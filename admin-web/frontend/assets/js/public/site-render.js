import { makeElement, replaceChildrenSafe } from "../shared/dom.js";
import { appRouteHref, authLandingHref, defaultAppRouteForRole } from "../shared/app-routes.js";

const MC_ICON_ROOT = "/assets/mc-icons/item";

function mcIcon(fileName) {
  return `${MC_ICON_ROOT}/${fileName}`;
}

function createSprite(path, className = "mc-sprite") {
  const img = document.createElement("img");
  img.className = className;
  img.src = String(path || "");
  img.alt = "";
  img.loading = "lazy";
  return img;
}

function materialIconName(material) {
  const raw = String(material || "").trim().toLowerCase();
  return raw ? `${raw}.png` : "";
}

function resolveShopIcon(row = {}, mode = "ar") {
  const material = materialIconName(row.base_material);
  if (material) return mcIcon(material);
  const itemId = String(row.item_id || "").toLowerCase();
  if (itemId.includes("pickaxe")) return mcIcon("diamond_pickaxe.png");
  if (itemId.includes("sword")) return mcIcon("diamond_sword.png");
  if (itemId.includes("book")) return mcIcon("written_book.png");
  return mode === "ar" ? mcIcon("diamond_ore.png") : mcIcon("totem_of_undying.png");
}

function formatAr(value) {
  const amount = Number(value || 0);
  return `${(Number.isFinite(amount) ? amount : 0).toLocaleString("ru-RU")} AR`;
}

function formatDonate(value) {
  const amount = Number(value || 0);
  return `${(Number.isFinite(amount) ? amount : 0).toLocaleString("ru-RU")} DC`;
}

function formatDate(value) {
  const raw = Number(value || 0);
  if (!raw) return "нет даты";
  const date = raw > 1000000000000 ? new Date(raw) : new Date(raw * 1000);
  if (!Number.isFinite(date.getTime())) return "нет даты";
  return date.toLocaleString("ru-RU");
}

function formatLatency(value) {
  const ms = Number(value);
  if (!Number.isFinite(ms) || ms <= 0) return "нет ответа";
  return `${ms.toFixed(1)} мс`;
}

function formatPlayers(server = {}) {
  const online = Number(server.playersOnline || 0);
  const cap = Number(server.playerCap || 0);
  return cap > 0 ? `${online} / ${cap}` : `${online}`;
}

function formatMegabytes(bytes) {
  const value = Number(bytes || 0);
  if (!Number.isFinite(value) || value <= 0) return "0 МБ";
  return `${(value / (1024 * 1024)).toFixed(2)} МБ`;
}

function shortSha(value) {
  const sha = String(value || "").trim();
  if (!sha) return "нет";
  return sha.length > 12 ? `${sha.slice(0, 12)}…` : sha;
}

function cabinetRoute(targetRoute) {
  const raw = String(targetRoute || "cabinet").replace(/^#/, "").trim().toLowerCase() || "cabinet";
  return appRouteHref(raw);
}

function publicPageRoute(pageName) {
  const pathname = String(window.location.pathname || "").toLowerCase();
  const fileName = String(pageName || "index.html").toLowerCase();
  if (pathname.endsWith(`/${fileName}`) || pathname.endsWith(fileName)) {
    return pathname;
  }
  return fileName;
}

function roleRoute(role) {
  return appRouteHref(defaultAppRouteForRole(role || ""));
}

function roleLabel(role) {
  switch (String(role || "")) {
    case "player":
      return "Открыть кабинет";
    case "junior_admin":
      return "Открыть кабинет";
    case "admin":
      return "Открыть кабинет";
    case "owner":
      return "Открыть кабинет";
    default:
      return "Открыть кабинет";
  }
}

function cardStrong(title, value, note = "", iconPath = "") {
  const card = makeElement("article", "showcase-card");
  if (iconPath) {
    const icon = makeElement("div", "showcase-card-icon");
    icon.append(createSprite(iconPath));
    card.append(icon);
  }
  card.append(
    makeElement("strong", "", title),
    makeElement("p", "", value),
  );
  if (note) {
    card.append(makeElement("span", "treasury-history-date", note));
  }
  return card;
}

function buildModpackMeta(label, value) {
  const card = makeElement("article", "modpack-stat");
  card.append(
    makeElement("span", "", label),
    makeElement("strong", "", value),
  );
  return card;
}

function buildExternalModCard(row = {}) {
  const card = makeElement("article", "modpack-file-card");
  const meta = makeElement("div", "modpack-file-meta");
  const icon = makeElement("div", "modpack-file-icon");
  icon.append(createSprite(mcIcon("knowledge_book.png")));
  if (row.license) meta.append(makeElement("span", "", String(row.license)));
  if (row.distribution) meta.append(makeElement("span", "", String(row.distribution)));
  card.append(
    icon,
    makeElement("span", "modpack-file-badge", "official"),
    makeElement("strong", "", String(row.component || "Внешний мод")),
    makeElement("p", "", String(row.feature || row.reason || "Отдельная загрузка с официальной страницы.")),
    meta,
  );
  const links = makeElement("div", "public-actions public-actions-compact");
  if (row.clientSource) {
    const clientLink = makeElement("a", "btn btn-ghost public-ghost", "Клиент");
    clientLink.href = String(row.clientSource);
    clientLink.target = "_blank";
    clientLink.rel = "noopener noreferrer";
    links.append(clientLink);
  }
  if (row.serverSource) {
    const serverLink = makeElement("a", "btn btn-ghost public-ghost", "Сервер");
    serverLink.href = String(row.serverSource);
    serverLink.target = "_blank";
    serverLink.rel = "noopener noreferrer";
    links.append(serverLink);
  }
  if (links.childNodes.length) {
    card.append(links);
  }
  return card;
}

function buildShopItem(row, mode = "ar") {
  const card = makeElement("article", "shop-item-chip");
  const icon = makeElement("div", "shop-item-icon");
  icon.append(createSprite(resolveShopIcon(row, mode)));
  const head = makeElement("div", "shop-item-head");
  const title = makeElement("strong", "", String(row.display_name || row.item_id || "Товар"));
  const price = makeElement(
    "span",
    "shop-item-price",
    mode === "ar" ? formatAr(row.price_ar || 0) : formatDonate(row.price_donation || 0),
  );
  head.append(title, price);
  card.append(icon, head);

  const meta = makeElement("div", "shop-item-meta");
  const material = String(row.base_material || "").trim();
  const effect = String(row.effect_description || row.effect_profile_id || "").trim();
  if (material) meta.append(makeElement("span", "", material));
  if (effect) meta.append(makeElement("span", "", effect));
  if (Number(row.cooldown_seconds || 0) > 0) {
    meta.append(makeElement("span", "", `${Number(row.cooldown_seconds)} сек. кулдаун`));
  }
  card.append(meta);
  return card;
}

export function createHomepageRenderer() {
  const budgetCounter = document.getElementById("presidentBudgetCounter");
  const budgetDetail = document.getElementById("presidentBudgetDetail");
  const budgetOwner = document.getElementById("presidentBudgetOwner");
  const historyMount = document.getElementById("publicTreasuryHistory");
  const presidentName = document.getElementById("presidentCardName");
  const presidentMeta = document.getElementById("presidentCardMeta");
  const skinShell = document.getElementById("presidentSkinShell");
  const skinTilt = document.getElementById("presidentSkinTilt");
  const skinImage = document.getElementById("presidentSkinImage");
  const leftButton = document.getElementById("presidentSkinLeft");
  const rightButton = document.getElementById("presidentSkinRight");
  const serverIpText = document.getElementById("serverIpText");
  const serverPulseText = document.getElementById("serverPulseText");
  const downloadModsBtn = document.getElementById("downloadModsBtn");
  const statusGrid = document.getElementById("publicStatusGrid");
  const onlineBoard = document.getElementById("publicOnlineBoard");
  const cabinetButton = document.getElementById("publicCabinetBtn");
  const heroMiniTitle = document.getElementById("heroMiniTitle");
  const heroMiniText = document.getElementById("heroMiniText");
  const modpackSummaryLead = document.getElementById("modpackSummaryLead");
  const modpackMetaGrid = document.getElementById("modpackMetaGrid");
  const modpackFileGrid = document.getElementById("modpackFileGrid");
  const modpackExternalGrid = document.getElementById("modpackExternalGrid");
  const modpackNotes = document.getElementById("modpackNotes");
  const arShopMount = document.getElementById("publicArShopPreview");
  const donationShopMount = document.getElementById("publicDonationShopPreview");
  const openArShopBtn = document.getElementById("openArShopBtn");
  const openDonationShopBtn = document.getElementById("openDonationShopBtn");

  let currentBudgetValue = 0;
  let currentAuth = { role: "", cookieAuth: false };

  function animateCounter(nextValue) {
    if (!budgetCounter) return;
    const target = Math.max(0, Number(nextValue || 0));
    const startedAt = performance.now();
    const start = currentBudgetValue;
    const duration = 850;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches) {
      currentBudgetValue = target;
      budgetCounter.textContent = formatAr(target);
      return;
    }
    const step = (now) => {
      const progress = Math.min(1, (now - startedAt) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      const frame = Math.round(start + (target - start) * eased);
      budgetCounter.textContent = formatAr(frame);
      if (progress < 1) {
        requestAnimationFrame(step);
        return;
      }
      currentBudgetValue = target;
      budgetCounter.textContent = formatAr(target);
    };
    requestAnimationFrame(step);
  }

  function routePublicCommerce(targetRoute) {
    if (currentAuth?.role || currentAuth?.cookieAuth) {
      window.location.href = cabinetRoute(targetRoute);
      return;
    }
    window.location.href = authLandingHref("signin");
  }

  function bindCabinetButton() {
    if (!cabinetButton || cabinetButton.dataset.bound === "true") return;
    cabinetButton.dataset.bound = "true";
    cabinetButton.addEventListener("click", () => {
      const routeTarget = cabinetButton.dataset.routeTarget || "";
      if (!routeTarget) return;
      window.location.href = routeTarget;
    });
  }

  function bindCommerceButtons() {
    if (openArShopBtn && openArShopBtn.dataset.bound !== "true") {
      openArShopBtn.dataset.bound = "true";
      openArShopBtn.addEventListener("click", () => routePublicCommerce("artifacts"));
    }
    if (openDonationShopBtn && openDonationShopBtn.dataset.bound !== "true") {
      openDonationShopBtn.dataset.bound = "true";
      openDonationShopBtn.addEventListener("click", () => routePublicCommerce("donation-shop"));
    }
  }

  function renderCommerce(arCatalog = {}, donationCatalog = {}) {
    if (arShopMount) {
      const cards = Array.isArray(arCatalog.items) && arCatalog.items.length
        ? arCatalog.items.slice(0, 6).map((row) => buildShopItem(row, "ar"))
        : [cardStrong("AR-лавка недоступна", "Каталог временно недоступен.", "", mcIcon("diamond_ore.png"))];
      replaceChildrenSafe(arShopMount, cards);
    }
    if (donationShopMount) {
      const cards = Array.isArray(donationCatalog.items) && donationCatalog.items.length
        ? donationCatalog.items.slice(0, 6).map((row) => buildShopItem(row, "donation"))
        : [cardStrong("Donation-лавка недоступна", "Каталог временно недоступен.", "", mcIcon("totem_of_undying.png"))];
      replaceChildrenSafe(donationShopMount, cards);
    }
  }

  function renderModpack(modpack = {}, config = {}) {
    const manifest = modpack && typeof modpack === "object" ? (modpack.manifest || {}) : {};
    const files = Array.isArray(manifest.files) ? manifest.files : [];
    const requiredExternal = Array.isArray(manifest.requiredExternal) ? manifest.requiredExternal : [];
    const notes = Array.isArray(manifest.notes) ? manifest.notes : [];
    const available = Boolean(modpack.available);
    const downloadUrl = modpack.downloadUrl || config.modpackDownloadPath || "/downloads/CopiMineMods.zip";

    const pageKind = String(document.body?.dataset?.pageKind || "").toLowerCase();
    if (heroMiniTitle) {
      heroMiniTitle.textContent = available
        ? (modpack.filename || "CopiMineMods.zip")
        : "Клиент и моды";
    }
    if (heroMiniText) {
      if (available) {
        const versionText = `${manifest.loader || "Fabric"} ${manifest.minecraftVersion || config.serverVersion || ""}`.trim();
        const fileText = `файлов ${files.length || 0}`;
        const extraText = requiredExternal.length ? ` · отдельно ${requiredExternal.length}` : "";
        heroMiniText.textContent = pageKind === "public-home"
          ? `${versionText} · ${fileText}`
          : `${versionText} · ${fileText}${extraText}`;
      } else {
        heroMiniText.textContent = "Архив модов недоступен.";
      }
    }
    if (modpackSummaryLead) {
      modpackSummaryLead.textContent = available
        ? `Размер ${formatMegabytes(modpack.size || 0)}`
        : "Архив модов недоступен.";
    }
    if (modpackMetaGrid) {
      replaceChildrenSafe(modpackMetaGrid, [
        buildModpackMeta("Minecraft", manifest.minecraftVersion || config.serverVersion || "1.21.1"),
        buildModpackMeta("Loader", manifest.loader || "Fabric"),
        buildModpackMeta("Файлов", String(files.length || 0)),
        buildModpackMeta("Отдельно", String(requiredExternal.length || 0)),
        buildModpackMeta("Размер", formatMegabytes(modpack.size || 0)),
        buildModpackMeta("Обновлён", modpack.modified ? formatDate(modpack.modified * 1000) : "нет данных"),
      ]);
    }
    if (modpackFileGrid) {
        if (!available || !files.length) {
          replaceChildrenSafe(modpackFileGrid, [
            cardStrong("Архив недоступен", "Список файлов недоступен.", "", mcIcon("bundle.png")),
          ]);
      } else {
        replaceChildrenSafe(
          modpackFileGrid,
          files.map((file) => {
            const card = makeElement("article", "modpack-file-card");
            const meta = makeElement("div", "modpack-file-meta");
            const icon = makeElement("div", "modpack-file-icon");
            icon.append(createSprite(mcIcon("bundle.png")));
            meta.append(makeElement("span", "", String(file.path || "mods/unknown.jar")));
            if (file.license) meta.append(makeElement("span", "", String(file.license)));
            card.append(
              icon,
              makeElement("span", "modpack-file-badge", "mods"),
              makeElement("strong", "", String(file.component || file.path || "Мод")),
              makeElement("p", "", String(file.version || "без версии")),
              meta,
            );
            return card;
          }),
        );
      }
    }
    if (modpackExternalGrid) {
      if (!requiredExternal.length) {
        replaceChildrenSafe(modpackExternalGrid, [
          cardStrong("Дополнительные моды не нужны", "Всё нужное уже внутри архива.", "", mcIcon("book.png")),
        ]);
      } else {
        replaceChildrenSafe(modpackExternalGrid, requiredExternal.map((row) => buildExternalModCard(row)));
      }
    }
    if (modpackNotes) {
      const noteCards = (notes.length ? notes : ["Сверь версию Minecraft и состав архива."]).map((note) => {
        const card = makeElement("article", "modpack-note-card");
        card.append(
          makeElement("strong", "", "Примечание"),
          makeElement("p", "", String(note)),
        );
        return card;
      });
      replaceChildrenSafe(modpackNotes, noteCards);
    }
    if (downloadModsBtn && available) {
      downloadModsBtn.href = downloadUrl;
    }
  }

  function renderServerHero(config = {}, status = {}, modpack = {}) {
    const server = status.server || {};
    const serverAddress = String(config.serverAddress || "").trim();
    const address = serverAddress || "copimine.ru";
    const onlineText = server.online
      ? `Онлайн ${formatPlayers(server)} · задержка ${formatLatency(server.latencyMs)}`
      : "Сервер не ответил";

    if (serverIpText) {
      serverIpText.textContent = address;
      serverIpText.dataset.serverAddress = address;
    }
    if (serverPulseText) {
      serverPulseText.textContent = onlineText;
    }
    if (downloadModsBtn) {
      if (modpack.available) {
        downloadModsBtn.href = modpack.downloadUrl || config.modpackDownloadPath || "/downloads/CopiMineMods.zip";
        downloadModsBtn.textContent = `Скачать моды (${formatMegabytes(modpack.size || 0)})`;
        downloadModsBtn.classList.remove("btn-disabled");
        downloadModsBtn.removeAttribute("aria-disabled");
      } else {
        downloadModsBtn.href = publicPageRoute("mods.html");
        downloadModsBtn.textContent = "Архив недоступен";
        downloadModsBtn.classList.add("btn-disabled");
        downloadModsBtn.setAttribute("aria-disabled", "true");
      }
    }
    renderModpack(modpack, config);
  }

  function renderStatus(status = {}, config = {}) {
    if (!statusGrid) return;
    const server = status.server || {};
    const elections = status.elections || {};
    const electionDetail = elections.active
      ? [
          `кандидатов ${Number(elections.candidates || 0)}`,
          `голосов ${Number(elections.votes || 0)}`,
          elections.stations ? `участков ${Number(elections.stations || 0)}` : "",
        ].filter(Boolean).join(" · ")
      : "Голосование не запущено";
    replaceChildrenSafe(statusGrid, [
      cardStrong("Сервер", server.online ? "Онлайн" : "Нет ответа", formatLatency(server.latencyMs), mcIcon("beacon.png")),
      cardStrong("Игроки", formatPlayers(server), server.playerListAvailable ? "Список открыт" : "Список скрыт", mcIcon("totem_of_undying.png")),
      cardStrong("Выборы", elections.active ? "Активны" : "Пауза", electionDetail, mcIcon("written_book.png")),
      cardStrong("Версия", config.serverVersion || "1.21.1", config.resourcePackRequired ? "Ресурспак обязателен" : "Ресурспак опционален", mcIcon("compass_00.png")),
    ]);
  }

  function renderOnline(server = {}) {
    if (!onlineBoard) return;
    const players = Array.isArray(server.samplePlayers) ? server.samplePlayers.filter(Boolean) : [];
    if (!players.length) {
      replaceChildrenSafe(onlineBoard, [
        cardStrong("Игроки онлайн", "Список скрыт.", "", mcIcon("filled_map.png")),
      ]);
      return;
    }
    replaceChildrenSafe(
      onlineBoard,
      players.slice(0, 12).map((player) => cardStrong("Онлайн", String(player))),
    );
  }

  function renderHistory(items = []) {
    if (!historyMount) return;
    const rows = Array.isArray(items) ? items : [];
    if (!rows.length) {
      replaceChildrenSafe(historyMount, [
        cardStrong("Операций нет", "Открытых движений казны нет.", "", mcIcon("book.png")),
      ]);
      return;
    }
    replaceChildrenSafe(
      historyMount,
      rows.slice(0, 6).map((row) => {
        const card = makeElement("article", "treasury-history-card");
        const head = makeElement("div", "treasury-history-row");
        head.append(
          makeElement("span", "treasury-history-type", String(row.label || row.type || "Операция")),
          makeElement("strong", "", formatAr(row.amount || row.amount_ar || 0)),
        );
        card.append(
          head,
          makeElement("p", "", String(row.comment || row.item_name || row.public_actor_name || "Операция казны")),
          makeElement("span", "treasury-history-date", formatDate(row.createdAt || row.created_at)),
        );
        return card;
      }),
    );
  }

  function renderPresidentCard(president = {}) {
    if (!presidentName || !presidentMeta) return;
    const name = String(president.current_president_name || president.ownerName || "").trim();
    const uuid = String(president.current_president_uuid || president.ownerUuid || "").trim();
    if (!name) {
      presidentName.textContent = "Президент пока не избран";
      presidentMeta.textContent = "Действующего президента нет.";
      skinShell?.classList.add("hidden");
      if (skinImage) skinImage.removeAttribute("src");
      return;
    }
    presidentName.textContent = name;
    presidentMeta.textContent = "Действующий президент сервера.";
    if (!uuid || !skinImage || !skinShell) {
      skinShell?.classList.add("hidden");
      return;
    }
    skinImage.src = `/api/public/president/skin/body?uuid=${encodeURIComponent(uuid)}`;
    skinImage.alt = `Скин президента ${name}`;
    skinShell.classList.remove("hidden");
  }

  function renderBudget(payload = {}) {
    const balance = Number(payload.balance_ar ?? payload.balance ?? 0);
    const currentPresidentName = String(payload.current_president_name || payload.ownerName || "").trim();
    if (budgetOwner) {
      budgetOwner.textContent = currentPresidentName
        ? `Казной управляет ${currentPresidentName}`
        : "Активный президент не назначен";
    }
    if (budgetDetail) {
      budgetDetail.textContent = payload.updated_at || payload.updatedAt
        ? `Обновлено ${formatDate(payload.updated_at || payload.updatedAt)}`
        : "Баланс казны.";
    }
    animateCounter(balance);
    renderPresidentCard(payload);
  }

  function renderStatusPayload(detail = {}) {
    const status = detail.status || detail || {};
    const config = detail.config || {};
    const modpack = detail.modpack || {};
    const treasury = status.treasury || {};
    renderServerHero(config, status, modpack);
    renderStatus(status, config);
    renderOnline(status.server || {});
    renderBudget({
      balance_ar: treasury.balance,
      current_president_name: treasury.ownerName || status.elections?.president || "",
      current_president_uuid: treasury.ownerUuid || "",
      updated_at: status.generatedAt || Date.now(),
    });
    renderHistory((treasury.history || []).map((row) => ({
      ...row,
      public_actor_name: row.actor || "",
      created_at: row.createdAt || row.created_at || 0,
    })));
  }

  function renderAuthState(auth = {}) {
    currentAuth = auth || { role: "", cookieAuth: false };
    const role = String(auth.role || "");
    const authed = Boolean(role || auth.cookieAuth);
    if (cabinetButton) {
      cabinetButton.classList.toggle("hidden", !authed);
      cabinetButton.dataset.routeTarget = authed ? roleRoute(role) : "";
      cabinetButton.textContent = roleLabel(role);
    }
    if (openArShopBtn) {
      openArShopBtn.textContent = authed ? "Открыть AR-лавку" : "AR-лавка";
    }
    if (openDonationShopBtn) {
      openDonationShopBtn.textContent = authed ? "Открыть донат-лавку" : "Донат-лавка";
    }
  }

  function renderUnavailableState() {
    if (budgetDetail) {
      budgetDetail.textContent = "Данные по казне недоступны.";
    }
    if (serverPulseText) {
      serverPulseText.textContent = "Свежих данных нет.";
    }
    if (modpackSummaryLead) {
      modpackSummaryLead.textContent = "Данные по архиву модов недоступны.";
    }
  }

  function setSkinRotation(nextRotation) {
    if (!skinTilt) return;
    skinTilt.style.transform = `rotateX(4deg) rotateY(${nextRotation}deg)`;
  }

  function bindSkinControls() {
    if (leftButton && leftButton.dataset.bound !== "true") {
      leftButton.dataset.bound = "true";
      leftButton.addEventListener("click", () => setSkinRotation(-34));
    }
    if (rightButton && rightButton.dataset.bound !== "true") {
      rightButton.dataset.bound = "true";
      rightButton.addEventListener("click", () => setSkinRotation(2));
    }
    if (skinImage && skinImage.dataset.bound !== "true") {
      skinImage.dataset.bound = "true";
      skinImage.addEventListener("error", () => {
        skinShell?.classList.add("hidden");
        skinImage.removeAttribute("src");
      });
    }
  }

  bindSkinControls();
  bindCabinetButton();
  bindCommerceButtons();
  setSkinRotation(-16);

  return {
    renderBudget,
    renderCommerce,
    renderHistory,
    renderStatus,
    renderOnline,
    renderStatusPayload,
    renderServerHero,
    renderPresidentCard,
    renderAuthState,
    renderUnavailableState,
    renderModpack,
  };
}
