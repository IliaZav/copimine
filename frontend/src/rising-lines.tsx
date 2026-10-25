import { useEffect, useRef } from "react";

type Particle = { x: number; y: number; speed: number; length: number; alpha: number };
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

export function RisingLines({ className = "", particles = 72, color = "#38dda4", opacity = 0.17 }: RisingLinesProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d", { alpha: true });
    if (!container || !canvas || !context) return;

    let frame = 0;
    let lastTime = 0;
    let width = 1;
    let height = 1;
    let dpr = 1;
    const rng = seededRandom(0xc0ffee);
    const count = Math.min(180, Math.max(0, particles));
    let points: Particle[] = [];
    const colors = color.match(/[0-9a-f]{2}/gi)?.map((value) => parseInt(value, 16)) ?? [56, 221, 164];
    const rgb = colors[0] + "," + colors[1] + "," + colors[2];
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches
      || window.CM?.state?.preferences?.motion === false;

    const resize = () => {
      const rect = container.getBoundingClientRect();
      width = Math.max(1, Math.floor(rect.width));
      height = Math.max(1, Math.floor(rect.height));
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      canvas.style.width = width + "px";
      canvas.style.height = height + "px";
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      points = Array.from({ length: count }, () => ({
        x: rng() * width,
        y: rng() * height,
        speed: 6 + rng() * 17,
        length: 10 + Math.pow(rng(), 0.7) * 34,
        alpha: 0.15 + rng() * 0.55,
      }));
      if (reduced) draw(0);
    };

    const draw = (delta: number) => {
      context.clearRect(0, 0, width, height);
      context.globalCompositeOperation = "lighter";
      const dt = Math.min(0.04, Math.max(0, delta));
      for (const point of points) {
        if (!reduced) point.y -= point.speed * dt;
        if (!reduced && point.y + point.length < 0) {
          point.x = rng() * width;
          point.y = height + rng() * 24;
          point.speed = 6 + rng() * 17;
          point.length = 10 + Math.pow(rng(), 0.7) * 34;
          point.alpha = 0.15 + rng() * 0.55;
        }
        const progress = 1 - point.y / height;
        const fade = Math.max(0, Math.min(1, Math.min(progress * 3, (1 - progress) * 2.2)));
        const gradient = context.createLinearGradient(point.x, point.y, point.x, point.y + point.length);
        gradient.addColorStop(0, "rgba(" + rgb + ",0)");
        gradient.addColorStop(0.72, "rgba(" + rgb + "," + (opacity * point.alpha * fade) + ")");
        gradient.addColorStop(1, "rgba(" + rgb + "," + (opacity * point.alpha * fade) + ")");
        context.fillStyle = gradient;
        context.fillRect(Math.floor(point.x), Math.floor(point.y), 1, point.length);
      }
      context.globalCompositeOperation = "source-over";
    };

    const tick = (time: number) => {
      const delta = lastTime ? (time - lastTime) / 1000 : 0;
      lastTime = time;
      draw(delta);
      if (!reduced) frame = requestAnimationFrame(tick);
    };

    const observer = new ResizeObserver(resize);
    observer.observe(container);
    resize();
    if (!reduced) frame = requestAnimationFrame(tick);

    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
      context.clearRect(0, 0, width, height);
    };
  }, [color, opacity, particles]);

  return <div ref={containerRef} className={"cm-react-rising-lines " + className} aria-hidden="true"><canvas ref={canvasRef} /></div>;
}
