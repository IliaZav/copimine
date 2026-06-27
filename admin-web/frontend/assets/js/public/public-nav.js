function setExpanded(button, expanded) {
  button.setAttribute("aria-expanded", expanded ? "true" : "false");
}

function createToggleButton() {
  const button = document.createElement("button");
  button.id = "mobileNavToggle";
  button.type = "button";
  button.className = "btn icon-btn mobile-only hidden public-mobile-toggle";
  button.setAttribute("aria-label", "Открыть меню");
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

  let toggle = shell.querySelector("#mobileNavToggle");
  if (!(toggle instanceof HTMLButtonElement)) {
    toggle = createToggleButton();
    brand.insertAdjacentElement("afterend", toggle);
  } else {
    toggle.classList.add("public-mobile-toggle");
    toggle.textContent = "\u2630";
    toggle.setAttribute("aria-label", "Открыть меню");
    setExpanded(toggle, false);
  }

  const media = window.matchMedia("(max-width: 720px)");

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
