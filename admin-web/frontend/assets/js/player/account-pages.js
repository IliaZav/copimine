export function createPlayerAccountPages(deps) {
  const {
    state,
    setLoading,
    setView,
    panel,
    kv,
    safetyRail,
    safeApi,
    dt,
    bankPinState,
  } = deps;

  async function loadPlayerSettings() {
    setLoading("Загрузка настроек");
    const me = await safeApi("/api/player/me", { account: state.user || {} });
    const account = me.account || state.user || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Профиль", "Аккаунт, Minecraft и email.", kv([
          ["Логин", account.username || "игрок"],
          ["Minecraft-ник", account.minecraftName || "не привязан"],
          ["Email", account.email || "не указан"],
          ["Создан", dt(account.createdAt)]
        ]), `<button class="btn btn-secondary" data-click="setTab('link')">Настроить Minecraft</button>`)}
        ${panel("Разделы кабинета", "Основные разделы.", safetyRail([
          ["Привязка", "Minecraft-ник", account.minecraftName ? "good" : "warn"],
          ["Банк", "AR и PIN", "neutral"],
          ["История", "Переводы и покупки", "good"]
        ]))}
      </section>
    `);
  }

  async function loadPlayerSecurity() {
    setLoading("Загрузка безопасности");
    const bank = await safeApi("/api/player/bank", { pin: {}, ledger: [] });
    const pin = bank.pin || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("PIN банка", "PIN для переводов и банка.", kv([
          ["PIN задан", pin.set || false],
          ["Статус", bankPinState(pin)],
          ["Заблокирован", pin.locked || false],
          ["Нужна замена", pin.mustChange || false]
        ]), `<button class="btn btn-primary" data-click="setTab('bank')">Открыть банк</button>`)}
        ${panel("Сессии", "Текущий вход.", `
          <div class="notice">Если доступ чужой, смените пароль и PIN.</div>
          <button class="btn btn-secondary full" data-click="logout(true)">Выйти из текущей сессии</button>
        `)}
      </section>
    `);
  }

  async function loadPlayerSupport() {
    setLoading("Загрузка поддержки");
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Помощь", "Основные контакты.", safetyRail([
          ["/report в игре", "Сообщение администрации", "good"],
          ["Администрация", "Банк, привязка и спорные операции", "neutral"],
          ["PIN и пароль", "Никому не сообщай", "warn"]
        ]))}
        ${panel("Частые вопросы", "Коротко.", safetyRail([
          ["Банк", "Нужны привязка и PIN", "good"],
          ["Артефакты", "Выдача идёт в игре", "neutral"],
          ["Выдача", "Если инвентарь заполнен, выдача остаётся в очереди", "warn"]
        ]))}
      </section>
    `);
  }

  return {
    loadPlayerSettings,
    loadPlayerSecurity,
    loadPlayerSupport,
  };
}
