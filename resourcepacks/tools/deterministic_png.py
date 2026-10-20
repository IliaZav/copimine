"""PNG bytes independent of Pillow's version-specific filters and encoder."""
import struct
import zlib

def png_bytes(image):
    image = image.convert('RGBA')
    pixels = image.tobytes()
    stride = image.width * 4
    rows = b''.join(b'\0' + pixels[y * stride:(y + 1) * stride] for y in range(image.height))
    # Authored assets are small. Stored DEFLATE blocks make byte reproduction
    # independent of compression heuristics without changing a single pixel.
    blocks = [rows[i:i + 65535] for i in range(0, len(rows), 65535)]
    compressed = b'\x78\x01' + b''.join(bytes([int(i == len(blocks) - 1)])
        + struct.pack('<HH', len(block), 65535 - len(block)) + block for i, block in enumerate(blocks))
    compressed += struct.pack('>I', zlib.adler32(rows) & 0xffffffff)
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', image.width, image.height, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', compressed) + chunk(b'IEND', b'')
