import me.copimine.endevent.EventLayoutState;
import me.copimine.endevent.EventLayoutStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class EventLayoutStoreTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("copimine-end-layout-test-");
        EventLayoutStore store = new EventLayoutStore(directory);
        check(store.load().gateStatus().equals("UNSET"), "an empty layout must start with the explicit UNSET gate status");
        EventLayoutState legacy = new EventLayoutState(null, null, null, null, Map.of(), "NONE", null);
        check(legacy.gateStatus().equals("UNSET"), "legacy NONE gate status must migrate to UNSET");
        EventLayoutState first = new EventLayoutState(
                new EventLayoutState.Point("CopiMine", 1, 64, 2),
                new EventLayoutState.Point("CopiMine", 5, 70, 8),
                new EventLayoutState.Point("CopiMine_the_end", 10, 80, 10),
                new EventLayoutState.Point("CopiMine_the_end", 12, 82, 12),
                Map.of("10,80,10", "minecraft:stone"), "PREVIEW",
                new EventLayoutState.Portal("CopiMine_the_end", 0.5D, 80.0D, 0.5D, 90.0F, 0.0F));
        check(store.save(first), "initial layout save must succeed");
        EventLayoutState loaded = store.load();
        check(loaded.gateSnapshot().equals(first.gateSnapshot()), "gate snapshot must round-trip");
        check(loaded.portalRoom().world().equals("CopiMine_the_end"), "portal room must round-trip");

        EventLayoutState opening = new EventLayoutState(first.arenaPos1(), first.arenaPos2(), first.gatePos1(), first.gatePos2(),
                first.gateSnapshot(), "OPENING", first.portalRoom());
        check(store.save(opening), "opening layout save must succeed");
        check(store.load().gateStatus().equals("OPENING"), "opening status must round-trip before mutation");

        EventLayoutState closing = new EventLayoutState(first.arenaPos1(), first.arenaPos2(), first.gatePos1(), first.gatePos2(),
                first.gateSnapshot(), "CLOSING", first.portalRoom());
        check(store.save(closing), "closing layout save must succeed");
        check(store.load().gateStatus().equals("CLOSING"), "closing status must round-trip before restoration");

        EventLayoutState second = new EventLayoutState(first.arenaPos1(), first.arenaPos2(), first.gatePos1(), first.gatePos2(),
                Map.of(), "RESTORED", first.portalRoom());
        check(store.save(second), "second layout save must succeed");
        Files.writeString(directory.resolve("event-layout.yml"), "broken: [\n");
        EventLayoutState recovered = store.load();
        check(recovered.gateStatus().equals("CLOSING"), "corrupt primary must recover the immediately previous closing snapshot");
        System.out.println("EventLayoutStoreTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
