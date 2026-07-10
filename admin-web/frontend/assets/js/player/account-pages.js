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
          ${report.lastResponse ? `<div class="report-response"><strong>Ответ:</strong> ${esc(report.lastResponse)}</div>` : ""}
          ${report.lastReason ? `<div class="report-response muted"><strong>Комментарий:</strong> ${esc(report.lastReason)}</div>` : ""}
        </article>
      `).join("")}
    </div>
  `;
}

export function createPlayerAccountPages(deps) {
  const {
    state,
    setLoading,
    setView,
    panel,
    kv,
    safetyRail,
    safeApi,
    api,
    toast,
    dt,
    bankPinState,
  } = deps;

  window.submitPlayerSupportReport = async () => {
    const target = document.getElementById("playerSupportTarget")?.value?.trim() || "";
    const world = document.getElementById("playerSupportWorld")?.value?.trim() || "";
    const severity = document.getElementById("playerSupportSeverity")?.value || "normal";
    const message = document.getElementById("playerSupportMessage")?.value?.trim() || "";
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
            route: state.tab || "support",
            page: "player-support",
          },
        }),
      });
      toast("Обращение отправлено.");
      await loadPlayerSupport();
    } catch (error) {
      toast(error.message || "Не удалось отправить обращение.", true);
    }
  };

  async function loadPlayerSettings() {
    setLoading("Загружаю аккаунт");
    const me = await safeApi("/api/player/me", { account: state.user || {} });
    const account = me.account || state.user || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Профиль", "Основные данные аккаунта и связанного персонажа.", kv([
          ["Логин", account.username || "игрок"],
          ["Minecraft-ник", account.minecraftName || "не привязан"],
          ["Email", account.email || "не указан"],
          ["Создан", dt(account.createdAt)],
        ]), `<button class="btn btn-secondary" data-click="setTab('link')">Открыть привязку</button>`)}
        ${panel("Состояние кабинета", "Коротко по ключевым разделам.", safetyRail([
          ["Привязка", account.minecraftName ? "подключена" : "ожидается", account.minecraftName ? "good" : "warn"],
          ["Банк", "AR, переводы и PIN", "neutral"],
          ["Поддержка", "личные обращения и ответы администрации", "good"],
        ]))}
      </section>
    `);
  }

  async function loadPlayerSecurity() {
    setLoading("Загружаю безопасность");
    const bank = await safeApi("/api/player/bank", { pin: {}, ledger: [] });
    const pin = bank.pin || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("PIN банка", "Используется для переводов и оплаты с банковского счёта.", kv([
          ["PIN задан", pin.set || false],
          ["Статус", bankPinState(pin)],
          ["Заблокирован", pin.locked || false],
          ["Нужна замена", pin.mustChange || false],
        ]), `<button class="btn btn-primary" data-click="setTab('bank')">Открыть банк</button>`)}
        ${panel("Сессия", "Если вход выполнен не тобой, сразу смени пароль и PIN.", `
          <div class="notice">Сайт хранит только текущую сессию и не показывает скрытые коды в интерфейсе.</div>
          <button class="btn btn-secondary full" data-click="logout(true)">Выйти из этой сессии</button>
        `)}
      </section>
    `);
  }

  async function loadPlayerSupport() {
    setLoading("Загружаю обращения");
    const [reports, me] = await Promise.all([
      safeApi("/api/player/reports", { reports: [] }),
      safeApi("/api/player/me", { account: state.user || {} }),
    ]);
    const account = me.account || state.user || {};
    setView(`
      <section class="layout-grid grid-2">
        ${panel("Новое обращение", "Сообщение попадёт в очередь администрации и останется в истории кабинета.", `
          <div class="form-grid">
            <input id="playerSupportTarget" placeholder="Кого касается или что сломалось" />
            <input id="playerSupportWorld" placeholder="Мир или место, если это важно" value="${esc(account.minecraftName ? "world" : "")}" />
            <select id="playerSupportSeverity">
              <option value="normal">Обычное</option>
              <option value="high">Срочное</option>
              <option value="critical">Критичное</option>
            </select>
            <textarea id="playerSupportMessage" class="full" placeholder="Опиши проблему, что уже пробовал и какой результат ожидал."></textarea>
            <button class="btn btn-primary full" data-click="submitPlayerSupportReport()">Отправить обращение</button>
          </div>
        `)}
        ${panel("Что прикладывать", "Короткий ориентир, чтобы обращение не потерялось в общей очереди.", safetyRail([
          ["Координаты", "если проблема привязана к месту", "neutral"],
          ["Ник второго игрока", "если это конфликт или перевод", "neutral"],
          ["Один понятный сценарий", "что сделал и какой итог получил", "good"],
        ]))}
      </section>
      <section class="layout-grid grid-1">
        ${panel("Мои обращения", "История по этому аккаунту. Здесь же появятся ответы администрации.", renderReportList(reports.reports || [], dt))}
      </section>
    `);
  }

  return {
    loadPlayerSettings,
    loadPlayerSecurity,
    loadPlayerSupport,
  };
}
