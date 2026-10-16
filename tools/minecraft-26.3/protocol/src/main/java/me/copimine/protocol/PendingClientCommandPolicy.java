package me.copimine.protocol;

import java.util.Locale;
import java.util.Set;

/** Limits commands available while the required client-mod handshake is pending. */
public final class PendingClientCommandPolicy {
    private static final String AUTHENTICATION_NAMESPACE = "authme";
    private static final Set<String> AUTHENTICATION_COMMANDS = Set.of(
            "login", "l", "register", "reg", "2fa", "premium", "freemium"
    );

    private PendingClientCommandPolicy() {
    }

    public static boolean allowsAuthenticationCommand(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        String token = input.trim().split("\\s+", 2)[0];
        if (!token.startsWith("/") || token.startsWith("//")) {
            return false;
        }

        String command = token.substring(1);
        if (command.isEmpty() || command.indexOf('/') >= 0) {
            return false;
        }

        int namespaceSeparator = command.indexOf(':');
        if (namespaceSeparator >= 0) {
            if (namespaceSeparator == 0
                    || namespaceSeparator == command.length() - 1
                    || command.indexOf(':', namespaceSeparator + 1) >= 0
                    || !command.substring(0, namespaceSeparator).equalsIgnoreCase(AUTHENTICATION_NAMESPACE)) {
                return false;
            }
            command = command.substring(namespaceSeparator + 1);
        }

        return AUTHENTICATION_COMMANDS.contains(command.toLowerCase(Locale.ROOT));
    }
}
