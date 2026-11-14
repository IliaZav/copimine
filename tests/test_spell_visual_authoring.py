"""Every End Rift spell must have one authored visual, not a particle fallback.

`SpellVisualPolicy` has promised this in its own docstring since it was written:
"keeping the names, layers and budgets here makes it possible to test that a
newly added spell cannot silently fall back to one generic particle". No such
test existed, and the promise was not kept. Measured on the production lineage:

* five of the fourteen spells -- `arrow_salvo`, `rift_step`, `void_snare`,
  `echo_pulse`, `rift_euphoria` -- had no entry in
  `BossSpellVisualCompositionPolicy`, so they had no authored hue at all;
* three of those five plus `final_strike` and `rift_obelisks` fell through to
  the `default` arm of the renderer's colour switch, which is the same
  near-white for all of them: a bone volley, a binding snare and a euphoria
  helix were literally the same colour;
* four of them had no `BossSpellGeometryPolicy.Shape`, so they could only ever
  be drawn as camera-facing rings -- flat decals on the floor, which is the
  "particle-only substitute" the Waves specification forbids for a
  player-facing spell;
* `renderSpellImpactVisual` built its halo from a hard-coded
  `Color.fromRGB(190, 76, 255)`, so *every* spell's impact arcs were purple
  regardless of the authored palette.

The fix is one source of truth: the composition table owns the colour and the
particle pair, the geometry table owns the silhouette, and both renderers read
them instead of carrying private copies. This test compiles and executes the
two policies, so it fails on behaviour rather than on the presence of a string.
"""

import json
import re
import subprocess
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "copimine-end-event" / "src"
DOMAIN = SRC / "me" / "copimine" / "endevent" / "domain"
SPELL_VISUAL = DOMAIN / "SpellVisualPolicy.java"
COMPOSITIONS = DOMAIN / "BossSpellVisualCompositionPolicy.java"
GEOMETRY = DOMAIN / "BossSpellGeometryPolicy.java"
EVENT = SRC / "me" / "copimine" / "endevent" / "CopiMineEndEvent.java"
CLIENT_JAVA = ROOT / "tools" / "minecraft-26.3" / "client" / "src" / "main" / "java" / "me" / "copimine" / "client"
MESH_NAMES = CLIENT_JAVA / "GuardianSpellMesh.java"
RENDERER = CLIENT_JAVA / "GuardianSpellRenderer.java"
MESH_DIR = (ROOT / "CopiMineClient" / "src" / "main" / "resources" / "assets"
            / "copimineclient" / "geometry" / "guardian_spells")

