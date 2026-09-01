const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const FINE_POINTER_QUERY = "(pointer: fine)";

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

const REVEAL_SELECTORS = [
  ".public-hero",
  ".news-hero",
  ".public-page-shell > .public-section",
  ".public-page-shell > .patch-page",
  ".public-section .launcher-feature-card",
  ".public-section .launcher-step",
  ".public-section .launcher-faq-card",
  ".public-section .news-card",
  ".public-section .launcher-system-card",
  ".public-section .hero-side-card",
  ".public-section .public-panel"
];

function updateScrollState(page) {
  const documentElement = document.documentElement;
  const scrollableHeight = Math.max(1, documentElement.scrollHeight - window.innerHeight);
  const progress = clamp(window.scrollY / scrollableHeight, 0, 1);
  page.style.setProperty("--scroll-progress", progress.toFixed(3));
  page.dataset.scrollState = "ready";
}

function initScrollState(page) {
  let scrollFrame = 0;
  const schedule = () => {
    if (scrollFrame) return;
    scrollFrame = window.requestAnimationFrame(() => {
      scrollFrame = 0;
      updateScrollState(page);
    });
  };

  updateScrollState(page);
  window.addEventListener("scroll", schedule, { passive: true });
  window.addEventListener("resize", schedule, { passive: true });
}

function initScrollReveal(page, reducedMotion) {
  let sequence = 0;
  let observer;

  const show = (node) => node.classList.add("is-visible");
  const mark = () => {
    const nodes = Array.from(page.querySelectorAll(REVEAL_SELECTORS.join(",")))
      .filter((node) => !node.hasAttribute("data-motion-reveal"));
    if (!nodes.length) return;

    page.dataset.motionReveal = "ready";
    nodes.forEach((node) => {
      node.setAttribute("data-motion-reveal", "");
      node.style.setProperty("--motion-delay", `${Math.min(sequence * 42, 252)}ms`);
      sequence += 1;
      if (reducedMotion || !("IntersectionObserver" in window)) {
        show(node);
      } else {
        observer.observe(node);
      }
    });
  };

  if (!reducedMotion && "IntersectionObserver" in window) {
    observer = new window.IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        show(entry.target);
        observer.unobserve(entry.target);
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
  }

  mark();
  const mutations = new window.MutationObserver(mark);
  mutations.observe(page, { childList: true, subtree: true });
}

export function initPublicMotion() {
  const page = document.querySelector(".public-site");
  if (!(page instanceof HTMLElement)) return;
  if (page.dataset.motionInitialized === "true") return;
  page.dataset.motionInitialized = "true";

  const reducedMotion = window.matchMedia?.(REDUCED_MOTION_QUERY)?.matches === true;
  const finePointer = window.matchMedia?.(FINE_POINTER_QUERY)?.matches === true;
  initScrollState(page);
  initScrollReveal(page, reducedMotion);
  if (reducedMotion || !finePointer) {
    page.dataset.copimineMotion = reducedMotion ? "reduced" : "off";
    return;
  }

  page.dataset.copimineMotion = "on";
  let frame = 0;
  let targetX = 0;
  let targetY = 0;
  let currentX = 0;
  let currentY = 0;

  const draw = () => {
    frame = 0;
    currentX += (targetX - currentX) * .18;
    currentY += (targetY - currentY) * .18;
    page.style.setProperty("--scene-x", currentX.toFixed(3));
    page.style.setProperty("--scene-y", currentY.toFixed(3));
    if (Math.abs(targetX - currentX) > .002 || Math.abs(targetY - currentY) > .002) {
      frame = window.requestAnimationFrame(draw);
    }
  };

  page.addEventListener("pointermove", (event) => {
    const rect = page.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    targetX = clamp(((event.clientX - rect.left) / rect.width - .5) * 2, -1, 1);
    targetY = clamp(((event.clientY - rect.top) / rect.height - .5) * 2, -1, 1);
    if (!frame) frame = window.requestAnimationFrame(draw);
  }, { passive: true });

  page.addEventListener("pointerleave", () => {
    targetX = 0;
    targetY = 0;
    if (!frame) frame = window.requestAnimationFrame(draw);
  }, { passive: true });
}
