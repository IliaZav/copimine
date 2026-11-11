package me.copimine.client;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Check the pinned game bytecode, not a stub that mirrors the mixin's assumptions. */
class BossBarMixinTargetTest {
    @Test void barBodyAndTitleAreSeparateCallsInThePinnedClient() throws Exception {
        var methods = new HashSet<String>();
        var titleCalls = new HashSet<String>();
        try (var input = getClass().getResourceAsStream(
                "/net/minecraft/client/gui/components/BossHealthOverlay.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    methods.add(name + descriptor);
                    if (!name.equals("extractRenderState")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                              String desc, boolean isInterface) {
                            titleCalls.add(owner + "." + method + desc);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(methods.contains("extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"));
        assertTrue(methods.contains("extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V"));
        assertTrue(titleCalls.contains("net/minecraft/client/gui/components/LerpingBossEvent.getName()Lnet/minecraft/network/chat/Component;"),
                "cancelling extractBar alone leaves the vanilla title outside that method");
    }
}
