"""Deterministic Minecraft cuboid meshes using the original generated material atlas.

The PNG is an authored input. This generator never repaints it or the Guardian skin.
"""
import argparse
import json
import math
from pathlib import Path

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def box(x, y, z, sx, sy, sz, tile, rotation=None):
    col, row = tile % 4, tile // 4
    uv = [col * 4 + .04, row * 4 + .04, col * 4 + 3.96, row * 4 + 3.96]
    element = {'from': [round(x - sx / 2, 4), round(y - sy / 2, 4), round(z - sz / 2, 4)],
               'to': [round(x + sx / 2, 4), round(y + sy / 2, 4), round(z + sz / 2, 4)],
               'faces': {face: {'uv': uv, 'texture': '#surface'} for face in FACES}}
    if rotation:
        axis, angle = rotation
        element['rotation'] = {'origin': [x, y, z], 'axis': axis, 'angle': angle, 'rescale': False}
    return element


def crown(tile, accent):
    pieces = [box(8, 8, 8, 4, 6, 4, tile, ('y', 45)),
              box(8, 8, 8, 1.4, 8, 1.4, accent, ('y', 45))]
    for index in range(8):
        angle = index * math.pi / 4
        x, z = 8 + math.cos(angle) * 4, 8 + math.sin(angle) * 4
        pieces.append(box(x, 7 + index % 2, z, 1.4, 4 + index % 3, 1.4, tile, ('y', 45)))
        pieces.append(box(x, 10 + index % 2, z, .7, 1.5, .7, accent, ('y', 45)))
    return pieces


