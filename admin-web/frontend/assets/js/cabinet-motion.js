const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

export function initCabinetMotion() {
  const workspace = document.querySelector(".workspace");
  if (!(workspace instanceof HTMLElement)) return;
  if (workspace.dataset.motionInitialized === "true") return;
  workspace.dataset.motionInitialized = "true";

  const reducedMotion = window.matchMedia?.(REDUCED_MOTION_QUERY)?.matches === true;
  const canObserve = "IntersectionObserver" in window && !reducedMotion;
  let sequence = 0;
  let observer;

  const show = (node) => node.classList.add("is-visible");
  const mark = () => {
    const nodes = Array.from(workspace.querySelectorAll(".topbar, .view > *"))
      .filter((node) => !node.hasAttribute("data-cabinet-reveal"));
    if (!nodes.length) return;

    workspace.dataset.cabinetMotion = "ready";
    nodes.forEach((node) => {
      node.setAttribute("data-cabinet-reveal", "");
      node.style.setProperty("--cabinet-motion-delay", `${Math.min(sequence * 42, 252)}ms`);
      sequence += 1;
      if (!canObserve) show(node);
      else observer.observe(node);
    });
  };

  if (canObserve) {
    observer = new window.IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        show(entry.target);
        observer.unobserve(entry.target);
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
  }

  let scrollFrame = 0;
  const updateScrollState = () => {
    const documentElement = document.documentElement;
    const scrollableHeight = Math.max(1, documentElement.scrollHeight - window.innerHeight);
    const progress = clamp(window.scrollY / scrollableHeight, 0, 1);
    workspace.style.setProperty("--cabinet-scroll-progress", progress.toFixed(3));
    workspace.dataset.scrollState = "ready";
  };
  const scheduleScrollState = () => {
    if (scrollFrame) return;
    scrollFrame = window.requestAnimationFrame(() => {
      scrollFrame = 0;
      updateScrollState();
    });
  };

  updateScrollState();
  window.addEventListener("scroll", scheduleScrollState, { passive: true });
  window.addEventListener("resize", scheduleScrollState, { passive: true });
  mark();

  const mutations = new window.MutationObserver(mark);
  mutations.observe(workspace, { childList: true, subtree: true });
  workspace.dataset.cabinetMotion = reducedMotion ? "reduced" : "ready";
}
