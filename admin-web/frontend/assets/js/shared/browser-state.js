const ALLOWED_KEYS = new Set([
  "copimineLastRole",
  "copimineDonationSessionId",
  "copiminePlayerBankScope",
]);

function safeStorage() {
  try {
    return window.localStorage;
  } catch (_error) {
    return null;
  }
}

function normalizeKey(key) {
  const normalized = String(key || "");
  if (!ALLOWED_KEYS.has(normalized)) {
    throw new Error(`Storage key is not allowlisted: ${normalized}`);
  }
  return normalized;
}

export function getStoredUiState(key, fallback = "") {
  const storage = safeStorage();
  if (!storage) return fallback;
  try {
    const value = storage.getItem(normalizeKey(key));
    return value == null ? fallback : value;
  } catch (_error) {
    return fallback;
  }
}

export function setStoredUiState(key, value) {
  const storage = safeStorage();
  if (!storage) return;
  try {
    storage.setItem(normalizeKey(key), String(value ?? ""));
  } catch (_error) {
    // Ignore browser storage failures; UI should continue to work in-memory.
  }
}

export function removeStoredUiState(key) {
  const storage = safeStorage();
  if (!storage) return;
  try {
    storage.removeItem(normalizeKey(key));
  } catch (_error) {
    // Ignore browser storage failures; UI should continue to work in-memory.
  }
}
