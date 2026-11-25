#!/usr/bin/env python3
"""Generate the original V5.1 End Rift spell sounds.

Section 9A.03(F)/9A.05 require every hero ability to carry an authored
anticipation layer, a high-contrast release transient and a tail, without ever
lifting copyrighted samples. Every file here is synthesised from seeded noise
and simple oscillators by this script, so the audio is original, reproducible
and auditable. Re-run with::

    python scripts/minecraft/GenerateEndRiftSpellSounds.py

Requires numpy + soundfile (libsndfile writes the Ogg Vorbis that Minecraft
loads). The generated ``.ogg`` files live in the Minecraft 26.3 client tree:

    tools/minecraft-26.3/client/src/main/resources/
        assets/copimineclient/sounds/spell/<token>.ogg

They are byte-static once committed, so the pinned client jar stays
reproducible; the script is the editable source of truth.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

try:
    import soundfile as sf
except ImportError:  # pragma: no cover - environment guard
    sys.exit("soundfile is required to write Ogg Vorbis spell sounds")

RATE = 44100
PEAK = 0.89  # leave headroom so the game mix never clips

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = (
    ROOT
    / "tools/minecraft-26.3/client/src/main/resources/assets/copimineclient/sounds/spell"
)


def samples(seconds: float) -> int:
    return max(1, int(round(seconds * RATE)))


def timeline(n: int) -> np.ndarray:
    return np.arange(n, dtype=np.float64) / RATE


def white(n: int, seed: int) -> np.ndarray:
    return np.random.default_rng(seed).standard_normal(n)


def band(x: np.ndarray, low: float, high: float) -> np.ndarray:
    """Zero-phase spectral band limit with smooth fourth-order shoulders."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(x.size, 1.0 / RATE)
    mask = np.ones_like(freqs)
    if low > 0.0:
        mask *= 1.0 / (1.0 + (low / np.maximum(freqs, 1.0e-6)) ** 4)
    if high > 0.0:
        mask *= 1.0 / (1.0 + (freqs / high) ** 4)
    return np.fft.irfft(spec * mask, n=x.size)


def sweep(n: int, start_hz: float, end_hz: float, curve: float = 1.0) -> np.ndarray:
    """Phase-correct exponential frequency sweep."""
    t = np.linspace(0.0, 1.0, n, endpoint=False)
    shaped = np.power(t, max(0.05, curve))
    freq = start_hz * np.power(max(1.0e-3, end_hz / start_hz), shaped)
    phase = 2.0 * math.pi * np.cumsum(freq) / RATE
    return np.sin(phase)


def decay(n: int, tau: float) -> np.ndarray:
    return np.exp(-timeline(n) / max(1.0e-4, tau))


def rise(n: int, tau: float) -> np.ndarray:
    return 1.0 - np.exp(-timeline(n) / max(1.0e-4, tau))


def env(n: int, attack: float, tau: float, curve: float = 1.0) -> np.ndarray:
    a = np.clip(timeline(n) / max(1.0e-4, attack), 0.0, 1.0) ** curve
    return a * decay(n, tau)


def env_at(n: int, start: float, attack: float, tau: float, curve: float = 1.0) -> np.ndarray:
    """A percussive layer that begins at ``start`` seconds, not at zero.

    The staged Phase 3 geyser needs its impact transient to land on the real
    eruption instant (~1 s after the warning cue), so a plain ``env`` that is
    anchored to t=0 cannot place it.  This shifts the attack and decay window
    and stays exactly zero before ``start``.
    """
    t = timeline(n)
    shifted = np.maximum(t - start, 0.0)
    a = np.clip(shifted / max(1.0e-4, attack), 0.0, 1.0) ** curve
    return np.where(t >= start, a * np.exp(-shifted / max(1.0e-4, tau)), 0.0)


def thump(n: int, start_hz: float, end_hz: float, tau: float) -> np.ndarray:
    return sweep(n, start_hz, end_hz, 0.35) * decay(n, tau)


def soft_clip(x: np.ndarray) -> np.ndarray:
    return np.tanh(x * 1.25) / math.tanh(1.25)


def finish(x: np.ndarray, fade_in: float = 0.004, fade_out: float = 0.09) -> np.ndarray:
    x = np.asarray(x, dtype=np.float64)
    x = x - float(np.mean(x))
    peak = float(np.max(np.abs(x)))
    if peak > 1.0e-9:
        x = x / peak
    n = x.size
    fi = min(n, samples(fade_in))
    fo = min(n, samples(fade_out))
    if fi > 0:
        x[:fi] *= np.linspace(0.0, 1.0, fi)
    if fo > 0:
        x[-fo:] *= np.linspace(1.0, 0.0, fo)
    return (x * PEAK).astype(np.float32)


