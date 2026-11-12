package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.RiftRamPolicy;
import me.copimine.endevent.domain.RiftRamRecoveryPolicy;
import me.copimine.endevent.domain.WaveSixPillarSkeletonPolicy;
import org.bukkit.*;
import me.copimine.endevent.runtime.compat.Attribute;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** One generation's Wave 6. No independent scheduled tasks or terrain writes. */
public final class RiftRamEncounter {
    public interface Host {
        boolean active();
        List<Player> players();
        Location core();
        int floorY();
        boolean inArena(Location location);
        void own(Entity entity, boolean combat);
        boolean owns(Entity entity);
        boolean damage(Ravager source, Player player, double amount);
        /** Damages a player from an event-owned actor that is not the Ram. */
        boolean damage(LivingEntity source, Player player, double amount);
        /** Spawns a visible, event-owned modelled arrow and returns its handle. */
        Arrow spawnBoneArrow(Location origin, Vector direction, double speed, double damage);
        int particleBudget();
        void remove(Entity entity);
        /** Moves an owned entity through the internal permit path; reports refusal. */
        boolean move(Entity entity, Location destination);
        void fail(String reason);
        void visualsChanged();
        boolean checkpoint();
    }
    private enum Action { CHASE, CHARGE_WARNING, CHARGE, STUN, STOMP_WARNING, STOMP, RECOVER, DEAD }
    private record Piece(BlockDisplay display, Vector offset, Vector3f size, int section) {}
    private record Debris(BlockDisplay display, Location start, Vector velocity, int born) {}
    private final Host host;
    private Ravager ram;
    private UUID persistentId;
    private Action action = Action.CHASE;
    private int tick, actionTick, lastCharge, lastStomp, impacts, nextPillarTick;
    /**
     * Collisions already absorbed by the pillar that is currently standing.
     *
     * <p>Distinct from {@link #impacts}, which is the encounter-wide total that
     * drives the armour ladder and the boss bar. The final pillar survives two
     * collisions, so the runtime cannot retire the pillar just because the
     * global count moved.</p>
     */
    private int pillarImpacts;
    private boolean completed, closed, banked;
    private UUID target;
    private Vector direction;
    private Location pillar, stompOrigin;
    private UUID pillarId;
    private double lastRadius;
    private final List<BlockDisplay> pillarDisplays = new ArrayList<>();
    private final List<Piece> armor = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final Set<UUID> chargeHits = new HashSet<>(), stompHits = new HashSet<>(), stompRoster = new HashSet<>();
    private final Set<Endermite> mites = new HashSet<>();
    /**
     * The elite marksman that holds the final pillar.
     *
     * <p>This is the only skeleton-on-a-pillar mechanic in the event. It exists
     * for the duration of the final pillar only, and a survivor drops to the
     * arena floor when that pillar collapses so it can never be orphaned.</p>
     */
    private Skeleton pillarSkeleton;
    private UUID pillarSkeletonId;
    private int skeletonLastTriple = -1, skeletonLastBone = -1, skeletonLastMove = -1, skeletonStep;
    private boolean skeletonFallen, pillarSkeletonSpawned, pillarSkeletonDefeated, ramDefeated;

    public RiftRamEncounter(Host host) { this.host = host; }
    public UUID id() { return ram == null ? persistentId : ram.getUniqueId(); }
    public int impacts() { return impacts; }
    public boolean complete() { return completed; }
    public boolean ready() { return !closed && (ramDefeated ? skeletonAlive()
            : ram != null && ram.isValid() && !ram.isDead()); }
    /** Live body health, or zero when the actor is gone. Presentation only. */
    public double health() {
        return ram != null && ram.isValid() && !ram.isDead() ? Math.max(0.0D, ram.getHealth()) : 0.0D;
    }
    /** Authored maximum health, so the bar's fill is a true share of the real bar. */
    public double maximumHealth() {
        if (ram == null || !ram.isValid()) return 0.0D;
        return Math.max(1.0D, ram.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
    }

    public void start(int players) {
        if (ram != null || closed) throw new IllegalStateException("Rift Ram already started");
        Location core = host.core();
        if (core == null) { fail("no-core-anchor"); return; }
        Location site = findSite(0, null);
        if (site == null) { fail("no-safe-ravager-site"); return; }
        try {
            ram = site.getWorld().spawn(site, Ravager.class, entity -> {
                entity.setPersistent(true);
                entity.setRemoveWhenFarAway(false);
                entity.setCustomName("Таран Разлома");
                entity.setCustomNameVisible(false);
                entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(RiftRamPolicy.healthForPlayers(players));
                entity.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(RiftRamPolicy.BOSS_SCALE);
                entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(RiftRamPolicy.MELEE_DAMAGE);
                entity.setHealth(RiftRamPolicy.healthForPlayers(players));
            });
            host.own(ram, true);
            persistentId=ram.getUniqueId();
            // One 3D section per remaining plate, attached in local body space.
            // No collectible equipment: the plates are display geometry, so they
            // cannot be picked up, dropped or found in a chest.
            createArmor();
            if (!raisePillar()) { fail("no-safe-first-pillar"); return; }
            lastCharge = 0;
            lastStomp = 0;
            if (!host.checkpoint()) { fail("initial-checkpoint-failed"); return; }
            host.visualsChanged();
        } catch (RuntimeException failure) { fail("spawn-failed:" + failure.getClass().getSimpleName()); }
    }

    /**
     * Bolts the armour onto the body, W6-01.
     *
     * <p>Seven independently shed pieces match the seven accepted collisions:
     * two shoulder plates, a back casing and four lower guards. The fixed
     * section identities survive a checkpoint without restoring shed pieces.</p>
     *
     * <p>Sections already shed are skipped, so a restored encounter rebuilds
     * exactly the plates its checkpoint says are still bolted on.</p>
     */
    private void createArmor() {
            addArmor(-1.35,1.35,-.85, .55f,1.75f,2.35f, 1);
            addArmor( 1.05,1.35,-.85, .55f,1.75f,2.35f, 2);
            addArmor(-.95,2.85,-.95, 2.15f,.55f,2.15f, 3);
            addArmor(-1.25,.55,-1.10, .55f,.95f,.75f, 4);
            addArmor( .85,.55,-1.10, .55f,.95f,.75f, 5);
            addArmor(-1.25,.55, .65, .55f,.95f,.75f, 6);
            addArmor( .85,.55, .65, .55f,.95f,.75f, 7);
    }

    /** Deterministic, bounded search; all occupied volume and floor cells must be safe. */
    private Location findSite(int ordinal, Location avoid) {
        Location core = host.core();
        if (core == null) return null;
        for (int i=0;i<32;i++) {
            double angle = (ordinal * 2.399963 + i * Math.PI / 16);
            double radius = 10 + i % 3 * 2;
            Location candidate = new Location(core.getWorld(), core.getBlockX()+.5+Math.cos(angle)*radius,
                    host.floorY()+1,core.getBlockZ()+.5+Math.sin(angle)*radius);
            if (avoid != null && candidate.distanceSquared(avoid)<36) continue;
            if (safeVolume(candidate)) return candidate;
        }
        return null;
    }

    private boolean safeVolume(Location at) {
        if (!host.inArena(at)) return false;
        World world = at.getWorld();
        if (!world.isChunkLoaded(at.getBlockX()>>4, at.getBlockZ()>>4)) return false;
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            Location floor = at.clone().add(x,-1,z);
            if (!host.inArena(at.clone().add(x,0,z)) || !floor.getBlock().getType().isSolid()) return false;
            for(int y=0;y<4;y++) if(!at.clone().add(x,y,z).getBlock().isPassable()) return false;
        }
        return true;
    }

