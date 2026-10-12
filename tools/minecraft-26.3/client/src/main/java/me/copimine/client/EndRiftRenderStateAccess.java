package me.copimine.client;

/** Event identity copied into a render snapshot before the entity can leave the world. */
public interface EndRiftRenderStateAccess {
    void copimine$bindRenderState(String uuid, int entityId, boolean echo, String visual,
                                  String wavePose, String eventAnimation, boolean endBoss,
                                  String bossPhase, long bossPhaseTransitionMillis,
                                  String bossAnimation);
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