# --------------------------------------------------------------------------
# One builder per authored cast token. Each layers anticipation, transient
# and tail, and each has a different motif so the player can name the spell
# with their eyes shut.
# --------------------------------------------------------------------------
def fangline() -> np.ndarray:
    n = samples(1.7)
    t = timeline(n)
    air = band(white(n, 11), 1800.0, 12000.0) * env(n, 0.01, 0.10) * 0.5
    blade = sweep(n, 5200.0, 480.0, 0.55) * decay(n, 0.06) * 0.9
    ring = (
        np.sin(2.0 * math.pi * 1890.0 * t) + 0.5 * np.sin(2.0 * math.pi * 2870.0 * t)
    ) * decay(n, 0.22) * 0.35
    tail = band(white(n, 12), 300.0, 2600.0) * decay(n, 0.55) * 0.22
    return finish(air + blade + ring + tail)


def circle() -> np.ndarray:
    n = samples(2.2)
    t = timeline(n)
    growl = sweep(n, 78.0, 96.0, 1.6) * (0.55 + 0.45 * np.sin(2.0 * math.pi * 7.0 * t))
    growl = growl * env(n, 0.16, 1.5) * 0.7
    jaws = band(white(n, 21), 220.0, 1900.0) * (0.5 + 0.5 * np.sin(2.0 * math.pi * 3.2 * t)) ** 4
    jaws = jaws * env(n, 0.3, 1.1) * 0.35
    shimmer = np.sin(2.0 * math.pi * 880.0 * t + 3.0 * np.sin(2.0 * math.pi * 4.5 * t))
    shimmer = shimmer * env(n, 0.5, 1.2) * 0.12
    return finish(growl + jaws + shimmer, fade_out=0.35)


def crown() -> np.ndarray:
    n = samples(2.4)
    t = timeline(n)
    whoosh = band(white(n, 31), 420.0, 6800.0) * env(n, 0.7, 1.0) * 0.5
    tone = sweep(n, 220.0, 660.0, 1.4) * env(n, 0.9, 1.4) * 0.3
    chime = np.zeros(n)
    for index, hz in enumerate((523.25, 783.99, 1046.5)):
        gate = np.clip((t - (0.55 + index * 0.28)) * 14.0, 0.0, 1.0)
        chime += np.sin(2.0 * math.pi * hz * t) * gate * decay(n, 0.5) * 0.12
    return finish(whoosh + tone + chime, fade_out=0.3)


def separation() -> np.ndarray:
    n = samples(1.9)
    boom = thump(n, 120.0, 34.0, 0.42) * 0.95
    wave = band(white(n, 41), 700.0, 9000.0) * rise(n, 0.08) * decay(n, 0.5) * 0.4
    ring = (
        np.sin(2.0 * math.pi * 330.0 * timeline(n)) * decay(n, 0.6) * 0.2
    )
    return finish(boom + wave + ring, fade_out=0.28)


def sphere() -> np.ndarray:
    n = samples(2.6)
    t = timeline(n)
    drone = np.zeros(n)
    for hz, gain in ((110.0, 0.55), (165.0, 0.35), (220.0, 0.2), (330.0, 0.12)):
        drone += np.sin(2.0 * math.pi * hz * t) * gain
    drone = drone * env(n, 0.5, 2.0) * 0.5
    pulse = (0.5 + 0.5 * np.sin(2.0 * math.pi * 2.4 * t)) ** 6
    pulse = pulse * band(white(n, 51), 900.0, 5000.0) * env(n, 0.4, 1.5) * 0.22
    return finish(drone + pulse, fade_out=0.45)


def sweep_spell() -> np.ndarray:
    n = samples(1.8)
    slice_ = sweep(n, 1400.0, 130.0, 0.4) * decay(n, 0.18) * 0.7
    weight = thump(n, 92.0, 40.0, 0.5) * 0.75
    dust = band(white(n, 61), 160.0, 3200.0) * decay(n, 0.7) * 0.3
    return finish(slice_ + weight + dust, fade_out=0.26)


def sculk() -> np.ndarray:
    n = samples(2.1)
    t = timeline(n)
    crawl = band(white(n, 71), 90.0, 900.0)
    crawl = crawl * (0.4 + 0.6 * (0.5 + 0.5 * np.sin(2.0 * math.pi * 11.0 * t)) ** 8)
    crawl = crawl * env(n, 0.25, 1.4) * 0.55
    wet = band(white(n, 72), 1200.0, 5200.0) * env(n, 0.05, 0.35) * 0.3
    low = np.sin(2.0 * math.pi * 58.0 * t) * env(n, 0.4, 1.5) * 0.35
    return finish(crawl + wet + low, fade_out=0.35)


