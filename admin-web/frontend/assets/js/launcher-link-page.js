import { authLandingHref, launcherBindingHrefFromSearch } from "./shared/app-routes.js?v=20260828launcherlink3";

const statusRoot = document.getElementById("launcherLinkStatus");
const closeButton = document.getElementById("launcherLinkClose");
const safeBindingHref = launcherBindingHrefFromSearch(window.location.search || "");
const launcherReturn = safeBindingHref;
const bindingQuery = safeBindingHref ? new URL(safeBindingHref, window.location.origin).search : "";
const bindingParams = new URLSearchParams(bindingQuery);
const challengeId = bindingParams.get("launcher_challenge") || "";
const launcherCode = bindingParams.get("launcher_code") || "";
const launcherNick = bindingParams.get("launcher_nick") || "";

function setPageState(state) {
  document.body?.setAttribute("data-state", state);
}

function element(tag, className = "", text = "") {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text) node.textContent = text;
  return node;
}

function buttonLink(label, href, className = "btn btn-primary") {
  const link = element("a", className, label);
  link.href = href;
  return link;
}

function actionButton(label, className = "btn btn-primary") {
  const button = element("button", className, label);
  button.type = "button";
  return button;
}

function renderBody(...children) {
  if (!statusRoot) return;
  statusRoot.replaceChildren(...children);
  statusRoot.setAttribute("aria-busy", "false");
}

function renderLoading(message = "Проверяем вход…") {
  const loading = element("div", "launcher-link-loading");
  loading.append(element("span", "launcher-link-spinner"), element("span", "", message));
  renderBody(loading);
  setPageState("loading");
}

function renderInvalidRequest() {
  const notice = element("div", "launcher-link-notice");
  notice.append(
    element("strong", "", "Запрос привязки не найден"),
    element("span", "Откройте привязку кнопкой в Launcher ещё раз. Одноразовый запрос не принимается из адресной строки вручную.")
  );
  const actions = element("div", "launcher-link-actions");
  actions.append(buttonLink("Открыть Launcher", "/launcher.html"));
  renderBody(notice, actions);
  setPageState("error");
}

function renderGuest() {
  const notice = element("div", "launcher-link-notice");
  notice.append(
    element("strong", "", "Сначала войдите на сайт"),
    element("span", launcherNick
      ? `После входа подтвердим привязку ника ${launcherNick}. Код вводить не нужно.`
      : "После входа вернём вас сюда, чтобы подтвердить привязку. Код вводить не нужно.")
  );
  const actions = element("div", "launcher-link-actions");
  actions.append(
    buttonLink("Войти", authLandingHref("signin", launcherReturn)),
    buttonLink("Создать аккаунт", authLandingHref("register", launcherReturn), "btn btn-secondary")
  );
  renderBody(notice, actions);
  setPageState("guest");
}

function renderError(message, retry = true) {
  const notice = element("div", "launcher-link-notice");
  notice.append(
    element("strong", "launcher-link-error", "Не удалось открыть привязку"),
    element("span", "Сайт не получил ответ от сервиса аккаунтов. Повторите попытку через несколько секунд."),
    element("span", "", message || "Техническая причина не указана.")
  );
  const actions = element("div", "launcher-link-actions");
  if (retry) {
    const retryButton = actionButton("Повторить", "btn btn-primary");
    retryButton.addEventListener("click", () => void init());
    actions.append(retryButton);
  }
  actions.append(buttonLink("Вернуться к Launcher", "/launcher.html", "btn btn-secondary"));
  renderBody(notice, actions);
  setPageState("error");
}

