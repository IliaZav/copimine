import me.copimine.endevent.domain.BossHitFeedbackPolicy;

public final class BossHitFeedbackPolicyTest {
    public static void main(String[] args) {
        BossHitFeedbackPolicy.Feedback melee = BossHitFeedbackPolicy.forAcceptedHit(false);
        require(melee.outcome() == BossHitFeedbackPolicy.Outcome.ACCEPTED_MELEE,
                "melee hit must have the melee outcome");
        require(melee.redFlash() && melee.hurtAnimation() && melee.impactParticles(),
                "accepted melee must show flesh feedback");
        require(melee.physicalRecoilHorizontal() >= 0.08D
                        && melee.physicalRecoilHorizontal() <= 0.16D,
                "melee recoil must stay restrained");
        require(melee.physicalRecoilVertical() >= 0.0D
                        && melee.physicalRecoilVertical() <= 0.04D,
                "melee vertical recoil must stay restrained");
        require("ENTITY_PLAYER_ATTACK_STRONG".equals(melee.soundCue()),
                "melee hit must use the built-in strong attack cue");
        require(melee.attackerLocalCue(),
                "accepted melee must confirm the impact to its attacker");

        BossHitFeedbackPolicy.Feedback projectile = BossHitFeedbackPolicy.forAcceptedHit(true);
        require(projectile.outcome() == BossHitFeedbackPolicy.Outcome.ACCEPTED_PROJECTILE,
                "projectile hit must have the projectile outcome");
        require(projectile.redFlash() && projectile.hurtAnimation() && projectile.impactParticles(),
                "accepted projectile must show flesh feedback");
        require(projectile.physicalRecoilHorizontal() == 0.0D
                        && projectile.physicalRecoilVertical() == 0.0D,
                "projectile feedback must not invent melee recoil");
        require("ENTITY_ARROW_HIT".equals(projectile.soundCue()),
                "projectile hit must use the built-in arrow hit cue");

        BossHitFeedbackPolicy.Feedback shield = BossHitFeedbackPolicy.forShieldBlockedHit(false);
        require(shield.outcome() == BossHitFeedbackPolicy.Outcome.SHIELD_BLOCKED_MELEE,
                "shielded melee must have the shield outcome");
        require(!shield.redFlash() && !shield.hurtAnimation() && !shield.impactParticles()
                        && shield.shieldParticles(),
                "shielded melee must not show flesh feedback");
        require("BLOCK_ANVIL_HIT".equals(shield.soundCue()),
                "shielded hit must use a metallic shield cue");
        require(shield.attackerLocalCue(),
                "shield block must confirm the deflection to its attacker");

        BossHitFeedbackPolicy.Feedback shieldProjectile =
                BossHitFeedbackPolicy.forShieldBlockedHit(true);
        require(shieldProjectile.outcome()
                        == BossHitFeedbackPolicy.Outcome.SHIELD_BLOCKED_PROJECTILE,
                "shielded projectile must have the projectile shield outcome");

        BossHitFeedbackPolicy.Feedback phase =
                BossHitFeedbackPolicy.forOutcome(BossHitFeedbackPolicy.Outcome.PHASE_IMMUNE);
        BossHitFeedbackPolicy.Feedback cinematic =
                BossHitFeedbackPolicy.forOutcome(BossHitFeedbackPolicy.Outcome.CINEMATIC_IMMUNE);
        require(!phase.redFlash() && !phase.hurtAnimation() && !phase.impactParticles()
                        && !phase.shieldParticles() && phase.soundCue().isBlank()
                        && phase.feedbackDurationTicks() == 0,
                "phase immunity must be visually silent");
        require(!cinematic.redFlash() && !cinematic.hurtAnimation()
                        && cinematic.soundCue().isBlank(),
                "cinematic immunity must be visually silent");
        System.out.println("BossHitFeedbackPolicyTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