def fan() -> np.ndarray:
    n = samples(2.0)
    t = timeline(n)
    roar = band(white(n, 81), 140.0, 4200.0) * env(n, 0.22, 1.0) * 0.6
    push = sweep(n, 180.0, 540.0, 1.7) * env(n, 0.35, 0.9) * 0.3
    crackle = band(white(n, 82), 2600.0, 10000.0)
    crackle = crackle * (np.random.default_rng(83).random(n) > 0.985) * env(n, 0.1, 1.1) * 0.4
    return finish(roar + push + crackle, fade_out=0.3)


def heavy() -> np.ndarray:
    n = samples(2.0)
    impact = thump(n, 150.0, 28.0, 0.55) * 1.0
    crunch = band(white(n, 91), 70.0, 2400.0) * decay(n, 0.38) * 0.6
    debris = band(white(n, 92), 200.0, 5200.0) * decay(n, 0.9) * 0.28
    return finish(impact + crunch + debris, fade_out=0.3)


def gravity() -> np.ndarray:
    n = samples(2.8)
    t = timeline(n)
    sub = sweep(n, 70.0, 26.0, 1.2) * env(n, 0.35, 2.1) * 0.8
    tear = band(white(n, 101), 60.0, 1500.0) * rise(n, 0.5) * decay(n, 1.4) * 0.35
    whine = np.sin(2.0 * math.pi * (420.0 + 60.0 * np.sin(2.0 * math.pi * 0.7 * t)) * t)
    whine = whine * env(n, 0.8, 1.5) * 0.14
    return finish(sub + tear + whine, fade_out=0.5)


def beam() -> np.ndarray:
    n = samples(3.0)
    t = timeline(n)
    charge = sweep(n, 90.0, 520.0, 1.8) * env(n, 0.9, 1.0) * 0.45
    core = np.zeros(n)
    for hz, gain in ((140.0, 0.5), (280.0, 0.3), (560.0, 0.18), (1120.0, 0.1)):
        core += np.sin(2.0 * math.pi * hz * t) * gain
    core = core * env(n, 0.35, 2.2) * 0.55
    fire = band(white(n, 111), 120.0, 6500.0) * env(n, 0.4, 1.6) * 0.5
    blast = thump(n, 210.0, 32.0, 0.7) * 0.85
    return finish(charge + core + fire + blast, fade_out=0.5)


def geyser() -> np.ndarray:
    # Staged to the real Phase 3 fracture timeline, because the server now sends
    # the v51-geyser cue at the warning tick and the client anchors its audio and
    # its authored mesh clock to that first receipt:
    #   * 0.00..1.00 s  the warning: ground splits (crack), a sub rumble and a
    #     rising pressure tone build the "get off the footprint" anticipation;
    #   * 1.00 s        the eruption transient lands on the real hit instant -
    #     the blast and the low boom are the spike a player reads with eyes shut;
    #   * 1.00..3.10 s  the fire column and its falling debris decay away.
    n = samples(3.1)
    crack = band(white(n, 122), 800.0, 9000.0) * decay(n, 0.08) * 0.78
    rumble = band(white(n, 121), 28.0, 320.0) * env(n, 0.75, 1.35) * 0.52
    pressure = sweep(n, 44.0, 132.0, 1.4) * env(n, 0.95, 1.15) * 0.34
    blast = band(white(n, 123), 180.0, 5200.0) * env_at(n, 1.0, 0.05, 0.92) * 0.88
    boom = sweep(n, 180.0, 44.0, 0.5) * env_at(n, 1.0, 0.02, 0.78) * 0.76
    spires = sweep(n, 150.0, 700.0, 1.5) * env_at(n, 1.06, 0.40, 1.45) * 0.24
    debris = band(white(n, 124), 300.0, 3600.0) * env_at(n, 1.30, 0.25, 1.05) * 0.26
    return finish(crack + rumble + pressure + blast + boom + spires + debris, fade_out=0.5)


BUILDERS = {
    "fangline": fangline,
    "circle": circle,
    "crown": crown,
    "separation": separation,
    "sphere": sphere,
    "sweep": sweep_spell,
    "sculk": sculk,
    "fan": fan,
    "heavy": heavy,
    "gravity": gravity,
    "beam": beam,
    "geyser": geyser,
}


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for token in sorted(BUILDERS):
        data = BUILDERS[token]()
        target = OUTPUT / f"{token}.ogg"
        sf.write(str(target), data, RATE, format="OGG", subtype="VORBIS")
        print(f"{target.relative_to(ROOT)} {target.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
