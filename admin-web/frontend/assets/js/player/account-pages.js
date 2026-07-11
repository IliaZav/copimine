function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[char] || char));
}

function reportTone(status = "") {
  switch (String(status || "").trim().toLowerCase()) {
    case "closed":
    case "resolved":
    case "approved":
      return "good";
    case "rejected":
    case "declined":
      return "bad";
    case "in_progress":
    case "review":
      return "warn";
    default:
      return "neutral";
  }
}

function renderReportList(reports = [], dt) {
  if (!Array.isArray(reports) || !reports.length) {
    return `
      <div class="empty-state compact">
        <strong>Пока пусто</strong>
        <span>Как только ты отправишь обращение, оно появится здесь со статусом и ответом администрации.</span>
      </div>
    `;
  }
  return `
    <div class="stack-list">
      ${reports.map((report) => `
        <article class="stack-card report-card tone-${reportTone(report.status)}">
          <header class="stack-card-head">
            <div>
              <strong>${esc(report.target || "Общее обращение")}</strong>
              <span>${esc(report.severity || "normal")} · ${esc(report.status || "open")}</span>
            </div>
            <time datetime="${esc(report.createdAt || "")}">${esc(dt(report.updatedAt || report.createdAt))}</time>
          </header>
          <p>${esc(report.message || "")}</p>
          ${report.errorCode ? `<div class="report-response"><strong>Код ошибки:</strong> ${esc(report.errorCode)}</div>` : ""}
          ${report.errorSummary ? `<div class="report-response muted"><strong>Сводка:</strong> ${esc(report.errorSummary)}</div>` : ""}
          ${report.lastResponse ? `<div class="report-response"><strong>Ответ:</strong> ${esc(report.lastResponse)}</div>` : ""}
          ${report.lastReason ? `<div class="report-response muted"><strong>Комментарий:</strong> ${esc(report.lastReason)}</div>` : ""}
        </article>
      `).join("")}
    </div>
  `;
}

