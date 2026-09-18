import me.copimine.worldcore.api.WorldAccessResult;
import me.copimine.worldcore.api.WorldAccessService;

public final class WorldAccessServiceContractTest {
    public static void main(String[] args) {
        WorldAccessResult result = new WorldAccessResult(true, true, "OPENED", "End opened");
        if (!result.success() || !result.changed() || !"OPENED".equals(result.code())) {
            throw new AssertionError("WorldAccessResult must expose structured open result");
        }
        if (WorldAccessService.class.getDeclaredMethods().length < 3) {
            throw new AssertionError("WorldAccessService must expose typed End access operations");
        }
        System.out.println("WorldAccessServiceContractTest OK");
    }
}