PROBE = textwrap.dedent(
    """
    import java.util.ArrayList;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;
    import me.copimine.endevent.domain.BossSpellGeometryPolicy;
    import me.copimine.endevent.domain.BossSpellVisualCompositionPolicy;
    import me.copimine.endevent.domain.SpellVisualPolicy;

    public final class SpellAuthoringProbe {
        private static int failures = 0;

        private static void check(boolean condition, String message) {
            if (!condition) {
                failures++;
                System.out.println("FAIL: " + message);
            }
        }

        public static void main(String[] args) {
            List<String> ids = new ArrayList<>(SpellVisualPolicy.profiles().keySet());
            check(ids.size() >= 14, "the spell roster shrank to " + ids.size());

            Set<String> telegraphHues = new HashSet<>();
            Set<String> impactHues = new HashSet<>();
            Set<String> shapes = new HashSet<>();

            for (String id : ids) {
                SpellVisualPolicy.VisualProfile profile = SpellVisualPolicy.profile(id);
                check(profile != null, id + " has no profile");
                check(profile.estimatedParticles() > 0, id + " has a zero particle budget");
                check(profile.layers() >= 2, id + " has fewer than two layers");

                // 1. An authored composition, i.e. a colour and a particle pair.
                check(BossSpellVisualCompositionPolicy.isAuthored(id),
                        id + " has no authored composition and would fall back to a generic cue");
                BossSpellVisualCompositionPolicy.Composition composition =
                        BossSpellVisualCompositionPolicy.composition(id);
                check(composition.spellId().equals(id), id + " resolves to composition " + composition.spellId());
                check(!composition.telegraphSound().isBlank(), id + " has no telegraph sound");
                check(!composition.impactSignature().isBlank(), id + " has no impact signature");
                telegraphHues.add(Integer.toHexString(composition.telegraphHue()));
                impactHues.add(Integer.toHexString(composition.impactHue()));

                // 2. A real silhouette, so the spell is a shape in space.
                BossSpellGeometryPolicy.Shape shape = BossSpellGeometryPolicy.shape(id);
                check(shape != null, id + " has no authored geometry and can only be a flat ring");
                if (shape != null) shapes.add(shape.name());
                for (BossSpellGeometryPolicy.Beat beat : BossSpellGeometryPolicy.Beat.values()) {
                    var points = BossSpellGeometryPolicy.sample(shape, beat, 0.5, 3.0);
                    check(!points.isEmpty(), id + " has no points for " + beat);
                    check(points.size() <= BossSpellGeometryPolicy.MAX_POINTS,
                            id + " exceeds the point budget for " + beat);
                    boolean lifted = false;
                    double minSide = Double.MAX_VALUE, maxSide = -Double.MAX_VALUE;
                    double minUp = Double.MAX_VALUE, maxUp = -Double.MAX_VALUE;
                    double minForward = Double.MAX_VALUE, maxForward = -Double.MAX_VALUE;
                    for (var point : points) {
                        check(Double.isFinite(point.side() + point.up() + point.forward()),
                                id + " produced a non-finite point");
                        minSide = Math.min(minSide, point.side());
                        maxSide = Math.max(maxSide, point.side());
                        minUp = Math.min(minUp, point.up());
                        maxUp = Math.max(maxUp, point.up());
                        minForward = Math.min(minForward, point.forward());
                        maxForward = Math.max(maxForward, point.forward());
                        if (Math.abs(point.up()) > 0.35) lifted = true;
                    }
                    // A silhouette that collapses onto one axis is a decal or a
                    // line, not a volume. This is the invariant that a
                    // "particle-only substitute" always violates -- the arrow
                    // rack was exactly zero-thickness before this was pinned.
                    check(maxSide - minSide > 0.10, id + " has no side extent for " + beat);
                    check(maxUp - minUp > 0.10, id + " has no vertical extent for " + beat);
                    check(maxForward - minForward > 0.10, id + " has no forward extent for " + beat);
                    // A shape anchored on the floor must actually be reachable
                    // there; one anchored at the chest must not start underground.
                    check(BossSpellGeometryPolicy.anchor(shape, beat) != null,
                            id + " has no anchor for " + beat);
                }
            }

            // 3. Two spells must never share a colour, at either stage.
            check(telegraphHues.size() == ids.size(),
                    "two spells share a telegraph colour: " + telegraphHues.size() + "/" + ids.size());
            check(impactHues.size() == ids.size(),
                    "two spells share an impact colour: " + impactHues.size() + "/" + ids.size());

            // 4. The five spells that used to be unauthored kept their own art.
            for (String id : List.of("arrow_salvo", "rift_step", "void_snare", "echo_pulse", "rift_euphoria")) {
                check(ids.contains(id), id + " left the roster");
                check(BossSpellVisualCompositionPolicy.isAuthored(id), id + " lost its composition");
                check(BossSpellGeometryPolicy.shape(id) != null, id + " lost its silhouette");
            }

            // 5. The impact colour is genuinely hotter than the telegraph, so a
            //    glance distinguishes "winding up" from "it landed".
            for (String id : ids) {
                int cold = BossSpellVisualCompositionPolicy.composition(id).telegraphHue();
                int hot = BossSpellVisualCompositionPolicy.composition(id).impactHue();
                check(((hot >> 16) & 0xFF) >= ((cold >> 16) & 0xFF),
                        id + " impact is colder than its telegraph");
            }

            if (failures == 0) {
                System.out.println("OK");
            } else {
                System.out.println("FAILURES=" + failures);
                System.exit(1);
            }
        }
    }
    """
)


def test_every_spell_is_fully_authored(tmp_path):
    probe = tmp_path / "SpellAuthoringProbe.java"
    probe.write_text(PROBE, encoding="utf-8")
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-sourcepath", str(SRC), "-d", str(tmp_path), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "SpellAuthoringProbe"],
        cwd=str(tmp_path),
        capture_output=True,
        text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr
    assert "OK" in ran.stdout


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[start : index + 1]
    raise AssertionError(f"unterminated method: {signature}")


def test_the_renderers_read_the_authored_tables_instead_of_private_copies():
    event = EVENT.read_text(encoding="utf-8", errors="surrogateescape")

    telegraph = _method(event, "private void renderSpellTelegraphVisual(")
    # The telegraph has to emit the authored volume, not only per-spell rings.
    assert "emitAuthoredSpellFrame(" in telegraph
    assert "BossSpellGeometryPolicy.Beat.CHARGE" in telegraph

    impact = _method(event, "private void renderSpellImpactVisual(")
    # The one-shot burst still gets a bounded authored expansion.
    assert "BossSpellPresentationEffects.impact(" in impact
    assert "BossSpellGeometryPolicy.shape(spellId)" in impact
    # ...and its halo is the spell's own palette, not a literal.
    assert "composition(spellId).impactHue()" in impact
    assert "Color.fromRGB(190, 76, 255)" not in impact

    halo = _method(event, "private void spawnSpellImpactHalo(")
    assert "spellVisualColor(spellId)" in halo
    glyph = _method(event, "private void spawnSpellTelegraphGlyph(")
    assert "spellVisualColor(spellId)" in glyph

    # One palette: the authored table wins, the legacy switch is only fallback.
    colour = _method(event, "private Color spellVisualColor(")
    assert "BossSpellVisualCompositionPolicy.isAuthored(spellId)" in colour
    assert colour.index("isAuthored") < colour.index("switch"), (
        "the legacy colour switch still shadows the authored palette"
    )