export function createPlayerAccountPages(deps) {
  const {
    $,
    state,
    setLoading,
    setView,
    panel,
    metric,
    kv,
    safetyRail,
    safeApi,
    api,
    toast,
    dt,
    bankPinState,
  } = deps;

  async function reloadSettingsData() {
    const [me, bank, reports] = await Promise.all([
      safeApi("/api/player/me", { account: state.user || {} }),
      safeApi("/api/player/bank", { account: null, pin: {}, ledger: [] }),
      safeApi("/api/player/reports", { reports: [] }),
    ]);
    return {
      account: me.account || state.user || {},
      bank,
      reports: reports.reports || [],
    };
  }

  window.submitPlayerSupportReport = async () => {
    const target = $("playerSupportTarget")?.value?.trim() || "";
    const world = $("playerSupportWorld")?.value?.trim() || "";
    const severity = $("playerSupportSeverity")?.value || "normal";
    const message = $("playerSupportMessage")?.value?.trim() || "";
    if (message.length < 8) {
      toast("Опиши проблему хотя бы одной понятной фразой.", true);
      return;
    }
    try {
      await api("/api/player/reports", {
        method: "POST",
        body: JSON.stringify({
          target,
          world,
          severity,
          message,
          metadata: {
            route: state.tab || "settings",
            page: "player-settings",
          },
        }),
      });
      toast("Обращение отправлено.");
      await loadPlayerSettings();
    } catch (error) {
      toast(error.message || "Не удалось отправить обращение.", true);
    }
  };

  window.changePlayerUsername = async () => {
    const newUsername = $("playerSettingsUsername")?.value?.trim() || "";
    const currentPassword = $("playerSettingsUsernamePassword")?.value || "";
    if (newUsername.length < 3) {
      toast("Новый логин слишком короткий.", true);
      return;
    }
    if (!currentPassword) {
      toast("Нужен текущий пароль.", true);
      return;
    }
    try {
      const result = await api("/api/player/account/username", {
        method: "POST",
        body: JSON.stringify({
          new_username: newUsername,
          current_password: currentPassword,
        }),
      });
      state.user = result.account || state.user;
      toast("Логин обновлён.");
      await loadPlayerSettings();
    } catch (error) {
      toast(error.message || "Не удалось изменить логин.", true);
    }
  };

  window.changePlayerPassword = async () => {
    const currentPassword = $("playerSettingsCurrentPassword")?.value || "";
    const newPassword = $("playerSettingsNewPassword")?.value || "";
    if (!currentPassword || !newPassword) {
      toast("Заполни текущий и новый пароль.", true);
      return;
    }
    try {
      await api("/api/player/account/password", {
        method: "POST",
        body: JSON.stringify({
          current_password: currentPassword,
          new_password: newPassword,
        }),
      });
      toast("Пароль обновлён.");
      if ($("playerSettingsCurrentPassword")) $("playerSettingsCurrentPassword").value = "";
      if ($("playerSettingsNewPassword")) $("playerSettingsNewPassword").value = "";
    } catch (error) {
      toast(error.message || "Не удалось изменить пароль.", true);
    }
  };

  window.playerSettingsPinUpdate = async () => {
    const oldPin = $("playerSettingsOldPin")?.value || "";
    const newPin = $("playerSettingsNewPin")?.value || "";
    if (!newPin) {
      toast("Укажи новый PIN.", true);
      return;
    }
    try {
      await api("/api/player/bank/pin", {
        method: "POST",
        body: JSON.stringify({
          old_pin: oldPin,
          new_pin: newPin,
          account_scope: "PERSONAL",
        }),
      });
      toast("PIN обновлён.");
      if ($("playerSettingsOldPin")) $("playerSettingsOldPin").value = "";
      if ($("playerSettingsNewPin")) $("playerSettingsNewPin").value = "";
      await loadPlayerSettings();
    } catch (error) {
      toast(error.message || "Не удалось изменить PIN.", true);
    }
  };

  async function loadPlayerSettings() {
    setLoading("Загружаю настройки");
    const { account, bank, reports } = await reloadSettingsData();
    const pin = bank.pin || {};
    setView(`
      <section class="layout-grid grid-4">
        ${metric("Логин", account.username || "игрок", account.minecraftName || "Minecraft не привязан", "good")}
        ${metric("Связь", account.linked ? "есть" : "нет", account.linked ? "банк и покупки открыты" : "нужно привязать аккаунт", account.linked ? "good" : "warn")}
        ${metric("PIN", bankPinState(pin), pin.locked ? "есть блокировка" : "для переводов и покупок", pin.set ? "good" : "warn")}
        ${metric("Репорты", Array.isArray(reports) ? reports.length : 0, "история обращений", "neutral")}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Профиль", "Основные данные аккаунта и привязки.", kv([
          ["Логин", account.username || "игрок"],
          ["Minecraft-ник", account.minecraftName || "не привязан"],
          ["Создан", dt(account.createdAt)],
          ["Последний вход", dt(account.lastLoginAt)],
        ]), `<button class="btn btn-secondary" data-click="setTab('link')">Открыть привязку Minecraft</button>`)}
        ${panel("Состояние кабинета", "Коротко по ключевым зонам личного кабинета.", safetyRail([
          ["Привязка", account.minecraftName ? "подключена" : "ожидается", account.minecraftName ? "good" : "warn"],
          ["Переводы", pin.set ? "доступны после ввода PIN" : "нужно задать PIN", pin.set ? "good" : "warn"],
          ["Покупки", account.linked ? "AR и donation доступны" : "сначала привяжи Minecraft", account.linked ? "good" : "warn"],
          ["Поддержка", "репорт можно отправить прямо с сайта", "good"],
        ]))}
      </section>
      <section class="layout-grid grid-2">
        ${panel("Сменить логин", "Логин сайта меняется только после подтверждения текущим паролем.", `
          <div class="form-grid">
            <input id="playerSettingsUsername" value="${esc(account.username || "")}" placeholder="Новый логин" />
            <input id="playerSettingsUsernamePassword" type="password" placeholder="Текущий пароль" />
            <button class="btn btn-primary full" data-click="changePlayerUsername()">Сохранить логин</button>
          </div>
        `)}
        ${panel("Сменить пароль", "Пароль сайта не связан с паролем Minecraft.", `
          <div class="form-grid">
            <input id="playerSettingsCurrentPassword" type="password" placeholder="Текущий пароль" />
            <input id="playerSettingsNewPassword" type="password" placeholder="Новый пароль" />
            <button class="btn btn-primary full" data-click="changePlayerPassword()">Сохранить пароль</button>
          </div>
        `)}
      </section>
      <section class="layout-grid grid-2">
        ${panel("PIN банка", "Нужен для переводов AR и покупок за AR.", `
          <div class="form-grid">
            <input id="playerSettingsOldPin" type="password" inputmode="numeric" placeholder="${pin.set ? "Текущий PIN" : "Если есть временный PIN"}" />
            <input id="playerSettingsNewPin" type="password" inputmode="numeric" placeholder="Новый PIN, 4-8 цифр" />
            <button class="btn btn-primary full" data-click="playerSettingsPinUpdate()">Сохранить PIN</button>
          </div>
          <div class="spacer-12"></div>
          ${kv([
            ["Статус", bankPinState(pin)],
            ["Задан", Boolean(pin.set)],
            ["Заблокирован", Boolean(pin.locked)],
            ["Нужна замена", Boolean(pin.mustChange)],
          ])}
        `, `<button class="btn btn-secondary" data-click="setTab('transfer')">Открыть вкладку перевода</button>`)}
        ${panel("Сообщить о проблеме", "Если что-то пошло не так прямо на сайте, отправь репорт отсюда.", `
          <div class="form-grid">
            <input id="playerSupportTarget" placeholder="Что сломалось или кого касается" />
            <input id="playerSupportWorld" placeholder="Мир / раздел / страница" value="site-settings" />
            <select id="playerSupportSeverity">
              <option value="normal">Обычное</option>
              <option value="high">Срочное</option>
              <option value="critical">Критичное</option>
            </select>
            <textarea id="playerSupportMessage" class="full" placeholder="Опиши, что сделал, что ожидал и что получил."></textarea>
            <button class="btn btn-primary full" data-click="submitPlayerSupportReport()">Отправить репорт</button>
          </div>
        `)}
      </section>
      <section class="layout-grid grid-1">
        ${panel("Мои обращения", "История по этому аккаунту. Здесь же будут ответы администрации.", renderReportList(reports, dt))}
      </section>
    `);
  }

  async function loadPlayerSecurity() {
    return loadPlayerSettings();
  }

  async function loadPlayerSupport() {
    return loadPlayerSettings();
  }

  return {
    loadPlayerSettings,
    loadPlayerSecurity,
    loadPlayerSupport,
  };
}