def meshes():
    result = {'void_core': crown(0, 1), 'inferno_core': crown(6, 7)}
    lance = [box(8, 8, 7, 1.6, 1.6, 11, 2, ('z', 45)),
             box(8, 8, 12, 5, 5, 2, 3, ('z', 45)),
             box(8, 8, 14, 3, 3, 2, 2, ('z', 45)),
             box(8, 8, 15.5, 1, 1, 1, 14, ('z', 45))]
    for side in (-1, 1):
        lance += [box(8 + side * 2.6, 8, 6, 4, .8, 4, 2, ('y', side * 22.5)),
                  box(8, 8 + side * 2.6, 6, .8, 4, 4, 3, ('x', side * 22.5))]
    result['rift_lance'] = lance
    result['echo_shard'] = [box(8 + side * 3, 8 + side, 8, 2.2, 7 - abs(side), 2.2, 11, ('z', side * 22.5))
                           for side in (-1, 0, 1)]
    result['echo_shard'] += [box(8, 8, 8, 6.5, .8, 6.5, 8, ('y', 45))]
    seal = [box(8, 8, 8, 2.4, 5, 2.4, 14, ('y', 45))]
    for x in (4, 12):
        for z in (4, 12):
            seal.append(box(x, 8, z, 1.2, 10, 1.2, 12))
    for y in (3, 13):
        seal += [box(8, y, 4, 8, 1.1, 1.1, 13), box(8, y, 12, 8, 1.1, 1.1, 13),
                 box(4, y, 8, 1.1, 1.1, 8, 13), box(12, y, 8, 1.1, 1.1, 8, 13)]
    result['final_seal'] = seal
    iris = [box(8, 8, 8, 2, 2, 2, 14, ('z', 45))]
    for index in range(8):
        a = index * math.pi / 4
        iris.append(box(8 + math.cos(a) * 4, 8 + math.sin(a) * 4, 8,
                        2.6 if index % 2 == 0 else 1.3, 1.3 if index % 2 == 0 else 2.6,
                        1.5, 12, ('z', 45 if index % 2 else 0)))
        iris.append(box(8 + math.cos(a) * 2.5, 8 + math.sin(a) * 2.5, 8.6, .8, .8, .7, 1))
    result['chest_iris'] = iris
    result['kagune_splinter'] = [box(7.5, 4, 8, 3, 5, 3, 9, ('z', -22.5)),
                                 box(9, 8, 8, 2.2, 5, 2.2, 9, ('z', 22.5)),
                                 box(9.2, 11.5, 8, 1, 3, 1, 10, ('z', -22.5)),
                                 box(8, 3.3, 8, 5.5, 1, 5.5, 9, ('y', 45))]
    result['fault_fragment'] = [box(8, 5, 8, 6, 3, 6, 15, ('y', 45)),
                               box(7, 8, 8, 3, 4, 3, 0, ('z', -22.5)),
                               box(10, 7, 9, 2, 2, 2, 6, ('z', 22.5))]
    # Four ability-specific silhouettes on the existing standalone material atlas.
    rune = [box(8, 8, 8, 2, .45, 2, 1, ('y', 45))]
    for i in range(8):
        a = i * math.pi / 4
        rune.append(box(8 + math.cos(a) * 5, 8, 8 + math.sin(a) * 5,
                        3.8 if i % 2 == 0 else 1.1, .45, 1.1 if i % 2 == 0 else 3.8,
                        0, ('y', 45 if i % 2 else 0)))
        rune.append(box(8 + math.cos(a) * 3, 8.35, 8 + math.sin(a) * 3,
                        .65, .4, .65, 1, ('y', 45)))
    result['void_rune'] = rune
    fire = []
    for i in range(8):
        a = i * math.pi / 4
        x, z = 8 + math.cos(a) * 5, 8 + math.sin(a) * 5
        fire += [box(x, 7.2, z, 1.6, 4 + i % 3, 1.6, 6, ('y', 45)),
                 box(x, 10.3 + (i % 3) * .5, z, .65, 2, .65, 7, ('y', 45))]
    for x, z, sx, sz in ((8, 3, 9, .8), (8, 13, 9, .8), (3, 8, .8, 9), (13, 8, .8, 9)):
        fire.append(box(x, 5.2, z, sx, .8, sz, 15))
    result['inferno_crown'] = fire
    arch = []
    for side in (-1, 1):
        for tier in range(4):
            arch.append(box(8 + side * 5, 3 + tier * 2.2, 8, 1.3, 2.3, 1.3, 11 if tier % 2 else 8))
        arch.append(box(8 + side * 3.3, 12.1, 8, 4.3, 1.3, 1.3, 11, ('z', -side * 22.5)))
        arch.append(box(8 + side * 4.2, 8, 8, .45, 7, .45, 14))
    arch.append(box(8, 13, 8, 2.5, 1.6, 1.8, 8))
    result['summon_arch'] = arch
    rack = []
    for lane in (-1, 0, 1):
        x = 8 + lane * 4.2
        rack += [box(x, 8, 7, .8, .8, 10, 4),
                 box(x, 8, 12.4, 1.8, 1.8, 1.8, 5, ('z', 45)),
                 box(x, 8, 14, .65, .65, 1.8, 14), box(x, 8, 3.4, 2, .5, 2.5, 4)]
    result['arrow_rack'] = rack
    # The four miniboss signatures. Until now these four spells had an authored
    # particle silhouette and no body at all, so the ability that closes on the
    # player (the step, the snare) was the one drawn only in dust. Each mesh
    # mirrors its silhouette in BossSpellGeometryPolicy: a door with depth, six
    # chains pulling inward, a bloom of three latitudes on a column, and two
    # strands climbing a base ring.
    gate = []
    for depth in (8.0, 11.0):
        for x in (3.0, 13.0):
            gate.append(box(x, 8, depth, .9, 12, .9, 13))
        gate.append(box(8, 14, depth, 10.9, .9, .9, 13))
    for x in (3.0, 13.0):
        gate.append(box(x, 14, 9.5, .9, .9, 3.9, 14))
    result['rift_gate'] = gate
    chains = []
    for index in range(6):
        a = index * math.pi / 3
        for step in (0.18, 0.46, 0.74):
            radius = 5 - 4.2 * step
            chains.append(box(8 + math.cos(a) * radius, 11 - 10 * step,
                              8 + math.sin(a) * radius, 1.15, 1.15, 1.15,
                              11 if index % 2 else 14, ('y', 45)))
    chains += [box(8, 1.1, 8, 3.6, .8, 3.6, 8, ('y', 45)),
               box(8, 1.1, 8, 5.2, .4, .8, 8), box(8, 1.1, 8, .8, .4, 5.2, 8)]
    result['snare_chains'] = chains
    bloom = []
    for ring, (height, radius) in enumerate(((2.2, 5.0), (5.4, 3.3), (7.8, 1.7))):
        for index in range(6):
            a = index * math.pi / 3
            bloom.append(box(8 + math.cos(a) * radius, height,
                             8 + math.sin(a) * radius, 1.3, .7, 1.3,
                             0 if ring % 2 == 0 else 1, ('y', 45)))
    bloom.append(box(8, 6.0, 8, 1.2, 12.4, 1.2, 2, ('y', 45)))
    result['echo_bloom'] = bloom
    helix = []
    for strand in (-1, 1):
        for index in range(5):
            t = 0.14 + index * 0.19
            a = t * math.pi * 3.2 + (0 if strand > 0 else math.pi)
            helix.append(box(8 + math.cos(a) * 2.3, 1.6 + t * 10.5,
                             8 + math.sin(a) * 2.3, 1.35, 1.35, 1.35,
                             3 if index % 2 == 0 else 6, ('y', 45)))
    for index in range(4):
        a = index * math.pi / 2
        helix.append(box(8 + math.cos(a) * 3.1, 1.0, 8 + math.sin(a) * 3.1,
                         3.4, .7, 1.0, 12, ('y', 45)))
    result['euphoria_helix'] = helix
    # The signature chest beam's exposed socket (9H.01): one compressed core,
    # three counter-rotated gyro rings and four rails along the firing axis. It
    # is deliberately compact -- the ray itself is drawn from the server's
    # authored geometry, so this body only has to read as the chest socket.
    beam = [box(8, 8, 8, 2.8, 2.8, 2.8, 14)]
    for ring in range(3):
        radius = 5.0 + ring
        tile = 12 if ring % 2 == 0 else 13
        length = radius * 2.1
        if ring == 0:
            bars = [box(8, 8 + radius, 8, length, 1.2, 1.2, tile),
                    box(8, 8 - radius, 8, length, 1.2, 1.2, tile),
                    box(8 - radius, 8, 8, 1.2, length, 1.2, tile),
                    box(8 + radius, 8, 8, 1.2, length, 1.2, tile)]
        elif ring == 1:
            bars = [box(8, 8, 8 + radius, 1.2, length, 1.2, tile),
                    box(8, 8, 8 - radius, 1.2, length, 1.2, tile),
                    box(8, 8 + radius, 8, 1.2, 1.2, length, tile),
                    box(8, 8 - radius, 8, 1.2, 1.2, length, tile)]
        else:
            bars = [box(8, 8, 8 + radius, length, 1.2, 1.2, tile),
                    box(8, 8, 8 - radius, length, 1.2, 1.2, tile),
                    box(8 + radius, 8, 8, 1.2, 1.2, length, tile),
                    box(8 - radius, 8, 8, 1.2, 1.2, length, tile)]
        beam += bars
    for dx in (-1.2, 1.2):
        for dy in (-1.2, 1.2):
            beam.append(box(8 + dx, 8 + dy, 19, .9, .9, 10, 2))
    result['chest_beam'] = beam
    # N23-P1-06/N23-P1-08: the geyser, the floor rift and the inferno column
    # each get an authored body of their own. They used to share fault_fragment
    # with the ground slam, so a rock eruption, a cut in the floor and a fire
    # column were the same visible object.
    geyser = [box(8, 2.4, 8, 8.6, 2.2, 8.6, 15, ('y', 45))]
    for index in range(5):
        a = index * math.pi * 2 / 5 + 0.35
        radius = 2.4 + (index % 2) * 0.9
        height = 8.5 + (index % 3) * 2.2
        x, z = 8 + math.cos(a) * radius, 8 + math.sin(a) * radius
        geyser.append(box(x, 2.2 + height / 2, z, 1.7, height, 1.7,
                          0 if index % 2 else 6,
                          ('x' if index % 2 else 'z', 18 if index % 2 else -18)))
        geyser.append(box(x, 2.2 + height + 0.6, z, .9, 1.4, .9, 1, ('y', 45)))
    geyser.append(box(8, 12.8, 8, 1.1, 2.4, 1.1, 14, ('y', 45)))
    result['geyser'] = geyser
    rift = []
    for index in range(7):
        x = 2.0 + index * 2.0
        z = 8 + (1.3 if index % 2 else -1.3)
        rift.append(box(x, 7.4, z, 2.0, .8, 1.4, 15, ('y', 24 if index % 2 else -24)))
        rift.append(box(x, 9.2, z, 1.0, 2.2, .7, 6, ('z', 18 if index % 2 else -18)))
    for index in range(4):
        x = 4.0 + index * 2.6
        rift.append(box(x, 10.6, 8, .7, 2.6, .7, 7, ('x', 12)))
    result['rift_fracture'] = rift
    inferno_geyser = [box(8, 7.8, 8, 6.4, 1.2, 6.4, 15, ('y', 45))]
    for index in range(6):
        a = index * math.pi / 3
        x, z = 8 + math.cos(a) * 2.6, 8 + math.sin(a) * 2.6
        height = 7.0 + (index % 3) * 1.8
        inferno_geyser.append(box(x, 8.4 + height / 2, z, 1.3, height, 1.3,
                                  6 if index % 2 else 7,
                                  ('x' if index % 2 else 'z', 16)))
        inferno_geyser.append(box(x, 8.4 + height + 0.6, z, .8, 1.4, .8, 1, ('y', 45)))
    result['inferno_geyser'] = inferno_geyser
    # N23-P1-06 residual: three Kagune roles, three authored bodies. A stab
    # spine, a floor eruption of S-curved tentacles and a compact claw-knot.
    # They used to be the rift_lance, the rock geyser and the ambush knot, so a
    # Kagune ability and an unrelated mechanic were the same visible object.
    stab = [box(8, 2.4, 7.4, 5.6, 1.8, 5.2, 9, ('y', 45))]
    for x, y, z, sx, sy, sz, angle in (
            (8.0, 4.6, 8.0, 3.4, 4.2, 2.6, 14.0),
            (8.0, 7.4, 8.9, 2.8, 4.4, 2.2, 30.0),
            (8.4, 10.2, 10.0, 2.2, 4.2, 1.8, 40.0),
            (9.0, 12.8, 11.2, 1.6, 3.6, 1.3, 45.0),
            (9.6, 14.9, 12.3, 1.0, 2.6, .9, 45.0),
            (10.1, 16.2, 13.2, .55, 1.6, .55, 45.0)):
        stab.append(box(x, y, z, sx, sy, sz, 9 if angle < 45 else 10, ('x', angle)))
    for side in (-1, 1):
        stab.append(box(8 + side * 1.9, 8.4, 9.3, 2.6, .9, 1.2, 1, ('y', side * 30)))
        stab.append(box(8 + side * 2.6, 10.0, 10.2, 1.8, .7, .9, 1, ('y', side * 22)))
    result['kagune_stab'] = stab
    eruption = [box(8, 2.0, 8, 9.0, 2.0, 9.0, 15, ('y', 45))]
    for arm in range(4):
        a = arm * math.pi / 2 + 0.25
        for step in range(5):
            t = 0.12 + step * 0.20
            radius = 1.4 + t * 3.4
            sway = math.sin(t * math.pi * 1.5) * (1.3 if arm % 2 == 0 else -1.3)
            x = 8 + math.cos(a) * radius - math.sin(a) * sway
            z = 8 + math.sin(a) * radius + math.cos(a) * sway
            y = 2.4 + t * 9.5
            eruption.append(box(x, y, z, 1.9 - step * .18, 2.6, 1.9 - step * .18,
                                9 if arm % 2 else 6,
                                ('x' if arm % 2 else 'z', 18 + step * 5)))
        tip_sway = math.sin(0.92 * math.pi * 1.5) * (1.3 if arm % 2 == 0 else -1.3)
        tx = 8 + math.cos(a) * 4.53 - math.sin(a) * tip_sway
        tz = 8 + math.sin(a) * 4.53 + math.cos(a) * tip_sway
        eruption.append(box(tx - math.cos(a) * 1.0, 12.6, tz - math.sin(a) * 1.0,
                            1.2, 2.8, 1.2, 1, ('y', 45)))
    result['kagune_eruption'] = eruption
    small = [box(8, 1.7, 8, 6.4, 1.4, 6.4, 15, ('y', 45))]
    for tendril in range(3):
        a = tendril * math.pi * 2 / 3 + 0.2
        for step in range(4):
            t = 0.18 + step * 0.24
            radius = 1.0 + math.sin(t * math.pi * .85) * 2.6
            curl = 1.5 * t * t
            x = 8 + math.cos(a) * radius - math.sin(a) * curl
            z = 8 + math.sin(a) * radius + math.cos(a) * curl
            y = 2.2 + t * 5.4
            small.append(box(x, y, z, 1.7 - step * .22, 1.9, 1.7 - step * .22,
                             9 if tendril % 2 else 6,
                             ('y', 45 if step % 2 else -30)))
    for thorn in range(6):
        a = thorn * math.pi / 3
        small.append(box(8 + math.cos(a) * 2.2, 2.2, 8 + math.sin(a) * 2.2,
                         2.4, .8, .9, 10, ('y', 45)))
    result['kagune_small'] = small
    return result


def outputs():
    # Private entity geometry uses a standalone texture. Never put the authored
    # non-POT material sheet in the shared Minecraft block/item texture atlas.
    return {f'assets/copimineclient/geometry/guardian_spells/{name}.json': {'elements': elements}
            for name, elements in meshes().items()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'src/main/resources')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for relative, document in outputs().items():
        expected = (json.dumps(document, indent=2) + '\n').encode('utf-8')
        path = args.output / relative
        if args.check:
            if not path.exists() or path.read_bytes() != expected:raise SystemExit(f'Generated mesh differs: {path}')
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(expected)
    print(f'Authored {len(meshes())} bounded standalone spell meshes')


if __name__ == '__main__':main()
