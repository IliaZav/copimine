import { getShopCartCount } from "./shop-cart.js";

const OPEN_MENU_LABEL = "\u041e\u0442\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e";
const CLOSE_MENU_LABEL = "\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u043c\u0435\u043d\u044e";
const CART_PATH = "/cart.html";

function createCartLink(mobile = false) {
  const link = document.createElement("a");
  link.className = mobile ? "shop-cart-button shop-cart-mobile-shortcut" : "shop-cart-button";
  link.href = CART_PATH;

  const label = document.createElement("span");
  label.textContent = "Корзина";
  const count = document.createElement("span");
  count.className = "shop-cart-count";
  count.setAttribute("aria-live", "polite");
  count.textContent = "0";
  link.append(label, count);
  return link;
}

function syncCartButtons(shell, count = getShopCartCount()) {
  const safeCount = Number.isFinite(Number(count)) ? Math.max(0, Number(count)) : 0;
  const current = window.location.pathname.endsWith(CART_PATH);
  shell.querySelectorAll(".shop-cart-count").forEach((node) => {
    node.textContent = String(safeCount);
  });
  shell.querySelectorAll(".shop-cart-button").forEach((node) => {
    node.classList.toggle("has-items", safeCount > 0);
    node.setAttribute("aria-label", safeCount ? `Корзина: ${safeCount} предмета` : "Корзина пуста");
    if (current) node.setAttribute("aria-current", "page");
    else node.removeAttribute("aria-current");
  });
}

function ensureCartShortcuts(shell, nav) {
  const cartLinks = [...shell.querySelectorAll(`a.shop-cart-button[href="${CART_PATH}"]`)].filter(
    (node) => node instanceof HTMLAnchorElement,
  );
  let desktop = cartLinks.find((node) => nav.contains(node));
  if (!(desktop instanceof HTMLAnchorElement)) {
    desktop = createCartLink();
    nav.append(desktop);
  }

  let mobile = cartLinks.find((node) => node !== desktop && node.classList.contains("shop-cart-mobile-shortcut"));
  if (!(mobile instanceof HTMLAnchorElement)) {
    mobile = createCartLink(true);
    shell.append(mobile);
  }

  // Desktop and compact headers use two presentations of the same action.
  // Keep exactly one node for each presentation and remove every stale copy
  // before syncing its state. The visibility sync below also uses inline
  // !important so an older cached stylesheet cannot show both controls.
  for (const link of cartLinks) {
    if (link !== desktop && link !== mobile) link.remove();
  }

  const media = window.matchMedia("(max-width: 1080px)");
  const syncCartVisibility = () => {
    const compact = media.matches;
    desktop.style.setProperty("display", compact ? "none" : "inline-flex", "important");
    mobile.style.setProperty("display", compact ? "inline-flex" : "none", "important");
  };
  syncCartVisibility();

  if (shell.dataset.cartBound !== "true") {
    shell.dataset.cartBound = "true";
    window.addEventListener("shopCartChanged", (event) => {
      syncCartButtons(shell, event.detail?.count);
    });
  }
  if (shell.dataset.cartVisibilityBound !== "true") {
    shell.dataset.cartVisibilityBound = "true";
    media.addEventListener("change", syncCartVisibility);
  }
  syncCartButtons(shell);
}

function setExpanded(button, expanded) {
  button.setAttribute("aria-expanded", expanded ? "true" : "false");
  button.setAttribute("aria-label", expanded ? CLOSE_MENU_LABEL : OPEN_MENU_LABEL);
}

function createToggleButton() {
  const button = document.createElement("button");
  // Keep the public header toggle separate from the cabinet sidebar toggle.
  // Reusing the same id made getElementById() bind the cabinet handler to the
  // wrong button, so every sidebar tab appeared to do nothing on mobile.
  button.id = "publicMobileNavToggle";
  button.type = "button";
  button.className = "btn icon-btn mobile-only hidden public-mobile-toggle";
  button.setAttribute("aria-label", OPEN_MENU_LABEL);
  button.setAttribute("aria-hidden", "true");
  button.tabIndex = -1;
  button.textContent = "\u2630";
  setExpanded(button, false);
  return button;
}

export function initPublicNav() {
  const shell = document.querySelector(".public-nav");
  const nav = shell?.querySelector("nav");
  const brand = shell?.querySelector(".public-brand");
  if (!(shell instanceof HTMLElement) || !(nav instanceof HTMLElement) || !(brand instanceof HTMLElement)) {
    return;
  }

  ensureCartShortcuts(shell, nav);

  // Several public pages predate this module and already contain
  // #mobileNavToggle. Reuse that element so the enhancer never adds a second
  // visually identical menu control to the compact header.
  let toggle = shell.querySelector("#publicMobileNavToggle, #mobileNavToggle, .public-mobile-toggle");
  if (!(toggle instanceof HTMLButtonElement)) {
    toggle = createToggleButton();
    brand.insertAdjacentElement("afterend", toggle);
  } else {
    toggle.id = "publicMobileNavToggle";
    toggle.classList.add("public-mobile-toggle");
    toggle.textContent = "\u2630";
    toggle.setAttribute("aria-label", OPEN_MENU_LABEL);
    setExpanded(toggle, false);
  }

  const media = window.matchMedia("(max-width: 1080px)");

  const closeMenu = () => {
    shell.classList.remove("public-nav-open");
    toggle.classList.remove("is-active");
    setExpanded(toggle, false);
  };

  const openMenu = () => {
    shell.classList.add("public-nav-open");
    toggle.classList.add("is-active");
    setExpanded(toggle, true);
  };

  const syncMode = () => {
    const compact = media.matches;
    toggle.classList.toggle("hidden", !compact);
    toggle.setAttribute("aria-hidden", compact ? "false" : "true");
    toggle.tabIndex = compact ? 0 : -1;
    if (!compact) {
      closeMenu();
    }
  };

  if (toggle.dataset.bound !== "true") {
    toggle.dataset.bound = "true";
    toggle.addEventListener("click", () => {
      if (!media.matches) return;
      if (shell.classList.contains("public-nav-open")) {
        closeMenu();
        return;
      }
      openMenu();
    });

    nav.addEventListener("click", (event) => {
      const target = event.target instanceof Element ? event.target : null;
      if (!target || !media.matches) return;
      if (target.closest("a") || target.closest("button")) {
        window.setTimeout(closeMenu, 0);
      }
    });

    document.addEventListener("click", (event) => {
      if (!media.matches) return;
      const target = event.target instanceof Element ? event.target : null;
      if (!target) return;
      if (target.closest(".public-nav")) return;
      closeMenu();
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        closeMenu();
      }
    });

    media.addEventListener("change", syncMode);
  }

  syncMode();
}