    private BlockDisplay block(Location at, Material material, Vector3f size) {
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(1);
            entity.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),size,new Quaternionf()));
        });
        if(display==null) throw new IllegalStateException("display-spawn-refused");
        host.own(display, false);
        return display;
    }

    private void addArmor(double x,double y,double z,float sx,float sy,float sz,int section) {
        if (section <= impacts) return;
        Vector offset = new Vector(x,y,z);
        Vector3f size = new Vector3f(sx,sy,sz);
        BlockDisplay display = block(ram.getLocation(),Material.NETHERITE_BLOCK,size);
        armor.add(new Piece(display,offset,size,section));
    }

    private boolean raisePillar() {
        if (!RiftRamPolicy.mayRaisePillar(impacts) || pillarId!=null) return false;
        Location site = findSite(impacts+1,ram.getLocation());
        if (site==null) return false;
        pillar = site;
        pillarId = UUID.randomUUID();
        pillarImpacts = 0;
        return buildPillar();
    }

    private boolean buildPillar() {
        Location site = pillar;
        try {
            // The final pillar is the finale of the wave, so it is built at
            // roughly double the footprint and height of an ordinary one and
            // carries a glowing core seam that reads from across the arena.
            int ordinal = RiftRamPolicy.pillarOrdinal(impacts);
            boolean finale = RiftRamPolicy.isFinalPillar(ordinal);
            float footprint = finale ? 3.6f : 1.8f;
            int damageStage = finale ? RiftRamPolicy.finalPillarStage(impacts) : 0;
            float shrink = damageStage >= 2 ? .82f : damageStage == 1 ? .94f : 1f;
            float height    = (finale ? 7.4f : 3.7f)
                    * (damageStage >= 2 ? .68f : damageStage == 1 ? .88f : 1f);
            footprint *= shrink;
            float capWidth  = (finale ? 4.4f : 2.2f) * shrink;
            float bodyHalf  = footprint / 2f;
            float capHalf   = capWidth / 2f;
            pillarDisplays.add(block(site.clone().add(-bodyHalf,0,-bodyHalf),
                    damageStage >= 2 ? Material.MAGMA_BLOCK : Material.OBSIDIAN,
                    new Vector3f(footprint,height-.65f,footprint)));
            pillarDisplays.add(block(site.clone().add(-capHalf,height-.65f,-capHalf),
                    Material.CRYING_OBSIDIAN,new Vector3f(capWidth,.45f,capWidth)));
            pillarDisplays.add(block(site.clone().add(-(capHalf-.05f),height-.2f,-(capHalf-.05f)),
                    finale ? Material.MAGMA_BLOCK : Material.OBSIDIAN,
                    new Vector3f(capWidth-.1f,.2f,capWidth-.1f)));
            if (finale) {
                // Exposed core band, hidden again by crackPillar() once the
                // shell finally breaks.
                pillarDisplays.add(block(site.clone().add(-(bodyHalf+.03f),height*.45f,-(bodyHalf+.03f)),
                        Material.CRYING_OBSIDIAN,new Vector3f(footprint+.06f,.25f,footprint+.06f)));
            }
            nextPillarTick = tick;
            site.getWorld().playSound(site,Sound.BLOCK_DEEPSLATE_PLACE, finale?2.4f:1.6f, finale?.35f:.5f);
            if (finale && !pillarSkeletonSpawned) {
                spawnPillarSkeleton();
                if (!skeletonAlive()) throw new IllegalStateException("final-skeleton-spawn-failed");
            }
            return true;
        } catch(RuntimeException failure) { clearPillar(); return false; }
    }

    public void tick() {
        if (closed || completed || !host.active()) return;
        try { advance(); }
        catch (RuntimeException failure) { fail("tick-exception:" + failure.getClass().getSimpleName()); }
    }

    private void advance() {
        tick++;
        if (ramDefeated) {
            if (!pillarSkeletonDefeated && !skeletonAlive()) {
                fail("final-skeleton-lost-without-death-receipt"); return;
            }
            tickPillarSkeleton(host.players());
            return;
        }
        if (ram==null || !ram.isValid() || ram.isDead()) { fail("ravager-lost-without-death-receipt"); return; }
        if (!host.inArena(ram.getLocation())) { fail("ravager-outside-arena"); return; }
        if(armor.size()!=RiftRamPolicy.REQUIRED_IMPACTS-impacts
                || armor.stream().anyMatch(piece->!piece.display().isValid())) {
            fail("armor-visual-missing"); return;
        }
        int expectedPillarDisplays = RiftRamPolicy.isFinalPillar(
                RiftRamPolicy.pillarOrdinal(impacts)) ? 4 : 3;
        if(pillarId!=null && (pillarDisplays.size()!=expectedPillarDisplays
                || pillarDisplays.stream().anyMatch(display->!display.isValid()))) {
            fail("active-pillar-visual-missing"); return;
        }
        List<Player> players=host.players();
        mites.removeIf(e->!e.isValid() || e.isDead());
        for(Endermite mite:mites) if(mite.getTarget()==null) mite.setTarget(nearest(mite.getLocation(),players));
        tickPillarSkeleton(players);
        enforceHealthFloor();
        updateDisplays();
        if (pillarId==null && RiftRamPolicy.mayRaisePillar(impacts) && tick>=nextPillarTick) {
            if(!raisePillar()) { fail("no-safe-next-pillar:"+(impacts+1)); return; }
            if(!host.checkpoint()) { fail("pillar-checkpoint-failed"); return; }
        }
        switch(action) {
            case CHASE -> {
                ram.setAI(true);
                Player focus=nearest(ram.getLocation(),players);
                ram.setTarget(focus);
                if(focus==null) return;
                // A stomp is refused during a charge, a stun, the spawn frame and
                // the completion transition: firing it there would either cancel a
                // committed charge or hit a party still reading the charge line.
                if(RiftRamPolicy.mayStartStomp(RiftRamPolicy.State.CHASING, (long)tick*50L,
                        (long)lastStomp*50L, RiftRamPolicy.stompCooldownTicks(players.size()))) {
                    begin(Action.STOMP_WARNING); hold();
                    ram.getWorld().playSound(ram.getLocation(),Sound.ENTITY_RAVAGER_STUNNED,1.6f,.6f);
                } else if(pillarId!=null && tick-nextPillarTick>=20
                        && tick-lastCharge>=RiftRamPolicy.chargeCooldownTicks(players.size())) {
                    target=focus.getUniqueId(); face(focus.getLocation()); begin(Action.CHARGE_WARNING); hold();
                    ram.getWorld().playSound(ram.getLocation(),Sound.ENTITY_RAVAGER_ROAR,1.7f,.8f);
                } else avoidPillar();
            }
            case CHARGE_WARNING -> {
                hold();
                Player focus=players.stream().filter(p->p.getUniqueId().equals(target)).findFirst().orElse(null);
                if(focus==null) { recover(); return; }
                face(focus.getLocation());
                chargeTell(focus, tick-actionTick);
                if(tick-actionTick>=RiftRamPolicy.chargeWindupTicks()) {
                    double[] locked=RiftRamPolicy.lockChargeDirection(ram.getX(),ram.getY(),ram.getZ(),focus.getX(),focus.getY(),focus.getZ());
                    if(locked==null) { recover(); return; }
                    direction=new Vector(locked[0],0,locked[1]); banked=false; chargeHits.clear(); begin(Action.CHARGE);
                }
            }
            case CHARGE -> charge(players);
            case STUN -> { hold(); if(tick-actionTick>=RiftRamPolicy.IMPACT_STUN_TICKS) begin(Action.CHASE); }
            case RECOVER -> { hold(); if(tick-actionTick>=30) begin(Action.CHASE); }
            case STOMP_WARNING -> {
                hold(); dust(ram.getLocation(),18,.8,.15);
                if(tick-actionTick>=RiftRamPolicy.STOMP_WARNING_TICKS) {
                    stompOrigin=ram.getLocation().clone(); lastRadius=0; stompHits.clear(); stompRoster.clear();
                    for(Player p:players) stompRoster.add(p.getUniqueId());
                    lastStomp=tick; begin(Action.STOMP);
                    dust(stompOrigin,120,1.7,.25);
                    ram.getWorld().playSound(stompOrigin,Sound.ENTITY_RAVAGER_STEP,2.4f,.5f);
                    ram.getWorld().playSound(stompOrigin,Sound.BLOCK_ANVIL_LAND,1.5f,.55f);
                }
            }
            case STOMP -> stomp(players);
            case DEAD -> { }
        }
    }

    private void begin(Action next) { action=next; actionTick=tick; }
    private void hold() {
        ram.setAI(false);
        ram.setTarget(null);
        // Do NOT zero the velocity here. Bukkit turns a zeroed velocity on a body
        // whose position is already stale into a server-side teleport, and that
        // teleport arrives WITHOUT an internal permit, so the wave teleport guard
        // cancelled it and logged WAVE_TELEPORT_BLOCKED every ~11 seconds. The
        // AI is already off and the target already cleared, which is what "hold"
        // means; the residual velocity decays on its own without a position write.
    }
    private void recover() { lastCharge=tick; begin(Action.RECOVER); }
    private Player nearest(Location origin,List<Player> players) {
        return players.stream().filter(p->p.getWorld().equals(origin.getWorld()) && !p.isDead())
                .min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(origin))).orElse(null);
    }
    private void face(Location target) {
        Vector v=target.toVector().subtract(ram.getLocation().toVector());
        ram.setRotation((float)Math.toDegrees(Math.atan2(-v.getX(),v.getZ())),0);
    }
    private void avoidPillar() {
        if(pillar==null || ram.getLocation().distanceSquared(pillar)>16) return;
        Vector away=ram.getLocation().toVector().subtract(pillar.toVector()).setY(0);
        if(away.lengthSquared()<.01) away=new Vector(1,0,0);
        Vector tangent=new Vector(-away.getZ(),0,away.getX()).normalize().multiply(.25);
        ram.setVelocity(tangent.setY(ram.getVelocity().getY()));
    }
    /**
     * The run-up tell, W6-02.
     *
     * <p>The playtest asked for "red eyes and smoke from the nostrils while it
     * winds up", because the old warning was a roar and a stationary body: from
     * across the arena a charging Ravager looked exactly like an idle one until
     * it was already moving. The tell is deliberately three separate channels so
     * a player who has lost one of them (fog, darkness, a bad camera angle) still
     * reads the others:</p>
     *
     * <ul>
     *   <li>a red glow at the eye line that grows as the commit approaches;</li>
     *   <li>smoke that pours forward from the snout along the locked heading;</li>
     *   <li>deepslate dust kicked up under the hooves, faster each second.</li>
     * </ul>
     *
     * <p>All three are viewer-local and bounded, so a large arena does not pay
     * for players who cannot see the Ram anyway.</p>
     */
    private void chargeTell(Player focus, int elapsed) {
        int windup = Math.max(1, RiftRamPolicy.chargeWindupTicks());
        double progress = Math.min(1.0D, (double) elapsed / (double) windup);
        double bodyScale = RiftRamPolicy.BOSS_SCALE;
        Vector heading = new Vector(focus.getX() - ram.getX(), 0, focus.getZ() - ram.getZ());
        if (heading.lengthSquared() < 1.0E-4D) heading = new Vector(0, 0, 1);
        heading.normalize();
        Location eyes = ram.getLocation().clone().add(heading.clone().multiply(1.05D * bodyScale))
                .add(0, 1.75D * bodyScale, 0);
        Vector eyeSide = new Vector(-heading.getZ(), 0, heading.getX()).multiply(0.3D * bodyScale);
        Location snout = ram.getLocation().clone()
                .add(heading.clone().multiply(1.35D * bodyScale)).add(0, 1.55D * bodyScale, 0);
        Location hooves = ram.getLocation().clone().add(0, 0.12D, 0);
        // Eyes brighten in discrete steps rather than continuously: a visible
        // jump each second reads as "it is getting angrier", where a smooth ramp
        // just looks like static.
        int eyeCount = 2 + (int) Math.round(progress * 6.0D);
        int smokeCount = 3 + (int) Math.round(progress * 9.0D);
        int hoofCount = 4 + (elapsed % 6 == 0 ? 6 : 0);
        for (Player viewer : host.players()) {
            if (!viewer.getWorld().equals(ram.getWorld())) continue;
            if (viewer.getLocation().distanceSquared(eyes) > 48 * 48) continue;
            for (int side : new int[]{-1, 1}) {
                viewer.spawnParticle(Particle.DUST, eyes.clone().add(eyeSide.clone().multiply(side)),
                        Math.max(1, eyeCount / 2), 0.035D, 0.035D, 0.035D, 0.0D,
                        new Particle.DustOptions(Color.fromRGB(255, 26, 18), 0.75F));
            }
            viewer.spawnParticle(Particle.SMOKE, snout, smokeCount, 0.18D, 0.12D, 0.18D, 0.02D);
            if (progress > 0.5D) {
                // Late in the windup the snout smoke turns into the hot,
                // suppressed charge that precedes the launch.
                viewer.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, snout, 1, 0.12D, 0.08D, 0.12D, 0.0D);
            }
            viewer.spawnParticle(Particle.BLOCK, hooves, hoofCount, 0.7D, 0.02D, 0.7D, 0.0D,
                    Material.DEEPSLATE.createBlockData());
        }
    }

    private void charge(List<Player> players) {
        Location from=ram.getLocation(), to=from.clone().add(direction.clone().multiply(RiftRamPolicy.chargeSpeed(impacts)));
        if(tick-actionTick>=RiftRamPolicy.CHARGE_TIMEOUT_TICKS || !safeVolume(to)) { recover(); return; }
        // The objective is the active uniquely-owned pillar. Core never participates.
        if(pillarId!=null && !banked && tick-nextPillarTick>=20 && RiftRamPolicy.segmentHitsPillar(
                from.getX(),from.getY(),from.getZ(),to.getX(),to.getY(),to.getZ(),pillar.getX(),pillar.getY(),pillar.getZ())) {
            impact(); return;
        }
        if(!host.move(ram, to)) { recover(); return; }
        for(Player player:players) {
            if(Math.abs(player.getY()-from.getY())>3 || !RiftRamPolicy.chargeHitsPlayer(from.getX(),from.getZ(),to.getX(),to.getZ(),
                    player.getX(),player.getZ(),chargeHits.contains(player.getUniqueId()))) continue;
            chargeHits.add(player.getUniqueId());
            if(host.damage(ram, player, RiftRamPolicy.CHARGE_DAMAGE)) {
                player.setVelocity(direction.clone().multiply(.5).setY(.2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,20,0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,100,1));
                ram.getWorld().playSound(player.getLocation(),Sound.ENTITY_RAVAGER_ATTACK,1.2f,.8f);
                recover();
            }
        }
    }

    private void impact() {
        if(action!=Action.CHARGE || banked || pillarId==null
                || impacts>=RiftRamPolicy.REQUIRED_IMPACTS) return;
        banked=true; impacts++; lastCharge=tick;
        // The roster decides whether this collision SPENT the pillar. An ordinary
        // pillar dies to its first hit; the final pillar absorbs three, cracking
        // visibly on each, and only the third brings it down.
        int pillarOrdinal = RiftRamPolicy.pillarOrdinal(impacts - 1);
        boolean spent = !RiftRamPolicy.isFinalPillar(pillarOrdinal)
                || RiftRamPolicy.impactsRemainingOnPillar(impacts) == 0;
        // Per-pillar absorption, not the encounter total: an ordinary pillar is
        // always 1, and the final one counts 1, 2, 3 and then resets with it.
        pillarImpacts = spent ? 0 : pillarImpacts + 1;
        Location struck = pillar.clone();
        if (spent) { pillarId=null; pillar=null; nextPillarTick=tick+35; }
        begin(Action.STUN);
        if(!host.checkpoint()) { fail("impact-checkpoint-failed"); return; }
        if (spent) {
            // A surviving marksman rides the falling pillar down rather than
            // being deleted with it: it lands and keeps fighting on the floor.
            dropPillarSkeleton();
            if (closed) return;
            clearPillar();
            dust(struck.clone().add(0,1.5,0),100,1.2,1.5);
            struck.getWorld().playSound(struck,Sound.BLOCK_DEEPSLATE_BREAK,2,.6f);
            struck.getWorld().playSound(ram.getLocation(),Sound.BLOCK_ANVIL_BREAK,2,.75f);
        } else {
            // A surviving pillar must show that the collision landed on IT,
            // not that the party hit a wall for nothing.
            crackPillar();
            dust(struck.clone().add(0,1.5,0),40,.7,.6);
            struck.getWorld().playSound(struck,Sound.BLOCK_DEEPSLATE_HIT,2,.55f);
            struck.getWorld().playSound(ram.getLocation(),Sound.ENTITY_IRON_GOLEM_HURT,1.8f,.7f);
        }
        for(Iterator<Piece> it=armor.iterator();it.hasNext();) {
            Piece p=it.next(); if(p.section()==impacts) { host.remove(p.display()); it.remove(); }
        }
        int shards = 3 + (impacts % 3);
        for(int i=0;i<shards;i++) {
            double angle=i*2*Math.PI/shards;
            Location start=ram.getLocation().clone().add(0,2,0);
            BlockDisplay shard=block(start,Material.NETHERITE_BLOCK,new Vector3f(.25f,.15f,.4f));
            debris.add(new Debris(shard,start,new Vector(Math.cos(angle)*.15,.22,Math.sin(angle)*.15),tick));
        }
        // The cap is on the WHOLE encounter, so a pillar that finds the room
        // already full releases nothing. That is legal and must not fail the
        // wave, otherwise the fifth pillar would look broken.
        int requested = 2 + (impacts % 3) + RiftRamPolicy.releasePressureBonus(host.players().size());
        int budget = impacts < RiftRamPolicy.REQUIRED_IMPACTS
                ? RiftRamPolicy.endermitesToRelease(mites.size(), requested) : 0;
        for(int i=0;i<budget;i++) {
            Endermite mite=struck.getWorld().spawn(struck,Endermite.class);
            host.own(mite,true); mite.setPersistent(true); mite.setRemoveWhenFarAway(false);
            mite.setTarget(nearest(struck,host.players())); mites.add(mite);
        }
        hold(); begin(Action.STUN); host.visualsChanged();
    }

    private void stomp(List<Player> players) {
        hold();
        int elapsed=tick-actionTick;
        double radius=Math.min(RiftRamPolicy.STOMP_RADIUS, RiftRamPolicy.STOMP_RADIUS*elapsed/RiftRamPolicy.STOMP_EXPANSION_TICKS);
        // 192 samples/update maximum, independent of roster size; nearby viewers only.
        int samples=Math.max(32,Math.min(Math.min(96,host.particleBudget()/3),(int)Math.ceil(2*Math.PI*radius/.65)));
        // W6-02: the stomp was a flat dust ring that read as a texture on the
        // floor. Three channels now share one fast-moving front so the party can
        // read where it is and when to jump: the ground ring, a lifted lip of
        // debris above it, and a thin obsidian seam at the leading edge.
        double lipY = host.floorY() + 1.35D;
        for(Player viewer:players) {
            if(!viewer.getWorld().equals(stompOrigin.getWorld()) || viewer.getLocation().distanceSquared(stompOrigin)>48*48) continue;
            for(int i=0;i<samples;i++) {
                double angle=2*Math.PI*i/samples;
                double cos=Math.cos(angle), sin=Math.sin(angle);
                double x=stompOrigin.getX()+cos*radius, z=stompOrigin.getZ()+sin*radius;
                viewer.spawnParticle(Particle.BLOCK,x,host.floorY()+1.12,z,2,.25,.1,.25,0,Material.DEEPSLATE.createBlockData());
                if((i&1)==0) viewer.spawnParticle(Particle.BLOCK,x,host.floorY()+1.5,z,1,.3,.2,.3,0,Material.OBSIDIAN.createBlockData());
                // Leading edge: a single lit seam per sample, so the front has a
                // crisp boundary instead of a soft haze.
                viewer.spawnParticle(Particle.BLOCK,x,lipY,z,1,.12,.16,.12,0,Material.CRYING_OBSIDIAN.createBlockData());
                // Every eighth sample throws real debris upward. Sparse enough
                // that it never becomes confetti, dense enough to sell weight.
                if((i&7)==0) {
                    viewer.spawnParticle(Particle.BLOCK,x,host.floorY()+1.05D,z,1,.2,.5,.2,0.06,
                            Material.DEEPSLATE.createBlockData());
                }
            }
        }
        // A slow, heavy thud on every fifth tick keeps the stomp audible for the
        // whole expansion instead of one opening hit.
        if(elapsed % 5 == 0) {
            float pitch = 0.55F + Math.min(0.35F, elapsed * 0.012F);
            ram.getWorld().playSound(stompOrigin, Sound.ENTITY_RAVAGER_STEP, 1.9F, pitch);
        }
        Set<UUID> eligible=new HashSet<>();
        for(Player p:players) {
            eligible.add(p.getUniqueId());
            if(!stompRoster.contains(p.getUniqueId()) || !p.getWorld().equals(stompOrigin.getWorld())) continue;
            double distance=Math.hypot(p.getX()-stompOrigin.getX(),p.getZ()-stompOrigin.getZ());
            if(RiftRamPolicy.stompHits(distance,p.getY()-host.floorY()-1,p.isOnGround(),lastRadius,radius,stompHits.contains(p.getUniqueId()))) {
                stompHits.add(p.getUniqueId());
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        RiftRamPolicy.stompSlowTicks(),RiftRamPolicy.STOMP_SLOW_AMPLIFIER));
                // The hit is the moment the player needs feedback: impact sound at
                // their feet, not at the Ram. Distance already limited the ring.
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.9F, 0.55F);
            }
        }
        stompRoster.retainAll(eligible);
        lastRadius=radius;
        if(elapsed>=RiftRamPolicy.STOMP_EXPANSION_TICKS) { stompRoster.clear(); stompHits.clear(); begin(Action.RECOVER); }
    }
    private void dust(Location at,int count,double spread,double height) {
        for(Player viewer:host.players()) if(viewer.getWorld().equals(at.getWorld()) && viewer.getLocation().distanceSquared(at)<48*48)
            viewer.spawnParticle(Particle.BLOCK,at,count,spread,height,spread,0,Material.DEEPSLATE.createBlockData());
    }
    /**
     * Keep the Ravager above the floor while its armour holds.
     *
     * <p>This is a floor, not healing: the damage lands and the bar moves, but a
     * burst cannot end the encounter before the fifth pillar. Without it the
     * whole mechanic is optional and a geared party skips the fight.</p>
     */
    private void enforceHealthFloor() {
        double maximum = ram.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double floor = RiftRamPolicy.healthFloor(impacts, maximum);
        if (ram.getHealth() < floor) ram.setHealth(floor);
    }

    private void updateDisplays() {
        double yaw=Math.toRadians(ram.getLocation().getYaw());
        for(Piece piece:armor) {
            Vector v=piece.offset();
            Location at=ram.getLocation().clone().add(v.getX()*Math.cos(yaw)-v.getZ()*Math.sin(yaw),v.getY(),v.getX()*Math.sin(yaw)+v.getZ()*Math.cos(yaw));
            at.setYaw(ram.getLocation().getYaw()); host.move(piece.display(), at);
        }
        if(pillar!=null && tick-nextPillarTick<=20) {
            float progress=Math.min(1,(tick-nextPillarTick)/20f);
            for(int i=0;i<pillarDisplays.size();i++) {
                BlockDisplay d=pillarDisplays.get(i);
                double offset=i==0?-3:i==1?-.2:.3;
                Location at=pillar.clone().add(i==0?-.9:i==1?-1.1:-1.05,offset+3*progress,i==0?-.9:i==1?-1.1:-1.05);
                host.move(d, at);
            }
        }
        for(Iterator<Debris> it=debris.iterator();it.hasNext();) {
            Debris d=it.next(); int age=tick-d.born();
            if(age>=30) { host.remove(d.display()); it.remove(); continue; }
            Location at=d.start().clone().add(d.velocity().clone().multiply(age)).add(0,-.012*age*age,0);
            host.move(d.display(), at);
            Transformation t=d.display().getTransformation();
            d.display().setTransformation(new Transformation(t.getTranslation(),new Quaternionf().rotateXYZ(age*.18f,age*.12f,age*.09f),t.getScale(),t.getRightRotation()));
        }
    }
    /** Only the real owned entity's death event can commit completion. */
    public void died(UUID entity) {
        if (closed || completed || entity == null) return;
        if (Objects.equals(pillarSkeletonId, entity)) {
            if (!pillarSkeletonSpawned || pillarSkeletonDefeated) return;
            pillarSkeletonDefeated = true;
        } else if (Objects.equals(id(), entity)) {
            if (ramDefeated) return;
            if (impacts != RiftRamPolicy.REQUIRED_IMPACTS) {
                fail("premature-ravager-death"); return;
            }
            ramDefeated = true;
        } else return;
        completed = ramDefeated && pillarSkeletonDefeated;
        if (ramDefeated) action = Action.DEAD;
        if (!host.checkpoint()) {
            completed = false; fail("actor-death-checkpoint-failed"); return;
        }
        // A surviving marksman remains a required combat actor after the Ram
        // dies. Neither its removal nor the Ram's corpse is a victory receipt.
        if (completed) cleanupDisplays();
    }

    /**
     * Show real damage on a pillar that survived the collision.
     *
     * <p>The final pillar absorbs three collisions, so two of them must leave a
     * mark. Each stage re-seats the display pieces lower and narrower and drops
     * the crown section further, so an intact pillar, a cracked one and one with
     * its core exposed are three different silhouettes rather than one shape
     * with a counter behind it.</p>
     */
    private void crackPillar() {
        if (pillar==null || pillarDisplays.isEmpty()) return;
        int stage = RiftRamPolicy.finalPillarStage(impacts);
        // Absolute geometry from the receipt, never cumulative scale/sink.
        // Cold recovery uses the same dimensions in buildPillar().
        float shrink = stage >= 2 ? .82F : stage == 1 ? .94F : 1F;
        float height = 7.4F * (stage >= 2 ? .68F : stage == 1 ? .88F : 1F);
        float footprint = 3.6F * shrink, cap = 4.4F * shrink;
        double[][] offsets = {{-footprint/2,0,-footprint/2},
                {-cap/2,height-.65F,-cap/2},
                {-(cap/2-.05F),height-.2F,-(cap/2-.05F)},
                {-(footprint/2+.03F),height*.45F,-(footprint/2+.03F)}};
        Vector3f[] sizes = {new Vector3f(footprint,height-.65F,footprint),
                new Vector3f(cap,.45F,cap), new Vector3f(cap-.1F,.2F,cap-.1F),
                new Vector3f(footprint+.06F,.25F,footprint+.06F)};
        for (int i=0;i<pillarDisplays.size();i++) {
            BlockDisplay display=pillarDisplays.get(i);
            double[] offset=offsets[i];
            if (!host.move(display,pillar.clone().add(offset[0],offset[1],offset[2])))
                throw new IllegalStateException("damaged-pillar-relocation-refused");
            Transformation t=display.getTransformation();
            display.setTransformation(new Transformation(t.getTranslation(),t.getLeftRotation(),
                    sizes[i],t.getRightRotation()));
            if (i==0) display.setBlock((stage >= 2 ? Material.MAGMA_BLOCK : Material.OBSIDIAN).createBlockData());
        }
        if (skeletonAlive() && !skeletonFallen
                && !host.move(pillarSkeleton,pillar.clone().add(0,standingPillarPlatformHeight(),0)))
            throw new IllegalStateException("damaged-pillar-skeleton-relocation-refused");
        // Loose rubble reads as material that actually left the pillar.
        for (int i=0;i<6;i++) {
            double angle=i*Math.PI/3;
            Location start=pillar.clone().add(Math.cos(angle)*1.1,1.4,Math.sin(angle)*1.1);
            BlockDisplay shard=block(start,Material.OBSIDIAN,new Vector3f(.22f,.18f,.3f));
            debris.add(new Debris(shard,start,new Vector(Math.cos(angle)*.1,.18,Math.sin(angle)*.1),tick));
        }
    }

    private double standingPillarPlatformHeight() {
        int stage = RiftRamPolicy.finalPillarStage(impacts);
        return 7.4D * (stage >= 2 ? .68D : stage == 1 ? .88D : 1D) + .2D;
    }

    /**
     * Bring up the elite marksman on top of the final pillar.
     *
     * <p>It is a real, killable actor with its own health so the party can choose
     * to answer it while they line the Ram up. It deliberately does not carry a
     * custom model here: the runtime owns gameplay, the client owns presentation,
     * and the actor carries a stable visual id the client binds to.</p>
     */
    private void spawnPillarSkeleton() {
        if (pillar == null || pillarSkeletonSpawned) return;
        Location at = pillar.clone().add(0, standingPillarPlatformHeight(), 0);
        try {
            pillarSkeleton = at.getWorld().spawn(at, Skeleton.class, entity -> {
                entity.setPersistent(true);
                entity.setRemoveWhenFarAway(false);
                entity.setCustomName("РЎС‚СЂРµР»РѕРє Р Р°Р·Р»РѕРјР°");
                entity.setCustomNameVisible(false);
                entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)
                        .setBaseValue(WaveSixPillarSkeletonPolicy.HEALTH);
                entity.setHealth(WaveSixPillarSkeletonPolicy.HEALTH);
                // The marksman never shoots a vanilla arrow: every shot is an
                // event-owned modelled projectile released by fireBoneVolley().
                entity.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.BONE));
                entity.getEquipment().setItemInMainHandDropChance(0f);
            });
            host.own(pillarSkeleton, true);
            pillarSkeletonId = pillarSkeleton.getUniqueId();
            pillarSkeletonSpawned = true;
            pillarSkeletonDefeated = false;
            skeletonFallen = false;
            skeletonStep = 0;
            skeletonLastTriple = -1; skeletonLastBone = -1; skeletonLastMove = -1;
            at.getWorld().playSound(at, Sound.ENTITY_SKELETON_AMBIENT, 1.6f, .8f);
            host.visualsChanged();
        } catch (RuntimeException failure) {
            if (pillarSkeleton != null) host.remove(pillarSkeleton);
            pillarSkeleton = null; pillarSkeletonId = null;
            pillarSkeletonSpawned = false;
        }
    }

    /** True while the marksman is alive and still our actor. */
    private boolean skeletonAlive() {
        return pillarSkeleton != null && pillarSkeleton.isValid() && !pillarSkeleton.isDead();
    }

    /**
     * Drive the marksman: it repositions and fires real modelled volleys.
     *
     * <p>It only fires when it has a live target and the phase allows it; a dead
     * skeleton stops firing immediately because the actor no longer answers.</p>
     */
    private void tickPillarSkeleton(List<Player> players) {
        if (!skeletonAlive()) return;
        if (pillar == null && !skeletonFallen) return;
        Player target = nearest(pillarSkeleton.getLocation(), players);
        boolean hasTarget = target != null;
        if (hasTarget) {
            Location look = target.getLocation().clone();
            pillarSkeleton.setRotation(look.getYaw(), look.getPitch());
        }
        if (skeletonFallen) {
            pillarSkeleton.setGravity(true);
            pillarSkeleton.setAI(true);
            pillarSkeleton.setAware(true);
            pillarSkeleton.setTarget(target);
            if (target != null) pillarSkeleton.getPathfinder().moveTo(target.getLocation(), 1.0D);
        }
        // Only a standing platform owns the elevated patrol. Once landed the
        // same actor keeps its normal pathing and its authored ranged attacks.
        if (!skeletonFallen && pillar != null && WaveSixPillarSkeletonPolicy.shouldReposition(tick, skeletonLastMove)) {
            skeletonLastMove = tick;
            skeletonStep++;
            double[] offset = WaveSixPillarSkeletonPolicy.patrolOffset(skeletonStep);
            int damageStage = RiftRamPolicy.finalPillarStage(impacts);
            double cap = 4.4D * (damageStage >= 2 ? .82D : damageStage == 1 ? .94D : 1D);
            double patrolScale = Math.max(0D, cap / 2D - .6D)
                    / WaveSixPillarSkeletonPolicy.PATROL_RADIUS;
            Location next = pillar.clone().add(offset[0] * patrolScale,
                    standingPillarPlatformHeight(), offset[1] * patrolScale);
            host.move(pillarSkeleton, next);
        }
        WaveSixPillarSkeletonPolicy.Ability ability =
                WaveSixPillarSkeletonPolicy.nextAbility(tick, skeletonLastTriple, skeletonLastBone, hasTarget);
        if (ability == WaveSixPillarSkeletonPolicy.Ability.NONE) return;
        if (ability == WaveSixPillarSkeletonPolicy.Ability.BONE_RAIN) {
            skeletonLastBone = tick;
            fireBoneVolley(target, WaveSixPillarSkeletonPolicy.boneRainDirections(
                    target.getX() - pillarSkeleton.getX(), target.getZ() - pillarSkeleton.getZ()),
                    WaveSixPillarSkeletonPolicy.BONE_RAIN_SPEED);
            pillarSkeleton.getWorld().playSound(pillarSkeleton.getLocation(),
                    Sound.ENTITY_SKELETON_HURT, 1.5f, .55f);
        } else {
            skeletonLastTriple = tick;
            fireBoneVolley(target, WaveSixPillarSkeletonPolicy.tripleShotDirections(
                    target.getX() - pillarSkeleton.getX(), target.getZ() - pillarSkeleton.getZ()),
                    WaveSixPillarSkeletonPolicy.TRIPLE_SHOT_SPEED);
            pillarSkeleton.getWorld().playSound(pillarSkeleton.getLocation(),
                    Sound.ENTITY_SKELETON_SHOOT, 1.4f, .85f);
        }
        host.visualsChanged();
    }

    /** Releases one modelled volley along the given horizontal directions. */
    private void fireBoneVolley(Player target, double[][] directions, double speed) {
        for (double[] dir : directions) {
            if (dir == null || dir.length < 2) continue;
            double dy = (target.getY() + 0.9D) - (pillarSkeleton.getY() + 1.5D);
            Vector aim = new Vector(dir[0], 0, dir[1]);
            double horizontal = Math.hypot(target.getX() - pillarSkeleton.getX(),
                    target.getZ() - pillarSkeleton.getZ());
            if (horizontal > 1.0E-6D) aim.setY(dy / horizontal);
            aim.normalize();
            Location origin = pillarSkeleton.getLocation().clone().add(0, 1.5D, 0);
            Arrow arrow = host.spawnBoneArrow(origin, aim, speed, WaveSixPillarSkeletonPolicy.ARROW_DAMAGE);
            if (arrow != null) {
                arrow.setShooter(pillarSkeleton);
                arrow.setDamage(WaveSixPillarSkeletonPolicy.ARROW_DAMAGE);
            }
        }
    }

    /**
     * Drop a surviving marksman to the floor when the final pillar collapses.
     *
     * <p>Without this the actor would be left standing on a pillar that no longer
     * exists, i.e. an unreachable orphan. It lands on the arena floor and keeps
     * fighting; a skeleton already killed on the pillar stays dead.</p>
     */
    private void dropPillarSkeleton() {
        if (!WaveSixPillarSkeletonPolicy.shouldFall(impacts, skeletonAlive(), skeletonFallen)) return;
        Location landing = pillarSkeleton.getLocation().clone();
        landing.setY(host.floorY() + 1.0D);
        if (!host.inArena(landing)) landing = ram.getLocation().clone();
        if (!host.inArena(landing) || !host.move(pillarSkeleton, landing)
                || !landing.getWorld().equals(pillarSkeleton.getLocation().getWorld())
                || pillarSkeleton.getLocation().distanceSquared(landing) > .25D) {
            fail("skeleton-landing-refused"); return;
        }
        skeletonFallen = true;
        if (!host.checkpoint()) { fail("skeleton-landing-checkpoint-failed"); return; }
        pillarSkeleton.setGravity(true);
        landing.getWorld().playSound(landing, Sound.ENTITY_SKELETON_STEP, 1.5f, .7f);
        dust(landing, 30, .8, .5);
    }

    private void clearPillar() {
        for(BlockDisplay d:pillarDisplays) host.remove(d);
        pillarDisplays.clear(); pillar=null; pillarId=null;
    }
    private void cleanupDisplays() { cleanupDisplays(false); }
    private void cleanupDisplays(boolean preserveSkeleton) {
        clearPillar();
        for(Piece p:armor) host.remove(p.display()); armor.clear();
        for(Debris d:debris) host.remove(d.display()); debris.clear();
        for(Endermite e:mites) host.remove(e); mites.clear();
        // The marksman is an owned event actor: it never survives the encounter.
        if (!preserveSkeleton) {
            if (pillarSkeleton != null && pillarSkeleton.isValid()) host.remove(pillarSkeleton);
            // The actor is gone; its UUID remains the durable death identity.
            pillarSkeleton = null;
        }
        stompHits.clear(); stompRoster.clear(); chargeHits.clear();
    }
    public void close() {
        if(closed) return;
        closed=true; cleanupDisplays();
        if(ram!=null && ram.isValid()) host.remove(ram);
    }
    /** Remove transient presentation at shutdown while retaining the persistent actor. */
    public void suspend() {
        closed=true; cleanupDisplays(true);
        if (skeletonAlive()) { pillarSkeleton.setAI(false); pillarSkeleton.setAware(false); }
        if(ram!=null && ram.isValid()) hold();
    }

    public Map<String,String> snapshot() {
        Map<String,String> data=new LinkedHashMap<>();
        data.put("ram.schema","3");
        data.put("ram.defeated",Boolean.toString(ramDefeated));
        data.put("ram.skeleton-spawned",Boolean.toString(pillarSkeletonSpawned));
        data.put("ram.skeleton-defeated",Boolean.toString(pillarSkeletonDefeated));
        data.put("ram.skeleton-fallen",Boolean.toString(skeletonFallen));
        data.put("ram.uuid",String.valueOf(id()));
        data.put("ram.impacts",Integer.toString(impacts));
        data.put("ram.tick",Integer.toString(tick));
        data.put("ram.last-charge",Integer.toString(lastCharge));
        data.put("ram.last-stomp",Integer.toString(lastStomp));
        data.put("ram.action",action.name());
        data.put("ram.action-tick",Integer.toString(actionTick));
        data.put("ram.next-pillar",Integer.toString(nextPillarTick));
        data.put("ram.pillar-impacts",Integer.toString(pillarImpacts));
        if (pillarSkeletonId != null) data.put("ram.skeleton",pillarSkeletonId.toString());
        data.put("ram.complete",Boolean.toString(completed));
        data.put("ram.target",target==null?"":target.toString());
        if(direction!=null) data.put("ram.direction",direction.getX()+","+direction.getZ());
        if(pillarId!=null) {
            data.put("ram.pillar-id",pillarId.toString());
            data.put("ram.pillar",pillar.getX()+","+pillar.getY()+","+pillar.getZ());
        }
        return Map.copyOf(data);
    }

    public void restore(Map<String,String> data) {
        try {
            if(!"2".equals(data.get("ram.schema")) && !"3".equals(data.get("ram.schema")))
                throw new IllegalArgumentException("legacy-schema");
            impacts=Integer.parseInt(data.get("ram.impacts"));
            // A schema-2 receipt was written under the old five-plate model, so
            // an impact count above the current plate count belongs to a build
            // that no longer exists. Refusing it fails closed into recovery
            // instead of silently reinterpreting plate state.
            if(impacts<0 || impacts>RiftRamPolicy.REQUIRED_IMPACTS)
                throw new IllegalArgumentException("impact-range");
            RiftRamRecoveryPolicy.Actors actors = RiftRamRecoveryPolicy.decodeActors(data, impacts);
            ramDefeated = actors.ramDefeated();
            pillarSkeletonSpawned = actors.skeletonSpawned();
            pillarSkeletonDefeated = actors.skeletonDefeated();
            skeletonFallen = actors.skeletonFallen();
            pillarSkeletonId = actors.skeletonId();
            persistentId=UUID.fromString(data.get("ram.uuid"));
            String receipt=data.get("ram.complete");
            if(!"true".equals(receipt) && !"false".equals(receipt)) throw new IllegalArgumentException("invalid-completion-receipt");
            if("true".equals(receipt)) {
                if(impacts!=RiftRamPolicy.REQUIRED_IMPACTS)
                    throw new IllegalArgumentException("premature-completion-receipt");
                completed=true; action=Action.DEAD; return;
            }
            tick=Integer.parseInt(data.get("ram.tick"));
            lastCharge=Integer.parseInt(data.get("ram.last-charge"));
            lastStomp=Integer.parseInt(data.get("ram.last-stomp"));
            if(tick<0 || lastCharge<0 || lastStomp<0 || lastCharge>tick || lastStomp>tick)
                throw new IllegalArgumentException("invalid-clock");
            Entity entity=Bukkit.getEntity(persistentId);
            if (!ramDefeated) {
                if (!(entity instanceof Ravager existing) || !existing.isValid() || existing.isDead())
                    throw new IllegalArgumentException("missing-persistent-ravager");
                if (!host.owns(existing)) throw new IllegalArgumentException("foreign-ravager");
                ram = existing;
                if (!host.inArena(ram.getLocation())) throw new IllegalArgumentException("actor-outside-arena");
            } else if (entity != null && entity.isValid() && !entity.isDead()) {
                throw new IllegalArgumentException("dead-ravager-still-live");
            }
            Location core = host.core();
            World actorWorld = ram != null ? ram.getWorld() : core == null ? null : core.getWorld();
            if (actorWorld == null) throw new IllegalArgumentException("missing-encounter-world");
            // In-flight actions never replay after restart. Require fresh warning after recovery.
            action=ramDefeated ? Action.DEAD : Action.RECOVER;
            actionTick=tick; lastCharge=tick; lastStomp=tick;
            nextPillarTick=tick+35;
            // How much of the standing pillar is already gone, so a restarted
            // final pillar resumes at its real damage stage instead of rebuilding
            // intact and silently refunding two collisions.
            pillarImpacts = RiftRamRecoveryPolicy.pillarImpacts(data, impacts);
            // Resolve and validate the saved combat actor BEFORE rebuilding
            // presentation. Never overwrite a freshly spawned replacement.
            if (pillarSkeletonSpawned) {
                Entity saved = Bukkit.getEntity(pillarSkeletonId);
                if (pillarSkeletonDefeated) {
                    if (saved != null && saved.isValid() && !saved.isDead())
                        throw new IllegalArgumentException("dead-skeleton-still-live");
                } else {
                    if (!(saved instanceof Skeleton marksman) || !marksman.isValid()
                            || marksman.isDead() || !host.owns(marksman)
                            || !actorWorld.equals(marksman.getWorld()))
                        throw new IllegalArgumentException("missing-or-foreign-saved-skeleton");
                    Location savedAt = marksman.getLocation();
                    Location floorProjection = savedAt.clone();
                    floorProjection.setY(host.floorY() + 1.0D);
                    // Platform actors legitimately stand above the player's arena
                    // ceiling. Validate their footprint and bounded role height,
                    // without expanding the arena used for player eligibility.
                    if (!host.inArena(floorProjection)
                            || !Double.isFinite(savedAt.getY())
                            || savedAt.getY() < host.floorY() + .5D
                            || savedAt.getY() > host.floorY() + 3.0D + WaveSixPillarSkeletonPolicy.PLATFORM_HEIGHT)
                        throw new IllegalArgumentException("saved-skeleton-outside-role-volume");
                    pillarSkeleton = marksman;
                }
            }
            if (!ramDefeated) createArmor();
            if(data.containsKey("ram.pillar-id")) {
                if(impacts>=RiftRamPolicy.REQUIRED_IMPACTS)
                    throw new IllegalArgumentException("pillar-after-final-impact");
                pillarId=UUID.fromString(data.get("ram.pillar-id"));
                String[] xyz=data.get("ram.pillar").split(",");
                if(xyz.length!=3) throw new IllegalArgumentException("pillar-coordinate-count");
                double x=Double.parseDouble(xyz[0]),y=Double.parseDouble(xyz[1]),z=Double.parseDouble(xyz[2]);
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalArgumentException("pillar-nonfinite");
                pillar=new Location(ram.getWorld(),x,y,z);
                if(!safeVolume(pillar) || !buildPillar()) throw new IllegalArgumentException("pillar-site-invalid");
            }
            // A crash after collision commit but before the accepted landing
            // resumes that landing, never a replacement actor or a new impact.
            if (impacts == RiftRamPolicy.REQUIRED_IMPACTS && skeletonAlive() && !skeletonFallen) {
                dropPillarSkeleton();
                if (closed) return;
            }
            if (!ramDefeated) hold();
            host.visualsChanged();
        } catch(RuntimeException failure) { fail("restore-refused:"+failure.getMessage()); }
    }
    private void fail(String reason) {
        // Preserve the last authoritative identity before presentation cleanup.
        if(ram!=null) host.checkpoint();
        close(); host.fail("RIFT_RAM:"+reason);
    }
}
