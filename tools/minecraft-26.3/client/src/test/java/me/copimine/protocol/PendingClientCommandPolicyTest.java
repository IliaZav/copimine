package me.copimine.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingClientCommandPolicyTest {
    @Test
    void allowsBareAndAuthMeNamespacedAuthenticationCommands() {
        assertTrue(PendingClientCommandPolicy.allowsAuthenticationCommand("/login secret"));
        assertTrue(PendingClientCommandPolicy.allowsAuthenticationCommand("/REG user secret"));
        assertTrue(PendingClientCommandPolicy.allowsAuthenticationCommand("/authme:login secret"));
        assertTrue(PendingClientCommandPolicy.allowsAuthenticationCommand("/AuthMe:register user secret"));
        assertTrue(PendingClientCommandPolicy.allowsAuthenticationCommand("/2fa 123456"));
    }

    @Test
    void rejectsUnrelatedNamespacesAndMalformedCommands() {
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("/otherplugin:login secret"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("/minecraft:register user secret"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("/otherplugin:premium"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("/authme:unknown"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("/other:authme:login secret"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("//login secret"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("login secret"));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand("  "));
        assertFalse(PendingClientCommandPolicy.allowsAuthenticationCommand(null));
    }
}
