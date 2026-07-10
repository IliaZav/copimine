export function createPlayerTreasuryPages(deps) {
  const {
    $,
    state,
    setLoading,
    setView,
    panel,
    kv,
    safetyRail,
    api,
    dt,
    metric,
    formatAr,
    asArray,
    bankPinState,
    esc,
    transactionFeed,
    number,
    toast,
    setMiniHealthSummary,
    setStoredUiState,
  } = deps;

  let recipientCache = [];

  function bankPinStateTone(pin) {
    if (pin.locked) return "bad";
    if (pin.mustChange || pin.status === "temporary-expired") return "warn";
    if (pin.set) return "good";
    return "warn";
  }

  async function loadRecipients(query = "") {
    const data = await api(`/api/player/bank/recipients?q=${encodeURIComponent(query)}`);
    recipientCache = asArray(data.recipients || []);
    return recipientCache;
  }

  function recipientOptions(rows) {
    return asArray(rows)
      .map((row) => {
        const name = String(row.name || "").trim();
        if (!name) return "";
        const meta = [row.username, row.bankLinked ? "банк привязан" : ""].filter(Boolean).join(" · ");
        return `<option value="${esc(name)}">${esc(meta || name)}</option>`;
      })
      .filter(Boolean)
      .join("");
  }

  async function loadPlayerBank() {
    setLoading("Загрузка банка AR");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    if (!state.user.linked) {
      setView(`
        ${panel("Банк AR закрыт", "Нужна привязка Minecraft-ника.", `
          <div class="notice">Сначала привяжи Minecraft-ник.</div>
        `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`)}
      `);
      return;
    }

    const [bank, recipients, taxProfile] = await Promise.all([
      api("/api/player/bank"),
      loadRecipients(""),
      api("/api/player/elections/tax").catch(() => ({ tax: null, due: 0, paid: 0, president: {} })),
    ]);
    const accounts = asArray(bank.accounts);
    if (!accounts.some((row) => String(row.scope || "").toUpperCase() === state.playerBankScope)) {
      state.playerBankScope = "PERSONAL";
    }

    const selectedAccount = accounts.find((row) => String(row.scope || "").toUpperCase() === state.playerBankScope) || accounts[0] || {};
    const pin = bank.pin || {};
    const tempPin = bank.temporaryPin || {};
    const treasuryPin = bank.treasuryPin || {};
    const transferLocked = Boolean(pin.mustChange || pin.status === "temporary-expired");
    const ledger = asArray(selectedAccount.ledger || bank.ledger);
    const usingTreasury = String(selectedAccount.scope || "").toUpperCase() === "TREASURY";
    const tax = taxProfile?.tax || null;
    const taxDue = number(taxProfile?.due || 0);
    const taxPaid = number(taxProfile?.paid || 0);
    const taxPresident = String(taxProfile?.president?.name || "").trim();
    const selectedPinState = usingTreasury ? (treasuryPin.visiblePin ? "Настроен" : "Не задан") : bankPinState(pin);
    const accountTabs = bank.canAccessTreasury ? `
      <div class="segmented">
        <button class="btn ${state.playerBankScope === "PERSONAL" ? "btn-primary" : "btn-secondary"}" data-click="selectPlayerBankScope('PERSONAL')">Личный счёт</button>
        <button class="btn ${state.playerBankScope === "TREASURY" ? "btn-primary" : "btn-secondary"}" data-click="selectPlayerBankScope('TREASURY')">Казна</button>
      </div>
    ` : "";

    setMiniHealthSummary(state.user.username || "игрок", [
      `${selectedAccount.label || "Банк AR"}: ${formatAr(selectedAccount.balance || bank.account?.balance || 0)}`,
      `PIN: ${selectedPinState}`,
    ]);

    setView(`
      <section class="layout-grid grid-4">
        ${metric("Баланс", formatAr(selectedAccount.balance || bank.account?.balance || 0), usingTreasury ? "Казна" : "Личный счёт", "good")}
        ${metric("Операции", ledger.length, "Подтверждённые записи", "neutral")}
        ${metric("PIN", selectedPinState, usingTreasury ? (treasuryPin.visiblePin ? `PIN казны: ${treasuryPin.visiblePin}` : "Задай PIN для казны") : (pin.locked ? `Заблокирован до ${dt(pin.lockedUntil)}` : (tempPin.code ? `Временный PIN до ${dt(tempPin.expiresAt)}` : "Нужен для переводов")), usingTreasury ? (treasuryPin.visiblePin ? "good" : "warn") : bankPinStateTone(pin))}
        ${metric("Minecraft", state.user.minecraftName || "—", "Привязан", "good")}
      </section>
      ${!usingTreasury ? panel("Добровольный налог", tax ? `Президент: ${esc(taxPresident || "не назначен")}. Оплата только вручную.` : "Активный налог сейчас не назначен.", `
        <div class="form-grid">
          <div class="notice full">К оплате за текущий период: <strong>${formatAr(taxDue)}</strong>. Уже оплачено: <strong>${formatAr(taxPaid)}</strong>.</div>
          <input id="electionTaxAmount" type="number" min="1" max="${Math.max(0, taxDue)}" value="${taxDue > 0 ? taxDue : ""}" placeholder="Сумма налога" ${tax ? "" : "disabled"} />
          <input id="electionTaxPin" type="password" inputmode="numeric" placeholder="PIN" ${tax ? "" : "disabled"} />
          <button class="btn btn-primary full" data-click="playerPayElectionTax()" ${!tax || taxDue <= 0 ? "disabled" : ""}>Оплатить налог</button>
        </div>
      `) : ""}

      ${accountTabs}

      ${safetyRail([
        ["Счёт", usingTreasury ? "Доступен президенту и администраторам" : "Сайт, банкомат и игровые оплаты", "good"],
        ["PIN", usingTreasury ? (treasuryPin.visiblePin ? `PIN казны: ${treasuryPin.visiblePin}` : "Задайте PIN казны") : (tempPin.code ? "Сейчас действует временный PIN" : (pin.set ? "PIN уже задан" : "Задайте PIN")), usingTreasury ? (treasuryPin.visiblePin ? "good" : "warn") : (tempPin.code ? "warn" : (pin.set ? "good" : "warn"))],
        ["Блокировка", usingTreasury ? "Ограничение по ошибкам действует и для казны" : (pin.locked ? `PIN заблокирован до ${dt(pin.lockedUntil)}` : "После ошибок PIN временно блокируется"), pin.locked && !usingTreasury ? "bad" : "neutral"],
      ])}

      ${!usingTreasury && tempPin.code ? panel("Временный PIN", "Сначала войди по нему, потом задай новый.", kv([
        ["Временный PIN", tempPin.code],
        ["Выдан", dt(tempPin.createdAt)],
        ["Истекает", dt(tempPin.expiresAt)],
      ])) : ""}

      <section class="layout-grid grid-2">
        ${panel(usingTreasury ? "PIN казны" : "Задать или изменить PIN", usingTreasury ? "PIN казны виден президенту и администраторам." : (tempPin.code ? "Сначала введи временный PIN." : "PIN нужен для переводов."), `
          <div class="form-grid">
            ${usingTreasury ? `<div class="notice full">Текущий PIN казны: <strong>${esc(treasuryPin.visiblePin || "не задан")}</strong></div>` : ""}
            <input id="bankOldPin" type="password" inputmode="numeric" placeholder="${usingTreasury ? "Текущий PIN казны, если уже задан" : (tempPin.code ? "Текущий временный PIN" : "Текущий PIN, если уже задан")}" />
            <input id="bankNewPin" type="password" inputmode="numeric" placeholder="Новый PIN, 4-8 цифр" />
            <button class="btn btn-primary full" data-click="playerSetPin()">Сохранить PIN</button>
          </div>
        `)}

        ${panel(usingTreasury ? "Перевести AR из казны" : "Перевести AR", !usingTreasury && transferLocked ? "Переводы закрыты, пока временный PIN не заменён." : (usingTreasury ? "Доступно президенту и администраторам." : "Выбери получателя из списка или введи ник вручную."), (!usingTreasury && transferLocked) ? `
          <div class="notice">${pin.status === "temporary-expired" ? "Временный PIN истёк. Попроси команду сервера сбросить его ещё раз." : "Сначала задай личный PIN. После этого переводы и ATM-операции откроются автоматически."}</div>
        ` : `
          <div class="form-grid">
            <div class="field-stack full">
              <label for="bankRecipient">Получатель</label>
              <input id="bankRecipient" list="bankRecipientList" placeholder="Начни вводить ник игрока" />
              <datalist id="bankRecipientList">${recipientOptions(recipients)}</datalist>
            </div>
            <input id="bankAmount" type="number" min="1" step="1" placeholder="Сумма" />
            <input id="bankPinInput" type="password" inputmode="numeric" placeholder="PIN" />
            <input id="bankNote" class="full" placeholder="Комментарий, необязательно" />
            <button class="btn btn-primary full" data-click="playerTransfer()">Отправить AR</button>
          </div>
        `)}
      </section>

      ${panel("Журнал операций", "Переводы, оплаты и покупки.", transactionFeed(ledger, 18))}
    `);
  }

  async function playerSetPin() {
    try {
      await api("/api/player/bank/pin", {
        method: "POST",
        body: JSON.stringify({
          old_pin: $("bankOldPin")?.value || "",
          new_pin: $("bankNewPin")?.value || "",
          account_scope: state.playerBankScope || "PERSONAL",
        }),
      });
      toast("PIN обновлён.");
      if ($("bankOldPin")) $("bankOldPin").value = "";
      if ($("bankNewPin")) $("bankNewPin").value = "";
      await loadPlayerBank();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function playerTransfer() {
    try {
      const result = await api("/api/player/bank/transfer", {
        method: "POST",
        body: JSON.stringify({
          recipient: $("bankRecipient")?.value?.trim() || "",
          amount: number($("bankAmount")?.value || 0),
          pin: $("bankPinInput")?.value || "",
          note: $("bankNote")?.value?.trim() || "",
          from_account: state.playerBankScope || "PERSONAL",
        }),
      });
      toast(`Переведено ${result.amount} AR получателю ${result.recipient}.`);
      ["bankRecipient", "bankAmount", "bankPinInput", "bankNote"].forEach((id) => {
        if ($(id)) $(id).value = "";
      });
      await loadPlayerBank();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function playerPayElectionTax() {
    try {
      const result = await api("/api/player/elections/tax/pay", {
        method: "POST",
        body: JSON.stringify({
          amount: number($("electionTaxAmount")?.value || 0),
          pin: $("electionTaxPin")?.value || "",
        }),
      });
      toast(`Налог оплачен: ${result.amount} AR.`);
      if ($("electionTaxPin")) $("electionTaxPin").value = "";
      await loadPlayerBank();
    } catch (err) {
      toast(err.message, true);
    }
  }

  async function selectPlayerBankScope(scope = "PERSONAL") {
    state.playerBankScope = String(scope || "PERSONAL").toUpperCase();
    setStoredUiState("copiminePlayerBankScope", state.playerBankScope);
    if (state.tab === "bank") {
      await loadPlayerBank();
    }
  }

  return {
    loadPlayerBank,
    playerPayElectionTax,
    playerSetPin,
    playerTransfer,
    selectPlayerBankScope,
  };
}
