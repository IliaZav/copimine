from pathlib import Path


def test_missing_shield_repair_is_bounded_and_exhaustion_aborts_invisible_gameplay():
    source = (Path(__file__).resolve().parents[1] / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    assert 'ritualShieldRepairAttempts' in source
    assert 'shieldRepairAttempt >= 1' in source
    assert 'ritualVisualRecoveryFailure = "SHIELD_REPAIR_EXHAUSTED"' in source
    assert 'abortRitualVisualRecovery();' in source
ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / 'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java'


def test_guard_shield_fallback_inherits_the_existing_boss_shield_asset():
    import json
    root = ROOT / 'resourcepacks/src/assets/copimine/models/item'
    shield = json.loads((root / 'end_event_ritual_caster_shield.json').read_text(encoding='utf-8'))
    assert shield.get('parent') == 'copimine:item/end_event_rift_guardian_shield'
    boss = json.loads((root / 'end_event_rift_guardian_shield.json').read_text(encoding='utf-8'))
    assert boss['elements'] and boss['textures']['shield'] == 'copimine:item/end_event_rift_guardian_shield_hd'


def test_guards_hold_posts_without_the_generic_ai_reenabling_wandering():
    ensure = method('private void ensureEventCombatAi(')
    assert 'isCurrentRitualGuard(entity)' in ensure
    assert ensure.index('isCurrentRitualGuard(entity)') < ensure.index('mob.setAI(true)')
    assert 'isCurrentRitualGuard(entity)' in method('private boolean isWaveAiCombatEntity(')
    tick = method('private void tickRitualGuardGroups(')
    assert 'tickRitualGuardPost(' in tick
    post = method('private void tickRitualGuardPost(')
    assert 'RitualGuardAggroPolicy.stance(' in post
    assert 'guard.setAI(false)' in post and 'guard.setAware(false)' in post
    assert 'faceRitualCaster(guard,' in post


def test_skeleton_type_is_not_mistaken_for_an_incoming_ranged_attack():
    for signature in ('private Player ritualNearestGuardTarget(', 'private boolean ritualGuardTargetAllowed('):
        assert 'guard instanceof Skeleton' not in method(signature)


def test_player_projectiles_wake_the_guard_group_even_when_the_caster_is_shielded():
    trace = method('private Player ritualDamageAttacker(')
    assert 'Projectile' in trace and 'getShooter() instanceof Player' in trace
    source = SERVER.read_text(encoding='utf-8')
    assert 'Player player = ritualDamageAttacker(byEntity)' in source
    assert 'Player player = ritualDamageAttacker(event)' in source


def test_ritual_beam_material_is_independent_of_third_party_beacon_textures():
    renderer = (ROOT / 'CopiMineClient/src/main/java/me/copimine/client/EndEventWorldVfxManager.java').read_text(encoding='utf-8')
    assert 'RenderLayer.getLightning()' in renderer
    assert 'beacon_beam.png' not in renderer
    assert 'RitualBeamMesh.quads(' in renderer
    assert 'drawRibbon(buffer, entry, beam, nowMillis)' in renderer, 'other wave beam mechanics keep their existing renderer'


def test_authoritative_health_consumer_rechecks_its_own_casters_guards_before_damage():
    # Both protection and health listeners have HIGHEST priority; registration
    # order cannot be used as a guarantee before a direct setHealth transaction.
    body = method('public void onWaveMobPlayerDamageAuthoritative(')
    assert 'blockRitualCasterShieldHit(event, victim)' in body
    assert body.index('blockRitualCasterShieldHit(event, victim)') < body.index('EventRealHealthDamagePolicy.apply(')
    gate = method('private boolean blockRitualCasterShieldHit(')
    assert 'ritualGuardsByCaster.getOrDefault(caster.getUniqueId()' in gate
    assert 'ritualGuardCasters.get(guardId)' in gate
    assert 'isLiveOwnedEntity(guardId)' in gate
    assert 'event.setCancelled(true)' in gate


def test_accepted_guard_hit_sets_alarm_before_the_native_damage_is_cancelled():
    body = method('public void onWaveMobPlayerDamageAuthoritative(')
    assert 'alertRitualGuardGroup(victim, attacker)' in body
    assert body.index('alertRitualGuardGroup(victim, attacker)') < body.index('EventRealHealthDamagePolicy.apply(')
    alert = method('private void alertRitualGuardGroup(')
    assert 'isCurrentRitualGuard(guard)' in alert
    assert 'ritualGuardCasters.get(guardId)' in alert
    assert 'ritualCasterAlertUntil.put' in alert


def test_awakened_caster_has_no_desired_or_refreshed_sphere_beam():
    render = method('private void renderCurrentRitualSphere(')
    assert render.count('RitualCasterTacticsPolicy.castsSphere(ritualCasterTacticsState(') >= 2
    assert 'clearWorldVfxBeamsOutside("wave6-ritual-", desired)' in render


def test_caster_shields_face_each_viewer_and_the_fallback_billboards_too():
    renderer = (ROOT / 'CopiMineClient/src/main/java/me/copimine/client/RitualSphereRenderer.java').read_text(encoding='utf-8')
    assert 'matrices.multiply(context.camera().getRotation())' in renderer
    assert 'setBillboard(Display.Billboard.CENTER)' in method('private void reconcileRitualCasterShields(')


def test_player_projectile_impact_on_a_shield_has_its_own_feedback_path():
    hit = method('public void onRitualCasterShieldProjectile(')
    assert 'event.getHitEntity()' in hit
    assert 'RitualCasterShieldPolicy.blocksDamage' in hit
    assert 'renderRitualShieldHit' in hit
    feedback = method('private void renderRitualShieldHit(')
    assert 'Sound.BLOCK_METAL_HIT' in feedback
    assert 'Sound.ENTITY_ARROW_HIT' in feedback
    assert 'Sound.ITEM_SHIELD_BLOCK' in feedback


def test_sphere_spell_choice_uses_accumulated_unlocks_not_the_dead_casters_role():
    cast = method('private void castNextRitualAbility(')
    assert 'RitualCasterProgressionPolicy.availableSpells(ritualCasterDeathCount)' in cast
    assert 'ritualSpellForRole(candidateRole)' not in cast
    assert 'Role.FINAL_SEAL' not in cast
    death = method('private void handleRitualCasterDeath(')
    assert 'progression.disabledSpell()' not in death
    assert 'progression.addedSpell()' in death


def test_ritual_beams_start_at_both_raised_hands_and_floating_carrier_uses_owned_teleport():
    render = method('private void renderCurrentRitualSphere(')
    assert 'RitualCasterHandPolicy.hands(' in render
    assert 'getEyeLocation().add(0.0D, 1.1D' not in render
    assert '"-hand-" + handIndex' in render
    channel = method('private void renderRitualSphereChanneling(')
    assert 'teleportCombatEntity(display, center)' in channel
    assert 'display.teleport(center)' not in channel
    model = (ROOT / 'CopiMineClient/src/main/java/me/copimine/client/RiftEventEndermanModel.java').read_text(encoding='utf-8')
    policy = (ROOT / 'copimine-end-event/src/me/copimine/endevent/domain/RitualCasterHandPolicy.java').read_text(encoding='utf-8')
    assert '-2.62' in model and '-2.62' in policy
    assert '0.035' in model and '0.035' in policy
    assert '0.18' in model and '0.18' in policy
    pulse = method('private void castRitualSpherePulse(')
    assert 'RitualCasterHandPolicy.hands(' in pulse
    assert 'Location start = caster.getEyeLocation()' not in pulse


def test_solo_guard_roster_includes_all_roles_and_spider_snare_is_an_owned_projectile():
    spawn = method('private boolean startRitualSphereObjective(')
    assert 'RitualGuardAbilityPolicy.roleSlot(casterSlot, guardSlot)' in spawn
    tick = method('private void tickRitualGuardAbilities(')
    assert 'RitualGuardAbilityPolicy.roleSlot(' in tick
    release = method('private void resolveRitualGuardAbility(')
    assert 'Role.WEB_SNARE' in release
    assert 'spawnRitualGuardWebProjectile(' in release
    projectile = method('private boolean spawnRitualGuardWebProjectile(')
    assert 'ARROW_SPELL_RITUAL_GUARD_WEB' in projectile
    assert 'tagRitualProjectile(arrow)' in projectile and 'trackEventArrow(arrow)' in projectile
    assert 'committedAim' in projectile
    hit = method('private void onCustomEventArrowDamage(')
    assert 'ARROW_SPELL_RITUAL_GUARD_WEB.equals(spell)' in hit
    assert 'RitualGuardAbilityPolicy.forEntityType("SPIDER").damage()' in hit
    trail = method('private void spawnEventArrowTrail(')
    assert 'ARROW_SPELL_RITUAL_GUARD_WEB.equals(spell)' in trail
    assert 'Material.COBWEB' in trail


def test_major_spell_visuals_are_world_packets_with_immediate_owned_cleanup():
    tick = method('private void tickCurrentRitualSphereObjective(')
    assert 'renderRitualSpellPresentation(now)' in tick
    visual = method('private void renderRitualSpellPresentation(')
    assert 'isFreeRitualTarget(target)' in visual
    for spell in ('RIFT_BARRAGE', 'GRAVITY_WELL', 'SOUL_BRAND', 'RIFT_CHAINS'):
        assert 'case ' + spell in visual
    emit = method('private void emitRitualSpellSegment(')
    assert 'sendWorldBeamPacket(' in emit
    assert 'isInsideRitualSphereViewer' in emit
    for signature in ('private void cancelActiveRitualSpellEffects(', 'private void clearRitualSphereObjective('):
        assert 'clearWorldVfxBeamsByPrefix("wave6-spell-")' in method(signature)


def test_spider_web_retires_with_its_own_guard_and_caster_before_damage():
    launch = method('private boolean spawnRitualGuardWebProjectile(')
    assert 'tagRitualProjectileOrigin(arrow, start)' in launch
    assert 'tagRitualProjectileTarget(arrow, guard, target)' in launch
    provenance = method('private boolean ritualGuardWebProvenanceAllowed(')
    assert 'isCurrentRitualGuard(guard)' in provenance
    assert 'ritualGuardCasters.get(' in provenance
    assert 'ritualCasterUuids.contains(caster)' in provenance and 'isLiveOwnedEntity(caster)' in provenance
    assert 'RitualSphereProjectileProvenancePolicy.accepts(' in provenance
    for signature in ('private boolean isEventArrowPhaseAllowed(', 'private boolean ritualProjectileTargetAllowed('):
        assert 'ritualGuardWebProvenanceAllowed(' in method(signature)


def test_force_spell_hook_does_not_require_the_caster_role_that_has_already_died():
    hook = method('private boolean forceRitualCoreAbilityForTest(')
    assert 'RitualCasterProgressionPolicy.isSpellEnabled(' in hook
    assert 'roleForSlot(' not in hook
    assert 'isLiveOwnedEntity(entity.getUniqueId())' in hook


def test_spell_textures_and_screen_cues_share_the_actual_stage_and_target_gates():
    visual = method('private void renderRitualSpellPresentation(')
    assert '"wave6-spell-glyph-"' in visual
    assert '"wave6-spell-screen-"' in visual
    assert 'sendWorldBeamPacket(target,' in visual
    assert 'ritualSpellTargetLocation' in visual and 'RitualZoneEffectPolicy.RADIUS_BLOCKS' in visual
    audio = method('private void renderRitualSpellAmbientFeedback(')
    assert 'ritualNextSpellSoundMillis' in audio
    for sound in ('BLOCK_BEACON_AMBIENT', 'BLOCK_AMETHYST_BLOCK_CHIME', 'BLOCK_CHAIN_HIT'):
        assert sound in audio
    assert 'isInsideRitualSphereViewer' in audio
    for spell in ('barrage', 'gravity', 'brand', 'chains'):
        from PIL import Image
        texture = ROOT / f'CopiMineClient/src/main/resources/assets/copimineclient/textures/entity/ritual_spell_{spell}.png'
        with Image.open(texture) as image:
            assert image.size == (64, 64) and image.mode == 'RGBA'
            alpha = list(image.getchannel('A').getdata())
            assert min(alpha) == 0 and 160 <= max(alpha) <= 224
            assert sum(value == 0 for value in alpha) > len(alpha) / 3, 'open glyphs leave the arena visible'

def method(signature):
    source = SERVER.read_text(encoding='utf-8')
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

def test_capture_command_places_tester_on_seal_and_calls_shared_capture():
    assert 'prepareSandboxRitualCapture(' in method('private void handleTest(')
    prepare = method('private boolean prepareSandboxRitualCapture(')
    assert 'attemptRitualPrisonerCapture(' in prepare
    assert '.teleport(' in prepare
    assert 'RitualSphereEncounterPolicy.capture(' not in prepare
    assert 'ritualPrisonerId()' in prepare
    assert 'isPhysicallyEligibleWaveParticipant(player, wave == 6 && !combatMode)' in method('private boolean spawnTestWave(')
    assert 'captureTestRoster' in method('private boolean isPhysicallyEligibleWaveParticipant(Player player, boolean captureTestRoster)')

def test_sphere_display_stays_centered_and_repairs_missing_carrier():
    spawn = method('private void spawnRitualSphereVisual(')
    assert 'setBillboard(Display.Billboard.FIXED)' in spawn
    assert 'new Vector3f()' in spawn
    tick = method('private void tickCurrentRitualSphereObjective(')
    assert 'ensureRitualSphereVisual(' in tick


def test_sphere_owned_teleport_exception_is_limited_to_its_calculated_anchor():
    teleport = method('public void onOwnedEntityTeleport(')
    assert teleport.index('if (!internalTeleport)') < teleport.index('isOwnedRitualSphereDestination(entity, target)')
    gate = method('private boolean isOwnedRitualSphereDestination(')
    assert 'ritualSphereVisualUuid' in gate and 'ownedBySession(entity, eventId, generation)' in gate
    assert 'RitualSpherePresentationPolicy.atSphereAnchor(' in gate
    assert 'ritualSphereCenter(coreCombatAnchorLocation())' in gate


def test_forced_caster_death_cannot_leave_orphan_guards_or_their_cast():
    death = method('private void handleRitualCasterDeath(')
    assert 'Set.copyOf(ritualGuardsByCaster.getOrDefault(casterId, Set.of()))' in death
    assert 'removeRitualEntity(guardId)' in death
    assert 'ritualGuardUuids.remove(guardId)' in death and 'ritualGuardCasters.remove(guardId)' in death
    remove = method('private void removeRitualEntity(')
    assert 'cancelRitualGuardAbilityCast(' in remove


def test_legacy_wave6_cleanup_preserves_current_pressure_mobs_and_guard_shields():
    # Bootstrap re-indexes survivors before restoring the ritual objective.
    # The legacy sweep must not delete that live pressure pack or its shields.
    cleanup = method('private void cleanupLegacyWave6Entities(')
    assert 'ownedBySession(entity, eventId, generation)' in cleanup
    assert 'EVENT_KIND_WAVE_MOB.equals(kind)' in cleanup
    assert 'EVENT_KIND_ELITE.equals(kind)' in cleanup
    assert '"CASTER_SHIELD".equals(readString(entity, keyRitualRole))' in cleanup
    assert cleanup.index('ownedBySession(entity, eventId, generation)') < cleanup.index('entity.remove()')


def test_raised_captive_still_receives_arena_visuals_above_the_arena_y_bounds():
    audience = method('private List<Player> eventAudience(')
    assert 'isInsideOwnedPrisonerAnchor(player)' in audience
    anchor = method('private boolean isInsideOwnedPrisonerAnchor(')
    assert 'isCurrentRitualPrisoner(player)' in anchor
    assert 'wave6PrisonBroken' in anchor and 'RitualSpherePresentationPolicy.insidePrisonerAnchor(' in anchor


def test_prisoner_support_abilities_accept_the_owned_high_anchor_and_still_bound_the_target():
    gate = method('private boolean isRitualAbilityTargetAllowed(')
    assert 'isInsideOwnedPrisonerAnchor(sender)' in gate
    assert 'isArenaLocation(target.getLocation())' in gate
    assert 'isCurrentRitualPrisoner(sender)' in method('private void handleRitualPrisonerAbilityRequest(')
    assert 'maximumRange * maximumRange' in gate and 'sender.getWorld().equals(target.getWorld())' in gate


def test_actual_prisoner_target_adapter_accepts_high_anchor_and_rejects_invalid_targets(tmp_path):
    """Execute the production admission methods with controlled Bukkit inputs."""
    import subprocess

    gate = method('private boolean isRitualAbilityTargetAllowed(')
    anchor = method('private boolean isInsideOwnedPrisonerAnchor(')
    arena = method('private boolean isArenaLocation(')
    harness = r"""
import java.util.UUID;
public final class RaisedPrisonerAbilityFixture {
    static final class World {
        String getName() { return "CopiMine"; }
    }
    static final class Location {
        final World world; final double x,y,z;
        Location(World w,double x,double y,double z) { world=w; this.x=x; this.y=y; this.z=z; }
        World getWorld() { return world; }
        double getX() { return x; } double getY() { return y; } double getZ() { return z; }
        int getBlockX() { return (int)Math.floor(x); }
        int getBlockY() { return (int)Math.floor(y); }
        int getBlockZ() { return (int)Math.floor(z); }
        double distanceSquared(Location b) { return (x-b.x)*(x-b.x)+(y-b.y)*(y-b.y)+(z-b.z)*(z-b.z); }
    }
    static class Entity {
        Location pos; boolean valid=true; boolean convertible;
        Entity(Location p) { pos=p; }
        boolean isValid() { return valid; }
        World getWorld() { return pos.world; }
        Location getLocation() { return pos; }
    }
    static class LivingEntity extends Entity {
        boolean dead; double health=20;
        LivingEntity(Location p) { super(p); }
        boolean isDead() { return dead; } double getHealth() { return health; }
    }
    static final class Player extends LivingEntity {
        final UUID id=UUID.randomUUID(); boolean online=true,combat=true,los=true;
        Player(Location p) { super(p); }
        boolean isOnline() { return online; }
        UUID getUniqueId() { return id; }
        boolean hasLineOfSight(LivingEntity e) { return los; }
    }
    static final class PrisonerAbilityController {
        enum Ability { HEAL,BATTLE_SURGE,GUARDIAN_LINK,TURNCOAT }
    }
    String worldName="CopiMine"; int arenaMinX=-12,arenaMaxX=28,arenaMinY=65,arenaMaxY=71,arenaMinZ=-59,arenaMaxZ=-19;
    Player prisoner; Location ritualPrisonerAnchor; boolean wave6PrisonBroken;
    boolean isConfigured() { return true; }
    boolean isCurrentRitualPrisoner(Player p) { return p==prisoner; }
    boolean isCombatTarget(Player p) { return p.combat && p.online && !p.dead; }
    boolean isRitualConvertibleHostile(Entity e) { return e.convertible; }
    ADAPTER_METHODS
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        var f=new RaisedPrisonerAbilityFixture(); var world=new World();
        f.ritualPrisonerAnchor=new Location(world,8.5,73.3,-38.5);
        f.prisoner=new Player(f.ritualPrisonerAnchor);
        var ally=new Player(new Location(world,11.5,68,-30.5));
        for(var ability:new PrisonerAbilityController.Ability[]{PrisonerAbilityController.Ability.HEAL,
            PrisonerAbilityController.Ability.BATTLE_SURGE,PrisonerAbilityController.Ability.GUARDIAN_LINK})
            check(f.isRitualAbilityTargetAllowed(f.prisoner,ability,ally),"high captive must support ally: "+ability);
        var mob=new LivingEntity(new Location(world,4.5,68,-29.5)); mob.convertible=true;
        check(f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.TURNCOAT,mob),
            "high captive must convert eligible ordinary mob");
        var outsider=new Player(f.ritualPrisonerAnchor);
        check(!f.isRitualAbilityTargetAllowed(outsider,PrisonerAbilityController.Ability.HEAL,ally),
            "outside sender without owned anchor must fail");
        f.wave6PrisonBroken=true;
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"broken prison must fail");
        f.wave6PrisonBroken=false;
        var original=f.prisoner.pos; f.prisoner.pos=new Location(world,8.5,74,-38.5);
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"off-anchor sender must fail");
        f.prisoner.pos=original; var allyPosition=ally.pos;
        ally.pos=new Location(world,11.5,72,-30.5);
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"target must remain in arena");
        ally.pos=new Location(new World(),11.5,68,-30.5);
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"different-world target must fail");
        ally.pos=allyPosition; f.prisoner.los=false;
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"occluded target must fail");
        f.prisoner.los=true; ally.combat=false;
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"non-roster ally must fail");
        ally.combat=true; ally.pos=new Location(world,28,68,-19);
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"out-of-range target must fail");
        ally.pos=allyPosition; ally.dead=true;
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.HEAL,ally),"dead ally must fail");
        ally.dead=false; mob.convertible=false;
        check(!f.isRitualAbilityTargetAllowed(f.prisoner,PrisonerAbilityController.Ability.TURNCOAT,mob),"ineligible hostile must fail");
        System.out.println("RaisedPrisonerAbilityFixture OK");
    }
}
"""
    policy = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain/RitualSpherePresentationPolicy.java'
    for name, actual_gate in (
        ('current', gate),
        ('before_fix', gate.replace(
            '!isArenaLocation(sender.getLocation()) && !isInsideOwnedPrisonerAnchor(sender)',
            '!isArenaLocation(sender.getLocation())')),
    ):
        output = tmp_path / name
        output.mkdir()
        fixture = output / 'RaisedPrisonerAbilityFixture.java'
        fixture.write_text(harness.replace('ADAPTER_METHODS', actual_gate + '\n' + anchor + '\n' + arena),
                           encoding='utf-8')
        compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(output), str(policy), str(fixture)],
                                  capture_output=True, text=True)
        assert compiled.returncode == 0, compiled.stdout + compiled.stderr
        run = subprocess.run(['java', '-cp', str(output), 'RaisedPrisonerAbilityFixture'],
                             capture_output=True, text=True)
        if name == 'current':
            assert run.returncode == 0, run.stdout + run.stderr
            assert 'RaisedPrisonerAbilityFixture OK' in run.stdout
        else:
            assert run.returncode != 0 and 'high captive must support ally: HEAL' in run.stderr, (
                'the regression must detect the old sender-height gate', run.stdout, run.stderr)

