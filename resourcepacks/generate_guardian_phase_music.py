"""Boss-only arrangements from the repository's already credited CC0 audio.

Inputs must be an immutable export of the original tracks, never this output
directory. numpy/soundfile are development dependencies, not client mods.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct

import numpy as np
import soundfile as sf
from generate_end_rift_music import tempo_window

SPECS = (
    ("awakening", "boss_cinematic.ogg", 4, 0.94, 76),
    ("hunt", "boss_hunt.ogg", 8, 1.00, 88),
    ("rift", "boss_rift.ogg", 14, 1.05, 100),
    ("overload", "boss_overload.ogg", 9, 1.10, 112),
    ("rage", "boss_rage.ogg", 5, 1.16, 124),
    ("last_seal", "boss_last_seal.ogg", 16, 1.21, 136),
)


def _crc_table():
    table = []
    for byte in range(256):
        value = byte << 24
        for _ in range(8):
            value = ((value << 1) ^ (0x04C11DB7 if value & 0x80000000 else 0)) & 0xFFFFFFFF
        table.append(value)
    return table


CRC_TABLE = _crc_table()


def normalize_ogg_serial(path: Path, serial: int):
    """Remove libsndfile's random OGG stream serial and recompute page CRCs."""
    data = bytearray(path.read_bytes())
    offset = 0
    while offset < len(data):
        assert data[offset:offset + 4] == b"OggS"
        segments = data[offset + 26]
        length = 27 + segments + sum(data[offset + 27:offset + 27 + segments])
        assert offset + length <= len(data)
        struct.pack_into("<I", data, offset + 14, serial)
        data[offset + 22:offset + 26] = b"\0" * 4
        crc = 0
        for value in data[offset:offset + length]:
            crc = ((crc << 8) ^ CRC_TABLE[((crc >> 24) ^ value) & 255]) & 0xFFFFFFFF
        struct.pack_into("<I", data, offset + 22, crc)
        offset += length
    path.write_bytes(data)


def arrange(source: np.ndarray, rate: int, index: int, offset: int, tempo: float, bpm: int):
    audio = tempo_window(source, int(offset * rate), 45 * rate, rate, tempo).astype(np.float64)
    if audio.shape[1] == 1:
        audio = np.repeat(audio, 2, axis=1)
    audio = audio[:, :2]
    audio *= 0.125 / max(0.001, float(np.sqrt(np.mean(audio * audio))))
    beat_frames = rate * 60.0 / bpm
    kick_time = np.arange(int(rate * 0.16), dtype=np.float64) / rate
    kick = np.sin(2 * np.pi * (42 * kick_time + 38 * (1 - np.exp(-kick_time * 24)) / 24))
    kick *= np.exp(-kick_time * 30) * (0.035 + index * 0.008)
    rng = np.random.default_rng(20261110 + index)
    hat = rng.uniform(-1, 1, int(rate * 0.045))
    hat = np.diff(hat, prepend=0) * np.exp(-np.arange(len(hat)) / (rate * 0.008))
    hat *= 0.0025 + index * 0.0012
    beat = 0
    while int(beat * beat_frames) < len(audio):
        start = int(beat * beat_frames)
        length = min(len(kick), len(audio) - start)
        audio[start:start + length] += kick[:length, None]
        if index >= 2:
            at = start + int(beat_frames * 0.5)
            length = min(len(hat), len(audio) - at)
            if length > 0:
                audio[at:at + length] += hat[:length, None]
        beat += 1
    # Short equal edge fades prevent clicks without the old 1.6 s quiet gap.
    fade_frames = min(int(rate * 0.12), len(audio) // 2)
    fade = np.linspace(0, 1, fade_frames)
    audio[:fade_frames] *= fade[:, None]
    audio[-fade_frames:] *= fade[::-1, None]
    peak = float(np.max(np.abs(audio)))
    if peak > 0.91:
        audio *= 0.91 / peak
    assert np.isfinite(audio).all()
    return audio.astype(np.float32)


def render(input_dir: Path, output_dir: Path):
    if input_dir.resolve() == output_dir.resolve():
        raise ValueError("Export immutable source tracks separately before rendering")
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest = {"sourceCommit": "f8ef41af7ef89a22fe807b62b3532644dfa39603", "tracks": []}
    for index, (phase, filename, offset, tempo, bpm) in enumerate(SPECS):
        source_path = input_dir / filename
        source, rate = sf.read(source_path, dtype="float32", always_2d=True)
        audio = arrange(source, rate, index, offset, tempo, bpm)
        target = output_dir / f"boss_{phase}.ogg"
        with sf.SoundFile(target, mode="w", samplerate=rate, channels=audio.shape[1],
                          format="OGG", subtype="VORBIS") as handle:
            for begin in range(0, len(audio), rate):
                handle.write(audio[begin:begin + rate])
        normalize_ogg_serial(target, int.from_bytes(hashlib.sha256(phase.encode()).digest()[:4], "little"))
        decoded, decoded_rate = sf.read(target, dtype="float32", always_2d=True)
        assert decoded_rate == rate and len(decoded) == 45 * rate and np.isfinite(decoded).all()
        manifest["tracks"].append({"phase": phase, "filename": target.name,
            "source": filename, "sourceSha256": hashlib.sha256(source_path.read_bytes()).hexdigest(),
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "durationSeconds": 45,
            "pulseBpm": bpm, "tempo": tempo, "peak": float(np.max(np.abs(decoded))),
            "rms": float(np.sqrt(np.mean(decoded * decoded)))})
        print(phase, bpm, target.stat().st_size, manifest["tracks"][-1]["sha256"])
    (output_dir / "guardian_music_v2_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    render(args.input_dir, args.output_dir)
