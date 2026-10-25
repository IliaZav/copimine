import { animate as anime } from "animejs";
import { animate as motionAnimate } from "motion";

let revealObserver;

function allowed(enabled = true) {
  return enabled && !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function route(node, enabled) {
  if (!node) return;
  if (!allowed(enabled)) {
    node.style.opacity = "";
    node.style.transform = "";
    return;
  }
  anime(node, {
    opacity: [0, 1],
    translateY: [8, 0],
    duration: 360,
    ease: "outCubic"
  });
}

function heroAtmosphere(root) {
  if (!root) return () => {};
  const photos = Array.from(root.querySelectorAll("[data-hero-frame]"));
  const risingLines = root.querySelector(".cm-react-hero-lines");
  const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
  let cameraAnimation = null;
  let lightAnimation = null;
  let frameAnimations = [];
  let frameTimer = 0;
  let activeFrame = Math.max(0, photos.findIndex((photo) => photo.dataset.active === "true"));
  let heroVisible = true;

  function stop() {
    window.clearTimeout(frameTimer);
    frameTimer = 0;
    cameraAnimation?.cancel();
    cameraAnimation = null;
    lightAnimation?.cancel();
    lightAnimation = null;
    frameAnimations.forEach((animation) => animation.cancel());
    frameAnimations = [];
    photos.forEach((photo, index) => {
      photo.dataset.active = String(index === activeFrame);
      photo.style.opacity = "";
      photo.style.transform = "";
    });
    if (risingLines) risingLines.style.opacity = "";
  }

  function motionEnabled() {
    return !mediaQuery.matches
      && document.documentElement.dataset.motion !== "off";
  }

  function advanceFrame() {
    frameTimer = 0;
    if (!motionEnabled() || root.dataset.heroPaused === "true" || !heroVisible || document.hidden || photos.length < 2) {
      sync();
      return;
    }

    const previousIndex = activeFrame;
    activeFrame = (activeFrame + 1) % photos.length;
    const previous = photos[previousIndex];
    const next = photos[activeFrame];
    cameraAnimation?.cancel();
    cameraAnimation = null;
    photos.forEach((photo, index) => { photo.dataset.active = String(index === activeFrame); });
    frameAnimations.forEach((animation) => animation.cancel());
    frameAnimations = [
      anime(previous, { opacity: [1, 0], duration: 1250, ease: "inOutSine" }),
      anime(next, { opacity: [0, 1], duration: 1750, ease: "inOutSine" })
    ];
    cameraAnimation = anime(next, {
      scale: [1.025, 1.12],
      translateX: [0, -30],
      translateY: [0, -18],
      duration: 12500,
      ease: "inOutSine",
      alternate: true,
      loop: true
    });
    frameTimer = window.setTimeout(advanceFrame, 7600);
  }

  function sync() {
    if (!motionEnabled()) {
      stop();
      return;
    }
    if (!lightAnimation && risingLines) lightAnimation = anime(risingLines, {
      opacity: [0.72, 1],
      duration: 4600,
      ease: "inOutSine",
      alternate: true,
      loop: true,
      delay: 900
    });
    const shouldPlay = heroVisible && !document.hidden && root.dataset.heroPaused !== "true";
    if (shouldPlay) {
      if (!cameraAnimation && photos.length) cameraAnimation = anime(photos[activeFrame], {
        scale: [1.025, 1.12],
        translateX: [0, -30],
        translateY: [0, -18],
        duration: 12500,
        ease: "inOutSine",
        alternate: true,
        loop: true
      });
      cameraAnimation?.resume();
      lightAnimation?.resume();
      frameAnimations.forEach((animation) => animation.resume());
      if (!frameTimer && photos.length > 1) frameTimer = window.setTimeout(advanceFrame, 3200);
    } else {
      window.clearTimeout(frameTimer);
      frameTimer = 0;
      cameraAnimation?.pause();
      lightAnimation?.pause();
      frameAnimations.forEach((animation) => animation.pause());
    }
  }

  const visibilityObserver = "IntersectionObserver" in window
    ? new IntersectionObserver((entries) => {
        heroVisible = entries[0]?.isIntersecting ?? true;
        sync();
      }, { rootMargin: "80px" })
    : null;
  const preferenceObserver = new MutationObserver(sync);
  preferenceObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-motion"] });
  preferenceObserver.observe(root, { attributes: true, attributeFilter: ["data-hero-paused"] });
  visibilityObserver?.observe(root);
  mediaQuery.addEventListener("change", sync);
  document.addEventListener("visibilitychange", sync);
  sync();

  return () => {
    preferenceObserver.disconnect();
    visibilityObserver?.disconnect();
    mediaQuery.removeEventListener("change", sync);
    document.removeEventListener("visibilitychange", sync);
    stop();
  };
}

function reveal(nodes, enabled) {
  revealObserver?.disconnect();
  revealObserver = undefined;
  const items = Array.from(nodes || []);
  if (!items.length || !allowed(enabled) || !("IntersectionObserver" in window)) return;

  let order = 0;
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      revealObserver.unobserve(entry.target);
      anime(entry.target, {
        opacity: [0, 1],
        translateY: [16, 0],
        duration: 520,
        delay: Math.min(order++ * 45, 180),
        ease: "outCubic"
      });
    });
  }, { threshold: 0.08 });

  items.forEach((item) => {
    item.style.opacity = "0";
    item.style.transform = "translateY(16px)";
    revealObserver.observe(item);
  });
}

