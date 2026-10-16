import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import me.copimine.endevent.runtime.PostWaveRecoveryService;

/** Exercises the real service with detached inventory copies and persisted player metadata. */
public final class PostWaveRecoveryServiceTest {
    public static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0]) {
                case "rollback-independent" -> rollbackAttemptsEveryActionAndPreservesSaveFailure();
                case "rollback-self" -> rollbackDoesNotSuppressOriginalFailureOntoItself();
                default -> throw new IllegalArgumentException("unknown regression scenario");
            }
            System.out.println("PostWaveRecoveryServiceTest " + args[0] + " OK");
            return;
        }
        Fixture fixture = new Fixture();
        PostWaveRecoveryService service = new PostWaveRecoveryService();
        PostWaveRecoveryService.Result first = service.recover("event", 7L, List.of(fixture.player));
        check(first.healedPlayers() == 1 && first.repairedItems() == 3, "heal and repair all inventory sections");
        check(fixture.health == 20.0 && fixture.storage[0].damage == 532
                && fixture.armor[0].damage == 192 && fixture.extra[0].damage == 32,
                "detached inventory modifications must be written back: repair is 30 percent of max durability");
        fixture.health = 8.0;
        fixture.storage[0].damage = 800;
        PostWaveRecoveryService.Result repeated = new PostWaveRecoveryService().recover("event", 7L, List.of(fixture.player));
        check(repeated.healedPlayers() == 0 && repeated.repairedItems() == 0
                && fixture.health == 8.0 && fixture.storage[0].damage == 800,
                "reinstantiating the service must not grant the same recovery twice");
        check(fixture.saves == 1, "receipt and recovered player state must share a player checkpoint");
        PostWaveRecoveryService.Result next = service.recover("event", 8L, List.of(fixture.player));
        check(next.healedPlayers() == 1 && fixture.storage[0].damage == 332,
                "a new attempt may grant its own recovery");
        Fixture failed = new Fixture();
        failed.failSave = true;
        try {
            service.recover("event", 7L, List.of(failed.player));
            throw new AssertionError("a failed checkpoint must surface to the encounter");
        } catch (IllegalStateException expected) {
            check(failed.health == 7.0 && failed.storage[0].damage == 1000
                    && failed.armor[0].damage == 350 && failed.extra[0].damage == 133
                    && failed.metadata.isEmpty(), "failed persistence rolls back recovery and its receipt");
        }
        failed.failSave = false;
        check(service.recover("event", 7L, List.of(failed.player)).healedPlayers() == 1,
                "a rolled back recovery can be retried once persistence works");
        Fixture ineligible = new Fixture();
        ineligible.online = false;
        check(service.recover("event", 7L, List.of(ineligible.player)).healedPlayers() == 0
                && ineligible.saves == 0, "offline players receive no recovery or receipt");
        rollbackAttemptsEveryActionAndPreservesSaveFailure();
        rollbackDoesNotSuppressOriginalFailureOntoItself();
        System.out.println("PostWaveRecoveryServiceTest OK");
    }

    private static void rollbackAttemptsEveryActionAndPreservesSaveFailure() {
        Fixture failed = new Fixture();
        RuntimeException saveFailure = new IllegalStateException("checkpoint unavailable");
        RuntimeException healthFailure = new IllegalStateException("health rollback unavailable");
        RuntimeException storageFailure = new IllegalStateException("storage rollback unavailable");
        NamespacedKey receiptKey = new NamespacedKey("copimine", "end_wave7_recovery");
        failed.metadata.put(receiptKey, "1:event:6");
        failed.failSave = true;
        failed.saveFailure = saveFailure;
        failed.rollbackHealthFailure = healthFailure;
        failed.rollbackStorageFailure = storageFailure;

        RuntimeException caught = recoveryFailure(failed);

        check(caught == saveFailure, "rollback must rethrow the exact original persistence failure");
        check(List.of(caught.getSuppressed()).equals(List.of(healthFailure, storageFailure)),
                "each independent rollback failure must be suppressed onto the original save failure");
        check(failed.health == 20.0 && failed.storage[0].damage == 532,
                "failed rollback actions must leave their actual failure state observable");
        check(failed.armor[0].damage == 350 && failed.extra[0].damage == 133,
                "armor and extra contents must be restored even when earlier rollback actions fail");
        check("1:event:6".equals(failed.metadata.get(receiptKey)),
                "the previous attempt receipt must be restored despite earlier rollback failures");
    }

    private static void rollbackDoesNotSuppressOriginalFailureOntoItself() {
        Fixture failed = new Fixture();
        RuntimeException saveFailure = new IllegalStateException("shared player failure");
        failed.failSave = true;
        failed.saveFailure = saveFailure;
        failed.rollbackHealthFailure = saveFailure;

        RuntimeException caught = recoveryFailure(failed);

        check(caught == saveFailure && caught.getSuppressed().length == 0,
                "rollback must avoid self-suppression while retaining the original failure");
        check(failed.storage[0].damage == 1000 && failed.armor[0].damage == 350
                && failed.extra[0].damage == 133 && failed.metadata.isEmpty(),
                "later rollback actions still restore inventory and clear an uncommitted receipt");
    }

    private static RuntimeException recoveryFailure(Fixture failed) {
        try {
            new PostWaveRecoveryService().recover("event", 7L, List.of(failed.player));
        } catch (RuntimeException error) {
            return error;
        }
        throw new AssertionError("a failed checkpoint must surface to the encounter");
    }

    private static final class Fixture {
        double health = 7.0;
        int saves;
        boolean failSave;
        boolean rollingBack;
        RuntimeException saveFailure = new IllegalStateException("checkpoint unavailable");
        RuntimeException rollbackHealthFailure;
        RuntimeException rollbackStorageFailure;
        boolean online = true;
        Stack[] storage = {new Stack(Material.DIAMOND_SWORD, 1000)};
        Stack[] armor = {new Stack(Material.DIAMOND_CHESTPLATE, 350)};
        Stack[] extra = {new Stack(Material.SHIELD, 133)};
        final Map<NamespacedKey, Object> metadata = new HashMap<>();
        final PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (method, args) -> switch (method) {
            case "get" -> metadata.get(args[0]);
            case "set" -> { metadata.put((NamespacedKey) args[0], args[2]); yield null; }
            case "remove" -> { metadata.remove(args[0]); yield null; }
            default -> throw new UnsupportedOperationException(method);
        });
        final AttributeInstance maximum = proxy(AttributeInstance.class, (method, args) -> {
            if (method.equals("getValue")) return 20.0;
            throw new UnsupportedOperationException(method);
        });
        final PlayerInventory inventory = proxy(PlayerInventory.class, (method, args) -> switch (method) {
            case "getStorageContents" -> copies(storage);
            case "getArmorContents" -> copies(armor);
            case "getExtraContents" -> copies(extra);
            case "setStorageContents" -> {
                if (rollingBack && rollbackStorageFailure != null) throw rollbackStorageFailure;
                storage = copies((ItemStack[]) args[0]); yield null;
            }
            case "setArmorContents" -> { armor = copies((ItemStack[]) args[0]); yield null; }
            case "setExtraContents" -> { extra = copies((ItemStack[]) args[0]); yield null; }
            default -> throw new UnsupportedOperationException(method);
        });
        final Player player = proxy(Player.class, (method, args) -> switch (method) {
            case "isOnline" -> online;
            case "isDead" -> false;
            case "getHealth" -> health;
            case "setHealth" -> {
                if (rollingBack && rollbackHealthFailure != null) throw rollbackHealthFailure;
                health = (double) args[0]; yield null;
            }
            case "getGameMode" -> GameMode.SURVIVAL;
            case "getAttribute" -> maximum;
            case "getInventory" -> inventory;
            case "getPersistentDataContainer" -> pdc;
            case "saveData" -> {
                if (failSave) { rollingBack = true; throw saveFailure; }
                saves++; yield null;
            }
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static final class Stack extends ItemStack {
        final Material material;
        int damage;
        Stack(Material material, int damage) { this.material = material; this.damage = damage; }
        @Override public Material getType() { return material; }
        @Override public Stack clone() { return new Stack(material, damage); }
        @Override public ItemMeta getItemMeta() {
            int[] value = {damage};
            return proxy(Damageable.class, (method, args) -> switch (method) {
                case "getDamage" -> value[0];
                case "setDamage" -> { value[0] = (int) args[0]; yield null; }
                default -> throw new UnsupportedOperationException(method);
            });
        }
        @Override public boolean setItemMeta(ItemMeta meta) { damage = ((Damageable) meta).getDamage(); return true; }
    }
    private static Stack[] copies(ItemStack[] input) {
        Stack[] result = new Stack[input.length];
        for (int i = 0; i < result.length; i++) result[i] = input[i] == null ? null : ((Stack) input[i]).clone();
        return result;
    }
    @FunctionalInterface private interface Call { Object invoke(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> call.invoke(method.getName(), args));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