def test_each_guard_has_a_lifecycle_owned_shield_and_death_breaks_it():
    reconcile = method('private void reconcileRitualCasterShields(')
    assert 'ritualGuardsByCaster' in reconcile
    assert 'isLiveOwnedEntity(guardId)' in reconcile
    assert 'ritualShieldDisplayByGuard' in reconcile
    assert 'RitualShieldOrbitPolicy.pose(' in reconcile
    source = SERVER.read_text(encoding='utf-8')
    assert 'removeRitualGuardShield(guardId, true)' in source
    assert 'clearRitualCasterShields(' in method('private void clearRitualSphereObjective(')

def test_prisoner_is_not_sent_central_particle_clouds():
    for signature in ('private void renderCurrentRitualSphere(', 'private void renderRitualSphereChanneling('):
        body = method(signature)
        assert 'isInsideRitualSphereViewer(viewer, center)' in body

def test_caster_channel_pose_stays_raised_during_release():
    body = method('private void tickRitualGuardGroups(')
    assert '&& !spellReleasing' not in body

def test_broken_prison_cannot_recreate_the_sphere_on_the_next_tick():
    tick = method('private void tickCurrentRitualSphereObjective(')
    assert tick.index('if (wave6PrisonBroken)') < tick.index('ensureRitualSphereVisual(')

