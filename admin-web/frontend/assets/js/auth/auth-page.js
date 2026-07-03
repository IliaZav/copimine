import { appRouteHref, authLandingHref, defaultAppRouteForRole } from "../shared/app-routes.js";

const CSRF_COOKIE = "cm_csrf";
const CSRF_HEADER = "X-CSRF-Token";

function $(id) {
  return document.getElementById(id);
}

function currentPageKind() {
  return String(document.body?.dataset.pageKind || "").trim().toLowerCase();
}

function currentAuthFlow() {
  return String(document.body?.dataset.authFlow || "").trim().toLowerCase() || "login";
}

function isRegisterPage() {
  return currentPageKind() === "register" || currentAuthFlow() === "register";
}

function roleHomeHref(role = "") {
  return appRouteHref(defaultAppRouteForRole(role || "player"));
}

function readCookie(name) {
  const prefix = `${String(name || "")}=`;
  return String(document.cookie || "")
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) || "";
}

function humanError(payload, fallback) {
  if (payload && typeof payload === "object") {
    const errorMessage = payload.error?.message;
    const errorDetail = payload.error?.detail;
    const detail = payload.detail;
    const message = payload.message;
    return String(errorMessage || errorDetail || detail || message || fallback || "Request failed").trim();
  }
  return String(fallback || "Request failed").trim();
}

async function requestJson(url, init = {}) {
  const headers = new Headers(init.headers || {});
  headers.set("Accept", "application/json");
  const method = String(init.method || "GET").toUpperCase();
  if (init.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (method !== "GET" && method !== "HEAD" && !headers.has(CSRF_HEADER)) {
    const csrf = readCookie(CSRF_COOKIE);
    if (csrf) {
      headers.set(CSRF_HEADER, csrf);
    }
  }
  const response = await fetch(url, {
    credentials: "same-origin",
    ...init,
    method,
    headers,
  });
  let payload = null;
  try {
    payload = await response.json();
  } catch (_error) {
    payload = null;
  }
  if (!response.ok) {
    throw new Error(humanError(payload, `HTTP ${response.status}`));
  }
  return payload;
}

async function ensureCsrfCookie() {
  await requestJson("/api/auth/csrf");
}

async function resolveExistingSession() {
  const session = await requestJson("/api/session/me");
  return {
    role: session.role || "player",
    username: session.username || session.account?.username || "",
    panel: session.kind === "panel",
  };
}

function setError(message = "") {
  const root = $("loginError");
  if (root) {
    root.textContent = String(message || "");
  }
}

function syncAuthForm() {
  const register = isRegisterPage();
  $("minecraftNameGroup")?.classList.toggle("hidden", !register);
}

function redirectToRoleHome(role = "") {
  const target = roleHomeHref(role || "player");
  window.location.replace(target);
}

async function submitAuth(event) {
  event.preventDefault();
  setError("");
  const username = String($("username")?.value || "").trim();
  const password = String($("password")?.value || "");
  if (!username || !password) {
    setError("Enter username and password.");
    return;
  }

  const register = isRegisterPage();
  const payload = { username, password };
  if (register) {
    payload.minecraft_name = String($("playerMinecraftName")?.value || "").trim();
  }

  const endpoint = register ? "/api/player/register" : "/api/session/login";
  try {
    await ensureCsrfCookie();
  } catch (_error) {
    // Backend failure will surface on the real request below.
  }

  let data;
  try {
    data = await requestJson(endpoint, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "";
    if (/csrf/i.test(message)) {
      await ensureCsrfCookie();
      data = await requestJson(endpoint, {
        method: "POST",
        body: JSON.stringify(payload),
      });
    } else {
      throw error;
    }
  }

  const role = String(data.role || "player").trim().toLowerCase() || "player";
  redirectToRoleHome(role);
}

export async function initAuthPage() {
  document.documentElement.dataset.legacyRuntime = "auth-page";
  syncAuthForm();
  try {
    await ensureCsrfCookie();
  } catch (_error) {
    // Static preview or temporarily unavailable backend should not break the page shell.
  }
  try {
    const session = await resolveExistingSession();
    if (session?.role) {
      redirectToRoleHome(session.role);
      return;
    }
  } catch (_error) {
    // No active session; keep the auth page open.
  }

  const form = $("loginForm");
  if (form instanceof HTMLFormElement && form.dataset.bound !== "true") {
    form.dataset.bound = "true";
    form.addEventListener("submit", async (event) => {
      try {
        await submitAuth(event);
      } catch (error) {
        setError(error instanceof Error ? error.message : "Login failed");
      }
    });
  }
}

export function redirectLegacyAuthRoute(hashValue = window.location.hash) {
  const route = String(hashValue || "").replace(/^#/, "").split("?", 1)[0].trim().toLowerCase();
  if (!route) return false;
  if (route === "signin") {
    window.location.replace(authLandingHref("signin"));
    return true;
  }
  if (route === "register") {
    window.location.replace(authLandingHref("register"));
    return true;
  }
  return false;
}
