"""GEAR-007 / GEAR-009: the personal Echo's Totem of Undying must be LIVE.

The failure this guards is precise: before this change a Totem of Undying in
the owner's inventory was classified ``INERT``, so the copy carried a real
totem it could never use - the "inert inventory descriptor" the contract
forbids. Two independent proofs are required.

1. Behaviour: the real compiled replica state and policy are executed. The
   totem must classify as a spendable kind, be findable only where it really
   is, activate only from a supported equipped state at a genuinely lethal
   boundary, and stay spent across a restart.
2. Wiring: the production Echo damage path must really call the policy and
   really persist the rescue. Removing the hook must fail this test.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"
STATE = BASE / "domain/wave7/EchoLoadoutState.java"
POLICY = BASE / "domain/wave7/EchoTotemPolicy.java"
LISTENER = BASE / "runtime/wave7/EchoCombatAdmissionListener.java"
EVENT = BASE / "CopiMineEndEvent.java"


def _text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def _method(source: str, signature: str) -> str:
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_the_copy_actually_rescues_at_a_lethal_boundary(tmp_path):
    probe = tmp_path / "EchoTotemProbe.java"
    probe.write_text(
        """
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Item;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Kind;
import me.copimine.endevent.domain.wave7.EchoTotemPolicy;

public final class EchoTotemProbe {
    static final UUID EVENT = new UUID(0, 1), OWNER = new UUID(0, 2), DUEL = new UUID(0, 3);

    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    static java.util.List<Item> source(int totemSlot) {
        var items = new ArrayList<Item>(Collections.nCopies(41, Item.empty()));
        items.set(0, new Item("IRON_SWORD", 1, 250, 0, Map.of(), "", false));
        if (totemSlot >= 0) items.set(totemSlot, new Item("TOTEM_OF_UNDYING", 1, 0, 0, Map.of(), "", false));
        return items;
    }

    public static void main(String[] args) {
        // The exact defect: a totem used to fall through to INERT.
        check(Kind.valueOf("TOTEM") != null, "the replica has no spendable totem kind");
        var totem = new Item("TOTEM_OF_UNDYING", 1, 0, 0, Map.of(), "", false);
        check(totem.kind() == Kind.TOTEM, "a Totem of Undying is still not classified as a totem");
        check(totem.usable(), "a real totem is still an inert descriptor");

        // Equipped in the offhand: found, spent exactly once, never twice.
        var state = EchoLoadoutState.create(EVENT, 7, DUEL, OWNER, source(40), 0);
        int slot = state.find(Kind.TOTEM);
        check(slot == 40, "the offhand totem was not found");
        check(EchoTotemPolicy.isEquippedSlot(slot, state.selectedSlot()),
                "an offhand totem is not a supported equipped state");
        long revision = state.revision();
        check(state.consume(slot, 1, revision), "the totem could not be spent");
        check(state.remaining(40) == 0, "spending the totem left it in the replica");
        check(state.find(Kind.TOTEM) == -1, "a spent totem is still findable");
        check(!state.consume(40, 1, state.revision()), "a spent totem was handed out twice");

        // Held in the active hotbar slot works; an inactive storage slot does not.
        var held = EchoLoadoutState.create(EVENT, 7, DUEL, OWNER, source(3), 3);
        check(held.find(Kind.TOTEM) == 3, "a held totem was not found");
        check(EchoTotemPolicy.isEquippedSlot(held.find(Kind.TOTEM), held.selectedSlot()),
                "the active hotbar slot is not a supported held state");
        var packed = EchoLoadoutState.create(EVENT, 7, DUEL, OWNER, source(14), 0);
        check(packed.find(Kind.TOTEM) >= 0, "the packed totem was not found");
        check(!EchoTotemPolicy.isEquippedSlot(packed.find(Kind.TOTEM), packed.selectedSlot()),
                "a totem loose in the pack counted as equipped");

        // A restart must not re-grant the spent totem.
        var encoded = state.encode();
        var restored = EchoLoadoutState.restore(encoded, EVENT, 7, DUEL, OWNER, state.revision());
        check(restored.find(Kind.TOTEM) == -1, "a restart refilled the spent totem");
        check(restored.remaining(40) == 0, "a restart resurrected the totem supply");

        // The runtime gate: only a lethal hit with an equipped, unused totem.
        check(EchoTotemPolicy.activationAllowed(4.0D, 5.0D, true, true, false),
                "a lethal hit must spend an equipped totem");
        check(!EchoTotemPolicy.activationAllowed(20.0D, 5.0D, true, true, false),
                "a survivable hit spent the totem");
        check(!EchoTotemPolicy.activationAllowed(4.0D, 5.0D, true, false, false),
                "an unequipped totem rescued the copy");
        check(!EchoTotemPolicy.activationAllowed(4.0D, 5.0D, true, true, true),
                "an already spent totem rescued a second time");

        // GEAR-009: the rescue never leaves a phantom shield behind.
        check(!EchoTotemPolicy.shieldUsableWhileHoldingTotem(),
                "a copy holding a totem can still block with a phantom shield");
        System.out.println("EchoTotemProbe OK");
    }
}
""",
        encoding="utf-8",
    )
    compiled = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(STATE), str(POLICY), str(probe)],
        capture_output=True,
        text=True,
    )
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    run = subprocess.run(
        ["java", "-cp", str(tmp_path), "EchoTotemProbe"],
        capture_output=True,
        text=True,
    )
    assert run.returncode == 0, run.stdout + run.stderr
    assert "EchoTotemProbe OK" in run.stdout


def test_the_echo_damage_path_really_calls_the_totem_policy():
    listener = _text(LISTENER)
    assert "LethalSurvivalHandler" in listener, "the admission listener cannot deliver a lethal save"
    on_damage = _method(listener, "public void onDamage(")
    assert "lethalSurvivalHandler.survive(" in on_damage, (
        "the listener never offers the copy's lethal hit to a totem"
    )
    # Only a genuinely lethal, still-cancelled hit may try to save the copy.
    assert "living.getHealth() <= event.getFinalDamage()" in on_damage, (
        "a non-lethal hit would spend the totem"
    )
    assert "!event.isCancelled()" in on_damage, (
        "the totem could double-save a hit the shield already blocked"
    )

    source = _text(EVENT)
    construction = _method(source, "private boolean configureNamedEchoRuntime(")
    assert "this::surviveNamedEchoOwnerLethalHit" in construction, (
        "the named Echo listener is never given the totem handler"
    )
    runtime = _method(source, "private boolean surviveNamedEchoOwnerLethalHit(")
    for required in [
        "EchoTotemPolicy.activationAllowed(",
        "EchoLoadoutState.Kind.TOTEM",
        "EchoTotemPolicy.isEquippedSlot(",
        "replica.state().consume(totemSlot, 1,",
        "registerPersonalEchoDuel(next)",
        "saveStateSync()",
        "writeNamedEchoTotemSpent(",
    ]:
        assert required in runtime, f"the totem rescue is missing {required}"
    # A failed checkpoint must not rescue the copy for free.
    assert "WAVE7_ECHO_TOTEM_CHECKPOINT_FAILED" in runtime
    # The totem is spent before the health is restored.
    assert runtime.index("replica.state().consume(") < runtime.index("activationHealth("), (
        "the copy was healed before the totem was durably spent"
    )
    # GEAR-009: the spent totem leaves no phantom shield in the offhand.
    assert "carrier.clearActiveItem();" in runtime, (
        "a phantom shield use survives the totem rescue"
    )