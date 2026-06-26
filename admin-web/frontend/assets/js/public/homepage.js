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

let currentBudgetValue = 0;
let currentRotation = -16;
let publicFallbackLoaded = false;

function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[char]);
}

function formatAr(value) {
  const amount = Number(value || 0);
  const normalized = Number.isFinite(amount) ? amount : 0;
  return `${normalized.toLocaleString("ru-RU")} AR`;
}

function formatDate(value) {
  const raw = Number(value || 0);
  if (!raw) return "—";
  const date = raw > 1000000000000 ? new Date(raw) : new Date(raw * 1000);
  if (!Number.isFinite(date.getTime())) return "—";
  return date.toLocaleString("ru-RU");
}

function animateCounter(nextValue) {
  if (!budgetCounter) return;
  const target = Math.max(0, Number(nextValue || 0));
  const start = currentBudgetValue;
  const startedAt = performance.now();
  const duration = 850;
  const prefersReduced = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches;
  if (prefersReduced) {
    currentBudgetValue = target;
    budgetCounter.textContent = formatAr(target);
    return;
  }
  const tick = (now) => {
    const progress = Math.min(1, (now - startedAt) / duration);
    const eased = 1 - Math.pow(1 - progress, 3);
    const frame = Math.round(start + (target - start) * eased);
    budgetCounter.textContent = formatAr(frame);
    if (progress < 1) {
      requestAnimationFrame(tick);
      return;
    }
    currentBudgetValue = target;
    budgetCounter.textContent = formatAr(target);
  };
  requestAnimationFrame(tick);
}

function renderHistory(items = []) {
  if (!historyMount) return;
  const rows = Array.isArray(items) ? items : [];
  if (!rows.length) {
    historyMount.innerHTML = `
      <article class="showcase-card">
        <strong>История пока пустая</strong>
        <p>Как только появятся доходы лавки, ремонты или выплаты, они отобразятся здесь.</p>
      </article>
    `;
    return;
  }
  historyMount.innerHTML = rows.slice(0, 6).map((row) => `
    <article class="treasury-history-card">
      <div class="treasury-history-row">
        <span class="treasury-history-type">${esc(row.label || row.type || "Операция")}</span>
        <strong>${esc(formatAr(row.amount || row.amount_ar || 0))}</strong>
      </div>
      <p>${esc(row.comment || row.item_name || row.public_actor_name || row.actor || "Публичная операция казны")}</p>
      <span class="treasury-history-date">${esc(formatDate(row.createdAt || row.created_at))}</span>
    </article>
  `).join("");
}

function renderPresidentCard(president = {}) {
  if (!presidentName || !presidentMeta) return;
  const name = String(president.current_president_name || president.ownerName || "").trim();
  const uuid = String(president.current_president_uuid || president.ownerUuid || "").trim();
  if (!name) {
    presidentName.textContent = "Президент пока не избран";
    presidentMeta.textContent = "Казна продолжает пополняться, но персональная карточка появится только после активного президентского срока.";
    skinShell?.classList.add("hidden");
    if (skinImage) skinImage.removeAttribute("src");
    return;
  }
  presidentName.textContent = name;
  presidentMeta.textContent = "Карточка подтягивается из public API. Если скин недоступен, сайт оставляет только имя и статус без битых изображений.";
  if (!uuid || !skinImage || !skinShell) {
    skinShell?.classList.add("hidden");
    return;
  }
  skinImage.src = `/api/public/president/skin/body?uuid=${encodeURIComponent(uuid)}`;
  skinImage.alt = `Скин президента ${name}`;
  skinShell.classList.remove("hidden");
}

function setSkinRotation(nextRotation) {
  if (!skinTilt) return;
  currentRotation = nextRotation;
  skinTilt.style.transform = `rotateX(4deg) rotateY(${nextRotation}deg)`;
}

function bindSkinControls() {
  if (leftButton) {
    leftButton.addEventListener("click", () => setSkinRotation(currentRotation - 18));
  }
  if (rightButton) {
    rightButton.addEventListener("click", () => setSkinRotation(currentRotation + 18));
  }
  if (skinImage) {
    skinImage.addEventListener("error", () => {
      skinShell?.classList.add("hidden");
      skinImage.removeAttribute("src");
    });
  }
}

function renderBudget(payload = {}) {
  const balance = Number(payload.balance_ar ?? payload.balance ?? 0);
  const currentPresidentName = String(payload.current_president_name || payload.ownerName || "").trim();
  if (budgetOwner) {
    budgetOwner.textContent = currentPresidentName
      ? `Казной управляет ${currentPresidentName}`
      : "Пока без активного президента";
  }
  if (budgetDetail) {
    budgetDetail.textContent = payload.updated_at || payload.updatedAt
      ? `Публичные данные обновлены ${formatDate(payload.updated_at || payload.updatedAt)}`
      : "Публичный API не вернул время обновления, поэтому показываем только подтверждённый баланс.";
  }
  animateCounter(balance);
  renderPresidentCard(payload);
}

async function loadFallback() {
  if (publicFallbackLoaded) return;
  publicFallbackLoaded = true;
  try {
    const [budgetRes, historyRes] = await Promise.all([
      fetch("/api/public/president-budget", { credentials: "same-origin" }),
      fetch("/api/public/president-budget/history?limit=6", { credentials: "same-origin" })
    ]);
    const budgetPayload = budgetRes.ok ? await budgetRes.json() : { ok: false, data: null };
    const historyPayload = historyRes.ok ? await historyRes.json() : { ok: false, data: null };
    if (budgetPayload?.ok && budgetPayload.data) renderBudget(budgetPayload.data);
    if (historyPayload?.ok && historyPayload.data) renderHistory(historyPayload.data.items || []);
  } catch (error) {
    if (budgetDetail) {
      budgetDetail.textContent = "Источник публичной казны временно недоступен. Попробуйте позже.";
    }
  }
}

window.addEventListener("copimine:public-status", (event) => {
  const detail = event.detail || {};
  const status = detail.status || {};
  const treasury = status.treasury || {};
  renderBudget({
    balance_ar: treasury.balance,
    current_president_name: treasury.ownerName || status.elections?.president || "",
    current_president_uuid: treasury.ownerUuid || "",
    updated_at: status.generatedAt || Date.now()
  });
  renderHistory((treasury.history || []).map((row) => ({
    ...row,
    public_actor_name: row.actor || "",
    created_at: row.createdAt || row.created_at || 0
  })));
});

bindSkinControls();
setSkinRotation(currentRotation);
window.setTimeout(loadFallback, 250);
