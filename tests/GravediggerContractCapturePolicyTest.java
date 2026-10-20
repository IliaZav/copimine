import me.copimine.artifacts.GravediggerContractCapturePolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GravediggerContractCapturePolicyTest {
    private record Contract(String uniqueId, boolean authenticated) {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Contract forgedInStorage = new Contract("forged", false);
        Contract consumedArmorContract = new Contract("armor-consumed", true);
        Contract unrelatedArmorContract = new Contract("armor-unrelated", true);
        List<Contract> storage = new ArrayList<>(List.of(forgedInStorage));
        List<Contract> armor = new ArrayList<>(Arrays.asList(consumedArmorContract, unrelatedArmorContract));

        GravediggerContractCapturePolicy.Candidate<Contract> candidate =
                GravediggerContractCapturePolicy.firstEligible(
                        storage,
                        null,
                        armor,
                        Contract::authenticated);

        check(candidate != null, "an authenticated contract inserted into armor is discovered");
        check(candidate.container() == GravediggerContractCapturePolicy.Container.ARMOR,
                "the selected source identifies the armor inventory");
        check(candidate.index() == 0 && candidate.stack() == consumedArmorContract,
                "the exact first authenticated armor instance is selected");

        // This is the exact-slot mutation used by capture after its durable ID
        // has been recorded. The other authentic contract must remain intact.
        armor.set(candidate.index(), null);
        check(armor.get(0) == null, "the selected armor contract is consumed");
        check(armor.get(1) == unrelatedArmorContract,
                "an unrelated authenticated contract remains in armor");
        check(storage.get(0) == forgedInStorage,
                "an unauthenticated item is not selected or removed");

        System.out.println("GravediggerContractCapturePolicyTest OK");
    }
}
