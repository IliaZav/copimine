import { useEffect, useRef } from "react";

type Particle = { x: number; y: number; speed: number; length: number; alpha: number };
type Glow = { x: number; y: number; speed: number; radius: number; alpha: number };
type RisingLinesProps = { className?: string; particles?: number; color?: string; opacity?: number };

const seededRandom = (seed: number) => {
  let value = seed >>> 0;
  return () => {
    value = (value + 0x6d2b79f5) >>> 0;
    let next = value;
    next = Math.imul(next ^ (next >>> 15), next | 1);
    next ^= next + Math.imul(next ^ (next >>> 7), next | 61);
    return ((next ^ (next >>> 14)) >>> 0) / 4294967296;
  };
};

export function RisingLines({ className = "", particles = 74, color = "#38dda4", opacity = 0.26 }: RisingLinesProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    if (!container || !canvas) return;
    const context = canvas.getContext("2d", { alpha: true });
    if (!context) return;
    const surface = container;
    const output = canvas;
    const ctx = context;

    let frame = 0;
    let lastTime = 0;
    let lastDraw = 0;
    let width = 1;
    let height = 1;
    let dpr = 1;
    let visible = true;
    const rng = seededRandom(0xc0ffee);
    let points: Particle[] = [];
    let glows: Glow[] = [];
    const mediaQuery = window.matchMedia("(prefers-reduced-motion: reduce)");
    const hex = color.replace(/^#/, "");
    const normalizedHex = hex.length === 3 ? hex.split("").map((part) => part + part).join("") : hex;
    const colors = /^[0-9a-f]{6}$/i.test(normalizedHex)
      ? normalizedHex.match(/.{2}/g)?.map((value) => parseInt(value, 16)) ?? [56, 221, 164]
      : [56, 221, 164];
    const rgb = colors[0] + "," + colors[1] + "," + colors[2];
    let reduced = motionIsReduced();

    function motionIsReduced() {
      return mediaQuery.matches
        || document.documentElement.dataset.motion === "off"
        || window.CM?.state?.preferences?.motion === false;
    }

    function fadeAt(y: number) {
      const progress = 1 - y / height;
      return Math.max(0, Math.min(1, Math.min(progress * 3, (1 - progress) * 2.2)));
    }

    function draw(delta: number) {
      ctx.clearRect(0, 0, width, height);
      ctx.globalCompositeOperation = "lighter";
      const dt = Math.min(0.04, Math.max(0, delta));

      for (const point of points) {
        if (!reduced) point.y -= point.speed * dt;
        if (!reduced && point.y + point.length < 0) {
          point.x = rng() * width;
          point.y = height + rng() * 24;
          point.speed = 8 + rng() * 20;
          point.length = 12 + Math.pow(rng(), 0.7) * 34;
          point.alpha = 0.25 + rng() * 0.75;
        }

        const alpha = opacity * point.alpha * fadeAt(point.y);
        if (alpha < 0.005) continue;
        const gradient = ctx.createLinearGradient(point.x, point.y, point.x, point.y + point.length);
        gradient.addColorStop(0, "rgba(" + rgb + ",0)");
        gradient.addColorStop(0.72, "rgba(" + rgb + "," + alpha + ")");
        gradient.addColorStop(1, "rgba(" + rgb + "," + alpha + ")");
        ctx.fillStyle = gradient;
        ctx.fillRect(Math.floor(point.x), Math.floor(point.y), 1, point.length);
      }

      for (const glow of glows) {
        if (!reduced) glow.y -= glow.speed * dt;
        if (!reduced && glow.y + glow.radius * 2 < 0) {
          glow.x = rng() * width;
          glow.y = height + rng() * 32;
          glow.speed = 5 + rng() * 14;
          glow.radius = 1.5 + rng() * 3.5;
          glow.alpha = 0.12 + rng() * 0.42;
        }
        const alpha = Math.min(0.18, opacity * glow.alpha * fadeAt(glow.y));
        if (alpha < 0.006) continue;
        const radius = glow.radius * 3.5;
        const gradient = ctx.createRadialGradient(glow.x, glow.y, 0, glow.x, glow.y, radius);
        gradient.addColorStop(0, "rgba(" + rgb + "," + alpha + ")");
        gradient.addColorStop(0.4, "rgba(" + rgb + "," + alpha * 0.45 + ")");
        gradient.addColorStop(1, "rgba(" + rgb + ",0)");
        ctx.fillStyle = gradient;
        ctx.fillRect(glow.x - radius, glow.y - radius, radius * 2, radius * 2);
      }

      ctx.globalCompositeOperation = "source-over";
    }

    function shouldAnimate() {
      return !reduced && visible && !document.hidden;
    }

    function tick(time: number) {
      frame = 0;
      if (!shouldAnimate()) return;
      if (!lastDraw || time - lastDraw >= 1000 / 30) {
        const delta = lastTime ? (time - lastTime) / 1000 : 1 / 30;
        lastTime = time;
        lastDraw = time;
        draw(delta);
      }
      frame = requestAnimationFrame(tick);
    }

    function updateLoop() {
      if (shouldAnimate()) {
        if (!frame) {
          lastTime = 0;
          lastDraw = 0;
          frame = requestAnimationFrame(tick);
        }
        return;
      }
      if (frame) cancelAnimationFrame(frame);
      frame = 0;
      lastTime = 0;
      draw(0);
    }

    function resize() {
      const rect = surface.getBoundingClientRect();
      width = Math.max(1, Math.floor(rect.width));
      height = Math.max(1, Math.floor(rect.height));
      dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      output.width = Math.floor(width * dpr);
      output.height = Math.floor(height * dpr);
      output.style.width = width + "px";
      output.style.height = height + "px";
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      const count = particles > 0
        ? Math.min(300, Math.max(24, Math.round(particles * width * height / (800 * 400))))
        : 0;
      points = Array.from({ length: count }, () => ({
        x: rng() * width,
        y: rng() * height,
        speed: 8 + rng() * 20,
        length: 12 + Math.pow(rng(), 0.7) * 34,
        alpha: 0.25 + rng() * 0.75,
      }));
      glows = Array.from({ length: Math.min(48, Math.round(count * 0.16)) }, () => ({
        x: rng() * width,
        y: rng() * height,
        speed: 5 + rng() * 14,
        radius: 1.5 + rng() * 3.5,
        alpha: 0.12 + rng() * 0.42,
      }));
      updateLoop();
    }

    function syncMotion() {
      const nextReduced = motionIsReduced();
      if (nextReduced === reduced) return;
      reduced = nextReduced;
      updateLoop();
    }

    function syncVisibility() {
      updateLoop();
    }

    const resizeObserver = new ResizeObserver(resize);
    const motionObserver = new MutationObserver(syncMotion);
    const visibilityObserver = "IntersectionObserver" in window
      ? new IntersectionObserver((entries) => {
          visible = entries[0]?.isIntersecting ?? true;
          updateLoop();
        }, { rootMargin: "80px" })
      : null;
    resizeObserver.observe(surface);
    motionObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["data-motion"] });
    visibilityObserver?.observe(surface);
    mediaQuery.addEventListener("change", syncMotion);
    document.addEventListener("visibilitychange", syncVisibility);
    resize();

    return () => {
      resizeObserver.disconnect();
      motionObserver.disconnect();
      visibilityObserver?.disconnect();
      mediaQuery.removeEventListener("change", syncMotion);
      document.removeEventListener("visibilitychange", syncVisibility);
      if (frame) cancelAnimationFrame(frame);
      ctx.clearRect(0, 0, width, height);
    };
  }, [color, opacity, particles]);

  return <div ref={containerRef} className={"cm-react-rising-lines " + className} aria-hidden="true"><canvas ref={canvasRef} /></div>;
}
