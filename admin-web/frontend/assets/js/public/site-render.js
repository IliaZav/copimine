import { makeElement, replaceChildrenSafe } from "../shared/dom.js";
import { appRouteHref, authLandingHref, defaultAppRouteForRole } from "../shared/app-routes.js";
import { addShopCartItem, getShopCartCount, hasShopCartItem, setShopCartScope } from "./shop-cart.js";

const MC_ICON_ROOT = "/assets/mc-icons/item";
const CSRF_COOKIE = "cm_csrf";
const CSRF_HEADER = "X-CSRF-Token";
const LIVE_DONATION_INSTANCE_STATUSES = new Set(["ACTIVE", "DELIVERING", "PENDING_DELIVERY"]);

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

const VANILLA_ITEM_ICON_ALIASES = Object.freeze({
  compass: "compass_00.png",
  clock: "clock_00.png",
  recovery_compass: "recovery_compass_00.png",
  shield: "empty_armor_slot_shield.png",
});

function materialIconName(material) {
  const raw = String(material || "").trim().toLowerCase();
  return raw ? (VANILLA_ITEM_ICON_ALIASES[raw] || `${raw}.png`) : "";
}

function resolveCustomShopIcon(row = {}) {
  const explicit = String(row.image_url || row.imageUrl || "").trim();
  if (/^\/assets\/item-textures\/[a-z0-9_-]+\.png$/i.test(explicit)) return explicit;
  const itemId = String(row.item_id || row.itemId || "").trim().toLowerCase();
  return /^[a-z0-9_-]+$/.test(itemId) ? `/assets/item-textures/${itemId}.png` : "";
}

function resolveVanillaShopIcon(row = {}, mode = "ar") {
  const material = materialIconName(row.base_material);
  if (material) return mcIcon(material);
  const itemId = String(row.item_id || "").toLowerCase();
  if (itemId.includes("pickaxe")) return mcIcon("diamond_pickaxe.png");
  if (itemId.includes("sword")) return mcIcon("diamond_sword.png");
  if (itemId.includes("book")) return mcIcon("written_book.png");
  return mode === "ar" ? mcIcon("diamond_ore.png") : mcIcon("totem_of_undying.png");
}

function resolveShopIcon(row = {}, mode = "ar") {
  return resolveCustomShopIcon(row) || resolveVanillaShopIcon(row, mode);
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

function customerFacingItemDescription(row = {}) {
  const raw = String(row.description || "").trim();
  if (!raw) return "Игровой предмет с выдачей после оплаты.";

  // Catalog imports may include a Minecraft material/model id in the copy.
  // It is useful to the plugin, but not to a player choosing a purchase.
  const cleaned = raw
    .replace(/\b(?:minecraft:)?[a-z0-9]+(?:_[a-z0-9]+){2,}\b/gi, "")
    .replace(/\s{2,}/g, " ")
    .replace(/^[\s:|,.;-]+|[\s:|,.;-]+$/g, "")
    .trim();
  return cleaned || "Игровой предмет с выдачей после оплаты.";
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
      return "Личный кабинет";
    case "junior_admin":
    case "admin":
    case "owner":
      return "Панель управления";
    default:
      return "Личный кабинет";
  }
}

function cmsEntry(payload = {}, key = "") {
  const items = Array.isArray(payload.items) ? payload.items : [];
  return items.find((item) => String(item.key || item.entry_key || "") === key && item.enabled !== false) || null;
}

function setCmsText(selector, text, mode = "text") {
  const node = document.querySelector(selector);
  const value = String(text || "").trim();
  if (!node || !value) return;
  if (mode === "href") {
    node.setAttribute("href", value);
    return;
  }
  node.textContent = value;
}

