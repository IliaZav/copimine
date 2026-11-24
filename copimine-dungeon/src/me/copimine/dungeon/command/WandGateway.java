package me.copimine.dungeon.command;

/**
 * Live hand-off for {@code /cmdungeon wand give <player>} (spec OV4.3).
 *
 * <p>The dispatcher itself stays pure Java so it can be compiled and driven by
 * the javac probes without a server. Minting the branded item, pinning its PDC
 * authenticity token and putting it into a real inventory needs the Bukkit API,
 * so that half is injected here by the plugin's live adapter. When no gateway is
 * attached the command must still resolve, but it must never claim that an item
 * was created.</p>
 */
public interface WandGateway {

    /**
     * Mints and delivers one authentic builder wand.
     *
     * @param playerName the {@code <player>} argument exactly as typed
     * @return the outcome; {@link Result#success()} is false for a known refusal
     *         such as an offline target, and the message is shown to the actor
     */
    Result give(String playerName);

    record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
