import me.copimine.endevent.domain.RiftGiantAuraEffectPolicy;

public final class RiftGiantAuraEffectPolicyTest {
    private RiftGiantAuraEffectPolicyTest() { }

    public static void main(String[] args) {
        check(RiftGiantAuraEffectPolicy.mayApply(true, false,
                        0, 0, false, true, true),
                "a missing effect can be claimed by the aura");
        check(!RiftGiantAuraEffectPolicy.mayApply(false, false,
                        2, 120, false, true, true),
                "an unrelated stronger effect is preserved without an aura lease");
        check(RiftGiantAuraEffectPolicy.mayApply(false, true,
                        20, 0, true, false, true),
                "the active source may refresh its exact signature after ticking");
        check(!RiftGiantAuraEffectPolicy.mayApply(false, true,
                        20, 1, true, false, true)
                        && !RiftGiantAuraEffectPolicy.mayApply(false, true,
                        -1, 0, true, false, true)
                        && !RiftGiantAuraEffectPolicy.mayApply(false, true,
                        20, 0, true, true, true),
                "a replaced, infinite, or visible effect is not overwritten as aura-owned");
        check(RiftGiantAuraEffectPolicy.mayRemove(true,
                        20, 0, true, false, true)
                        && !RiftGiantAuraEffectPolicy.mayRemove(false,
                        20, 0, true, false, true)
                        && !RiftGiantAuraEffectPolicy.mayRemove(true,
                        20, 1, true, false, true)
                        && !RiftGiantAuraEffectPolicy.mayRemove(true,
                        20, 0, true, true, true),
                "cleanup removes only effects bearing this source's managed aura signature");
        System.out.println("RiftGiantAuraEffectPolicyTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
