import me.serverrp.autheffects.AuthenticationHookReadinessPolicy;

public final class AuthenticationHookReadinessPolicyTest {
    public static void main(String[] args) {
        require(AuthenticationHookReadinessPolicy.canEnable(true, true, false),
                "login and logout hooks must support event-only authentication");
        require(AuthenticationHookReadinessPolicy.canEnable(false, true, true),
                "AuthMeApi can supply login state when logout is still observed");
        require(!AuthenticationHookReadinessPolicy.canEnable(true, false, true),
                "AuthMeApi must not permit startup without logout reconciliation");
        require(!AuthenticationHookReadinessPolicy.canEnable(false, false, true),
                "logout hook is mandatory even when AuthMeApi is available");
        require(!AuthenticationHookReadinessPolicy.canEnable(false, true, false),
                "event-only mode requires a login hook as well as logout");
        System.out.println("AuthenticationHookReadinessPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private AuthenticationHookReadinessPolicyTest() {
    }
}
