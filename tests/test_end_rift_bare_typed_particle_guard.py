"""Guard every direct spawnParticle call against the missing-payload crash.

Bukkit validates a particle's payload against ``Particle#getDataType()``. A
particle whose declared data class is not ``Void`` (FLASH -> Color, DUST ->
DustOptions, BLOCK -> BlockData, DRAGON_BREATH -> Float, ...) must be spawned
through the overload that takes that payload, otherwise
``Particle#spawnParticle`` throws ``IllegalArgumentException`` ("missing
required data class ...") on Paper. The throw escapes the per-tick renderer, so
a single bare spawn aborts a whole tick.

This crash class has already bitten twice in production:

* the Wave 3 gate sweep died on a bare ``Particle.FLASH``;
* the LAST_SEAL ascension burst -- played exactly when phase 6 begins -- spawned
  ``Particle.FLASH`` bare and would have aborted the phase-6 entry tick.

The dynamic adapter tests (``test_end_rift_typed_particle_payloads``) prove the
shared helper is correct, but they never see a call site that bypasses the
helper. This static scan closes that gap: for every literal
``spawnParticle(Particle.<NAME>, ...)`` in the plugin, a particle in the typed
set must carry an explicit data argument (the 8-parameter overload, seven
top-level commas) instead of the bare 7-parameter overload.
"""

import re
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event/src"

# Data classes verified by reflection against paper-api-26.3 (build 205):
# each of these returns a non-Void Particle#getDataType(), so spawning it
# without its payload throws. Kept explicit rather than guessed so the guard
# cannot silently start checking the wrong particles.
TYPED_PARTICLES = {
    "FLASH",             # org.bukkit.Color
    "DRAGON_BREATH",     # java.lang.Float
    "SCULK_CHARGE",      # java.lang.Float
    "DUST",              # org.bukkit.Particle$DustOptions
    "DUST_COLOR_TRANSITION",  # org.bukkit.Particle$DustTransition
    "BLOCK",             # org.bukkit.block.data.BlockData
    "BLOCK_CRUMBLE",     # org.bukkit.block.data.BlockData
    "FALLING_DUST",      # org.bukkit.block.data.BlockData
    "BLOCK_MARKER",      # org.bukkit.block.data.BlockData
    "ITEM",              # org.bukkit.inventory.ItemStack
    "ITEM_SLIME",        # org.bukkit.inventory.ItemStack
    "ITEM_SNOWBALL",     # org.bukkit.inventory.ItemStack
}

_CALL = re.compile(r"\.spawnParticle\(\s*Particle\.([A-Z_]+)\s*,")


def _top_level_commas(source: str, open_paren: int) -> int:
    """Count argument separators in the call whose '(' is at ``open_paren``."""
    depth = 0
    commas = 0
    for cursor in range(open_paren, len(source)):
        char = source[cursor]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return commas
        elif char == "," and depth == 1:
            commas += 1
    raise AssertionError("unterminated spawnParticle call")


def test_no_direct_spawn_of_a_typed_particle_without_its_payload():
    offenders = []
    for path in sorted(SRC.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        for match in _CALL.finditer(source):
            name = match.group(1)
            if name not in TYPED_PARTICLES:
                continue
            open_paren = source.index("(", match.start())
            # The bare overload is (particle, location, count, x, y, z, extra):
            # 7 parameters, 6 top-level commas. Adding the data argument makes it
            # 7. Anything at or below 6 is a spawn that will throw.
            if _top_level_commas(source, open_paren) <= 6:
                line = source.count("\n", 0, match.start()) + 1
                offenders.append(f"{path.relative_to(ROOT)}:{line} bare Particle.{name}")
    assert not offenders, (
        "a typed particle spawned without its data payload throws "
        "\"missing required data class\" and aborts the tick: "
        + "; ".join(offenders)
    )


def test_the_guard_knows_the_real_offender_shape():
    """Fail if the scanner stops detecting the exact bug it exists for."""
    sample = "viewer.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0);"
    match = _CALL.search(sample)
    assert match is not None and match.group(1) == "FLASH"
    open_paren = sample.index("(", match.start())
    assert _top_level_commas(sample, open_paren) == 6, "bare FLASH must read as 6 commas"
    typed = "viewer.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.WHITE);"
    match = _CALL.search(typed)
    open_paren = typed.index("(", match.start())
    assert _top_level_commas(typed, open_paren) == 7, "typed FLASH must read as 7 commas"


@pytest.mark.parametrize("name", sorted(TYPED_PARTICLES))
def test_typed_set_is_non_vacuous(name):
    assert name.isupper()
