"""First-party 64x64 vanilla head-UV mask; no external skin or bitmap input."""
from pathlib import Path
import argparse
import hashlib
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
OUTPUTS = (
    ROOT / "resourcepacks/src/assets/copimine/textures/entity/end_event_echo_eyes.png",
    ROOT / "CopiMineClient/src/main/resources/assets/copimine/textures/entity/end_event_echo_eyes.png",
)

def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

def image_bytes():
    rgba = bytearray(64 * 64 * 4)
    for left in (9, 13):
        for x in (left, left + 1):
            for y in (12, 13):
                color = (187, 106, 255, 255 if y == 12 else 112)
                rgba[(y * 64 + x) * 4:(y * 64 + x) * 4 + 4] = bytes(color)
    rows = b"".join(b"\0" + rgba[y * 256:(y + 1) * 256] for y in range(64))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = image_bytes()
    for output in OUTPUTS:
        if args.check:
            if not output.is_file() or output.read_bytes() != data:
                raise SystemExit("Echo eye asset differs: " + str(output))
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
    print("Echo eye mask 64x64 SHA256=" + hashlib.sha256(data).hexdigest())
