const STORAGE_KEY = "copimine.shop.cart.v1";
const CURRENCIES = new Set(["ar", "donation"]);
let cartScope = "guest";

function emptyCart() {
  return { ar: [], donation: [] };
}

function normalizeCurrency(value) {
  const currency = String(value || "").trim().toLowerCase();
  return CURRENCIES.has(currency) ? currency : "";
}

function normalizeItemId(value) {
  const itemId = String(value || "").trim().toLowerCase();
  return /^[a-z0-9_-]{2,120}$/.test(itemId) ? itemId : "";
}

function normalizeScope(value) {
  const scope = String(value || "").trim().toLowerCase();
  return /^[a-z0-9_-]{3,96}$/.test(scope) ? scope : "guest";
}

function scopedStorageKey() {
  return `${STORAGE_KEY}.${cartScope}`;
}

export function setShopCartScope(scope) {
  const nextScope = normalizeScope(scope);
  if (nextScope === cartScope) return cartScope;
  cartScope = nextScope;
  emitCartChanged(readShopCart());
  return cartScope;
}

export function getShopCartScope() {
  return cartScope;
}

function normalizeCart(value) {
  const next = emptyCart();
  for (const currency of CURRENCIES) {
    const rows = Array.isArray(value?.[currency]) ? value[currency] : [];
    const seen = new Set();
    next[currency] = rows
      .map((row) => ({ itemId: normalizeItemId(row?.itemId), currency }))
      .filter((row) => row.itemId && !seen.has(row.itemId) && seen.add(row.itemId));
  }
  return next;
}

export function readShopCart() {
  try {
    return normalizeCart(JSON.parse(window.localStorage.getItem(scopedStorageKey()) || "{}"));
  } catch (_error) {
    return emptyCart();
  }
}

function emitCartChanged(cart) {
  window.dispatchEvent(new CustomEvent("shopCartChanged", { detail: { cart, count: getShopCartCount(cart) } }));
}

export function writeShopCart(cart) {
  const normalized = normalizeCart(cart);
  window.localStorage.setItem(scopedStorageKey(), JSON.stringify(normalized));
  emitCartChanged(normalized);
  return normalized;
}

export function getShopCartCount(cart = readShopCart()) {
  return [...CURRENCIES].reduce((count, currency) => count + (Array.isArray(cart?.[currency]) ? cart[currency].length : 0), 0);
}

export function hasShopCartItem(itemId, currency) {
  const safeCurrency = normalizeCurrency(currency);
  const safeItemId = normalizeItemId(itemId);
  return Boolean(safeCurrency && safeItemId && readShopCart()[safeCurrency].some((row) => row.itemId === safeItemId));
}

export function addShopCartItem(itemId, currency) {
  const safeCurrency = normalizeCurrency(currency);
  const safeItemId = normalizeItemId(itemId);
  if (!safeCurrency || !safeItemId) return { added: false, cart: readShopCart() };
  const cart = readShopCart();
  if (cart[safeCurrency].some((row) => row.itemId === safeItemId)) {
    return { added: false, cart };
  }
  cart[safeCurrency].push({ itemId: safeItemId, currency: safeCurrency });
  return { added: true, cart: writeShopCart(cart) };
}

export function removeShopCartItem(itemId, currency) {
  const safeCurrency = normalizeCurrency(currency);
  const safeItemId = normalizeItemId(itemId);
  const cart = readShopCart();
  if (!safeCurrency || !safeItemId) return cart;
  cart[safeCurrency] = cart[safeCurrency].filter((row) => row.itemId !== safeItemId);
  return writeShopCart(cart);
}

export function clearShopCartItems(itemIds, currency) {
  const safeCurrency = normalizeCurrency(currency);
  const ids = new Set((Array.isArray(itemIds) ? itemIds : []).map(normalizeItemId).filter(Boolean));
  const cart = readShopCart();
  if (!safeCurrency || !ids.size) return cart;
  cart[safeCurrency] = cart[safeCurrency].filter((row) => !ids.has(row.itemId));
  return writeShopCart(cart);
}