def test_caster_phase_is_sent_on_binding_and_during_shared_gameplay():
    assert 'sendRitualCasterPhase(' in method('private void bindEventEntityClient(')
    assert 'sendRitualCasterPhase(' in method('private void tickRitualGuardGroups(')
    phase = method('private void sendRitualCasterPhase(')
    for state in ('RITUAL_CHANNEL', 'RITUAL_WINDUP', 'RITUAL_RELEASE', 'RITUAL_COMBAT'):
        assert state in phase

def test_sphere_repair_has_a_limit_and_keeps_the_ownership_scope():
    repair = method('private void ensureRitualSphereVisual(')
    assert 'ritualSphereRepairAttempts' in repair
    assert 'wave6PrisonBroken' in repair
    assert 'removeRitualEntity(' in repair


def test_official_live_probe_expectations_match_real_scaling_policy(tmp_path):
    """Run both real profile providers so a stale live oracle cannot reject the new cadence."""
    import json
    import subprocess

    runner = (ROOT / 'tests/RunEndRiftOfficialTwoPlayerLive.ps1').read_text(encoding='utf-8')
    function = runner[runner.index('function Get-RitualScalingProfile {'):runner.index('\n$ritualProfile =')]
    probe = tmp_path / 'profiles.ps1'
    probe.write_text(function + '''
$ErrorActionPreference = 'Stop'
@(0..21 | ForEach-Object {
    $profile = Get-RitualScalingProfile -Participants $_
    [pscustomobject]@{ Input = $_; Casters = $profile.Casters; Guards = $profile.Guards;
        Projectiles = $profile.Projectiles; Zones = $profile.Zones;
        MajorCooldownSeconds = $profile.MajorCooldownSeconds }
}) | ConvertTo-Json -Compress
''', encoding='utf-8')
    powershell = subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(probe)],
                               capture_output=True, text=True, timeout=30)
    assert powershell.returncode == 0, powershell.stderr
    actual = json.loads(powershell.stdout)

    fixture = tmp_path / 'ScalingProbe.java'
    fixture.write_text('''
import me.copimine.endevent.domain.RitualSphereScalingPolicy;
public class ScalingProbe {
    public static void main(String[] args) {
        for (int input=0; input<=21; input++) {
            var p=RitualSphereScalingPolicy.forPlayers(input);
            System.out.printf("%d,%d,%d,%d,%d,%d%n", input, p.casterCount(), p.guardCount(),
                p.projectilesPerVolley(), p.simultaneousZones(), p.majorCooldownSeconds());
        }
    }
}
''', encoding='utf-8')
    domain = ROOT / 'copimine-end-event/src/me/copimine/endevent/domain'
    classes = tmp_path / 'classes'
    classes.mkdir()
    compile_result = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes), str(fixture),
        *(str(domain / name) for name in ('RitualSphereScalingPolicy.java', 'WaveMechanicsPolicy.java',
                                          'PressureBudgetController.java'))],
        capture_output=True, text=True, timeout=30)
    assert compile_result.returncode == 0, compile_result.stderr
    server = subprocess.run(['java', '-cp', str(classes), 'ScalingProbe'],
                            capture_output=True, text=True, timeout=30)
    assert server.returncode == 0, server.stderr
    keys = ('Input', 'Casters', 'Guards', 'Projectiles', 'Zones', 'MajorCooldownSeconds')
    expected = [dict(zip(keys, map(int, line.split(',')))) for line in server.stdout.splitlines()]
    assert actual == expected, 'live probe scaling oracle must match the gameplay policy for every party size'


