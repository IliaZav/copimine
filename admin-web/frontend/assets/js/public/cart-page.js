import { makeElement, replaceChildrenSafe } from "../shared/dom.js";
import { appRouteHref, defaultAppRouteForRole } from "../shared/app-routes.js";
import { loadPublicAuthState, loadPublicShopsPageData } from "./site-data.js";
import { createCheckoutBlockGuard, shouldBlockCheckoutAfterRefresh } from "./cart-checkout-guard.js";
import {
  clearShopCartItems,
  getShopCartScope,
  getShopCartCount,
  readShopCart,
  removeShopCartItem,
  setShopCartScope,
} from "./shop-cart.js";

const CSRF_COOKIE = "cm_csrf";
const CSRF_HEADER = "X-CSRF-Token";
const LIVE_DONATION_INSTANCE_STATUSES = new Set(["ACTIVE", "DELIVERING", "PENDING_DELIVERY"]);
const CURRENCY_META = {
  ar: {
    catalogKey: "arCatalog",
    itemsId: "arCartItems",
    totalId: "arCartTotal",
    statusId: "arCartStatus",
    formId: "arCartCheckoutForm",
    inputId: "arCartPin",
    buttonId: "arCartCheckoutButton",
    label: "AR",
    route: "/api/player/shop/cart/ar/checkout",
    priceField: "price_ar",
  },
  donation: {
    catalogKey: "donationCatalog",
    itemsId: "donationCartItems",
    totalId: "donationCartTotal",
    statusId: "donationCartStatus",
    formId: "donationCartCheckoutForm",
    inputId: "donationCartPin",
    buttonId: "donationCartCheckoutButton",
    label: "DC",
    route: "/api/player/shop/cart/donation/checkout",
    priceField: "price_donation",
  },
};

let authState = { role: "", cookieAuth: false };
let catalogState = { arCatalog: { items: [] }, donationCatalog: { items: [] } };
const checkoutBlockGuard = createCheckoutBlockGuard();

function formatAmount(value, label) {
  const amount = Number(value || 0);
  return `${(Number.isFinite(amount) ? amount : 0).toLocaleString("ru-RU")} ${label}`;
}

function readCookie(name) {
  const prefix = `${String(name || "")}=`;
  return String(document.cookie || "")
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

function cartCheckoutKey(currency, itemIds) {
  const fingerprint = `${getShopCartScope()}:${currency}:${[...itemIds].sort().join(",")}`;
  const storageKey = `copimine.shop.checkout.${fingerprint}`;
  const current = window.sessionStorage.getItem(storageKey);
  if (current && /^[A-Za-z0-9-]{8,96}$/.test(current)) return current;
  const suffix = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
  const key = `cart-${currency}-${suffix}`.replace(/[^A-Za-z0-9-]/g, "-").slice(0, 96);
  window.sessionStorage.setItem(storageKey, key);
  return key;
}

function clearCartCheckoutKey(currency, itemIds) {
  const fingerprint = `${getShopCartScope()}:${currency}:${[...itemIds].sort().join(",")}`;
  window.sessionStorage.removeItem(`copimine.shop.checkout.${fingerprint}`);
}

function setStatus(currency, message = "", tone = "") {
  const node = document.getElementById(CURRENCY_META[currency].statusId);
  if (!node) return;
  node.textContent = message;
  node.dataset.tone = tone;
}

function checkoutCartSignature(currency, cart = readShopCart()) {
  const rows = Array.isArray(cart?.[currency]) ? cart[currency] : [];
  return rows.map((row) => String(row?.itemId || "")).sort().join(",");
}

function blockCheckoutCurrency(currency, submittedSignature) {
  return checkoutBlockGuard.blockIfSignatureMatches(
    currency,
    submittedSignature,
    checkoutCartSignature(currency),
  );
}

function clearChangedCheckoutBlocks(cart) {
  for (const currency of Object.keys(CURRENCY_META)) {
    if (checkoutBlockGuard.clearIfSignatureChanged(currency, checkoutCartSignature(currency, cart))) {
      setStatus(currency);
    }
  }
}

function setGlobalNotice(message = "", tone = "") {
  const node = document.getElementById("cartGlobalNotice");
  if (!node) return;
  node.hidden = !message;
  node.textContent = message;
  node.dataset.tone = tone;
}

function syncCartAuthNav() {
  const role = String(authState?.role || "").trim().toLowerCase();
  const authenticated = Boolean(authState?.cookieAuth && authState?.role);
  const signin = document.getElementById("publicSigninLink");
  const register = document.getElementById("publicRegisterLink");
  const cabinet = document.getElementById("publicCabinetBtn");
  const logout = document.getElementById("publicLogoutBtn");
  signin?.classList.toggle("hidden", authenticated);
  register?.classList.toggle("hidden", authenticated);
  cabinet?.classList.toggle("hidden", !authenticated);
  logout?.classList.toggle("hidden", !authenticated);
  if (cabinet instanceof HTMLAnchorElement) {
    cabinet.href = appRouteHref(defaultAppRouteForRole(role));
    cabinet.textContent = role === "player" ? "Личный кабинет" : "Панель управления";
  }
}

function bindCartAuthNav() {
  const logout = document.getElementById("publicLogoutBtn");
  if (!(logout instanceof HTMLButtonElement) || logout.dataset.bound === "true") return;
  logout.dataset.bound = "true";
  logout.addEventListener("click", async () => {
    try {
      await ensureCsrfCookie();
      const csrf = readCookie(CSRF_COOKIE);
      await fetch("/api/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(csrf ? { [CSRF_HEADER]: csrf } : {}) },
        body: "{}",
      });
    } finally {
      authState = { role: "", cookieAuth: false };
      checkoutBlockGuard.clear();
      setShopCartScope("guest");
      syncCartAuthNav();
      renderCart();
    }
  });
}

