import { appRouteHref } from "../shared/app-routes.js";

export function createPlayerDonationPages(deps) {
  const {
    $,
    state,
    setLoading,
    api,
    safeApi,
    setView,
    panel,
    metric,
    kv,
    dt,
    esc,
    short,
    formatDonate,
    asArray,
    cleanText,
    table,
    safetyRail,
    pill,
    number,
    donationSessionKey,
    statusLabel,
    artifactStatusTone,
    randomActionKey,
    setStoredUiState,
    removeStoredUiState,
    copyText,
    toast,
  } = deps;

  let lastShopPayload = null;
  // Keep purchases independent from the temporary public top-up switch.
  const DONATION_TOPUP_DISABLED_MESSAGE = "Пополнение donation временно отключено. Покупать можно за уже имеющийся баланс.";

  function paymentModeLabel(value) {
    const provider = String(value || "").toUpperCase();
    if (provider === "YOOKASSA") return "ЮKassa";
    if (provider === "MOCK_SBP") return "Тестовая оплата";
    return "Оплата недоступна";
  }

  function donationItemId(row) {
    return String(row?.item_id || "").trim();
  }

  function donationItemTitle(row) {
    return cleanText(row?.display_name || row?.item_id || "предмет");
  }

  function donationItemPrice(row) {
    return number(row?.price_donation || 0);
  }

  function donationItemOption(row, selectedId) {
    const id = donationItemId(row);
    if (!id) return "";
    const selected = id === selectedId ? " selected" : "";
    return `<option value="${esc(id)}"${selected}>${esc(donationItemTitle(row))} · ${esc(formatDonate(donationItemPrice(row)))}</option>`;
  }

  function resolveSelectedDonationItem(rows) {
    const enabled = asArray(rows).filter((row) => row.enabled !== false);
    const selectedId = String(state.playerDonationSelectedItemId || "").trim().toLowerCase();
    return enabled.find((row) => donationItemId(row).toLowerCase() === selectedId) || enabled[0] || rows[0] || null;
  }

  function donationPurchaseCard(rows, balance) {
    const selected = resolveSelectedDonationItem(rows);
    const selectedId = donationItemId(selected);
    state.playerDonationSelectedItemId = selectedId;
    const enoughBalance = selected ? number(balance.balance || 0) >= donationItemPrice(selected) : false;
    const pinReady = true;
    const helper = !selected
      ? "Каталог donation сейчас пуст."
      : selected.owned_active
        ? "Этот предмет уже активен у игрока."
        : selected.claim_available
          ? "Этот предмет уже куплен и ждёт выдачи в игре."
          : enoughBalance
            ? "Введи PIN и подтверди покупку."
            : "На donation-балансе не хватает средств.";
    return `
      <article class="artifact-purchase-card">
        <div class="artifact-purchase-head">
          <span>Donation-лавка</span>
          <strong>${selected ? esc(donationItemTitle(selected)) : "Нет предметов"}</strong>
          <p>${esc(helper)}</p>
        </div>
        <div class="artifact-purchase-price">
          <span>Цена</span>
          <strong>${selected ? esc(formatDonate(donationItemPrice(selected))) : "—"}</strong>
        </div>
        <div class="form-grid artifact-buy-form">
          <div class="field-stack full">
            <label for="playerDonationItemSelect">Предмет</label>
            <select id="playerDonationItemSelect" data-input="playerSelectDonationItem">${asArray(rows).filter((row) => row.enabled !== false).map((row) => donationItemOption(row, selectedId)).join("")}</select>
          </div>
          <div class="field-stack">
            <label for="playerDonationPin">PIN банка</label>
            <input id="playerDonationPin" type="password" inputmode="numeric" autocomplete="one-time-code" placeholder="PIN" />
          </div>
          <div class="field-stack artifact-buy-action">
            <label>&nbsp;</label>
            <button class="btn btn-primary" data-click="playerBuyDonationItem()"${selected && !selected.owned_active && !selected.claim_available && pinReady && enoughBalance ? "" : " disabled"}>Подтвердить покупку</button>
          </div>
        </div>
        <div class="artifact-buy-note">
          <span>Выдача</span>
          <code>только в игре через donation shop</code>
        </div>
        <div class="action-strip artifact-buy-reclaim">
          <button class="btn btn-secondary" data-click="setTab('donation-items')">Вернуть потерянный предмет</button>
        </div>
      </article>
    `;
  }

  async function loadPlayerDonationBalance() {
    setLoading("Загрузка donation-баланса");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    const linked = Boolean(state.user.linked);
    if (!linked) {
      setView(panel("Donation-баланс", "Нужна привязка Minecraft-ника.", `
        <div class="notice">Без привязки donation-раздел недоступен.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`));
      return;
    }

    const [balance, packs, history] = await Promise.all([
      safeApi("/api/player/donation/balance", { linked: true, balance: 0, topupEnabled: false }),
      safeApi("/api/player/donation/packs", { packs: [], provider: "MOCK_SBP", rubPerUnit: 1, topupEnabled: false }),
      safeApi("/api/player/donation/history", { history: [] }),
    ]);

    let session = null;
    if (state.donationSessionId) {
      const result = await safeApi(`/api/player/donation/sbp/session/${encodeURIComponent(state.donationSessionId)}`, null);
      session = result?.session || null;
      if (!session) {
        state.donationSessionId = "";
        removeStoredUiState("copimineDonationSessionId");
      }
    }

    const topupEnabled = packs.topupEnabled === true;
    const packButtons = topupEnabled
      ? asArray(packs.packs).map((pack) => `
        <button class="btn btn-primary" data-click="playerCreateDonationSession(${number(pack.amount || 0)})">${esc(`${pack.amount} Donation`)}</button>
      `).join("")
      : `<div class="notice" data-message-key="DONATION_TOPUP_DISABLED_MESSAGE">${esc(DONATION_TOPUP_DISABLED_MESSAGE)}</div>`;
    const confirmationUrl = String(session?.confirmation_url || "").trim();
    const providerCheckout = confirmationUrl ? `
      <div class="payment-provider-card">
        <strong>Оплата в ЮKassa</strong>
        <p>Откроется защищённая страница провайдера. Баланс изменится только после подтверждения платежа.</p>
        <a class="btn btn-primary" href="${esc(confirmationUrl)}" target="_blank" rel="noopener noreferrer">Перейти к оплате</a>
      </div>
    ` : `<img class="qr-image" src="/api/player/donation/sbp/session/${esc(donationSessionKey(session))}/qr.png?_fresh=${Date.now()}" alt="QR оплаты" />`;
    const sessionPanel = session ? `
      <div class="qr-block">
        ${providerCheckout}
        <div class="qr-copy">
          ${kv([
            ["Сессия", donationSessionKey(session)],
            ["Код", session.session_code || short(donationSessionKey(session), 8)],
            ["Сумма", formatDonate(session.amount || session.donation_units || 0)],
            ["Статус", statusLabel(session.status || "created")],
            ["Истекает", dt(session.expires_at)],
            ["Провайдер", paymentModeLabel(session.provider)],
          ])}
          <div class="action-strip">
            <button class="btn btn-secondary" data-click="playerCopyDonationPaymentUrl()">Скопировать ссылку</button>
            <button class="btn btn-secondary" data-click="playerCopyDonationSessionCode()">Скопировать код</button>
            <button class="btn btn-secondary" data-click="playerRefreshDonationSession()">Обновить статус</button>
            <button class="btn btn-secondary" data-click="playerForgetDonationSession()">Скрыть сессию</button>
          </div>
          <div class="notice">Баланс изменится после подтверждения оплаты.</div>
        </div>
      </div>
    ` : `<div class="notice">Активной платёжной сессии нет.</div>`;

    setView(`
      <section class="layout-grid grid-4">
        ${metric("Donation", formatDonate(balance.balance || 0), "Отдельно от AR", "good")}
        ${metric("Курс", `${packs.rubPerUnit || 1} ₽ = 1 DC`, "Фиксированный курс", "neutral")}
        ${metric("Режим оплаты", paymentModeLabel(packs.provider), packs.providerConfigured === false ? "Требуется настройка ЮKassa" : (String(packs.provider || "").toUpperCase() === "YOOKASSA" ? "Оплата на защищённой странице провайдера" : "Тестовый режим"), packs.providerConfigured === false ? "warn" : "good")}
        ${metric("Сессия", session ? statusLabel(session.status || "created") : "нет", session ? `Код ${session.session_code || short(donationSessionKey(session), 8)}` : "Создай новую сессию", session ? "neutral" : "good")}
      </section>
      ${panel("Donation-баланс", "Отдельный баланс для donation-лавки.", kv([
        ["Статус", linked ? "привязан" : "нет привязки"],
        ["Баланс", formatDonate(balance.balance || 0)],
        ["Пополнение", topupEnabled ? "фиксированные пакеты" : "временно отключено"],
        ["Выдача", "только в игре"],
      ]), `<button class="btn btn-secondary" data-click="setTab('donation-items')">Мои donation-предметы</button>`)}
      ${panel(topupEnabled ? "Пополнить" : "Пополнение", topupEnabled ? "Выбери пакет и создай сессию." : "Пополнение временно недоступно.", `
        <div class="action-strip wrap">${packButtons}</div>
        <div class="spacer-12"></div>
        <div class="notice">${topupEnabled ? "Пакеты: 50 / 100 / 250 / 500 / 1000. Donation не смешивается с AR." : "Покупать можно за уже имеющийся баланс."}</div>
      `)}
      ${panel("Платёжная сессия", "Ссылка ЮKassa или QR тестовой оплаты.", sessionPanel)}
      ${panel("История", "Только операции donation-баланса.", table("player-donation-history", asArray(history.history), [
        { key: "created_at", label: "Время", render: (value) => dt(value) },
        { key: "delta", label: "Изменение", render: (value) => formatDonate(value || 0) },
        { key: "balance_after", label: "После", render: (value) => formatDonate(value || 0) },
        { key: "reason", label: "Причина", render: (value) => short(value || "", 80) },
        { key: "source", label: "Источник", render: (value) => cleanText(value || "—") },
      ], { pageSize: 12 }))}
    `);
  }

  async function loadPlayerDonationShop() {
    setLoading("Загрузка donation-лавки");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    const linked = Boolean(state.user.linked);
    if (!linked) {
      setView(panel("Donation-лавка", "Нужна привязка Minecraft-ника.", `
        <div class="notice">Без привязки покупка недоступна.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`));
      return;
    }

    const [catalog, balance] = await Promise.all([
      safeApi("/api/player/shop/donation-items", { items: [], catalogVersion: 0 }),
      safeApi("/api/player/donation/balance", { balance: 0 }),
    ]);

    lastShopPayload = { catalog, balance };
    const rows = asArray(catalog.items).map((row) => {
      const status = row.owned_active
        ? "Уже у тебя"
        : (row.claim_available ? "Можно забрать в игре" : (row.enabled ? "Не куплен" : "Отключён"));
      const action = row.owned_active
        ? `<span class="btn btn-secondary btn-small disabled">Уже у тебя</span>`
        : row.claim_available
          ? `<button class="btn btn-secondary btn-small" data-click="setTab('donation-items')">Забрать в игре</button>`
          : row.enabled
            ? `<button class="btn btn-secondary btn-small" data-click='playerSelectDonationItem(${JSON.stringify(String(row.item_id || ""))})'>Выбрать</button>`
            : `<span class="btn btn-secondary btn-small disabled">Отключён</span>`;
      return {
        ...row,
        status_text: status,
        action_html: action,
      };
    });

    const focusItemId = String(state.donationFocusItemId || "").trim().toLowerCase();
    if (focusItemId) {
      rows.sort((a, b) => {
        const aMatch = String(a.item_id || "").toLowerCase() === focusItemId ? 0 : 1;
        const bMatch = String(b.item_id || "").toLowerCase() === focusItemId ? 0 : 1;
        return aMatch - bMatch;
      });
    }

    setView(`
      <section class="layout-grid grid-4">
        ${metric("Баланс", formatDonate(balance.balance || 0), "Отдельно от AR", "good")}
        ${metric("Каталог", asArray(catalog.items).length, `Версия ${catalog.catalogVersion || 0}`, "neutral")}
        ${metric("Готово к выдаче", rows.filter((row) => row.claim_available).length, "Можно забрать в игре", rows.some((row) => row.claim_available) ? "warn" : "good")}
        ${metric("Активные", rows.filter((row) => row.owned_active).length, "Сейчас у игрока", rows.some((row) => row.owned_active) ? "good" : "neutral")}
      </section>
      <section class="layout-grid grid-2 artifact-workbench">
        ${donationPurchaseCard(rows, balance)}
        ${panel("Порядок", "Покупка и возврат.", safetyRail([
          ["Покупка", "Donation списывается сразу после проверки PIN.", "good"],
          ["Выдача", "Предмет выдаётся в игре через donation shop.", "warn"],
          ["Баланс", "Для покупки используется уже зачисленный donation-баланс.", "neutral"],
        ]), `<button class="btn btn-secondary" data-click="setTab('donation-balance')">Открыть donation-баланс</button>`)}
      </section>
      ${panel("Donation-лавка", "Покупка на сайте. Выдача в игре.", `
        ${focusItemId ? `<div class="notice">Открыт товар по прямой ссылке: <strong>${esc(focusItemId)}</strong>.</div>` : ""}
        ${table("player-donation-shop", rows, [
          { key: "display_name", label: "Предмет", render: (value) => `<strong>${esc(cleanText(value || "Предмет"))}</strong>` },
          { key: "price_donation", label: "Цена", render: (value) => formatDonate(value || 0) },
          { key: "effect_description", label: "Эффект", render: (value) => short(value || "", 110) },
          { key: "cooldown_seconds", label: "Кулдаун", render: (value) => value ? `${value} сек.` : "—" },
          { key: "status_text", label: "Статус", render: (value, row) => pill(value, row.owned_active ? "good" : (row.claim_available ? "warn" : "neutral")) },
          { key: "action_html", label: "Действие", render: (value) => value },
        ], { pageSize: 10 })}
      `)}
      ${panel("Возврат", "Как работает выдача и возврат.", safetyRail([
        ["К выдаче", "После покупки появится запись на выдачу.", "good"],
        ["Забрать", "Забирать и проверять статус нужно в игре.", "warn"],
        ["Возврат", "Утерянные donation-предметы обслуживаются отдельно.", "neutral"],
      ]), `<button class="btn btn-secondary" data-click="setTab('donation-items')">Открыть мои donation-предметы</button>`)}
    `);
  }

  async function loadPlayerDonationItems() {
    setLoading("Загрузка donation-предметов");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    const linked = Boolean(state.user.linked);
    if (!linked) {
      setView(panel("Мои donation-предметы", "Нужна привязка Minecraft-ника.", `
        <div class="notice">Без привязки раздел недоступен.</div>
      `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`));
      return;
    }

    const owned = await safeApi("/api/player/shop/owned", { linked: true, claims: [], instances: [], summary: {} });

    setView(`
      <section class="layout-grid grid-4">
        ${metric("Покупки", asArray(owned.claims).length, "Ожидают выдачи или уже завершены", asArray(owned.claims).length ? "warn" : "neutral")}
        ${metric("Активные", owned.summary?.active || 0, "Сейчас у игрока", (owned.summary?.active || 0) ? "good" : "neutral")}
        ${metric("Можно вернуть", owned.summary?.reclaimable || 0, "Утерянные предметы", (owned.summary?.reclaimable || 0) ? "warn" : "neutral")}
        ${metric("Ждут выдачи", owned.summary?.claimPending || 0, "Забираются в игре", (owned.summary?.claimPending || 0) ? "warn" : "good")}
      </section>
      ${panel("Покупки и выдача", "Купленные предметы и выдача.", table("player-donation-owned-claims", asArray(owned.claims), [
        { key: "purchase_created_at", label: "Покупка", render: (value) => dt(value) },
        { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
        { key: "price_donation", label: "Цена", render: (value) => formatDonate(value || 0) },
        { key: "status", label: "Выдача", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
        { key: "purchase_status", label: "Покупка", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
      ], { pageSize: 12 }))}
      ${panel("Выданные экземпляры", "Статус каждого выданного предмета.", table("player-donation-owned-instances", asArray(owned.instances), [
        { key: "updated_at", label: "Обновлён", render: (value) => dt(value) },
        { key: "display_name", label: "Предмет", render: (value, row) => `<strong>${esc(cleanText(value || row.item_id || "Предмет"))}</strong><br><span class="muted">${esc(row.item_id || "—")}</span>` },
        { key: "status", label: "Статус", render: (value) => pill(statusLabel(value || "pending"), artifactStatusTone(value)) },
      ], { pageSize: 12 }))}
      ${panel("Возврат", "Как работает возврат утерянных предметов.", safetyRail([
        ["Забрать в игре", "Если покупка ждёт выдачи, получай предмет через игровую лавку.", "good"],
        ["Вернуть утерянное", "Бесплатный возврат доступен только для внешней потери предмета.", "warn"],
        ["Сломано или израсходовано", "Такие предметы бесплатно не возвращаются.", "bad"],
      ]), `<button class="btn btn-secondary" data-click="setTab('donation-shop')">Вернуться в donation-лавку</button>`)}
    `);
  }

  async function playerCreateDonationSession(amount) {
    if (state.donationBusy) return;
    try {
      state.donationBusy = true;
      const result = await api("/api/player/donation/sbp/session", {
        method: "POST",
        body: JSON.stringify({ amount: number(amount || 0), idempotency_key: randomActionKey("don-session") }),
      });
      state.donationSessionId = donationSessionKey(result?.session);
      if (state.donationSessionId) setStoredUiState("copimineDonationSessionId", state.donationSessionId);
      toast(result?.session?.confirmation_url ? "Сессия ЮKassa создана. Открой страницу оплаты." : `Создана сессия на ${number(amount || 0)} Donation.`);
      await loadPlayerDonationBalance();
    } catch (err) {
      toast(err.message, true);
    } finally {
      state.donationBusy = false;
    }
  }

  async function playerRefreshDonationSession() {
    if (!state.donationSessionId) {
      toast("Активной сессии нет.", true);
      return;
    }
    await loadPlayerDonationBalance();
  }

  function playerForgetDonationSession() {
    state.donationSessionId = "";
    removeStoredUiState("copimineDonationSessionId");
    void loadPlayerDonationBalance();
  }

  async function playerCopyDonationSessionCode() {
    if (!state.donationSessionId) return;
    await copyText(state.donationSessionId.slice(-8), "Код сессии скопирован");
  }

  async function playerCopyDonationPaymentUrl() {
    if (!state.donationSessionId) return;
    const result = await safeApi(`/api/player/donation/sbp/session/${encodeURIComponent(state.donationSessionId)}`, null);
    const confirmationUrl = String(result?.session?.confirmation_url || "").trim();
    await copyText(confirmationUrl || `${location.origin}${appRouteHref("donation-balance", { session: state.donationSessionId })}`, "Ссылка оплаты скопирована");
  }

  function playerSelectDonationItem(itemId) {
    state.playerDonationSelectedItemId = String(itemId || "").trim();
    if (lastShopPayload) {
      void loadPlayerDonationShop();
    }
  }

  async function playerBuyDonationItem(itemId = "") {
    try {
      const selected = String(itemId || state.playerDonationSelectedItemId || $("playerDonationItemSelect")?.value || "").trim();
      const pin = $("playerDonationPin")?.value?.trim() || "";
      if (!selected) return toast("Выбери donation-предмет.", true);
      if (!pin) return toast("Введи PIN банка.", true);
      const result = await api("/api/player/shop/purchase-intent", {
        method: "POST",
        body: JSON.stringify({ item_id: selected, pin, idempotency_key: randomActionKey("don-buy") }),
      });
      if ($("playerDonationPin")) $("playerDonationPin").value = "";
      toast(`Покупка создана: ${cleanText(result.itemId || selected)}. Забери предмет в игре.`);
      await loadPlayerDonationShop();
    } catch (err) {
      toast(err.message, true);
    }
  }

  return {
    loadPlayerDonationBalance,
    loadPlayerDonationShop,
    loadPlayerDonationItems,
    playerCreateDonationSession,
    playerRefreshDonationSession,
    playerForgetDonationSession,
    playerCopyDonationSessionCode,
    playerCopyDonationPaymentUrl,
    playerSelectDonationItem,
    playerBuyDonationItem,
  };
}