def test_real_wave_and_miniboss_target_adapters_exclude_the_captive(tmp_path):
    import subprocess

    declarations = method('private boolean isWaveTargetAllowed(') + method('private void tickMiniBosses(')
    source = SERVER.read_text(encoding='utf-8')
    if 'private boolean isMiniBossTargetAllowed(' in source:
        declarations += method('private boolean isMiniBossTargetAllowed(')
    if 'private boolean isWaveRitualTargetAllowed(' in source:
        declarations += method('private boolean isWaveRitualTargetAllowed(')
    execute = method('private void executeMiniBossSpell(')
    declarations += execute[:execute.index('        launchSpellFlight(')] + 'executions++;\n}\n'
    impact_guard = execute[execute.index('                    if (taskRegistry'):
                           execute.index('                    getLogger()')]
    declarations += '''
    void impactGate(LivingEntity miniBoss, Player target, EndRiftAiPolicy.MiniBossSpell spell,
                    long callbackGeneration) {
    ''' + impact_guard + 'executions++;\n}\n'
    fixture = tmp_path / 'FreeTargetProbe.java'
    fixture.write_text(r'''
import java.util.*;
public class FreeTargetProbe {
    int activeWave=6, waveTargetCursor, executions; Object ritualSphereState=new Object(), keyWave=new Object();
    Registry taskRegistry=new Registry();
    static class Registry { boolean owns(long generation){return generation==1;} }
    static class Location { }
    boolean wave6PrisonBroken, localTextureShowcase;
    Map<UUID,Entity> ownedEntities=new HashMap<>();
    Map<UUID,EndRiftAiPolicy.MiniBossSpell> miniBossSpells=new HashMap<>();
    Map<UUID,Long> nextMiniBossSpellMillis=new HashMap<>();
    Config config=new Config(); Player selected;
    record Config() { Config miniBossTuning(){return this;} int spellMinSeconds(){return 1;} int spellMaxSeconds(){return 1;} }
    static class Entity { UUID id=UUID.randomUUID(); int wave=6; UUID getUniqueId(){return id;}
        boolean isValid(){return true;} boolean isDead(){return false;} }
    static class LivingEntity extends Entity { }
    static class Player extends LivingEntity {
        boolean free, combat=true, room=true;
        Player(long id, boolean free){this.id=new UUID(0,id);this.free=free;}
    }
    static class Mob extends LivingEntity { LivingEntity target; LivingEntity getTarget(){return target;} void setTarget(Player p){target=p;} }
    static class Bukkit {
        static List<Player> players=List.of();
        static Player getPlayer(UUID id){return players.stream().filter(p->p.id.equals(id)).findFirst().orElse(null);}
    }
    static class EndRiftAiPolicy {
        enum MiniBossSpell { ECHO_PULSE }
        record TargetChoice(UUID target) { }
        static TargetChoice chooseFairTarget(List<UUID> ids,UUID current,List<UUID> recent,int cursor){
            return new TargetChoice(current!=null && ids.contains(current) ? current : ids.get(0));
        }
    }
    boolean isCombatTarget(Player p){return p!=null && p.combat;}
    boolean realitySplitTargetAllowed(Entity e,Player p){return p!=null && p.room;}
    boolean isFreeRitualTarget(Player p){return p!=null && p.free && p.combat;}
    int readInt(Entity e,Object key,int fallback){return e.wave;}
    boolean isCurrentRitualGuard(Entity e){return false;}
    boolean ritualGuardTargetAllowed(Entity e,Player p){return true;}
    boolean isCurrentRitualCaster(Entity e){return false;}
    boolean ritualCasterCanAttack(Entity e){return true;}
    boolean ritualTargetAllowed(Entity e,Player p){return true;}
    boolean isCurrentCollapseGuard(Entity e){return false;}
    boolean currentCollapseTargetAllowed(Entity e,Player p){return true;}
    boolean isMiniBossCombatPhase(){return true;}
    boolean isLiveOwnedEntity(UUID id){return true;}
    boolean isMiniBossCombatEntity(LivingEntity e){return true;}
    List<Player> activeWaveParticipants(){return Bukkit.players;}
    int randomSeconds(int a,int b){return a;}
    void telegraphMiniBossSpell(LivingEntity e,Player p,EndRiftAiPolicy.MiniBossSpell spell){selected=p;}
    static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
    void selection(Mob mob,Player expected){
        mob.target=Bukkit.players.get(0);
        selected=null; nextMiniBossSpellMillis.clear(); tickMiniBosses();
        check(selected==expected,"miniboss must select the eligible free target, including after its current target becomes captive");
    }
    public static void main(String[] args){
        var f=new FreeTargetProbe(); var captive=new Player(1,false); var free=new Player(2,true);
        Bukkit.players=List.of(captive,free); var mob=new Mob(); mob.target=captive;
        f.ownedEntities.put(mob.id,mob); f.miniBossSpells.put(mob.id,EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE);
        if(args[0].equals("ordinary")) {
            check(!f.isWaveTargetAllowed(mob,captive),"ordinary W6 pressure must not target the captive");
            check(f.isWaveTargetAllowed(mob,free),"ordinary W6 pressure must retain free targets");
            free.room=false; check(!f.isWaveTargetAllowed(mob,free),"cross-room protection remains authoritative"); free.room=true;
        } else f.selection(mob,free);
        f.executeMiniBossSpell(mob,captive,new Location(),EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE,1);
        f.impactGate(mob,captive,EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE,1);
        check(f.executions==0,"windup/flight callbacks must refuse a target captured after selection");
        f.executeMiniBossSpell(mob,free,new Location(),EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE,1);
        f.impactGate(mob,free,EndRiftAiPolicy.MiniBossSpell.ECHO_PULSE,1);
        check(f.executions==2,"eligible free targets retain spell execution");
        f.wave6PrisonBroken=true;
        check(f.isWaveTargetAllowed(mob,captive),"released W6 players remain combat targets");
        f.selection(mob,captive);
        f.wave6PrisonBroken=false; f.ritualSphereState=null;
        check(f.isWaveTargetAllowed(mob,captive),"inactive ritual preserves previous targeting"); f.selection(mob,captive);
        f.ritualSphereState=new Object();
        for(int wave=1;wave<=7;wave++)if(wave!=6){
            mob.wave=wave; check(f.isWaveTargetAllowed(mob,captive),"other waves preserve previous targeting"); f.selection(mob,captive);
        }
        System.out.println("FreeTargetProbe OK");
    }
''' + declarations + '\n}', encoding='utf-8')
    compile_result = subprocess.run(['javac', '-J-Xmx128m', '-encoding', 'UTF-8', str(fixture)],
                                    capture_output=True, text=True, timeout=30)
    assert compile_result.returncode == 0, compile_result.stderr
    for scenario in ('ordinary', 'miniboss'):
        result = subprocess.run(['java', '-Xms16m', '-Xmx128m', '-cp', str(tmp_path),
                                 'FreeTargetProbe', scenario], capture_output=True, text=True, timeout=30)
        assert result.returncode == 0, result.stdout + result.stderr


