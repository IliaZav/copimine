"""Render the canonical End Rift event music set into Minecraft OGG assets.

The checked-in tracks are instrumental material already credited by
END_RIFT_MUSIC_LICENSES.md.  This offline renderer makes deterministic
phase-specific arrangements by choosing different sections, tempo, stereo
motion, and a quiet original synth bed.  It is a development tool only; the
generated OGG files are the immutable resource-pack inputs used by the normal
builder.  Every output name below is part of the current seven-wave flow.

Requires the optional development packages ``numpy`` and ``soundfile``.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import soundfile as sf


SPECS = {
    # output: source, offset seconds, duration seconds, tempo, tremolo Hz,
    # synth notes (MIDI), synth gain
    "ritual_wait": ("boss_cinematic.ogg", 0.0, 22.0, 0.86, 0.05, (31, 36, 43, 48), 0.095),
    "wave_1": ("wave_1.ogg", 0.0, 48.0, 1.00, 0.07, (45, 48, 52, 55), 0.075),
    "wave_2": ("wave_2.ogg", 4.0, 46.0, 1.04, 0.11, (50, 53, 57, 60), 0.085),
    "wave_3": ("wave_3.ogg", 8.0, 44.0, 0.99, 0.15, (38, 41, 45, 50), 0.080),
    "wave_4": ("wave_4.ogg", 3.0, 50.0, 0.96, 0.19, (43, 46, 50, 55), 0.090),
    "wave_5": ("wave_5.ogg", 8.0, 54.0, 1.02, 0.23, (36, 39, 43, 48), 0.100),
    "wave_6": ("wave_6.ogg", 12.0, 54.0, 0.98, 0.27, (34, 38, 42, 47), 0.105),
    "wave_7": ("wave_6.ogg", 24.0, 58.0, 1.06, 0.31, (30, 34, 39, 45), 0.110),
    "intermission_1": ("intermission_1.ogg", 0.0, 14.0, 0.92, 0.08, (55, 59, 62), 0.060),
    "intermission_2": ("intermission_2.ogg", 2.0, 14.0, 1.08, 0.12, (48, 51, 55), 0.065),
    "intermission_3": ("intermission_3.ogg", 1.0, 16.0, 0.95, 0.17, (43, 46, 50), 0.070),
    "intermission_5": ("intermission_5.ogg", 0.0, 18.0, 1.02, 0.21, (40, 44, 47), 0.075),
    "intermission_6": ("intermission_5.ogg", 7.0, 18.0, 1.00, 0.25, (37, 41, 45), 0.080),
    "core_restoration": ("intermission_3.ogg", 4.0, 16.0, 0.90, 0.14, (46, 50, 55), 0.075),
    "pre_boss_cooldown": ("pre_boss_cooldown.ogg", 0.0, 20.0, 0.88, 0.06, (29, 33, 38, 43), 0.095),
    "boss_cinematic": ("boss_cinematic.ogg", 0.0, 22.0, 0.88, 0.05, (31, 36, 43, 48), 0.095),
    "boss_awakening": ("boss_awakening.ogg", 0.0, 45.0, 0.96, 0.08, (32, 36, 41, 48), 0.090),
    "boss_hunt": ("boss_hunt.ogg", 0.0, 45.0, 1.02, 0.14, (38, 43, 48, 53), 0.095),
    "boss_rift": ("boss_rift.ogg", 0.0, 45.0, 1.00, 0.20, (34, 39, 44, 50), 0.100),
    "boss_overload": ("boss_overload.ogg", 0.0, 45.0, 1.04, 0.26, (42, 46, 50, 55), 0.105),
    "boss_rage": ("boss_rage.ogg", 0.0, 45.0, 1.08, 0.32, (36, 40, 43, 48), 0.110),
    "boss_last_seal": ("boss_last_seal.ogg", 0.0, 45.0, 1.10, 0.38, (29, 33, 36, 41), 0.115),
    "boss_finish": ("boss_finish.ogg", 0.0, 20.0, 0.98, 0.42, (60, 64, 67, 72), 0.080),
    "victory": ("victory.ogg", 0.0, 20.0, 0.98, 0.10, (60, 64, 67, 72), 0.080),
}


def midi_hz(note: int) -> float:
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def source_window(audio: np.ndarray, start: int, frames: int) -> np.ndarray:
    if len(audio) == 0:
        raise ValueError("source audio is empty")
    if start >= len(audio):
        start %= len(audio)
    needed = start + max(1, frames)
    if needed <= len(audio):
        return audio[start:needed].copy()
    tail = audio[start:]
    repeats = (frames - len(tail) + len(audio) - 1) // len(audio)
    return np.concatenate((tail, np.tile(audio, (max(1, repeats), 1))))[:frames].copy()


def tempo_window(audio: np.ndarray, start: int, duration_frames: int,
                 sample_rate: int, tempo: float) -> np.ndarray:
    source_frames = max(2, int(duration_frames * max(0.65, tempo)) + 2)
    window = source_window(audio, start, source_frames)
    source_x = np.arange(len(window), dtype=np.float64)
    output_x = np.minimum(source_x[-1], np.arange(duration_frames, dtype=np.float64) * tempo)
    output = np.column_stack([
        np.interp(output_x, source_x, window[:, channel])
        for channel in range(window.shape[1])
    ])
    return output.astype(np.float32, copy=False)


def add_synth_bed(audio: np.ndarray, sample_rate: int, notes: tuple[int, ...],
                  gain: float, tremolo_hz: float) -> np.ndarray:
    frames = len(audio)
    time = np.arange(frames, dtype=np.float64) / sample_rate
    bed = np.zeros((frames, 2), dtype=np.float64)
    note_seconds = 2.0
    for index, note in enumerate(notes):
        begin = index * note_seconds
        local = time - begin
        active = (local >= 0.0) & (local < note_seconds)
        envelope = np.where(active, np.minimum(1.0, local / 0.18), 0.0)
        envelope *= np.where(active, np.minimum(1.0, (note_seconds - local) / 0.32), 0.0)
        frequency = midi_hz(note)
        tone = np.sin(2.0 * np.pi * frequency * local)
        sub = np.sin(2.0 * np.pi * frequency * 0.5 * local) * 0.28
        shimmer = np.sin(2.0 * np.pi * frequency * 2.01 * local) * 0.12
        voice = (tone + sub + shimmer) * envelope
        bed[:, 0] += voice
        bed[:, 1] += voice * (0.86 + 0.10 * np.sin(2.0 * np.pi * tremolo_hz * time + index))
    if notes:
        bed *= gain / max(1.0, len(notes) * 0.75)
    return (audio.astype(np.float64) * 0.82 + bed).astype(np.float32)


def finish(audio: np.ndarray, sample_rate: int, tremolo_hz: float) -> np.ndarray:
    time = np.arange(len(audio), dtype=np.float64) / sample_rate
    tremolo = 0.94 + 0.06 * np.sin(2.0 * np.pi * tremolo_hz * time)
    result = audio.astype(np.float64) * tremolo[:, None]
    fade_frames = min(len(result) // 2, max(1, int(sample_rate * 0.8)))
    fade = np.linspace(0.0, 1.0, fade_frames)
    result[:fade_frames] *= fade[:, None]
    result[-fade_frames:] *= fade[::-1, None]
    peak = float(np.max(np.abs(result))) if result.size else 0.0
    if peak > 0.94:
        result *= 0.94 / peak
    return result.astype(np.float32)


def render(input_dir: Path, output_dir: Path) -> None:
    cache: dict[str, tuple[np.ndarray, int]] = {}
    for output_name, (source_name, start_seconds, duration_seconds, tempo,
                      tremolo_hz, notes, synth_gain) in SPECS.items():
        if source_name not in cache:
            source, sample_rate = sf.read(input_dir / source_name, dtype="float32", always_2d=True)
            cache[source_name] = (source, sample_rate)
        source, sample_rate = cache[source_name]
        start = int(start_seconds * sample_rate)
        frames = int(duration_seconds * sample_rate)
        result = tempo_window(source, start, frames, sample_rate, tempo)
        result = add_synth_bed(result, sample_rate, notes, synth_gain, tremolo_hz)
        result = finish(result, sample_rate, tremolo_hz)
        destination = output_dir / f"{output_name}.ogg"
        destination.parent.mkdir(parents=True, exist_ok=True)
        # libsndfile on Windows can recurse deeply when handed a multi-minute
        # ndarray in one call.  Chunked writes keep the renderer bounded and
        # make a failed render impossible to masquerade as a valid asset.
        with sf.SoundFile(destination, mode="w", samplerate=sample_rate,
                          channels=result.shape[1], format="OGG", subtype="VORBIS") as handle:
            for offset in range(0, len(result), sample_rate):
                handle.write(result[offset:offset + sample_rate])
        print(f"rendered {destination} duration={len(result) / sample_rate:.3f}s")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    render(args.input_dir, args.output_dir)


if __name__ == "__main__":
    main()
