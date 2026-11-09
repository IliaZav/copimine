"""A living carrier owns one attached charge until confirmed death.

Executes the production policy without Paper. Repeated visual ticks must not
drop, duplicate or replace that attachment; death alone enables collection.
"""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/RiftCarrierPolicy.java"


@pytest.fixture(scope="module")
def attachment_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("carrier-attachment")
    source = out / "CarrierAttachmentProbe.java"
    source.write_text(r'''
import java.util.UUID;
import me.copimine.endevent.domain.RiftCarrierPolicy;
import me.copimine.endevent.domain.RiftCarrierRecoverySnapshot;
public final class CarrierAttachmentProbe {
    static final long GEN = 4;
    static final UUID CARRIER = UUID.randomUUID(), CHARGE = UUID.randomUUID(), HOLDER = UUID.randomUUID();
    static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    static RiftCarrierPolicy.State active() {
        return RiftCarrierPolicy.selectCarrier(RiftCarrierPolicy.initial(GEN), GEN, CARRIER);
    }
    static RiftCarrierPolicy.State attach(RiftCarrierPolicy.State state, long generation, UUID charge) {
        try {
            return (RiftCarrierPolicy.State) RiftCarrierPolicy.class.getMethod("attachCarrierCharge",
                    RiftCarrierPolicy.State.class, long.class, UUID.class).invoke(null, state, generation, charge);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("living carrier needs an attachment operation distinct from dropped-charge replacement", missing);
        }
    }
    static RiftCarrierPolicy.State reattach(RiftCarrierPolicy.State state, long generation, UUID oldCharge, UUID charge) {
        try {
            return (RiftCarrierPolicy.State) RiftCarrierPolicy.class.getMethod("reattachCarrierCharge",
                    RiftCarrierPolicy.State.class, long.class, UUID.class, UUID.class)
                    .invoke(null, state, generation, oldCharge, charge);
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("lost active presentation requires identity-fenced replacement", missing);
        }
    }
    public static void main(String[] args) {
        var initial = active();
        switch (args[0]) {
            case "attach" -> {
                var attached = attach(initial, GEN, CHARGE);
                check(attached != initial, "attachment commits a new immutable state");
                check(attached.phase() == RiftCarrierPolicy.Phase.CARRIER_ACTIVE, "carrier stays alive");
                check(CARRIER.equals(attached.carrier()), "carrier identity preserved");
                check(CHARGE.equals(attached.charge()), "charge entity becomes reachable");
                check(attached.holder() == null && attached.delivered() == 0, "no pickup or progress");
            }
            case "thirty-ticks" -> {
                var attached = attach(initial, GEN, CHARGE);
                for (int tick = 0; tick < 30; tick++) {
                    check(attach(attached, GEN, CHARGE) == attached, "same attachment is idempotent");
                    check(attach(attached, GEN, UUID.randomUUID()) == attached, "second charge rejected");
                    check(RiftCarrierPolicy.pickUp(attached, GEN, HOLDER, CHARGE, tick) == attached,
                            "living-carrier charge cannot be collected");
                }
            }
            case "death-handoff" -> {
                var attached = attach(initial, GEN, CHARGE);
                check(RiftCarrierPolicy.carrierDied(attached, GEN, UUID.randomUUID(), CHARGE, 30) == attached,
                        "foreign death cannot release charge");
                var dropped = RiftCarrierPolicy.carrierDied(attached, GEN, CARRIER, CHARGE, 31);
                check(dropped.phase() == RiftCarrierPolicy.Phase.CHARGE_DROPPED, "confirmed death releases charge");
                check(dropped.carrier() == null && CHARGE.equals(dropped.charge()), "one dropped identity");
                check(RiftCarrierPolicy.carrierDied(dropped, GEN, CARRIER, UUID.randomUUID(), 32) == dropped,
                        "duplicate death cannot create another charge");
                var picked = RiftCarrierPolicy.pickUp(dropped, GEN, HOLDER, CHARGE, 33);
                check(picked.phase() == RiftCarrierPolicy.Phase.CHARGE_CARRIED, "dead carrier charge collectible");
                check(RiftCarrierPolicy.pickUp(picked, GEN, UUID.randomUUID(), CHARGE, 34) == picked,
                        "duplicate pickup rejected");
            }
            case "fences" -> {
                check(attach(initial, GEN - 1, CHARGE) == initial, "stale generation rejected");
                check(attach(initial, GEN, null) == initial, "null charge rejected");
                var waiting = RiftCarrierPolicy.initial(GEN);
                check(attach(waiting, GEN, CHARGE) == waiting, "no carrier means no attachment");
                var dropped = RiftCarrierPolicy.carrierDied(initial, GEN, CARRIER, CHARGE, 1);
                check(attach(dropped, GEN, UUID.randomUUID()) == dropped, "dead carrier cannot attach");
            }
            case "replacement" -> {
                check(RiftCarrierPolicy.replaceMissingCharge(initial, GEN, CHARGE) == initial,
                        "drop replacement must not pretend a living carrier died");
                var dropped = RiftCarrierPolicy.carrierDied(initial, GEN, CARRIER, CHARGE, 1);
                UUID restored = UUID.randomUUID();
                var replaced = RiftCarrierPolicy.replaceMissingCharge(dropped, GEN, restored);
                check(restored.equals(replaced.charge()), "missing dropped presentation can be restored");
                var picked = RiftCarrierPolicy.pickUp(replaced, GEN, HOLDER, restored, 2);
                var carriedReplacement = RiftCarrierPolicy.replaceMissingCharge(picked, GEN, CHARGE);
                check(HOLDER.equals(carriedReplacement.holder()), "replacement preserves inventory owner");
                check(carriedReplacement.phase() == RiftCarrierPolicy.Phase.CHARGE_CARRIED, "replacement preserves custody");
            }
            case "reattach" -> {
                var attached = attach(initial, GEN, CHARGE);
                UUID restored = UUID.randomUUID();
                var replaced = reattach(attached, GEN, CHARGE, restored);
                check(restored.equals(replaced.charge()), "missing visual is replaced");
                check(CARRIER.equals(replaced.carrier()) && replaced.phase() == RiftCarrierPolicy.Phase.CARRIER_ACTIVE,
                        "replacement cannot release living carrier charge");
                check(reattach(replaced, GEN, CHARGE, UUID.randomUUID()) == replaced, "stale replacement fenced");
                check(reattach(replaced, GEN - 1, restored, CHARGE) == replaced, "wrong generation fenced");
                check(reattach(replaced, GEN, restored, restored) == replaced, "same identity is a no-op");
                check(reattach(replaced, GEN, null, CHARGE) == replaced, "missing expected identity rejected");
                check(reattach(replaced, GEN, restored, null) == replaced, "missing new identity rejected");
                var dropped = RiftCarrierPolicy.carrierDied(replaced, GEN, CARRIER, restored, 2);
                check(reattach(dropped, GEN, restored, CHARGE) == dropped, "drop cannot reattach to dead carrier");
            }
            case "checkpoint" -> {
                var attached = attach(initial, GEN, CHARGE);
                var encoded = RiftCarrierRecoverySnapshot.encode("event", GEN, attached, null, 30, 1000);
                var decoded = RiftCarrierRecoverySnapshot.decode(encoded, "event", GEN, 2000);
                check(decoded.state().equals(attached), "attached active state round trips without collectible transition");
                var legacy = RiftCarrierRecoverySnapshot.encode("event", GEN, initial, null, 30, 1000);
                check(RiftCarrierRecoverySnapshot.decode(legacy, "event", GEN, 2000).state().equals(initial),
                        "legacy active state without presentation remains valid");
            }
            default -> throw new AssertionError("unknown scenario");
        }
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(POLICY),
                               str(POLICY.with_name("RiftCarrierRecoverySnapshot.java")), str(source)],
                              capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stderr
    return out


@pytest.mark.parametrize("scenario", ["attach", "thirty-ticks", "death-handoff", "fences", "replacement", "reattach", "checkpoint"])
def test_living_carrier_attachment(attachment_probe, scenario):
    result = subprocess.run(["java", "-cp", str(attachment_probe), "CarrierAttachmentProbe", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