def test_actual_guard_warning_redraws_every_event_tick_before_resolve(tmp_path):
    """Execute the production warning branch at the actual five-tick cadence."""
    import subprocess

    body = method('private void tickRitualGuardAbilities(')
    warning = body[:body.index('        if (isRitualMajorSpellBusy())')] + '\n}'
    fixture = tmp_path / 'GuardWarningProbe.java'
    fixture.write_text('''
public class GuardWarningProbe {
    boolean active=true, valid=true, wave6PrisonBroken=false;
    Object ritualGuardAbilityGuard=new Object();
    long eventTickCounter, ritualGuardAbilityResolveAtTick;
    int renders, resolves, clears;
    boolean ritualWaveActive(){return active;}
    boolean isRitualGuardAbilityCastValid(){return valid;}
    void clearRitualGuardAbilityState(String reason){clears++;}
    void cancelRitualGuardAbilityCast(String reason){clears++;}
    void renderRitualGuardAbilityTelegraph(){renders++;}
    void resolveRitualGuardAbility(){resolves++;}
    public static void main(String[] args){
        for(int start=0;start<20;start+=5){
            var probe=new GuardWarningProbe();
            probe.ritualGuardAbilityResolveAtTick=start+20;
            for(int tick=start+5;tick<start+20;tick+=5){
                probe.eventTickCounter=tick;
                probe.tickRitualGuardAbilities(0);
            }
            if(probe.renders!=3||probe.resolves!=0)
                throw new AssertionError("guard warning must redraw at 5/10/15 ticks, start="+start+" renders="+probe.renders);
            probe.eventTickCounter=start+20; probe.tickRitualGuardAbilities(0);
            if(probe.resolves!=1||probe.renders!=3)throw new AssertionError("resolve does not redraw the expired warning");
            probe.valid=false; probe.eventTickCounter=start+25; probe.tickRitualGuardAbilities(0);
            if(probe.clears!=1||probe.renders!=3)throw new AssertionError("invalid owner/target cancels warning");
            probe.active=false; probe.tickRitualGuardAbilities(0);
            if(probe.clears!=2||probe.renders!=3)throw new AssertionError("inactive wave clears warning");
        }
    }
''' + warning + '\n}', encoding='utf-8')
    compiled = subprocess.run(['javac', '-J-Xmx96m', '-encoding', 'UTF-8', str(fixture)],
                              capture_output=True, text=True, timeout=30)
    assert compiled.returncode == 0, compiled.stderr
    executed = subprocess.run(['java', '-Xms8m', '-Xmx96m', '-XX:+UseSerialGC', '-cp', str(tmp_path),
                               'GuardWarningProbe'], capture_output=True, text=True, timeout=30)
    assert executed.returncode == 0, executed.stdout + executed.stderr


