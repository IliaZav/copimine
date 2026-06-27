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

  function bankPinStateTone(pin) {
    if (pin.locked) return "bad";
    if (pin.mustChange || pin.status === "temporary-expired") return "warn";
    if (pin.set) return "good";
    return "warn";
  }

  async function loadPlayerBank() {
    setLoading("Загрузка банка AR");
    const me = await api("/api/player/me");
    state.user = me.account || {};
    if (!state.user.linked) {
      setView(`
        ${panel("Банк AR закрыт", "Для банка нужна привязка Minecraft-ника.", `
          <div class="notice">Сначала запроси одноразовый код привязки. После этого банк откроется здесь и в игре.</div>
        `, `<button class="btn btn-primary" data-click="setTab('link')">Открыть привязку</button>`)}
      `);
      return;
    }

    const bank = await api("/api/player/bank");
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
        ${metric("Баланс", formatAr(selectedAccount.balance || bank.account?.balance || 0), usingTreasury ? "Казна президента и админов" : "Личный счёт для сайта и игры", "good")}
        ${metric("Последние операции", ledger.length, "Только подтверждённые записи", "neutral")}
        ${metric("PIN", selectedPinState, usingTreasury ? (treasuryPin.visiblePin ? `PIN казны: ${treasuryPin.visiblePin}` : "Задай PIN для казны") : (pin.locked ? `Заблокирован до ${dt(pin.lockedUntil)}` : (tempPin.code ? `Временный PIN до ${dt(tempPin.expiresAt)}` : "Нужен для переводов")), usingTreasury ? (treasuryPin.visiblePin ? "good" : "warn") : bankPinStateTone(pin))}
        ${metric("Minecraft", state.user.minecraftName || "—", "Привязан к кабинету", "good")}
      </section>
      ${accountTabs}
      ${safetyRail([
        ["Банк", usingTreasury ? "Обычные игроки не видят казну. Этот счёт доступен только президенту и админам." : "Баланс один и тот же на сайте, в банкомате и в игровых оплатах.", "good"],
        ["PIN", usingTreasury ? (treasuryPin.visiblePin ? `Текущий PIN казны: ${treasuryPin.visiblePin}` : "Установите PIN казны для переводов.") : (tempPin.code ? "Сейчас действует временный PIN. Замените его перед переводами." : (pin.set ? "PIN уже задан." : "Задайте PIN перед переводами.")), usingTreasury ? (treasuryPin.visiblePin ? "good" : "warn") : (tempPin.code ? "warn" : (pin.set ? "good" : "warn"))],
        ["Блокировка", usingTreasury ? "Для казны действуют те же ограничения по неверным попыткам PIN." : (pin.locked ? `PIN заблокирован до ${dt(pin.lockedUntil)}` : "Неверные попытки временно блокируют PIN."), pin.locked && !usingTreasury ? "bad" : "neutral"],
      ])}
      ${!usingTreasury && tempPin.code ? panel("Временный PIN", "Этот PIN выдан сбросом. Введи его как текущий и сохрани новый.", kv([
        ["Временный PIN", tempPin.code],
        ["Выдан", dt(tempPin.createdAt)],
        ["Истекает", dt(tempPin.expiresAt)],
      ])) : ""}
      <section class="layout-grid grid-2">
        ${panel(usingTreasury ? "PIN казны" : "Задать или изменить PIN", usingTreasury ? "Президент и админ видят PIN казны и могут менять его прямо здесь." : (tempPin.code ? "Активен временный PIN. Введи его как текущий и замени сейчас." : "PIN нужен для переводов на сайте и защищённых операций банкомата."), `
          <div class="form-grid">
            ${usingTreasury ? `<div class="notice full">Текущий PIN казны: <strong>${esc(treasuryPin.visiblePin || "не задан")}</strong></div>` : ""}
            <input id="bankOldPin" type="password" inputmode="numeric" placeholder="${usingTreasury ? "Текущий PIN казны, если уже задан" : (tempPin.code ? "Текущий временный PIN" : "Текущий PIN, если уже задан")}" />
            <input id="bankNewPin" type="password" inputmode="numeric" placeholder="Новый PIN, 4-8 цифр" />
            <button class="btn btn-primary full" data-click="playerSetPin()">Сохранить PIN</button>
          </div>
        `)}
        ${panel(usingTreasury ? "Перевести AR из казны" : "Перевести AR", !usingTreasury && transferLocked ? "Переводы закрыты, пока временный PIN не заменён." : (usingTreasury ? "Бюджетный перевод доступен только президенту и админам." : "Перевод отправит AR на другой счёт и сразу покажет результат в истории."), (!usingTreasury && transferLocked) ? `
          <div class="notice">${pin.status === "temporary-expired" ? "Временный PIN истёк. Попроси команду сервера сбросить его ещё раз." : "Сначала задай личный PIN. После этого переводы и ATM-операции откроются автоматически."}</div>
        ` : `
          <div class="form-grid">
            <input id="bankRecipient" placeholder="Логин получателя или Minecraft-ник" />
            <input id="bankAmount" type="number" min="1" step="1" placeholder="Сумма" />
            <input id="bankPinInput" type="password" inputmode="numeric" placeholder="PIN" />
            <input id="bankNote" class="full" placeholder="Комментарий, необязательно" />
            <button class="btn btn-primary full" data-click="playerTransfer()">Отправить AR</button>
          </div>
        `)}
      </section>
      ${panel("Журнал операций", "Последние переводы, оплаты и покупки по этому счёту.", transactionFeed(ledger, 18))}
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

  async function selectPlayerBankScope(scope = "PERSONAL") {
    state.playerBankScope = String(scope || "PERSONAL").toUpperCase();
    setStoredUiState("copiminePlayerBankScope", state.playerBankScope);
    if (state.tab === "bank") {
      await loadPlayerBank();
    }
  }

  return {
    loadPlayerBank,
    playerSetPin,
    playerTransfer,
    selectPlayerBankScope,
  };
}
