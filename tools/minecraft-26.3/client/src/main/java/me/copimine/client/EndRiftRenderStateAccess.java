package me.copimine.client;

/** Event identity copied into a render snapshot before the entity can leave the world. */
public interface EndRiftRenderStateAccess {
    void copimine$bindRenderState(String uuid, int entityId, boolean echo, String visual,
                                  String wavePose, String eventAnimation, boolean endBoss,
                                  String bossPhase, long bossPhaseTransitionMillis,
                                  String bossAnimation);
    default void copimine$bindTrialFrame(me.copimine.protocol.Wave7AnimationFrame frame) { }
    default me.copimine.protocol.Wave7AnimationFrame copimine$trialFrame() { return null; }
    default void copimine$poseProgress(float value) { }
    default float copimine$poseProgress() { return 0; }
    /** 0 while an echo's eyes are still dark, 1 once they have lit fully. */
    default void copimine$echoEyeGlow(float value) { }
    default float copimine$echoEyeGlow() { return 1.0F; }
    String copimine$uuid();
    int copimine$entityId();
    boolean copimine$isEcho();
    String copimine$visual();
    String copimine$wavePose();
    String copimine$eventAnimation();
    boolean copimine$endBoss();
    String copimine$bossPhase();
    long copimine$bossPhaseTransitionMillis();
    String copimine$bossAnimation();
}
