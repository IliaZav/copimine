from __future__ import annotations

import argparse
import struct
from pathlib import Path


OGG_CAPTURE = b"OggS"
VORBIS_IDENTIFICATION = b"\x01vorbis"
OGG_CRC_POLYNOMIAL = 0x04C11DB7


def crc32_ogg(page: bytes) -> int:
    value = 0
    for byte in page:
        value ^= byte << 24
        for _ in range(8):
            value = ((value << 1) ^ OGG_CRC_POLYNOMIAL) & 0xFFFFFFFF if value & 0x80000000 else (value << 1) & 0xFFFFFFFF
    return value


def parse_pages(data: bytes) -> list[tuple[int, int, int, int]]:
    pages = []
    offset = 0
    while offset < len(data):
        if data[offset:offset + 4] != OGG_CAPTURE:
            raise ValueError(f"invalid Ogg capture pattern at byte {offset}")
        if offset + 27 > len(data):
            raise ValueError("truncated Ogg page header")
        header = data[offset:offset + 27]
        segment_count = header[26]
        lacing_start = offset + 27
        payload_start = lacing_start + segment_count
        if payload_start > len(data):
            raise ValueError("truncated Ogg lacing table")
        payload_size = sum(data[lacing_start:payload_start])
        size = 27 + segment_count + payload_size
        if offset + size > len(data):
            raise ValueError("truncated Ogg page payload")
        granule = struct.unpack("<q", header[6:14])[0]
        pages.append((offset, size, granule, header[5]))
        offset += size
    return pages


def vorbis_sample_rate(data: bytes, pages: list[tuple[int, int, int, int]]) -> int:
    packet = bytearray()
    for offset, size, _granule, _flags in pages:
        header = data[offset:offset + 27]
        segment_count = header[26]
        lacing_start = offset + 27
        payload_start = lacing_start + segment_count
        payload = data[payload_start:offset + size]
        cursor = 0
        for segment_size in data[lacing_start:payload_start]:
            packet.extend(payload[cursor:cursor + segment_size])
            cursor += segment_size
            if segment_size < 255:
                if packet[:7] != VORBIS_IDENTIFICATION:
                    raise ValueError("input is not a Vorbis Ogg stream")
                if len(packet) < 16:
                    raise ValueError("truncated Vorbis identification packet")
                return struct.unpack("<I", packet[12:16])[0]
    raise ValueError("Vorbis identification packet not found")


def with_eos_and_granule(page: bytes, granule: int) -> bytes:
    result = bytearray(page)
    result[5] |= 0x04
    result[6:14] = struct.pack("<q", granule)
    result[22:26] = b"\x00\x00\x00\x00"
    result[22:26] = struct.pack("<I", crc32_ogg(result))
    return bytes(result)


def trim_ogg(source: Path, target: Path, seconds: float) -> tuple[float, float]:
    if seconds <= 0:
        raise ValueError("target duration must be positive")
    data = source.read_bytes()
    pages = parse_pages(data)
    sample_rate = vorbis_sample_rate(data, pages)
    target_granule = round(seconds * sample_rate)
    cut_index = next(
        (index for index, (_offset, _size, granule, flags) in enumerate(pages)
         if granule >= target_granule and not (flags & 0x02)),
        None,
    )
    if cut_index is None:
        raise ValueError("input is shorter than requested duration")

    output = bytearray()
    for index, (offset, size, _granule, _flags) in enumerate(pages[:cut_index + 1]):
        page = data[offset:offset + size]
        output.extend(with_eos_and_granule(page, target_granule) if index == cut_index else page)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(output)
    final_pages = parse_pages(bytes(output))
    final_granule = final_pages[-1][2]
    if not final_pages[-1][3] & 0x04 or final_granule != target_granule:
        raise ValueError("trimmed stream did not end with the requested EOS granule")
    return target_granule / sample_rate, final_granule / sample_rate


def main() -> None:
    parser = argparse.ArgumentParser(description="Trim a Vorbis Ogg stream at a page boundary.")
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("seconds", type=float)
    args = parser.parse_args()
    requested, actual = trim_ogg(args.source, args.target, args.seconds)
    print(f"trimmed={args.target} requested={requested:.6f}s actual={actual:.6f}s")


if __name__ == "__main__":
    main()
