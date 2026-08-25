const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
const FINE_POINTER_QUERY = "(pointer: fine)";

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

export function initPublicMotion() {
  const page = document.querySelector(".public-site");
  if (!(page instanceof HTMLElement)) return;

  const reducedMotion = window.matchMedia?.(REDUCED_MOTION_QUERY)?.matches === true;
  const finePointer = window.matchMedia?.(FINE_POINTER_QUERY)?.matches === true;
  if (reducedMotion || !finePointer) return;

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
