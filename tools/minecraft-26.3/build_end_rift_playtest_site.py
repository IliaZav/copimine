"""Build the End Rift survey site on a fresh, dedicated Paper playtest world."""

import argparse
import hashlib
import math
import os
import re
from pathlib import Path
import socket
import struct
import time
import uuid


class Rcon:
    def __init__(self, port, password):
        self.connection = socket.create_connection(("127.0.0.1", port), timeout=30)
        self.connection.settimeout(60)
        self.request(3, password)

    def receive(self, count):
        data = bytearray()
        while len(data) < count:
            block = self.connection.recv(count - len(data))
            if not block:
                raise ConnectionError("Paper closed RCON")
            data.extend(block)
        return data

    def request(self, kind, body):
        packet = struct.pack("<ii", 12, kind) + body.encode() + b"\0\0"
        self.connection.sendall(struct.pack("<i", len(packet)) + packet)
        length = struct.unpack("<i", self.receive(4))[0]
        response = self.receive(length)
        if struct.unpack_from("<i", response)[0] != 12:
            raise RuntimeError("RCON authentication or command failed")
        return response[8:-2].decode(errors="replace").strip()

    def command(self, body):
        return self.request(2, body)


class Site:
    def __init__(self, rcon, core, height):
        self.rcon = rcon
        self.x, self.y, self.z = core
        self.height = height
        self.count = 0

    def run(self, command):
        result = self.rcon.command("execute in minecraft:overworld run " + command)
        if any(msg in result.lower() for msg in (
                "not loaded", "out of this world", "unknown or incomplete command", "too many blocks")):
            raise RuntimeError(f"{command}: {result}")
        self.count += 1
        if self.count % 125 == 0:
            print(f"terrain operations: {self.count}", flush=True)
        return result

    def point(self, x, y, z):
        return f"{self.x + x} {self.y + y} {self.z + z}"

    def fill(self, x1, y1, z1, x2, y2, z2, block, mode=""):
        suffix = f" {mode}" if mode else ""
        return self.run(f"fill {self.point(x1, y1, z1)} {self.point(x2, y2, z2)} {block}{suffix}")

    def put(self, x, y, z, block):
        return self.run(f"setblock {self.point(x, y, z)} {block}")

    def room(self, start, end, width, height, floor="polished_deepslate", plain=False):
        self.fill(start, -1, -width, end, -1, width, floor)
        self.fill(start, height, -width, end, height, width, "deepslate_tiles")
        for z in (-width, width):
            self.fill(start, 0, z, end, height - 1, z, "deepslate_bricks")
        for x in (start, end):
            self.fill(x, 0, -width, x, height - 1, width, "deepslate_bricks")
            self.fill(x, 0, -2, x, 3, 2, "air")
        for x in range(start + 2, end - 1, 6):
            for z in (-width + 1, width - 1):
                if not plain:
                    self.fill(x, 0, z, x, height - 1, z, "chiseled_deepslate")
                self.put(x, 2, z + (1 if z < 0 else -1), "sea_lantern")

    def build(self):
        self.run(f"forceload add {self.x - 128} {self.z - 64} {self.x + 80} {self.z + 64}")
        time.sleep(7)
        radius = 57
        for dx in range(-radius, radius + 1):
            span = math.isqrt(radius * radius - dx * dx)
            self.fill(dx, -1, -span, dx, -1, span, "deepslate_tiles")
            self.fill(dx, self.height, -span, dx, self.height, span,
                      "deepslate_bricks" if dx % 8 else "chiseled_deepslate")
            for dz in range(-span, span + 1):
                r2 = dx * dx + dz * dz
                if r2 < (radius - 1) ** 2:
                    continue
                material = "polished_basalt[axis=y]" if (dx + dz) % 13 in (0, 1) else "deepslate_bricks"
                self.fill(dx, 0, dz, dx, self.height - 1, dz, material)
                for height in (2, self.height // 3, 2 * self.height // 3):
                    if (dx + dz) % 16 == 0:
                        self.put(dx, height, dz, "sea_lantern")
        for x in (-34, 34):
            self.fill(x, -1, -34, x, -1, 34, "polished_blackstone_bricks")
        for z in (-34, 34):
            self.fill(-34, -1, z, 34, -1, z, "polished_blackstone_bricks")
        for step in range(72):
            angle = math.tau * step / 72
            for band in (12, 25, 48):
                self.put(round(math.cos(angle) * band), -1, round(math.sin(angle) * band),
                         "ochre_froglight" if step % 6 == 0 else "polished_blackstone")
        for x, z in ((-20, -20), (-20, 20), (20, -20), (20, 20)):
            self.fill(x, 0, z, x, 7, z, "polished_blackstone_bricks")
            self.put(x, 8, z, "amethyst_block")
            self.put(x, 6, z - 1, "purple_stained_glass")
            self.put(x, 6, z + 1, "purple_stained_glass")
        self.fill(-58, 0, -3, -55, 4, 3, "air")
        self.fill(55, 0, -3, 58, 4, 3, "air")
        self.room(-116, -91, 13, 12, plain=True)
        self.room(-90, -77, 3, 7)
        self.room(-76, -61, 7, 9, "spruce_planks")
        self.room(-60, -57, 3, 6)
        self.room(58, 73, 8, 11)
        self.fill(-117, -1, -3, -117, -1, 3, "polished_deepslate")
        for x in (-72, -67):
            self.put(x, 0, 4, "purple_bed[facing=south,part=foot]")
            self.put(x, 0, 5, "purple_bed[facing=south,part=head]")
            self.put(x, 0, 6, "bookshelf")
            self.put(x, 2, 6, "lantern")
        self.put(-105, 0, 9, "chest[facing=north,type=single,waterlogged=false]")
        self.put(68, 0, 0, "lodestone")
        self.put(0, 0, 0, "lodestone")
        self.fill(-56, -1, -3, -34, -1, 3, "polished_deepslate")
        for z in (-3, 3):
            self.fill(-56, 0, z, -34, 2, z, "deepslate_bricks")
        self.fill(-56, 3, -3, -34, 3, 3, "deepslate_tiles")
        self.run(f"setworldspawn {self.point(-70, 0, 0)}")
        self.rcon.command("gamerule minecraft:spawn_radius 0")
        return self.rcon.command("save-all flush")

    def islands(self):
        for x, z in ((42, -13), (-21, 43), (13, -42)):
            for level in range(4):
                edge = 4 - level
                for dx in range(-edge, edge + 1):
                    span = edge - abs(dx)
                    self.fill(x + dx, level, z - span, x + dx, level, z + span, "polished_blackstone_bricks")
            self.fill(x - 3, 0, z - 1, x - 3, 0, z + 1, "deepslate_tile_slab[type=bottom,waterlogged=false]")
            self.fill(x - 2, 1, z - 1, x - 2, 1, z + 1, "deepslate_tile_slab[type=bottom,waterlogged=false]")
            self.put(x, 4, z, "amethyst_cluster[facing=up,waterlogged=false]")
            self.put(x, 2, z + 2, "ochre_froglight")
        for x in range(-114, -92, 6):
            for z in (-12, 12):
                self.fill(x, 0, z, x, 11, z, "air", "replace chiseled_deepslate")
        self.fill(-116, 0, -2, -116, 3, 2, "deepslate_bricks")
        self.rcon.command("save-all flush")

    def manifest(self, world_id):
        def absolute(box):
            return tuple(v + (self.x, self.y, self.z)[i % 3] for i, v in enumerate(box))
        corridor = absolute((-56, 0, -3, -34, 3, 3))
        safe = absolute((-76, -1, -7, -61, 9, 7))
        changes = []
        for x in range(-56, -33):
            for z in (-3, 3):
                for y in range(3):
                    changes.append(("corridor", self.x + x, self.y + y, self.z + z, "minecraft:deepslate_bricks"))
            for z in range(-3, 4):
                changes.append(("corridor", self.x + x, self.y + 3, self.z + z, "minecraft:deepslate_tiles"))
        fissures = ((37, -15), (37, -14), (38, -14), (38, -13), (39, -13), (39, -12), (39, -11),
                    (-27, 37), (-26, 37), (-25, 38), (-24, 38), (-23, 38), (-22, 39), (-21, 39),
                    (8, -38), (9, -38), (10, -38), (11, -39), (12, -39), (13, -39), (14, -40))
        for dx, dz in fissures:
            self.put(dx, -1, dz, 'deepslate_tiles')
            changes.append(("fissure", self.x + dx, self.y - 1, self.z + dz, "minecraft:deepslate_tiles"))
        head = ("last-seal-v1", world_id, self.x, self.z, 57, self.y - 1, self.y - 4,
                self.x, self.y, self.z + 12, len(changes))
        raw = "\n".join("\t".join(map(str, line)) for line in (head, *changes)) + "\n"
        def java_box(coords):
            names = ("minX", "minY", "minZ", "maxX", "maxY", "maxZ")
            return "Square[" + ", ".join(f"{name}={value}" for name, value in zip(names, coords)) + "]"
        digest = hashlib.sha256((raw + "\n" + java_box(corridor) + "\n" + java_box(safe)).encode()).hexdigest()
        return raw, corridor, safe, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--height", type=int, default=60)
    parser.add_argument("--core", type=int, nargs=3, default=(0, 72, 0))
    parser.add_argument("--rcon-port", type=int, default=25579)
    parser.add_argument("--finalize-existing", action="store_true",
                        help="survey and approve an already built disposable test site")
    parser.add_argument("--decorate-existing", action="store_true",
                        help="add the arena islands and refresh this dedicated site's manifest")
    args = parser.parse_args()
    root = args.root.resolve(strict=True)
    safety = root / "copimine-disposable-site"
    if not safety.is_file() or (root / "site-built").exists() and not args.decorate_existing:
        parser.error("a fresh disposable Minecraft directory with copimine-disposable-site is required")
    if not 16 <= args.height <= 84 or args.core[1] < -57 or args.core[1] + args.height > 310:
        parser.error("the selected floor and ceiling must fit the Minecraft Overworld")
    rcon = Rcon(args.rcon_port, os.environ["COPIMINE_SITE_RCON"])
    site = Site(rcon, args.core, args.height)
    if not args.finalize_existing and not args.decorate_existing:
        print(site.build(), flush=True)
    site.islands()
    surveyed = rcon.command("cmend arena survey")
    match = re.search(r"world-id=([a-fA-F0-9-]{36}) world=CopiMine\b", surveyed)
    if not match:
        raise RuntimeError(f"Paper did not confirm the exact event world: {surveyed}")
    world_id = uuid.UUID(match[1])
    raw, corridor, safe, digest = site.manifest(world_id)
    plugin = root / "plugins" / "CopiMineEndEvent"
    (plugin / "last-seal-arena.tsv").write_text(raw, encoding="utf-8")
    config = plugin / "config.yml"
    content = config.read_text(encoding="utf-8")
    content = re.sub(r"(?m)^  concealed-corridor:.*$", f"  concealed-corridor: {list(corridor)}", content)
    content = re.sub(r"(?m)^  protected-safe-zone:.*$", f"  protected-safe-zone: {list(safe)}", content)
    content = re.sub(r"(?m)^  approved-plan-sha256:.*$", f"  approved-plan-sha256: '{digest}'", content)
    rcon.command("save-all flush")
    config.write_text(content, encoding="utf-8")
    (root / "site-built").write_text(f"{world_id}\n{digest}\n", encoding="utf-8")
    print(f"ARENA_SITE_BUILT height={args.height} operations={site.count} manifest={digest}", flush=True)


if __name__ == "__main__":
    main()
