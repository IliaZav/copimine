export function fullTaxPaymentAmount(value) {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? amount : 0;
}

export function isPlayerBankRoute(tab) {
  return ["bank", "transfer"].includes(String(tab || "").trim().toLowerCase());
}
