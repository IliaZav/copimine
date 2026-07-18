function normalizeCurrency(value) {
  const currency = String(value || "").trim().toLowerCase();
  return currency === "ar" || currency === "donation" ? currency : "";
}

function normalizeSignature(value) {
  return String(value || "");
}

const CHECKOUT_BLOCKING_CONFLICTS = [
  "Лимит поставки",
  "Один из выбранных предметов пока нельзя купить на сайте",
  "Персональный лимит",
  "активный или ожидающий выдачи",
];

export function shouldBlockCheckoutAfterRefresh(message) {
  const value = String(message || "");
  return CHECKOUT_BLOCKING_CONFLICTS.some((fragment) => value.includes(fragment));
}

export function createCheckoutBlockGuard() {
  const blockedSignatures = new Map();

  return {
    block(currency, signature) {
      const key = normalizeCurrency(currency);
      if (!key) return;
      blockedSignatures.set(key, normalizeSignature(signature));
    },
    blockIfSignatureMatches(currency, submittedSignature, currentSignature) {
      const key = normalizeCurrency(currency);
      const submitted = normalizeSignature(submittedSignature);
      if (!key || submitted !== normalizeSignature(currentSignature)) return false;
      blockedSignatures.set(key, submitted);
      return true;
    },
    isBlocked(currency) {
      const key = normalizeCurrency(currency);
      return Boolean(key && blockedSignatures.has(key));
    },
    clearIfSignatureChanged(currency, signature) {
      const key = normalizeCurrency(currency);
      if (!key || !blockedSignatures.has(key)) return false;
      if (blockedSignatures.get(key) === normalizeSignature(signature)) return false;
      blockedSignatures.delete(key);
      return true;
    },
    resolveAfterRefresh(currency, signature, hasUnavailableRow) {
      const key = normalizeCurrency(currency);
      if (!key || !blockedSignatures.has(key)) return false;
      if (!hasUnavailableRow && blockedSignatures.get(key) === normalizeSignature(signature)) return false;
      blockedSignatures.delete(key);
      return true;
    },
    clear() {
      blockedSignatures.clear();
    },
  };
}
