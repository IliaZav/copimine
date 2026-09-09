package me.copimine.client;

import java.net.URL;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps the launcher-owned resource-pack flow quiet without accepting packs from arbitrary servers.
 */
public final class CopiMineResourcePackPolicy {
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");
    private static final Set<String> OFFICIAL_HTTPS_HOSTS = Set.of(
            "copimine.ru",
            "www.copimine.ru",
            "mc.copimine.ru"
    );
    private static final Set<String> LOCAL_HTTP_HOSTS = Set.of("127.0.0.1", "localhost", "::1");

    private CopiMineResourcePackPolicy() {
    }

    public static boolean accepts(URL url, String hash) {
        if (url == null || hash == null || !SHA1.matcher(hash).matches()) {
            return false;
        }
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        String host = url.getHost().toLowerCase(Locale.ROOT);
        boolean trustedTransport = "https".equals(protocol)
                ? OFFICIAL_HTTPS_HOSTS.contains(host)
                : "http".equals(protocol) && LOCAL_HTTP_HOSTS.contains(host);
        if (!trustedTransport || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) {
            return false;
        }
        String path = url.getPath();
        return path != null
                && (path.endsWith("/resourcepacks/CopiMineResourcePack.zip")
                || path.endsWith("/CopiMineResourcePack.zip"));
    }
}