function renderConfirmation(account) {
  const username = String(account?.username || "аккаунту сайта").trim();
  const notice = element("div", "launcher-link-notice");
  notice.append(
    element("strong", "", `Аккаунт: ${username}`),
    element("span", launcherNick
      ? `Launcher просит привязку ника ${launcherNick}. Нажмите кнопку — и связь будет подтверждена.`
      : "Launcher просит привязку к этому аккаунту. Нажмите кнопку, чтобы подтвердить связь."),
    element("span", "launcher-link-meta", "Подтверждение одноразовое и действует только для этого Launcher.")
  );
  const actions = element("div", "launcher-link-actions");
  const confirmButton = actionButton("Подтвердить привязку");
  const cancelButton = actionButton("Отмена", "btn btn-secondary");
  confirmButton.addEventListener("click", () => void confirmBinding(confirmButton, cancelButton, account));
  cancelButton.addEventListener("click", closePage);
  actions.append(confirmButton, cancelButton);
  renderBody(notice, actions);
  setPageState("confirm");
}

function readCookie(name) {
  const prefix = `${name}=`;
  return String(document.cookie || "")
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

async function requestJson(url, init = {}) {
  const headers = new Headers(init.headers || {});
  headers.set("Accept", "application/json");
  if (init.body != null && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const method = String(init.method || "GET").toUpperCase();
  const csrf = readCookie("cm_csrf");
  if (method !== "GET" && method !== "HEAD" && csrf) headers.set("X-CSRF-Token", csrf);
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, { ...init, method, headers, credentials: "include", signal: controller.signal });
    let payload = null;
    try { payload = await response.json(); } catch (_) { payload = null; }
    if (!response.ok) {
      const detail = payload?.detail || payload?.message || payload?.error?.message || `HTTP ${response.status}`;
      const error = new Error(String(detail));
      error.status = response.status;
      throw error;
    }
    return payload;
  } finally {
    window.clearTimeout(timer);
  }
}

async function refreshCsrf() {
  await requestJson(`/api/auth/csrf?_fresh=${Date.now()}`, { cache: "no-store" });
}

async function confirmBinding(confirmButton, cancelButton, account) {
  if (!challengeId || !launcherCode) return;
  confirmButton.disabled = true;
  cancelButton.disabled = true;
  confirmButton.textContent = "Подтверждаем…";
  try {
    await refreshCsrf();
    await requestJson("/api/player/launcher/link/authorize", {
      method: "POST",
      body: JSON.stringify({ challenge_id: challengeId, code: launcherCode })
    });
    const username = String(account?.username || "вашему аккаунту").trim();
    const message = `Привязка подтверждена к аккаунту ${username}. Эту страницу можно закрыть.`;
    const notice = element("div", "launcher-link-notice");
    notice.append(element("strong", "", "Готово"), element("span", "", message));
    renderBody(notice);
    setPageState("success");
    try { window.alert(message); } catch (_) { /* The in-page message remains available. */ }
    window.setTimeout(() => {
      try { window.location.href = `copimine://launcher/link?challenge=${encodeURIComponent(challengeId)}`; } catch (_) { /* polling is the fallback */ }
      window.setTimeout(closePage, 260);
    }, 80);
  } catch (error) {
    confirmButton.disabled = false;
    cancelButton.disabled = false;
    confirmButton.textContent = "Подтвердить привязку";
    renderError(error instanceof Error ? error.message : "Сервис привязки вернул ошибку.");
  }
}

function closePage() {
  try { window.close(); } catch (_) { /* A regular tab may refuse to close itself. */ }
  window.setTimeout(() => {
    if (!document.hidden) window.location.href = "/launcher.html";
  }, 160);
}

async function init() {
  if (!safeBindingHref || !challengeId || !launcherCode) {
    renderInvalidRequest();
    return;
  }
  renderLoading();
  try {
    const response = await requestJson("/api/player/me");
    renderConfirmation(response?.account || {});
  } catch (error) {
    if (error?.status === 401) {
      renderGuest();
      return;
    }
    renderError(error instanceof Error ? error.message : "Неизвестная ошибка.");
  }
}

closeButton?.addEventListener("click", closePage);
void init();
