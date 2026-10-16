package me.copimine.client.mixin;

import me.copimine.client.EndRiftRenderStateAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements EndRiftRenderStateAccess {
    @Unique private String copimine$uuid = "";
    @Unique private int copimine$entityId = -1;
    @Unique private boolean copimine$isEcho;
    @Unique private String copimine$visual = "";
    @Unique private String copimine$wavePose = "";
    @Unique private String copimine$eventAnimation = "";
    @Unique private boolean copimine$endBoss;
    @Unique private String copimine$bossPhase = "";
    @Unique private long copimine$bossPhaseTransitionMillis;
    @Unique private String copimine$bossAnimation = "";

    @Override
    public void copimine$bindRenderState(String uuid, int entityId, boolean echo, String visual,
                                         String wavePose, String eventAnimation, boolean endBoss,
                                         String bossPhase, long bossPhaseTransitionMillis,
                                         String bossAnimation) {
        copimine$uuid = uuid;
        copimine$entityId = entityId;
        copimine$isEcho = echo;
        copimine$visual = visual;
        copimine$wavePose = wavePose;
        copimine$eventAnimation = eventAnimation;
        copimine$endBoss = endBoss;
        copimine$bossPhase = bossPhase;
        copimine$bossPhaseTransitionMillis = bossPhaseTransitionMillis;
        copimine$bossAnimation = bossAnimation;
    }

    @Override public String copimine$uuid() { return copimine$uuid; }
    @Override public int copimine$entityId() { return copimine$entityId; }
    @Override public boolean copimine$isEcho() { return copimine$isEcho; }
    @Override public String copimine$visual() { return copimine$visual; }
    @Override public String copimine$wavePose() { return copimine$wavePose; }
    @Override public String copimine$eventAnimation() { return copimine$eventAnimation; }
    @Override public boolean copimine$endBoss() { return copimine$endBoss; }
    @Override public String copimine$bossPhase() { return copimine$bossPhase; }
    @Override public long copimine$bossPhaseTransitionMillis() { return copimine$bossPhaseTransitionMillis; }
    @Override public String copimine$bossAnimation() { return copimine$bossAnimation; }
}
