"""Section 9A.03(F) / 9A.05: each hero cast must actually *sound* like three layers.

The existing presentation contract proves the audio chain exists: one Ogg Vorbis
per token, a ``sounds.json`` registration, the one-shot runtime and the editable
generator.  That is a wiring proof, not an audio proof.  Section 9A.03(F) is
explicit that every hero ability needs

* an original **anticipation** layer (an audible early cue, not silence before
  the hit),
* a high-contrast **release/impact transient** (a sharp peak well above the
  body), and
* a **tail** that decays instead of an endless loud loop,

and Section 9A.05 asks for a clean fade rather than a wall of sound.  This file
decodes the shipped samples and measures that envelope, so a regenerated sound
that is flat, silent-first or never decays fails here instead of shipping.

the numbers below are measured bounds with margin, taken from the current
twelve authored samples:

* onset RMS        >= 0.02   (measured minimum 0.089)
* peak amplitude   >= 0.50   (measured minimum 0.861)
* crest factor     >= 2.0    (measured minimum 2.62)
* tail / peak      <= 0.35   (measured maximum 0.159)
* duration           0.8..6s (measured 1.70..3.00s)

``soundfile``/``numpy`` are workstation tools, not CI test dependencies, so this
check skips cleanly when they are absent: a skip is never reported as a pass, and
the CI-side wiring proof stays the always-on contract.
"""
from __future__ import annotations

import hashlib
import re
from pathlib import Path

import pytest

pytest.importorskip("numpy")
pytest.importorskip("soundfile")

import numpy as np  # noqa: E402
import soundfile as sf  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
CLIENT_SRC = ROOT / "tools/minecraft-26.3/client/src/main/java"
CLIENT_RESOURCES = ROOT / "tools/minecraft-26.3/client/src/main/resources"
GEOMETRY = CLIENT_SRC / "me/copimine/client/SpellVisualGeometry.java"
SOUNDS = CLIENT_RESOURCES / "assets/copimineclient/sounds/spell"

# Measured bounds with margin; see the module docstring for the raw minimums.
MIN_DURATION = 0.8
MAX_DURATION = 6.0
MIN_ONSET_RMS = 0.02
MIN_PEAK = 0.50
MIN_CREST = 2.0
MAX_TAIL_RATIO = 0.35
WINDOW_SECONDS = 0.010
EDGE_FRACTION = 0.125


def _tokens() -> list[str]:
    text = GEOMETRY.read_text(encoding="utf-8")
    return re.findall(r'^\s+[A-Z_]+\("([a-z]+)"\)', text, flags=re.MULTILINE)


def _envelope(mono: "np.ndarray", sample_rate: int) -> "np.ndarray":
    window = max(1, int(sample_rate * WINDOW_SECONDS))
    count = len(mono) // window
    if count < 4:
        window = max(1, len(mono))
        count = len(mono) // window
    trimmed = mono[: count * window].reshape(count, window)
    return np.sqrt(np.mean(trimmed ** 2, axis=1))


def _measure(path: Path) -> dict:
    info = sf.info(path)
    mono, sample_rate = sf.read(path, always_2d=False)
    if mono.ndim > 1:
        mono = mono.mean(axis=1)
    mono = np.asarray(mono, dtype=np.float64)
    envelope = _envelope(mono, sample_rate)
    edge = max(1, len(envelope) // 8)
    rms = float(np.sqrt(np.mean(mono ** 2)))
    peak = float(np.max(np.abs(mono)))
    envelope_peak = float(np.max(envelope))
    return {
        "channels": info.channels,
        "sample_rate": info.samplerate,
        "duration": len(mono) / sample_rate,
        "peak": peak,
        "rms": rms,
        "crest": peak / rms if rms > 0.0 else 0.0,
        "onset_rms": float(np.mean(envelope[:edge])),
        "tail_ratio": float(np.mean(envelope[-edge:])) / envelope_peak
        if envelope_peak > 0.0
        else 9.0,
    }


def test_every_authored_spell_has_anticipation_transient_and_decaying_tail() -> None:
    tokens = _tokens()
    assert len(tokens) == 12, f"expected 12 authored casts, found {tokens}"

    seen_hashes: dict[str, str] = {}
    for token in tokens:
        sample = SOUNDS / f"{token}.ogg"
        assert sample.is_file(), f"missing authored spell sound for {token}"

        # A positional world event is a mono 44.1 kHz Vorbis stream.
        metrics = _measure(sample)
        assert metrics["channels"] == 1, f"{token}.ogg must be mono for 3D playback"
        assert metrics["sample_rate"] == 44100, f"{token}.ogg must be 44.1 kHz"

        # Anticipation: an audible early cue, not silence into the hit.
        assert metrics["onset_rms"] >= MIN_ONSET_RMS, (
            f"{token}.ogg is nearly silent at the start "
            f"(onset RMS {metrics['onset_rms']:.3f} < {MIN_ONSET_RMS})"
        )
        # Release/impact transient: a peak well above the body energy.
        assert metrics["peak"] >= MIN_PEAK, (
            f"{token}.ogg is too quiet (peak {metrics['peak']:.3f} < {MIN_PEAK})"
        )
        assert metrics["crest"] >= MIN_CREST, (
            f"{token}.ogg lacks a high-contrast transient "
            f"(crest {metrics['crest']:.2f} < {MIN_CREST})"
        )
        # Tail: the sample must decay, not end on a loud sustained loop.
        assert metrics["tail_ratio"] <= MAX_TAIL_RATIO, (
            f"{token}.ogg does not fade out "
            f"(tail/peak {metrics['tail_ratio']:.3f} > {MAX_TAIL_RATIO})"
        )
        assert MIN_DURATION <= metrics["duration"] <= MAX_DURATION, (
            f"{token}.ogg duration {metrics['duration']:.2f}s is outside "
            f"{MIN_DURATION}..{MAX_DURATION}s"
        )

        digest = hashlib.sha256(sample.read_bytes()).hexdigest()
        if digest in seen_hashes:
            raise AssertionError(
                f"{token} and {seen_hashes[digest]} ship byte-identical audio; "
                "each hero cast must have its own motif"
            )
        seen_hashes[digest] = token

    assert len(seen_hashes) == 12, "the twelve spells must all differ"