function syncHeaderCount() {
  const count = getShopCartCount();
  document.querySelectorAll(".shop-cart-count").forEach((node) => { node.textContent = String(count); });
  document.querySelectorAll(".shop-cart-button").forEach((node) => {
    node.classList.toggle("has-items", count > 0);
    node.setAttribute("aria-label", count ? `Корзина: ${count} предмета` : "Корзина пуста");
  });
}

function catalogRows(currency) {
  const meta = CURRENCY_META[currency];
  const rows = catalogState?.[meta.catalogKey]?.items;
  return Array.isArray(rows) ? rows : [];
}

function itemForCartRow(currency, row) {
  const itemId = String(row?.itemId || "");
  return catalogRows(currency).find((item) => String(item?.item_id || "") === itemId) || null;
}

function cartItemAvailability(currency, item) {
  const itemId = String(item?.item_id || "").trim().toLowerCase();
  const ownership = catalogState?.ownership || {};
  if (!itemId) return { unavailable: true, label: "Недоступен" };
  if (item?.enabled === false) return { unavailable: true, label: "Снято с продажи" };
  if (currency === "ar") {
    if (Number(item?.per_player_limit || 0) <= 0) return {};
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
  if (claims.some((entry) => String(entry?.item_id || "").trim().toLowerCase() === itemId && ["UNCLAIMED", "RESERVED", "DELIVERING", "DELIVERY_REVIEW"].includes(String(entry?.status || "").toUpperCase()))) return { unavailable: true, label: "В выдаче" };
  const pendingInstance = instances.some((entry) => {
    const status = String(entry?.status || "").toUpperCase();
    return String(entry?.item_id || "").trim().toLowerCase() === itemId && status !== "ACTIVE" && LIVE_DONATION_INSTANCE_STATUSES.has(status);
  });
  if (pendingInstance) return { unavailable: true, label: "В выдаче" };
  const activeInstance = instances.some((entry) => String(entry?.item_id || "").trim().toLowerCase() === itemId && String(entry?.status || "").toUpperCase() === "ACTIVE");
  return activeInstance ? { unavailable: true, label: "Уже получен" } : {};
}

function cartHasUnavailableRow(currency) {
  const meta = CURRENCY_META[currency];
  const rows = readShopCart()[currency] || [];
  return rows.some((cartRow) => {
    const item = itemForCartRow(currency, cartRow);
    return !item || cartItemAvailability(currency, item).unavailable || Number(item[meta.priceField] || 0) <= 0;
  });
}

function buildEmptyState(currency) {
  const meta = CURRENCY_META[currency];
  const state = makeElement("div", "cart-empty-state");
  state.append(
    makeElement("strong", "", "Здесь пока нет предметов"),
    makeElement("p", "", `Добавьте товары из ${currency === "ar" ? "AR-магазина" : "donation-магазина"}.`),
  );
  const link = makeElement("a", "btn btn-secondary", "Перейти к лавке");
  link.href = "/shops.html";
  state.append(link);
  return state;
}

function buildCartItem(currency, cartRow, catalogItem, availability = {}) {
  const itemId = String(cartRow?.itemId || "");
  const row = catalogItem || {};
  const item = makeElement("article", "cart-item");
  const art = makeElement("div", "cart-item-art");
  const image = document.createElement("img");
  const explicitTextureRaw = String(row.image_url || row.imageUrl || "").trim();
  const explicitTexture = /^\/assets\/item-textures\/[a-z0-9_-]+\.png$/i.test(explicitTextureRaw)
    ? explicitTextureRaw
    : "";
  const derivedTexture = /^[a-z0-9_-]+$/i.test(itemId)
    ? `/assets/item-textures/${itemId.toLowerCase()}.png`
    : "";
  image.src = explicitTexture || derivedTexture || "/assets/mc-icons/item/barrel_top.png";
  image.alt = "";
  image.loading = "lazy";
  image.addEventListener("error", () => {
    image.src = "/assets/mc-icons/item/barrel_top.png";
  }, { once: true });
  art.append(image);

  const copy = makeElement("div", "cart-item-copy");
  copy.append(makeElement("strong", "", String(row.display_name || "Предмет больше недоступен")));
  copy.append(makeElement("p", "", catalogItem ? (availability.unavailable ? `Этот предмет: ${availability.label.toLowerCase()}.` : String(row.description || "Предмет CopiMine.")) : "Уберите этот предмет: его больше нет в каталоге."));

  const aside = makeElement("div", "cart-item-aside");
  const price = Number(row[CURRENCY_META[currency].priceField] || 0);
  aside.append(makeElement("strong", "cart-item-price", catalogItem ? (availability.unavailable ? String(availability.label) : formatAmount(price, CURRENCY_META[currency].label)) : "Недоступен"));
  const remove = makeElement("button", "cart-item-remove", "×");
  remove.type = "button";
  remove.title = "Убрать из корзины";
  remove.setAttribute("aria-label", "Убрать из корзины");
  remove.addEventListener("click", () => removeShopCartItem(itemId, currency));
  aside.append(remove);
  item.append(art, copy, aside);
  return item;
}

function renderCurrencyCart(currency) {
  const meta = CURRENCY_META[currency];
  const cartRows = readShopCart()[currency] || [];
  const mount = document.getElementById(meta.itemsId);
  const totalNode = document.getElementById(meta.totalId);
  const checkoutButton = document.getElementById(meta.buttonId);
  if (!mount || !totalNode || !(checkoutButton instanceof HTMLButtonElement)) return;

  const mapped = cartRows.map((cartRow) => {
    const item = itemForCartRow(currency, cartRow);
    return { cartRow, item, availability: item ? cartItemAvailability(currency, item) : { unavailable: true, label: "Недоступен" } };
  });
  const valid = mapped.filter((entry) => entry.item && !entry.availability.unavailable && Number(entry.item[meta.priceField] || 0) > 0);
  const total = valid.reduce((sum, entry) => sum + Number(entry.item[meta.priceField] || 0), 0);
  totalNode.textContent = formatAmount(total, meta.label);
  replaceChildrenSafe(mount, mapped.length ? mapped.map((entry) => buildCartItem(currency, entry.cartRow, entry.item, entry.availability)) : [buildEmptyState(currency)]);
  checkoutButton.disabled = !valid.length || valid.length !== mapped.length || !canCheckout() || checkoutBlockGuard.isBlocked(currency);
  if (authState?.cookieAuth && authState?.role === "player" && !authState?.linked && mapped.length) {
    setStatus(currency, "Сначала привяжите Minecraft-ник в личном кабинете.", "warning");
  } else if (!canCheckout() && mapped.length) {
    setStatus(currency, "Для оплаты войдите в личный кабинет игрока.", "warning");
  } else if (mapped.some((entry) => entry.availability.unavailable)) {
    setStatus(currency, "В корзине есть недоступный предмет. Уберите его перед оплатой.", "warning");
  } else if (!mapped.length) {
    setStatus(currency, "");
  }
}

function canCheckout() {
  return Boolean(authState?.cookieAuth && authState?.role === "player" && authState?.linked);
}

function shouldRefreshCatalogAfterCheckoutError(message) {
  const value = String(message || "");
  const staleCatalogFragments = [
    "Цена предметов изменилась",
    "Один из AR-предметов больше недоступен",
    "Один из donation-предметов больше недоступен",
    "Один из выбранных предметов пока нельзя купить на сайте",
    "Для одного из выбранных предметов не задана цена",
    "Лимит поставки",
    "Персональный лимит",
    "активный или ожидающий выдачи",
  ];
  return staleCatalogFragments.some((fragment) => value.includes(fragment));
}

function renderCart() {
  syncHeaderCount();
  renderCurrencyCart("ar");
  renderCurrencyCart("donation");
}

async function ensureCsrfCookie() {
  const response = await fetch("/api/auth/csrf", { credentials: "include", headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error("Не удалось подготовить безопасную оплату");
}

async function checkoutCurrency(currency) {
  const meta = CURRENCY_META[currency];
  const cartRows = readShopCart()[currency] || [];
  const entries = cartRows.map((cartRow) => {
    const item = itemForCartRow(currency, cartRow);
    return { cartRow, item, availability: item ? cartItemAvailability(currency, item) : { unavailable: true } };
  });
  const valid = entries.filter((entry) => entry.item && !entry.availability.unavailable && Number(entry.item[meta.priceField] || 0) > 0);
  if (!authState?.cookieAuth || authState?.role !== "player") {
    window.location.href = "/signin.html";
    return;
  }
  if (!authState?.linked) {
    setStatus(currency, "Сначала привяжите Minecraft-ник в личном кабинете.", "warning");
    return;
  }
  if (checkoutBlockGuard.isBlocked(currency)) {
    setStatus(currency, "Обновите состав корзины перед повторной оплатой.", "warning");
    return;
  }
  if (!valid.length || valid.length !== entries.length) {
    setStatus(currency, "Проверьте состав корзины перед оплатой.", "error");
    return;
  }
  const pinInput = document.getElementById(meta.inputId);
  const pin = String(pinInput?.value || "").trim();
  if (!/^\d{4,8}$/.test(pin)) {
    setStatus(currency, "Введите PIN из 4-8 цифр.", "error");
    pinInput?.focus();
    return;
  }
  const button = document.getElementById(meta.buttonId);
  const itemIds = valid.map((entry) => String(entry.cartRow.itemId));
  const expectedTotal = valid.reduce((sum, entry) => sum + Number(entry.item[meta.priceField] || 0), 0);
  const submittedSignature = checkoutCartSignature(currency);
  if (button instanceof HTMLButtonElement) {
    button.disabled = true;
    button.dataset.loading = "true";
    button.textContent = "Оплачиваем...";
  }
  setStatus(currency, "Проверяем оплату и готовим выдачу.", "pending");
  try {
    await ensureCsrfCookie();
    const csrf = readCookie(CSRF_COOKIE);
    const response = await fetch(meta.route, {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        ...(csrf ? { [CSRF_HEADER]: csrf } : {}),
      },
      body: JSON.stringify({ item_ids: itemIds, pin, expected_total: expectedTotal, idempotency_key: cartCheckoutKey(currency, itemIds) }),
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(String(payload?.detail || "Оплата не прошла"));
    const deliveredIds = Array.isArray(payload?.items) ? payload.items.map((item) => String(item?.itemId || "")).filter(Boolean) : itemIds;
    clearShopCartItems(deliveredIds, currency);
    clearCartCheckoutKey(currency, itemIds);
    if (pinInput instanceof HTMLInputElement) pinInput.value = "";
    const hint = String(payload?.pickupHint || "Предметы отправлены в отложенную выдачу.");
    setStatus(currency, hint, "success");
    setGlobalNotice("Оплата прошла. Предметы добавлены в отложенную выдачу.", "success");
  } catch (error) {
    const message = error instanceof Error ? error.message : "Оплата не прошла";
    if (shouldRefreshCatalogAfterCheckoutError(message)) {
      const shouldBlock = shouldBlockCheckoutAfterRefresh(message);
      const blockedSubmittedCart = shouldBlock && blockCheckoutCurrency(currency, submittedSignature);
      try {
        catalogState = await loadPublicShopsPageData(authState);
        if (blockedSubmittedCart) {
          checkoutBlockGuard.resolveAfterRefresh(currency, checkoutCartSignature(currency), cartHasUnavailableRow(currency));
        }
      } catch (_reloadError) {
        // The existing catalog remains visible if a refresh is temporarily unavailable.
        blockCheckoutCurrency(currency, submittedSignature);
      }
    }
    setStatus(currency, message, "error");
  } finally {
    if (button instanceof HTMLButtonElement) {
      button.dataset.loading = "false";
      button.textContent = currency === "ar" ? "Оплатить AR-корзину" : "Оплатить donation-корзину";
    }
    renderCart();
  }
}

function bindCheckoutForm(currency) {
  const form = document.getElementById(CURRENCY_META[currency].formId);
  if (!(form instanceof HTMLFormElement) || form.dataset.bound === "true") return;
  form.dataset.bound = "true";
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    void checkoutCurrency(currency);
  });
}

export async function initCartPage() {
  bindCheckoutForm("ar");
  bindCheckoutForm("donation");
  bindCartAuthNav();
  window.addEventListener("shopCartChanged", (event) => {
    clearChangedCheckoutBlocks(event?.detail?.cart);
    renderCart();
  });
  try {
    authState = await loadPublicAuthState();
    syncCartAuthNav();
    catalogState = await loadPublicShopsPageData(authState);
    const accountId = String(authState?.role === "player" ? authState?.accountId || "" : "").trim().toLowerCase();
    checkoutBlockGuard.clear();
    setShopCartScope(accountId ? `player-${accountId}` : "guest");
  } catch (_error) {
    setGlobalNotice("Не удалось загрузить каталог. Повторите попытку позже.", "error");
  }
  syncCartAuthNav();
  renderCart();
}
