"""Execute the per-viewer client-binding delta cache.

The boss tick used to re-sign every owned entity's binding to every viewer once
per second. These probes run the production ``ClientBindingSnapshotCache`` and
pin the properties the transport fix relies on: an unchanged signature is
coalesced, a changed one is delivered, viewers are independent, a generation
change or an explicit invalidation forces a resend, and growth is bounded.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ClientBindingSnapshotCache.java"

PROBE = r'''
import java.util.UUID;
import me.copimine.endevent.domain.ClientBindingSnapshotCache;

public final class ClientBindingCacheProbe {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static UUID id(int value) {
        return new UUID(0L, value);
    }
    public static void main(String[] args) {
        var cache = new ClientBindingSnapshotCache(16, 4);
        UUID viewer = id(1), entity = id(100);
        cache.observeGeneration(7L);
        check(cache.shouldSend(viewer, entity, "i|VISUAL_A"), "first delivery must send");
        cache.recordDelivered(viewer, entity, "i|VISUAL_A");
        check(!cache.shouldSend(viewer, entity, "i|VISUAL_A"), "unchanged signature must be coalesced");
        cache.recordCoalesced();
        check(cache.shouldSend(viewer, entity, "i|VISUAL_B"), "changed signature must send");
        cache.recordDelivered(viewer, entity, "i|VISUAL_B");
        check(!cache.shouldSend(viewer, entity, "i|VISUAL_B"), "updated signature must be cached");

        // Viewers are independent: one viewer's delivery must not hide the bind
        // from another viewer.
        UUID otherViewer = id(2);
        check(cache.shouldSend(otherViewer, entity, "i|VISUAL_B"), "second viewer has its own state");

        // Entity invalidation and viewer invalidation force a resend.
        cache.recordDelivered(otherViewer, entity, "i|VISUAL_B");
        cache.invalidateEntity(entity);
        check(cache.shouldSend(viewer, entity, "i|VISUAL_B"), "entity invalidation forces resend");
        check(cache.shouldSend(otherViewer, entity, "i|VISUAL_B"), "entity invalidation is global");
        cache.recordDelivered(viewer, entity, "i|VISUAL_B");
        cache.invalidateViewer(viewer);
        check(cache.shouldSend(viewer, entity, "i|VISUAL_B"), "viewer invalidation forces resend");

        // Generation rotation resets the whole baseline.
        cache.recordDelivered(viewer, entity, "i|VISUAL_B");
        cache.observeGeneration(8L);
        check(cache.shouldSend(viewer, entity, "i|VISUAL_B"), "generation change forces resend");
        check(cache.generation() == 8L, "generation recorded");
        check(cache.metrics().resets() == 2L, "two reset epochs");

        // A refused send must not be recorded, so the retry still sends.
        check(cache.shouldSend(viewer, id(101), "j|VISUAL_A"), "new entity sends");
        cache.recordFailed();
        check(cache.shouldSend(viewer, id(101), "j|VISUAL_A"), "failed delivery still needs a send");

        // Bounded growth: one viewer cannot pin unbounded state.
        var bounded = new ClientBindingSnapshotCache(64, 3);
        bounded.observeGeneration(1L);
        for (int i = 0; i < 10; i++) {
            bounded.recordDelivered(viewer, id(200 + i), "sig-" + i);
        }
        check(bounded.size() <= 3, "per-viewer ceiling, was " + bounded.size());
        check(!bounded.shouldSend(viewer, id(209), "sig-9"), "newest entry is still cached");
        check(bounded.metrics().evicted() >= 7L, "evictions counted");

        // Global ceiling still applies across viewers.
        var global = new ClientBindingSnapshotCache(2, 10);
        global.observeGeneration(1L);
        for (int i = 0; i < 5; i++) global.recordDelivered(id(10 + i), id(300), "s");
        check(global.size() <= 2, "global ceiling, was " + global.size());

        System.out.println("OK");
    }
}
'''


def test_client_binding_snapshot_cache_behavior(tmp_path):
    probe = tmp_path / "ClientBindingCacheProbe.java"
    probe.write_text(PROBE, encoding="utf-8")
    built = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(CACHE), str(probe)],
        capture_output=True, text=True,
    )
    assert built.returncode == 0, built.stdout + built.stderr
    ran = subprocess.run(
        ["java", "-cp", str(tmp_path), "ClientBindingCacheProbe"],
        capture_output=True, text=True,
    )
    assert ran.returncode == 0, ran.stdout + ran.stderr