def test_the_authored_helper_is_bounded_and_presentation_only():
    event = EVENT.read_text(encoding="utf-8", errors="surrogateescape")
    helper = _method(event, "private void emitAuthoredSpellFrame(")
    # It shares the sampled frame and the bounded renderer; it must not spawn
    # entities, schedule tasks or touch damage.
    assert "BossSpellGeometryPolicy.sample(" in helper
    assert "BossSpellPresentationEffects.frame(" in helper
    for forbidden in ("spawn(", "runTask", "damage(", "setHealth", "createExplosion"):
        assert forbidden not in helper, f"the visual helper must not call {forbidden}"
    # Its callers pass a real world-matched destination.
    assert "feet.getWorld().equals(destination.getWorld())" in helper


def test_geometry_and_composition_tables_cover_the_same_roster():
    spell_ids = set(re.findall(r"add\(profiles,\s*\"([a-z_]+)\"", SPELL_VISUAL.read_text(encoding="utf-8")))
    assert len(spell_ids) >= 14, spell_ids
    composition_source = COMPOSITIONS.read_text(encoding="utf-8")
    authored = set(re.findall(r'put\(result,\s*"([a-z_]+)"', composition_source))
    geometry_source = GEOMETRY.read_text(encoding="utf-8")
    mapped = set(re.findall(r'case ((?:"[a-z_]+"(?:,\s*)?)+)\s*->\s*Shape\.', geometry_source))
    # Flatten the multi-label `case "a", "b" ->` arms.
    flatten = set()
    for arm in mapped:
        flatten.update(re.findall(r'"([a-z_]+)"', arm))
    assert not (spell_ids - authored), f"no composition for {sorted(spell_ids - authored)}"
    assert not (spell_ids - flatten), f"no silhouette for {sorted(spell_ids - flatten)}"


def _braced(source: str) -> str:
    """The body between the first `{` and its matching `}`, braces excluded."""
    opening = source.index("{")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError("unterminated block")


def test_every_silhouette_has_a_real_client_mesh_and_an_authored_file():
    """A shape with no mesh is drawn only as dust -- the forbidden substitute.

    `modelData` returned 0 for four miniboss signatures, so `rift_step`,
    `void_snare`, `echo_pulse` and `rift_euphoria` had a sampled silhouette made
    of particles and no body at all: an ability that closes on the player was
    rendered as a puff of dust. This walks the whole chain -- enum arm, mesh
    selector, renderer table, `NAMES`, generated JSON -- so a future shape
    cannot silently lose its geometry the same way.
    """
    geometry = GEOMETRY.read_text(encoding="utf-8", errors="surrogateescape")
    enum_start = geometry.index("public enum Shape {")
    enum_body = _braced(geometry[enum_start:])
    enum_body = re.sub(r"/\*.*?\*/", " ", enum_body, flags=re.S)
    shapes = {
        token.strip()
        for token in enum_body.split(",")
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", token.strip())
    }
    assert len(shapes) >= 20, shapes

    selector = _method(geometry, "public static int modelData(Shape shape)")
    arms = {}
    for labels, custom_model_data in re.findall(r"case\s+([A-Z_,\s]+?)\s*->\s*(\d+)\s*;", selector):
        for label in re.findall(r"[A-Z][A-Z0-9_]*", labels):
            assert label not in arms, f"Shape.{label} has two mesh selectors"
            arms[label] = int(custom_model_data)
    assert set(arms) <= shapes, f"selector arms for unknown shapes: {set(arms) - shapes}"

    # Exactly two shapes are deliberately meshless, and only these two: a
    # contact arc must track the boss's own swing, and DEFEAT is a dissolution
    # a frozen body would contradict.
    assert shapes - set(arms) == {"SWIPE", "DEFEAT"}, shapes - set(arms)

    renderer = RENDERER.read_text(encoding="utf-8", errors="surrogateescape")
    rendered = {
        int(custom_model_data): name
        for custom_model_data, name in re.findall(
            r'case\s+(\d+)\s*->\s*"([a-z_]+)"\s*;', renderer)
    }
    assert set(rendered) == set(arms.values()), (
        f"renderer covers {sorted(set(rendered))} but the selector emits "
        f"{sorted(set(arms.values()))}")

    name_source = MESH_NAMES.read_text(encoding="utf-8", errors="surrogateescape")
    declared = set(re.findall(r'"([a-z_]+)"', _braced(name_source[name_source.index("String[] NAMES = {"):])))
    assert set(rendered.values()) <= declared, (
        f"renderer names missing from GuardianSpellMesh.NAMES: "
        f"{sorted(set(rendered.values()) - declared)}")

    generated = {path.stem for path in MESH_DIR.glob("*.json")}
    assert set(rendered.values()) <= generated, (
        f"renderer names without a generated mesh: {sorted(set(rendered.values()) - generated)}")
    assert declared == generated, (
        f"NAMES and the generated mesh set disagree: only in NAMES "
        f"{sorted(declared - generated)}, only on disk {sorted(generated - declared)}")
    for name in sorted(generated):
        mesh = json.loads((MESH_DIR / f"{name}.json").read_text(encoding="utf-8"))
        elements = mesh.get("elements")
        assert isinstance(elements, list) and elements, f"{name}.json has no cuboids"