def test_actual_visual_abort_preserves_core_pad_rewards_and_foreign_entities(tmp_path):
    """Run the production abort and wave cleanup, including loaded unindexed mobs."""
    import subprocess

    declarations = '\n'.join(method(signature) for signature in (
        'private void abortRitualVisualRecovery(',
        'private boolean isWaveCleanupKind(String kind)',
        'private boolean isWaveCleanupKind(String kind, boolean includeRewards)',
        'private void clearWaveEntities(boolean includeRewards)',
    ))
    constants = ('WAVE_MOB', 'ELITE', 'WAVE_GUARDIAN', 'REALITY_SPLIT_TRIAL',
                 'REALITY_SPLIT_OBJECTIVE', 'RITUAL_CASTER', 'RITUAL_GUARD', 'WAVE_REWARD')
    maps = ('waveGuardianEntities', 'nextWavePathRequestMillis', 'lastWavePathLogMillis',
            'recentWaveTargets', 'waveLastProgressLocations', 'waveLastProgressAt',
            'nextWaveStuckRepositionMillis', 'waveMobTactics', 'combatTeleportPermits',
            'blockedTeleportLogAt', 'nextSkeletonArrowMillis', 'spellServants',
            'miniBossSpells', 'nextMiniBossSpellMillis', 'waveCommanders', 'lootIssuedEntityUuids')
    fields = '\n'.join(f'static final String EVENT_KIND_{kind}="{kind}";' for kind in constants)
    fields += '\n' + '\n'.join(f'Map<UUID,Object> {name}=new HashMap<>();' for name in maps)
    fixture = tmp_path / 'VisualAbortProbe.java'
    fixture.write_text('''
import java.util.*;
import java.util.logging.Logger;
public class VisualAbortProbe {
    static class Entity {
        final UUID id=UUID.randomUUID(); final String kind,event; final long generation;
        boolean removed;
        Entity(String kind,String event,long generation){this.kind=kind;this.event=event;this.generation=generation;}
        UUID getUniqueId(){return id;} boolean isValid(){return !removed;}
        boolean isDead(){return false;} void remove(){removed=true;}
    }
    record World(List<Entity> entities){List<Entity> getEntities(){return entities;}}
    static class Bukkit {
        static List<World> worlds;
        static List<World> getWorlds(){return worlds;}
        static Entity getEntity(UUID id){return worlds.stream().flatMap(w->w.entities.stream())
            .filter(e->e.id.equals(id)).findFirst().orElse(null);}
    }
    enum EventPhase { ACTIVE, RECOVERY_REQUIRED }
    String eventId="this-event", ritualVisualRecoveryFailure="SHIELD_REPAIR_EXHAUSTED", recoveryReason="";
    long generation=42, nextWaveRoleVisualMillis, nextCommanderAuraMillis;
    int activeWave=6, objectiveClears, saves, arrowsCleared;
    boolean testWaveFrontVisualMode;
    EventPhase phase=EventPhase.ACTIVE;
    Object keyKind=new Object();
    Map<UUID,Entity> ownedEntities=new LinkedHashMap<>();
    Logger getLogger(){return Logger.getLogger("VisualAbortProbe");}
    String readString(Entity entity,Object key){return entity.kind;}
    boolean ownedByEvent(Entity entity,String expected){return entity.event.equals(expected);}
    Entity unregisterOwnedEntity(UUID id,String reason){return ownedEntities.remove(id);}
    void clearWaveObjectiveState(){
        objectiveClears++; testWaveFrontVisualMode=false;
        for(var world:Bukkit.worlds)for(var entity:world.entities)if(entity.kind.equals("SPHERE")){
            entity.remove(); ownedEntities.remove(entity.id);
        }
    }
    void clearActiveEventArrows(){arrowsCleared++;}
    void clearCommanderAura(){}
    void cleanupOwnedEntities(String expected,long expectedGeneration){
        for(var world:Bukkit.worlds)for(var entity:world.entities)
            if(entity.event.equals(expected)&&entity.generation==expectedGeneration){entity.remove(); ownedEntities.remove(entity.id);}
    }
    void forcePhase(EventPhase phase,String reason){this.phase=phase;}
    void saveStateAsync(){saves++;}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        for(boolean sandbox:List.of(false,true)){
            var probe=new VisualAbortProbe(); probe.testWaveFrontVisualMode=sandbox;
            var core=new Entity("CORE","this-event",42);
            var pad=new Entity("PAD","this-event",42);
            var gate=new Entity("GATE_MODEL","this-event",42);
            var reward=new Entity("WAVE_REWARD","this-event",42);
            var pressure=new Entity("WAVE_MOB","this-event",42);
            var guard=new Entity("RITUAL_GUARD","this-event",42);
            var caster=new Entity("RITUAL_CASTER","this-event",42);
            var sphere=new Entity("SPHERE","this-event",42);
            var unindexed=new Entity("ELITE","this-event",42);
            var foreign=new Entity("WAVE_MOB","other-event",42);
            var natural=new Entity("NATURAL","",0);
            var entities=List.of(core,pad,gate,reward,pressure,guard,caster,sphere,unindexed,foreign,natural);
            Bukkit.worlds=List.of(new World(entities));
            for(var entity:entities)if(entity.event.equals("this-event")&&entity!=unindexed)probe.ownedEntities.put(entity.id,entity);
            probe.abortRitualVisualRecovery();
            for(var retained:List.of(core,pad,gate,reward,foreign,natural))
                require(!retained.removed,"wave visual abort must preserve "+retained.kind+" sandbox="+sandbox);
            for(var retired:List.of(pressure,guard,caster,sphere,unindexed))
                require(retired.removed,"abort must retire indexed and loaded wave child "+retired.kind);
            require(probe.objectiveClears==1&&probe.arrowsCleared==1,"objective/projectile cleanup runs exactly once");
            require(probe.activeWave==0,"aborted wave is inactive");
            require(probe.saves==(sandbox?1:0),"sandbox save and official recovery remain separate");
            require(probe.phase==(sandbox?EventPhase.ACTIVE:EventPhase.RECOVERY_REQUIRED),"official failure remains fail-closed");
        }
    }
''' + fields + '\n' + declarations + '\n}', encoding='utf-8')
    compiled = subprocess.run(['javac', '-J-Xmx96m', '-encoding', 'UTF-8', str(fixture)],
                              capture_output=True, text=True, timeout=30)
    assert compiled.returncode == 0, compiled.stderr
    executed = subprocess.run(['java', '-Xms8m', '-Xmx96m', '-XX:+UseSerialGC', '-cp', str(tmp_path),
                               'VisualAbortProbe'], capture_output=True, text=True, timeout=30)
    assert executed.returncode == 0, executed.stdout + executed.stderr
