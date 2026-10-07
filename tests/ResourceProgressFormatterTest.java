import java.util.LinkedHashMap;
import java.util.Map;
import me.copimine.endevent.domain.ResourceProgressFormatter;

public final class ResourceProgressFormatterTest {
    public static void main(String[] args) {
        Map<String, Integer> required = new LinkedHashMap<>();
        required.put("DIAMOND", 100);
        required.put("ENDER_EYE", 64);
        required.put("AMETHYST_SHARD", 128);
        required.put("BLAZE_ROD", 64);

        Map<String, Integer> deposited = Map.of(
                "DIAMOND", 3,
                "ENDER_EYE", 4,
                "AMETHYST_SHARD", 5,
                "BLAZE_ROD", 6);

        String text = ResourceProgressFormatter.format(required, deposited);
        check(text.contains("§bАлмазы §f3/100"), "diamonds must use a cyan Russian label");
        check(text.contains("§aОко Эндера §f4/64"), "ender eye must use a green Russian label");
        check(text.contains("§dОсколки аметиста §f5/128"), "amethyst must use a purple Russian label");
        check(text.contains("§6Огненные стержни §f6/64"), "blaze rod must use a gold Russian label");
        check(!text.contains("DIAMOND=") && !text.contains("ENDER_EYE="),
                "raw material keys must not be shown to players");
        System.out.println("ResourceProgressFormatterTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
