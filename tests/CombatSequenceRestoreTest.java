import me.copimine.endevent.runtime.CombatSequence;

/**
 * Proves a copy keeps the behavioural shape it had before a restart.
 *
 * <p>Deliberately package-less: the pure-Java gate compiles every {@code tests/*.java}
 * into one unnamed package, so a declared package would collide with the
 * production class of the same name.</p>
 */
public final class CombatSequenceRestoreTest {
    private static int failures;

    public static void main(String[] args) {
        aRunCountSurvivesARoundTrip();
        theWholeShapeSurvives();
        aDamagedPayloadIsIgnored();
        aZeroCountIsDropped();
        transitionsArePreserved();
        System.out.println(failures == 0 ? "CombatSequenceRestoreTest: PASS"
                : "CombatSequenceRestoreTest: FAIL " + failures);
        if (failures > 0) System.exit(1);
    }

    private static void aRunCountSurvivesARoundTrip() {
        CombatSequence sequence = new CombatSequence();
        for (int i = 0; i < 9; i++) sequence.record(1, "SWING", 1);
        String encoded = sequence.encode();
        CombatSequence restored = CombatSequence.decode(encoded);
        check("encoded run has nine swings", encoded.equals("1:SWING:9"));
        check("restored run keeps nine swings",
                restored.encode().equals("1:SWING:9"));
        check("restored total", restored.totalActions() == 9L);
    }

    private static void theWholeShapeSurvives() {
        CombatSequence sequence = new CombatSequence();
        for (int i = 0; i < 3; i++) sequence.record(1, "SWING", 1);
        for (int i = 0; i < 4; i++) sequence.record(1, "SHOOT", 1);
        for (int i = 0; i < 2; i++) sequence.record(2, "SWING", 1);
        check("round trip is lossless",
                CombatSequence.decode(sequence.encode()).encode().equals(sequence.encode()));
    }

    private static void aDamagedPayloadIsIgnored() {
        CombatSequence restored = CombatSequence.decode("1:SWING:3;garbage;2:SHOOT:notanumber");
        check("damaged parts dropped", restored.encode().equals("1:SWING:3"));
    }

    private static void aZeroCountIsDropped() {
        check("zero count dropped", CombatSequence.decode("1:SWING:0").isEmpty());
    }

    private static void transitionsArePreserved() {
        CombatSequence sequence = new CombatSequence();
        for (int i = 0; i < 2; i++) sequence.record(1, "SWING", 1);
        for (int i = 0; i < 2; i++) sequence.record(1, "SHOOT", 1);
        check("one transition before restart", sequence.transitions() == 1);
        check("one transition after restart",
                CombatSequence.decode(sequence.encode()).transitions() == 1);
    }

    private static void check(String label, boolean condition) {
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + label);
        }
    }
}
