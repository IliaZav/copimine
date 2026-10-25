import { useEffect, useLayoutEffect, useRef, useState, type PointerEvent } from "react";

export type GallerySlide = { id?: string; title?: string; caption?: string; src?: string; alt?: string };
type CoverflowGalleryProps = {
  slides: GallerySlide[];
  startIndex?: number;
  asset: (path: string) => string;
  onOpen: (slide: GallerySlide, index: number) => void;
};

export function CoverflowGallery({ slides, startIndex = 0, asset, onOpen }: CoverflowGalleryProps) {
  const [active, setActive] = useState(startIndex);
  const startX = useRef<number | null>(null);
  const dragged = useRef(false);
  const stageRef = useRef<HTMLDivElement>(null);
  const canAnimate = window.CM?.state?.preferences?.motion !== false
    && !window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const count = slides.length;

  useEffect(() => {
    setActive((current) => count ? ((current % count) + count) % count : 0);
  }, [count]);

  useLayoutEffect(() => {
    if (!count) return;
    if (window.CM72) window.CM72.galleryIndex = active;
    const cards = document.querySelectorAll(".cm-react-carousel-slide");
    if (canAnimate && cards.length) window.CMSiteMotion?.carousel?.(cards, active, true);
  }, [active, canAnimate, count]);

  if (!count) return null;
  const move = (step: number) => setActive((index) => (index + step + count) % count);
  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    startX.current = event.clientX;
    dragged.current = false;
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (startX.current === null) return;
    const next = event.clientX - startX.current;
    if (Math.abs(next) > 7) dragged.current = true;
    if (dragged.current && stageRef.current) window.CMSiteMotion?.drag?.(stageRef.current, next, canAnimate);
  };
  const onPointerUp = (event: PointerEvent<HTMLDivElement>) => {
    if (startX.current === null) return;
    const delta = event.clientX - startX.current;
    startX.current = null;
    if (Math.abs(delta) > 48) {
      move(delta < 0 ? 1 : -1);
      dragged.current = true;
      window.setTimeout(() => { dragged.current = false; }, 0);
    } else dragged.current = false;
    if (stageRef.current) window.CMSiteMotion?.settle?.(stageRef.current, canAnimate);
  };
  const onKeyDown = (event: React.KeyboardEvent<HTMLElement>) => {
    if (event.key === "ArrowRight") { event.preventDefault(); move(1); }
    if (event.key === "ArrowLeft") { event.preventDefault(); move(-1); }
  };

  return (
    <section id="cm72-carousel" className="cm72-gallery cm72-reveal" role="region" aria-roledescription="карусель" aria-label="Скриншоты CopiMine" tabIndex={0} onKeyDown={onKeyDown}>
      <div className="cm72-section-heading">
        <div><h2>Скриншоты CopiMine</h2></div>
        <div className="cm72-gallery-controls">
          <span aria-live="polite">{String(active + 1).padStart(2, "0")} <i>/ {String(count).padStart(2, "0")}</i></span>
          {count > 1 && <>
            <button className="cm72-round" type="button" aria-label="Предыдущий кадр" onClick={() => move(-1)}><svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m14.5 5-7 7 7 7" /></svg></button>
            <button className="cm72-round" type="button" aria-label="Следующий кадр" onClick={() => move(1)}><svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m9.5 5 7 7-7 7" /></svg></button>
          </>}
        </div>
      </div>
      <div className={"cm72-gallery-stage" + (dragged.current ? " is-dragging" : "")} id="cm72-gallery-stage" ref={stageRef} aria-label="Перетаскивай кадры мышью или пальцем" onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={() => { startX.current = null; dragged.current = false; if (stageRef.current) window.CMSiteMotion?.settle?.(stageRef.current, canAnimate); }}>
        <div className="cm72-gallery-track">
          {slides.map((slide, index) => {
            let relative = index - active;
            if (relative > count / 2) relative -= count;
            if (relative < -count / 2) relative += count;
            const depth = Math.abs(relative);
            const visible = depth <= 1;
            const translate = "translate(-50%, -50%) translateX(" + relative * 200 + "px) translateZ(" + (-depth * 180) + "px) rotateY(" + (-relative * 11) + "deg) rotateZ(" + (relative * 1.4) + "deg) scale(" + Math.max(0.52, 1 - depth * 0.14) + ")";
            return (
              <button
                key={slide.id || slide.src || index}
                className="cm72-carousel-slide cm-react-carousel-slide"
                type="button"
                style={{ transform: translate, opacity: visible ? Math.max(0.25, 1 - depth * 0.28) : 0, zIndex: 5 - depth }}
                data-index={index}
                data-position={relative}
                aria-label={(slide.title || "Кадр") + ". " + (slide.caption || "")}
                aria-current={index === active}
                aria-hidden={!visible}
                tabIndex={visible ? 0 : -1}
                onClick={() => {
                  if (dragged.current) { dragged.current = false; return; }
                  if (index !== active) move(index - active);
                  else onOpen(slide, index);
                }}
              >
                <img src={asset(slide.src || "")} alt={slide.alt || slide.title || ""} loading="eager" decoding="async" draggable={false} />
                <span className="cm72-carousel-caption"><strong>{slide.title}</strong><small>{slide.caption}</small></span>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}