function auth(root, enabled) {
  if (!root) return;
  const fields = Array.from(root.querySelectorAll(".v7-auth-main form > .field, .v7-auth-main form > .check-row, .v7-auth-main form > .btn, .v7-auth-bottom"));
  const image = root.querySelector(".v7-auth-aside img");
  if (!allowed(enabled)) {
    root.style.opacity = "";
    root.style.transform = "";
    fields.forEach((field) => { field.style.opacity = ""; field.style.transform = ""; });
    if (image) image.style.transform = "";
    return;
  }
  anime(root, { opacity: [0, 1], translateY: [18, 0], duration: 520, ease: "outCubic" });
  if (image) anime(image, { scale: [1.045, 1], duration: 900, ease: "outCubic" });
  fields.forEach((field, index) => anime(field, {
    opacity: [0, 1],
    translateY: [10, 0],
    delay: 90 + index * 42,
    duration: 340,
    ease: "outCubic"
  }));
}

function linkGate(root, enabled) {
  if (!root) return;
  const intro = Array.from(root.querySelectorAll("[data-link-enter]"));
  const panel = root.querySelector("[data-link-panel]");
  const nodes = Array.from(root.querySelectorAll(".cm-link-gate-node"));
  const path = root.querySelector(".cm-link-gate-identity path");
  if (!allowed(enabled)) return;

  anime(root, { opacity: [0, 1], duration: 460, ease: "outCubic" });
  intro.forEach((node, index) => anime(node, {
    opacity: [0, 1],
    translateY: [16, 0],
    delay: 70 + index * 90,
    duration: 460,
    ease: "outCubic"
  }));
  if (panel) anime(panel, {
    opacity: [0, 1],
    translateY: [22, 0],
    scale: [0.985, 1],
    delay: 160,
    duration: 600,
    ease: "outCubic"
  });
  nodes.forEach((node, index) => anime(node, {
    scale: [0.84, 1],
    delay: 250 + index * 80,
    duration: 620,
    ease: "outBack(1.6)"
  }));
  if (path) anime(path, { strokeDashoffset: [172, 0], delay: 260, duration: 700, ease: "outCubic" });
}

function linkStep(panel, enabled) {
  if (!panel || !allowed(enabled)) return;
  const fields = Array.from(panel.querySelectorAll(".cm-link-form > *"));
  anime(panel, { opacity: [0.78, 1], translateY: [7, 0], duration: 300, ease: "outCubic" });
  fields.forEach((field, index) => anime(field, {
    opacity: [0.45, 1],
    translateY: [7, 0],
    delay: index * 45,
    duration: 300,
    ease: "outCubic"
  }));
}

function completeLink(root, onComplete, enabled) {
  if (!root || !allowed(enabled)) {
    onComplete?.();
    return;
  }
  anime(root, {
    opacity: [1, 0],
    scale: [1, 0.985],
    duration: 380,
    ease: "inCubic",
    onComplete
  });
}

function accountReveal(root, enabled) {
  if (!root || !allowed(enabled)) return;
  const panels = Array.from(root.querySelectorAll(".v71-page-heading, .v71-account-grid > *, .v71-account-section, .v9-account-section"));
  const targets = panels.length ? panels : Array.from(root.children);
  anime(root, { opacity: [0, 1], translateY: [12, 0], duration: 520, ease: "outCubic" });
  targets.forEach((panel, index) => anime(panel, {
    opacity: [0, 1],
    translateY: [14, 0],
    delay: 100 + Math.min(index * 65, 325),
    duration: 460,
    ease: "outCubic"
  }));
}

function carousel(slides, index, enabled) {
  const items = Array.from(slides || []);
  const count = items.length;
  if (!count) return;
  const width = items[0].getBoundingClientRect().width || 640;
  const step = Math.min(Math.max(width * 0.58, 130), 480);

  items.forEach((slide, i) => {
    let offset = ((i - index + Math.floor(count / 2) + count) % count) - Math.floor(count / 2);
    if (count % 2 === 0 && offset === -count / 2) offset = count / 2;
    const distance = Math.abs(offset);
    const visible = distance <= 1;
    const x = offset * step;
    const z = distance === 0 ? 70 : -100;
    const rotateY = -offset * 22;
    const scale = distance === 0 ? 1 : 0.78;
    const opacity = distance === 0 ? 1 : visible ? 0.72 : 0;

    slide.dataset.position = String(offset);
    slide.style.zIndex = String(5 - distance);
    slide.style.pointerEvents = visible ? "auto" : "none";
    slide.setAttribute("aria-current", String(distance === 0));
    slide.setAttribute("aria-hidden", String(!visible));
    slide.tabIndex = visible ? 0 : -1;
    if ("inert" in slide) slide.inert = !visible;

    if (allowed(enabled)) {
      motionAnimate(slide, { x, z, rotateY, scale, opacity }, {
        type: "spring",
        stiffness: 260,
        damping: 29,
        mass: 0.85
      });
    } else {
      slide.style.transform = `translate3d(${x}px, 0, ${z}px) rotateY(${rotateY}deg) scale(${scale})`;
      slide.style.opacity = String(opacity);
    }
  });
}

function drag(stage, x, enabled) {
  if (!stage) return;
  if (allowed(enabled)) motionAnimate(stage, { x }, { type: "tween", duration: 0.01, ease: "linear" });
  else stage.style.transform = `translateX(${x}px)`;
}

function settle(stage, enabled) {
  if (!stage) return;
  if (allowed(enabled)) motionAnimate(stage, { x: 0 }, { type: "spring", stiffness: 260, damping: 30 });
  else stage.style.transform = "";
}

window.CMSiteMotion = Object.freeze({ route, heroAtmosphere, reveal, auth, linkGate, linkStep, completeLink, accountReveal, carousel, drag, settle });
