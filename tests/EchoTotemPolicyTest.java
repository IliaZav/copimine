import me.copimine.endevent.domain.wave7.EchoTotemPolicy;

/**
 * GEAR-007 / GEAR-009: a copy's totem must actually work, but only from the
 * supported equipped state and only at a valid lethal boundary. An inert
 * inventory descriptor and an always-on resurrection are both wrong.
 */
public final class EchoTotemPolicyTest {
    public static void main(String[] args) {
        activatesOnlyAtALethalBoundary();
        requiresASupportedEquippedState();
        consumesTheTotemSoItCannotRepeat();
        offhandTotemAndShieldAreMutuallyConsistent();
        supportsExactlyTheOffhandOrHeldSlot();
        theRuntimeGateCombinesLethalityWithTheEquippedState();
        System.out.println("EchoTotemPolicyTest OK");
    }

    /**
     * GEAR-007: "equipped/held" is the offhand the copy actually carries, or the
     * hotbar slot it is using. A totem loose in the pack stays inert.
     */
    private static void supportsExactlyTheOffhandOrHeldSlot() {
        check(EchoTotemPolicy.isEquippedSlot(me.copimine.endevent.domain.wave7.EchoLoadoutState.OFFHAND_SLOT, 3),
                "a totem in the offhand is equipped");
        check(EchoTotemPolicy.isEquippedSlot(3, 3), "a totem in the active hotbar slot is held");
        check(!EchoTotemPolicy.isEquippedSlot(3, 4), "a totem in an inactive hotbar slot is not held");
        check(!EchoTotemPolicy.isEquippedSlot(12, 12),
                "storage slots are never a supported equipped state");
        check(!EchoTotemPolicy.isEquippedSlot(-1, 0), "a missing totem is not equipped");
        check(EchoTotemPolicy.isEquippedSlot(0, 0), "the first hotbar slot is a real held slot");
        check(!EchoTotemPolicy.isEquippedSlot(8, 9),
                "no held-slot index above the hotbar is accepted");
    }

    private static void theRuntimeGateCombinesLethalityWithTheEquippedState() {
        // A lethal hit with an equipped totem rescues; every other combination does not.
        check(EchoTotemPolicy.activationAllowed(4.0D, 5.0D, true, true, false),
                "a lethal hit spends an equipped, unused totem");
        check(!EchoTotemPolicy.activationAllowed(20.0D, 5.0D, true, true, false),
                "a survivable hit never spends the totem");
        check(!EchoTotemPolicy.activationAllowed(4.0D, 5.0D, true, false, false),
                "a lethal hit with an unequipped totem kills the copy");
        check(!EchoTotemPolicy.activationAllowed(4.0D, 5.0D, true, true, true),
                "an already spent totem cannot rescue a second time");
        check(!EchoTotemPolicy.activationAllowed(4.0D, 5.0D, false, true, false),
                "no totem means no activation");
    }

    private static void activatesOnlyAtALethalBoundary() {
        // Lethal only when damage would take the copy to zero or below.
        check(EchoTotemPolicy.wouldBeLethal(4.0D, 5.0D), "damage that reaches zero is lethal");
        check(EchoTotemPolicy.wouldBeLethal(4.0D, 9.0D), "overkill is lethal");
        check(!EchoTotemPolicy.wouldBeLethal(20.0D, 5.0D), "survivable damage is not lethal");
        check(!EchoTotemPolicy.wouldBeLethal(0.0D, 0.0D), "already defeated is not a totem trigger");
        check(EchoTotemPolicy.activationHealth(50.0D) > 0.0D,
                "a totem revives the copy above zero, not at zero");
        check(EchoTotemPolicy.activationHealth(50.0D) <= 50.0D,
                "activation health never exceeds the copy's maximum");
    }

    private static void requiresASupportedEquippedState() {
        check(EchoTotemPolicy.mayActivate(true, true, false),
                "a held totem activates at a lethal boundary");
        // An inert descriptor buried in the inventory is exactly the defect.
        check(!EchoTotemPolicy.mayActivate(true, false, false),
                "a totem that is not in a supported equipped state stays inert");
        check(!EchoTotemPolicy.mayActivate(false, true, false),
                "a totem cannot activate when the hit is not lethal");
        check(!EchoTotemPolicy.mayActivate(true, true, true),
                "a totem cannot activate twice in one life");
    }

    private static void consumesTheTotemSoItCannotRepeat() {
        check(EchoTotemPolicy.consumedOnActivation(),
                "activation spends the totem, so it cannot resurrect repeatedly");
    }

    private static void offhandTotemAndShieldAreMutuallyConsistent() {
        // Holding a totem must not leave a phantom shield available.
        check(!EchoTotemPolicy.shieldUsableWhileHoldingTotem(),
                "a copy cannot keep shielding with a phantom shield while holding a totem");
        check(EchoTotemPolicy.switchCostsATransition(),
                "switching hand or slot incurs the supported state transition");
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}