function readCookie(name) {
  const prefix = `${String(name || "")}=`;
  return String(document.cookie || "")
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

async function requestJson(url, init = {}) {
  const headers = new Headers(init.headers || {});
  headers.set("Accept", "application/json");
  const method = String(init.method || "GET").toUpperCase();
  if (init.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (method !== "GET" && method !== "HEAD" && !headers.has(CSRF_HEADER)) {
    const csrf = readCookie(CSRF_COOKIE);
    if (csrf) headers.set(CSRF_HEADER, csrf);
  }
  const response = await fetch(url, {
    credentials: "include",
    ...init,
    method,
    headers,
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  try {
    return await response.json();
  } catch (_error) {
    return {};
  }
}

async function ensureCsrfCookie() {
  await requestJson("/api/auth/csrf");
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
    makeElement("span", "modpack-file-badge", "external"),
    makeElement("strong", "", String(row.component || "Внешний мод")),
    makeElement("p", "", String(row.feature || row.reason || "Загружается отдельно с официальной страницы.")),
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
  if (Number(row.cooldown_seconds || 0) > 0) {
    meta.append(makeElement("span", "", `${Number(row.cooldown_seconds)} сек. кулдаун`));
  }
  card.append(meta);
  return card;
}

function shopCategoryLabel(value, mode) {
  const labels = {
    WEAPON: "Оружие",
    TOOL: "Инструмент",
    ARMOR: "Броня",
    UTILITY: "Полезное",
    RP: "Особое",
  };
  return labels[String(value || "").trim().toUpperCase()] || (mode === "ar" ? "AR-предмет" : "Donation-предмет");
}

function buildShopProductItem(row, mode = "ar", purchaseReady = false, needsLink = false, availability = {}) {
  const currency = mode === "ar" ? "ar" : "donation";
  const itemId = String(row.item_id || "").trim().toLowerCase();
  const card = makeElement("article", `shop-product-card shop-product-card-${currency}`);
  const visual = makeElement("div", "shop-product-art");
  const image = document.createElement("img");
  const customIcon = resolveCustomShopIcon(row);
  image.src = customIcon || resolveVanillaShopIcon(row, mode);
  image.alt = "";
  image.loading = "lazy";
  image.decoding = "async";
  image.addEventListener("error", () => {
    image.src = resolveVanillaShopIcon(row, mode);
  }, { once: true });
  visual.append(image);

  const body = makeElement("div", "shop-product-body");
  body.append(
    makeElement("span", "shop-product-category", shopCategoryLabel(row.category, mode)),
    makeElement("h3", "", String(row.display_name || itemId || "Товар")),
    makeElement("p", "", customerFacingItemDescription(row)),
  );

  const footer = makeElement("div", "shop-product-footer");
  const price = makeElement(
    "strong",
    "shop-product-price",
    mode === "ar" ? formatAr(row.price_ar || 0) : formatDonate(row.price_donation || 0),
  );
  const unavailable = Boolean(availability?.unavailable);
  const addButton = makeElement("button", "btn btn-primary shop-product-add", unavailable ? String(availability.label || "Недоступен") : needsLink ? "Привязать ник" : purchaseReady ? "В корзину" : "Войти для покупки");
  addButton.type = "button";
  addButton.disabled = unavailable;
  if (purchaseReady && !unavailable) {
    addButton.dataset.shopCartItem = itemId;
    addButton.dataset.shopCartCurrency = currency;
  }
  addButton.addEventListener("click", () => {
    if (!purchaseReady) {
      window.location.href = needsLink ? appRouteHref("link") : authLandingHref("signin");
      return;
    }
    if (!itemId) return;
    addShopCartItem(itemId, currency);
    syncShopCartItemButtons();
  });
  footer.append(price, addButton);
  card.append(visual, body, footer);
  return card;
}

function shopItemAvailability(row, currency, ownership = {}) {
  const itemId = String(row?.item_id || "").trim().toLowerCase();
  if (!itemId) return { unavailable: true, label: "Недоступен" };
  if (row?.enabled === false) return { unavailable: true, label: "Снято с продажи" };
  if (currency === "ar") {
    if (Number(row?.per_player_limit || 0) <= 0) return {};
    const purchases = Array.isArray(ownership?.artifacts?.purchases) ? ownership.artifacts.purchases : [];
    const pending = Array.isArray(ownership?.artifacts?.pending) ? ownership.artifacts.pending : [];
    const matchingPurchases = purchases.filter((entry) => String(entry?.item_id || "").trim().toLowerCase() === itemId);
    const livePurchase = matchingPurchases.filter((entry) => ["PAID", "DELIVERING", "DELIVERED", "PENDING_DELIVERY"].includes(String(entry?.status || "").toUpperCase()));
    const pendingDelivery = pending.some((entry) => String(entry?.item_id || "").trim().toLowerCase() === itemId && ["PENDING", "DELIVERING", "PENDING_DELIVERY"].includes(String(entry?.status || "").toUpperCase()));
    if (!livePurchase.length && !pendingDelivery) return {};
    return { unavailable: true, label: pendingDelivery ? "В выдаче" : "Уже получен" };
  }
  const claims = Array.isArray(ownership?.owned?.claims) ? ownership.owned.claims : [];
  const instances = Array.isArray(ownership?.owned?.instances) ? ownership.owned.instances : [];
  const claim = claims.some((entry) => String(entry?.item_id || "").trim().toLowerCase() === itemId && ["UNCLAIMED", "RESERVED", "DELIVERING", "DELIVERY_REVIEW"].includes(String(entry?.status || "").toUpperCase()));
  if (claim) return { unavailable: true, label: "В выдаче" };
  const pendingInstance = instances.some((entry) => {
    const status = String(entry?.status || "").toUpperCase();
    return String(entry?.item_id || "").trim().toLowerCase() === itemId && status !== "ACTIVE" && LIVE_DONATION_INSTANCE_STATUSES.has(status);
  });
  if (pendingInstance) return { unavailable: true, label: "В выдаче" };
  const activeInstance = instances.some((entry) => String(entry?.item_id || "").trim().toLowerCase() === itemId && String(entry?.status || "").toUpperCase() === "ACTIVE");
  return activeInstance ? { unavailable: true, label: "Уже получен" } : {};
}

function syncShopCartItemButtons() {
  document.querySelectorAll("[data-shop-cart-item][data-shop-cart-currency]").forEach((button) => {
    if (!(button instanceof HTMLButtonElement)) return;
    const itemId = String(button.dataset.shopCartItem || "");
    const currency = String(button.dataset.shopCartCurrency || "");
    const inCart = hasShopCartItem(itemId, currency);
    button.disabled = inCart;
    button.textContent = inCart ? "В корзине" : "В корзину";
    button.setAttribute("aria-pressed", inCart ? "true" : "false");
  });
}

function syncShopCartScope(auth = {}) {
  const accountId = String(auth?.role === "player" ? auth?.accountId || "" : "").trim().toLowerCase();
  setShopCartScope(accountId ? `player-${accountId}` : "guest");
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
  const publicSigninLink = document.getElementById("publicSigninLink");
  const publicRegisterLink = document.getElementById("publicRegisterLink");
  const cabinetButton = document.getElementById("publicCabinetBtn");
  const logoutButton = document.getElementById("publicLogoutBtn");
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
  const electionStage = document.getElementById("publicElectionStage");
  const electionMeta = document.getElementById("publicElectionMeta");
  const electionStats = document.getElementById("publicElectionStats");
  const electionCandidates = document.getElementById("publicElectionCandidates");
  const electionLaws = document.getElementById("publicElectionLaws");
  const electionUpdated = document.getElementById("publicElectionUpdated");

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

  async function logoutFromPublic() {
    if (!window.confirm("Выйти из кабинета CopiMine?")) return;
    try {
      await ensureCsrfCookie();
      await requestJson("/api/auth/logout", { method: "POST", body: "{}" });
    } catch (_error) {
      // Cookie cleanup still happens server-side on the next valid call.
    }
    currentAuth = { role: "", cookieAuth: false };
    renderAuthState(currentAuth);
    window.location.href = publicPageRoute("index.html");
  }

  function bindCabinetButton() {
    if (cabinetButton && cabinetButton.dataset.bound !== "true") {
      cabinetButton.dataset.bound = "true";
      cabinetButton.addEventListener("click", () => {
        const routeTarget = cabinetButton.dataset.routeTarget || "";
        if (!routeTarget) return;
        window.location.href = routeTarget;
      });
    }
    if (logoutButton && logoutButton.dataset.bound !== "true") {
      logoutButton.dataset.bound = "true";
      logoutButton.addEventListener("click", () => {
        void logoutFromPublic();
      });
    }
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

  function syncShopCartButton() {
    const count = getShopCartCount();
    document.querySelectorAll(".shop-cart-count").forEach((node) => { node.textContent = String(count); });
    document.querySelectorAll(".shop-cart-button").forEach((node) => {
      node.classList.toggle("has-items", count > 0);
      node.setAttribute("aria-label", count ? `Корзина: ${count} предмета` : "Корзина пуста");
    });
    syncShopCartItemButtons();
  }

  function renderCommerce(arCatalog = {}, donationCatalog = {}, auth = {}, ownership = {}) {
    syncShopCartScope(auth);
    const purchaseReady = Boolean(auth?.cookieAuth && auth?.role === "player" && auth?.linked);
    const needsLink = Boolean(auth?.cookieAuth && auth?.role === "player" && !auth?.linked);
    if (arShopMount) {
      const cards = Array.isArray(arCatalog.items) && arCatalog.items.length
        ? arCatalog.items.map((row) => buildShopProductItem(row, "ar", purchaseReady, needsLink, shopItemAvailability(row, "ar", ownership)))
        : [cardStrong("AR-магазин недоступен", "Каталог временно не загружен.", "", mcIcon("diamond_ore.png"))];
      replaceChildrenSafe(arShopMount, cards);
    }
    if (donationShopMount) {
      const cards = Array.isArray(donationCatalog.items) && donationCatalog.items.length
        ? donationCatalog.items.map((row) => buildShopProductItem(row, "donation", purchaseReady, needsLink, shopItemAvailability(row, "donation", ownership)))
        : [cardStrong("Донат-магазин недоступен", "Каталог временно не загружен.", "", mcIcon("totem_of_undying.png"))];
      replaceChildrenSafe(donationShopMount, cards);
    }
    syncShopCartButton();
  }

  function renderCms(payload = {}, pageKind = "") {
    const home = cmsEntry(payload, "home_status");
    if (home && pageKind === "public-home") {
      setCmsText(".public-hero-copy h1", home.title);
      setCmsText(".public-hero-copy p", home.body);
      if (home.imagePath || home.image_path) {
        const image = document.querySelector(".hero-media-art img");
        if (image) image.src = String(home.imagePath || home.image_path);
      }
      if (home.linkUrl || home.link_url) {
        setCmsText("#downloadModsBtn", home.linkUrl || home.link_url, "href");
      }
    }
    const shops = cmsEntry(payload, "shops_note");
    if (shops && pageKind === "public-shops") {
      setCmsText("#shopCmsNoteTitle", shops.title);
      setCmsText("#shopCmsNoteBody", shops.body);
    } else if (shops && pageKind === "public-home") {
      setCmsText(".public-section .section-head h2", shops.title);
      setCmsText(".public-section .section-head p", shops.body);
    }
    const rules = cmsEntry(payload, "rules_short");
    if (rules && pageKind === "public-server") {
      setCmsText(".public-panel-head h3", rules.title);
    }
  }

  function renderModpack(modpack = {}, config = {}) {
    const manifest = modpack && typeof modpack === "object" ? (modpack.manifest || {}) : {};
    const files = Array.isArray(manifest.files) ? manifest.files : [];
    const requiredExternal = Array.isArray(manifest.requiredExternal) ? manifest.requiredExternal : [];
    const notes = Array.isArray(manifest.notes) ? manifest.notes : [];
    const available = Boolean(modpack.available);
    const downloadUrl = modpack.downloadUrl || config.modpackDownloadPath || "/downloads/CopiMineMods.zip";

    if (heroMiniTitle) {
      heroMiniTitle.textContent = available
        ? (modpack.filename || "CopiMineMods.zip")
        : "Клиент и модпак";
    }
    if (heroMiniText) {
      if (available) {
        const versionText = `${manifest.loader || "Fabric"} ${manifest.minecraftVersion || config.serverVersion || ""}`.trim();
        const fileText = `${files.length || 0} файлов`;
        const externalText = requiredExternal.length ? `, отдельно ещё ${requiredExternal.length}` : "";
        heroMiniText.textContent = `${versionText} · ${fileText}${externalText}`;
      } else {
        heroMiniText.textContent = "Архив модов сейчас недоступен.";
      }
    }
    if (modpackSummaryLead) {
      modpackSummaryLead.textContent = available
        ? `Размер архива ${formatMegabytes(modpack.size || 0)} · SHA1 ${shortSha(modpack.sha1 || manifest.sha1 || "")}`
        : "Проверьте состояние downloads и сборки клиента.";
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
          cardStrong("Архив недоступен", "Список файлов пока не загружен.", "", mcIcon("bundle.png")),
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
          cardStrong("Дополнительные загрузки не нужны", "Всё обязательное уже включено в архив.", "", mcIcon("book.png")),
        ]);
      } else {
        replaceChildrenSafe(modpackExternalGrid, requiredExternal.map((row) => buildExternalModCard(row)));
      }
    }
    if (false && modpackNotes) {
      const noteCards = (notes.length ? notes : ["Проверьте версию Minecraft, Fabric и состав архива перед запуском."]).map((note) => {
        const card = makeElement("article", "modpack-note-card");
        card.append(
          makeElement("strong", "", ""),
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
      ? `Онлайн ${formatPlayers(server)} · отклик ${formatLatency(server.latencyMs)}`
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
        downloadModsBtn.textContent = `Скачать модпак (${formatMegabytes(modpack.size || 0)})`;
        downloadModsBtn.classList.remove("btn-disabled");
        downloadModsBtn.removeAttribute("aria-disabled");
      } else if (document.body?.dataset.pageKind === "public-home") {
        downloadModsBtn.href = publicPageRoute("mods.html");
        downloadModsBtn.textContent = "Открыть раздел модпака";
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
      : "Голосование сейчас не запущено";
    replaceChildrenSafe(statusGrid, [
      cardStrong("Сервер", server.online ? "Онлайн" : "Нет ответа", formatLatency(server.latencyMs), mcIcon("beacon.png")),
      cardStrong("Игроки", formatPlayers(server), server.playerListAvailable ? "Список игроков открыт" : "Список игроков скрыт", mcIcon("totem_of_undying.png")),
      cardStrong("Выборы", elections.active ? "Идут" : "Пауза", electionDetail, mcIcon("written_book.png")),
      cardStrong("Версия", config.serverVersion || "1.21.1", config.resourcePackRequired ? "Ресурспак обязателен" : "Ресурспак опционален", mcIcon("compass_00.png")),
    ]);
  }

  function renderOnline(server = {}) {
    if (!onlineBoard) return;
    const players = Array.isArray(server.samplePlayers) ? server.samplePlayers.filter(Boolean) : [];
    if (!players.length) {
      replaceChildrenSafe(onlineBoard, [
        cardStrong("Игроки онлайн", "Никого нет или список скрыт.", "", mcIcon("filled_map.png")),
      ]);
      return;
    }
    replaceChildrenSafe(
      onlineBoard,
      players.slice(0, 12).map((player) => cardStrong("Игрок", String(player))),
    );
  }

  function renderHistory(items = []) {
    if (!historyMount) return;
    const rows = Array.isArray(items) ? items : [];
    if (!rows.length) {
      replaceChildrenSafe(historyMount, [
        cardStrong("Открытых операций нет", "Публичных движений казны пока нет.", "", mcIcon("book.png")),
      ]);
      return;
    }
    replaceChildrenSafe(
      historyMount,
      rows.slice(0, 6).map((row) => {
        const amount = Number(row.amount ?? row.amount_ar ?? 0);
        const positive = Number.isFinite(amount) && amount > 0;
        const negative = Number.isFinite(amount) && amount < 0;
        const recipient = String(row.recipientName || row.recipient_name || row.public_actor_name || "").trim();
        const card = makeElement("article", `treasury-history-card ${positive ? "is-inflow" : negative ? "is-outflow" : "is-neutral"}`);
        const head = makeElement("div", "treasury-history-row");
        head.append(
          makeElement("span", "treasury-history-type", String(row.label || row.type || "Операция")),
          makeElement("strong", "treasury-history-amount", `${positive ? "+" : negative ? "−" : ""}${formatAr(Math.abs(Number.isFinite(amount) ? amount : 0))}`),
        );
        const meta = makeElement("div", "treasury-history-meta");
        if (recipient) meta.append(makeElement("span", "treasury-history-actor", recipient));
        meta.append(makeElement("span", "treasury-history-date", formatDate(row.createdAt || row.created_at)));
        card.append(head);
        if (recipient) card.append(makeElement("p", "", `Игрок: ${recipient}`));
        card.append(meta);
        return card;
      }),
    );
  }

  function electionStageLabel(stage) {
    const labels = {
      DRAFT: "Подготовка",
      APPLICATIONS_OPEN: "Приём заявок",
      APPLICATIONS_REVIEW: "Проверка заявок",
      VOTING_OPEN: "Голосование",
      COUNTING: "Подсчёт голосов",
      COMPLETED: "Завершены",
      PAUSED: "Пауза",
      ACTIVE: "Идут",
    };
    const normalized = String(stage || "").trim().toUpperCase();
    return labels[normalized] || (normalized ? normalized.replaceAll("_", " ") : "Этап не задан");
  }

  function renderElections(payload = {}) {
    if (!electionStage && !electionCandidates) return;
    const election = payload && typeof payload.election === "object" ? payload.election : {};
    const summary = payload && typeof payload.summary === "object" ? payload.summary : {};
    const candidates = Array.isArray(payload.candidates)
      ? payload.candidates.filter((row) => row && row.approved !== false && String(row.name || "").trim())
      : [];
    const laws = Array.isArray(payload.laws) ? payload.laws : [];
    const totalVotes = Math.max(0, Number(summary.totalVotes || candidates.reduce((sum, row) => sum + Math.max(0, Number(row.votes || 0)), 0)) || 0);
    const maxVotes = Math.max(1, ...candidates.map((row) => Math.max(0, Number(row.votes || 0) || 0)));
    const stage = electionStageLabel(election.stage || election.status);
    const round = Math.max(1, Number(election.round || 1) || 1);

    if (electionStage) electionStage.textContent = stage;
    if (electionMeta) electionMeta.textContent = `Тур ${round} · ${candidates.length} одобренных кандидатов · только просмотр`;
    if (electionUpdated) electionUpdated.textContent = payload.generatedAt ? `Обновлено ${formatDate(payload.generatedAt)}` : "Данные обновляются автоматически";

    if (electionStats) {
      const stat = (label, value, note) => {
        const card = makeElement("article", "election-stat-card");
        card.append(makeElement("span", "election-stat-label", label), makeElement("strong", "", value), makeElement("small", "", note));
        return card;
      };
      replaceChildrenSafe(electionStats, [
        stat("Этап", stage, "текущий статус процесса"),
        stat("Кандидаты", String(candidates.length), "заявки одобрены"),
        stat("Учтено голосов", totalVotes.toLocaleString("ru-RU"), "агрегированный результат"),
        stat("Участки", String(Number(summary.activePollingStations || summary.pollingStations || 0)), "активные участки"),
      ]);
    }

    if (electionCandidates) {
      if (!candidates.length) {
        replaceChildrenSafe(electionCandidates, [cardStrong("Одобренных кандидатов пока нет", "Когда ЦИК одобрит заявки, они появятся здесь.", "", mcIcon("written_book.png"))]);
      } else {
        replaceChildrenSafe(electionCandidates, candidates.map((row, index) => {
          const name = String(row.name || "Кандидат").trim();
          const votes = Math.max(0, Number(row.votes || 0) || 0);
          const percent = totalVotes > 0 ? Math.round((votes / totalVotes) * 100) : 0;
          const width = Math.max(0, Math.min(100, Math.round((votes / maxVotes) * 100)));
          const card = makeElement("article", "public-candidate-card");
          const identity = makeElement("div", "public-candidate-identity");
          const avatar = makeElement("img", "public-candidate-avatar");
          const uuid = String(row.uuid || "").trim().toLowerCase();
          avatar.src = /^[0-9a-f-]{32,36}$/.test(uuid)
            ? `https://mc-heads.net/avatar/${encodeURIComponent(uuid)}/64`
            : "/assets/brand/copimine-logo.png";
          avatar.alt = `Голова игрока ${name}`;
          avatar.loading = "lazy";
          avatar.addEventListener("error", () => { avatar.src = "/assets/brand/copimine-logo.png"; }, { once: true });
          const copy = makeElement("div", "public-candidate-copy");
          copy.append(makeElement("span", "candidate-rank", `№${index + 1}`), makeElement("strong", "", name), makeElement("small", "", "Одобренный кандидат"));
          identity.append(avatar, copy);

          const barRow = makeElement("div", "candidate-vote-row");
          const track = makeElement("div", "candidate-vote-track");
          const fill = makeElement("span", "candidate-vote-fill");
          fill.style.width = `${width}%`;
          track.append(fill);
          barRow.append(track, makeElement("strong", "candidate-vote-count", votes.toLocaleString("ru-RU")));
          const foot = makeElement("div", "candidate-vote-foot");
          foot.append(makeElement("span", "", `${percent}% от учтённых голосов`), makeElement("span", "candidate-readonly", "Только просмотр"));
          const voteBlock = makeElement("div", "candidate-vote-block");
          voteBlock.append(barRow, foot);
          card.append(identity, voteBlock);
          return card;
        }));
      }
    }

    if (electionLaws) {
      if (!laws.length) {
        replaceChildrenSafe(electionLaws, [makeElement("p", "election-empty-note", "Опубликованных законов для этого срока пока нет.")]);
      } else {
        replaceChildrenSafe(electionLaws, laws.slice(0, 12).map((row) => {
          const card = makeElement("article", "election-law-card");
          card.append(makeElement("strong", "", String(row.title || "Опубликованный закон")), makeElement("p", "", String(row.body || "Текст закона опубликован в игре.")));
          if (row.publishedAt) card.append(makeElement("small", "", formatDate(row.publishedAt)));
          return card;
        }));
      }
    }
  }

  function renderPresidentCard(president = {}) {
    if (!presidentName || !presidentMeta) return;
    const name = String(president.current_president_name || president.ownerName || "").trim();
    const uuid = String(president.current_president_uuid || president.ownerUuid || "").trim();
    if (!name) {
      presidentName.textContent = "Президент пока не выбран";
      presidentMeta.textContent = "Должность свободна.";
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
        ? `Казна закреплена за ${currentPresidentName}`
        : "Активный президент не назначен";
    }
    if (budgetDetail) {
      budgetDetail.textContent = payload.updated_at || payload.updatedAt
        ? `Обновлено ${formatDate(payload.updated_at || payload.updatedAt)}`
        : "Публичный баланс казны.";
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
      public_actor_name: row.recipientName || row.recipient_name || "",
      created_at: row.createdAt || row.created_at || 0,
    })));
  }

  function renderAuthState(auth = {}) {
    syncShopCartScope(auth);
    currentAuth = auth || { role: "", cookieAuth: false };
    const role = String(auth.role || "");
    const authed = Boolean(role || auth.cookieAuth);

    if (publicSigninLink) publicSigninLink.classList.toggle("hidden", authed);
    if (publicRegisterLink) publicRegisterLink.classList.toggle("hidden", authed);
    if (cabinetButton) {
      cabinetButton.classList.toggle("hidden", !authed);
      cabinetButton.dataset.routeTarget = authed ? roleRoute(role) : "";
      cabinetButton.textContent = roleLabel(role);
    }
    if (logoutButton) {
      logoutButton.classList.toggle("hidden", !authed);
    }
    if (openArShopBtn) {
      openArShopBtn.textContent = authed ? "Открыть AR-магазин" : "Перейти к входу";
    }
    if (openDonationShopBtn) {
      openDonationShopBtn.textContent = authed ? "Открыть донат-магазин" : "Перейти к входу";
    }
  }

  function renderUnavailableState() {
    if (budgetDetail) {
      budgetDetail.textContent = "Публичные данные казны недоступны.";
    }
    if (serverPulseText) {
      serverPulseText.textContent = "Свежих данных по серверу нет.";
    }
    if (modpackSummaryLead) {
      modpackSummaryLead.textContent = "Состояние архива модов пока не удалось получить.";
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
  window.addEventListener("shopCartChanged", syncShopCartButton);
  syncShopCartButton();
  setSkinRotation(-16);

  return {
    renderBudget,
    renderCommerce,
    renderElections,
    renderHistory,
    renderStatus,
    renderOnline,
    renderStatusPayload,
    renderServerHero,
    renderPresidentCard,
    renderAuthState,
    renderUnavailableState,
    renderModpack,
    renderCms,
  };
}
