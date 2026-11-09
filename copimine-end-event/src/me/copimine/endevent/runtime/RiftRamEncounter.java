package me.copimine.endevent.runtime;

import me.copimine.endevent.domain.RiftRamPolicy;
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
        int particleBudget();
        void remove(Entity entity);
        /** Moves an owned presentation entity through the internal permit path. */
        void move(Entity entity, Location destination);
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

    public RiftRamEncounter(Host host) { this.host = host; }
    public UUID id() { return ram == null ? persistentId : ram.getUniqueId(); }
    public int impacts() { return impacts; }
    public boolean complete() { return completed; }
    public boolean ready() { return !closed && ram != null && ram.isValid() && !ram.isDead(); }

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
                entity.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(RiftRamPolicy.SCALE);
                entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(RiftRamPolicy.MELEE_DAMAGE);
                entity.setHealth(RiftRamPolicy.healthForPlayers(players));
            });
            host.own(ram, true);
            persistentId=ram.getUniqueId();
            // Five real 3D sections, attached in local body space. No collectible equipment.
            createArmor();
            if (!raisePillar()) { fail("no-safe-first-pillar"); return; }
            lastCharge = 0;
            lastStomp = 0;
            if (!host.checkpoint()) { fail("initial-checkpoint-failed"); return; }
            host.visualsChanged();
        } catch (RuntimeException failure) { fail("spawn-failed:" + failure.getClass().getSimpleName()); }
    }

    private void createArmor() {
            addArmor(-1.22,1.25,-.9, .35f,1.45f,2.1f, 1);
            addArmor(.87,1.25,-.9, .35f,1.45f,2.1f, 2);
            addArmor(-.95,2.65,-.9, 1.9f,.3f,2.0f, 3);
            addArmor(-.8,1.5,-1.4, 1.6f,1.1f,.3f, 4);
            addArmor(-.75,2.65,-1.65, 1.5f,.45f,.8f, 5);
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
        return buildPillar();
    }

    private boolean buildPillar() {
        Location site = pillar;
        try {
            pillarDisplays.add(block(site.clone().add(-.9,-3,-.9),Material.OBSIDIAN,new Vector3f(1.8f,3.7f,1.8f)));
            pillarDisplays.add(block(site.clone().add(-1.1,-.2,-1.1),Material.CRYING_OBSIDIAN,new Vector3f(2.2f,.45f,2.2f)));
            pillarDisplays.add(block(site.clone().add(-1.05,.3,-1.05),Material.OBSIDIAN,new Vector3f(2.1f,.35f,2.1f)));
            nextPillarTick = tick;
            site.getWorld().playSound(site,Sound.BLOCK_DEEPSLATE_PLACE,1.6f,.5f);
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
        if (ram==null || !ram.isValid() || ram.isDead()) { fail("ravager-lost-without-death-receipt"); return; }
        if (!host.inArena(ram.getLocation())) { fail("ravager-outside-arena"); return; }
        if(armor.size()!=RiftRamPolicy.REQUIRED_IMPACTS-impacts
                || armor.stream().anyMatch(piece->!piece.display().isValid())) {
            fail("armor-visual-missing"); return;
        }
        if(pillarId!=null && (pillarDisplays.size()!=3
                || pillarDisplays.stream().anyMatch(display->!display.isValid()))) {
            fail("active-pillar-visual-missing"); return;
        }
        List<Player> players=host.players();
        mites.removeIf(e->!e.isValid() || e.isDead());
        for(Endermite mite:mites) if(mite.getTarget()==null) mite.setTarget(nearest(mite.getLocation(),players));
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
                if(RiftRamPolicy.mayStartStomp(RiftRamPolicy.State.CHASING, (long)tick*50L, (long)lastStomp*50L)) {
                    begin(Action.STOMP_WARNING); hold();
                    ram.getWorld().playSound(ram.getLocation(),Sound.ENTITY_RAVAGER_STUNNED,1.6f,.6f);
                } else if(pillarId!=null && tick-nextPillarTick>=20 && tick-lastCharge>=RiftRamPolicy.CHARGE_COOLDOWN_TICKS) {
                    target=focus.getUniqueId(); face(focus.getLocation()); begin(Action.CHARGE_WARNING); hold();
                    ram.getWorld().playSound(ram.getLocation(),Sound.ENTITY_RAVAGER_ROAR,1.7f,.8f);
                } else avoidPillar();
            }
            case CHARGE_WARNING -> {
                hold();
                Player focus=players.stream().filter(p->p.getUniqueId().equals(target)).findFirst().orElse(null);
                if(focus==null) { recover(); return; }
                face(focus.getLocation());
                if(tick-actionTick>=RiftRamPolicy.WARNING_TICKS) {
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
    private void hold() { ram.setAI(false); ram.setTarget(null); ram.setVelocity(new Vector()); }
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
    private void charge(List<Player> players) {
        Location from=ram.getLocation(), to=from.clone().add(direction.clone().multiply(RiftRamPolicy.CHARGE_SPEED));
        if(tick-actionTick>=RiftRamPolicy.CHARGE_TIMEOUT_TICKS || !safeVolume(to)) { recover(); return; }
        // The objective is the active uniquely-owned pillar. Core never participates.
        if(pillarId!=null && !banked && tick-nextPillarTick>=20 && RiftRamPolicy.segmentHitsPillar(
                from.getX(),from.getY(),from.getZ(),to.getX(),to.getY(),to.getZ(),pillar.getX(),pillar.getY(),pillar.getZ())) {
            impact(); return;
        }
        if(!ram.teleport(to)) { recover(); return; }
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
        if(action!=Action.CHARGE || banked || pillarId==null || impacts>=5) return;
        banked=true; impacts++; lastCharge=tick;
        Location broken=pillar.clone(); pillarId=null; pillar=null; nextPillarTick=tick+35;
        begin(Action.STUN);
        if(!host.checkpoint()) { fail("impact-checkpoint-failed"); return; }
        clearPillar();
        dust(broken.clone().add(0,1.5,0),100,1.2,1.5);
        broken.getWorld().playSound(broken,Sound.BLOCK_DEEPSLATE_BREAK,2,.6f);
        broken.getWorld().playSound(ram.getLocation(),Sound.BLOCK_ANVIL_BREAK,2,.75f);
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
        int budget = RiftRamPolicy.endermitesToRelease(mites.size(), 2 + (impacts % 3));
        for(int i=0;i<budget;i++) {
            Endermite mite=broken.getWorld().spawn(broken,Endermite.class);
            host.own(mite,true); mite.setPersistent(true); mite.setRemoveWhenFarAway(false);
            mite.setTarget(nearest(broken,host.players())); mites.add(mite);
        }
        hold(); begin(Action.STUN); host.visualsChanged();
    }

    private void stomp(List<Player> players) {
        hold();
        int elapsed=tick-actionTick;
        double radius=Math.min(RiftRamPolicy.STOMP_RADIUS, RiftRamPolicy.STOMP_RADIUS*elapsed/RiftRamPolicy.STOMP_EXPANSION_TICKS);
        // 192 samples/update maximum, independent of roster size; nearby viewers only.
        int samples=Math.max(32,Math.min(Math.min(96,host.particleBudget()/3),(int)Math.ceil(2*Math.PI*radius/.65)));
        for(Player viewer:players) {
            if(!viewer.getWorld().equals(stompOrigin.getWorld()) || viewer.getLocation().distanceSquared(stompOrigin)>48*48) continue;
            for(int i=0;i<samples;i++) {
                double angle=2*Math.PI*i/samples;
                double x=stompOrigin.getX()+Math.cos(angle)*radius, z=stompOrigin.getZ()+Math.sin(angle)*radius;
                viewer.spawnParticle(Particle.BLOCK,x,host.floorY()+1.12,z,2,.25,.1,.25,0,Material.DEEPSLATE.createBlockData());
                if((i&1)==0) viewer.spawnParticle(Particle.BLOCK,x,host.floorY()+1.5,z,1,.3,.2,.3,0,Material.OBSIDIAN.createBlockData());
            }
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
        if(closed || completed || !Objects.equals(id(),entity)) return;
        if(impacts!=5) { fail("premature-ravager-death"); return; }
        completed=true; action=Action.DEAD;
        if(!host.checkpoint()) { completed=false; fail("completion-checkpoint-failed"); return; }
        cleanupDisplays();
    }
    private void clearPillar() {
        for(BlockDisplay d:pillarDisplays) host.remove(d);
        pillarDisplays.clear(); pillar=null; pillarId=null;
    }
    private void cleanupDisplays() {
        clearPillar();
        for(Piece p:armor) host.remove(p.display()); armor.clear();
        for(Debris d:debris) host.remove(d.display()); debris.clear();
        for(Endermite e:mites) host.remove(e); mites.clear();
        stompHits.clear(); stompRoster.clear(); chargeHits.clear();
    }
    public void close() {
        if(closed) return;
        closed=true; cleanupDisplays();
        if(ram!=null && ram.isValid()) host.remove(ram);
    }
    /** Remove transient presentation at shutdown while retaining the persistent actor. */
    public void suspend() { closed=true; cleanupDisplays(); if(ram!=null && ram.isValid()) hold(); }

    public Map<String,String> snapshot() {
        Map<String,String> data=new LinkedHashMap<>();
        data.put("ram.schema","2");
        data.put("ram.uuid",String.valueOf(id()));
        data.put("ram.impacts",Integer.toString(impacts));
        data.put("ram.tick",Integer.toString(tick));
        data.put("ram.last-charge",Integer.toString(lastCharge));
        data.put("ram.last-stomp",Integer.toString(lastStomp));
        data.put("ram.action",action.name());
        data.put("ram.action-tick",Integer.toString(actionTick));
        data.put("ram.next-pillar",Integer.toString(nextPillarTick));
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
            if(!"2".equals(data.get("ram.schema"))) throw new IllegalArgumentException("legacy-schema");
            impacts=Integer.parseInt(data.get("ram.impacts"));
            if(impacts<0 || impacts>5) throw new IllegalArgumentException("impact-range");
            persistentId=UUID.fromString(data.get("ram.uuid"));
            String receipt=data.get("ram.complete");
            if(!"true".equals(receipt) && !"false".equals(receipt)) throw new IllegalArgumentException("invalid-completion-receipt");
            if("true".equals(receipt)) {
                if(impacts!=5) throw new IllegalArgumentException("premature-completion-receipt");
                completed=true; action=Action.DEAD; return;
            }
            tick=Integer.parseInt(data.get("ram.tick"));
            lastCharge=Integer.parseInt(data.get("ram.last-charge"));
            lastStomp=Integer.parseInt(data.get("ram.last-stomp"));
            if(tick<0 || lastCharge<0 || lastStomp<0 || lastCharge>tick || lastStomp>tick)
                throw new IllegalArgumentException("invalid-clock");
            Entity entity=Bukkit.getEntity(UUID.fromString(data.get("ram.uuid")));
            if(!(entity instanceof Ravager existing) || !existing.isValid() || existing.isDead())
                throw new IllegalArgumentException("missing-persistent-ravager");
            if(!host.owns(existing)) throw new IllegalArgumentException("foreign-ravager");
            ram=existing;
            if(!host.inArena(ram.getLocation())) throw new IllegalArgumentException("actor-outside-arena");
            // In-flight actions never replay after restart. Require fresh warning after recovery.
            action=Action.RECOVER; actionTick=tick; lastCharge=tick; lastStomp=tick;
            nextPillarTick=tick+35;
            createArmor();
            if(data.containsKey("ram.pillar-id")) {
                if(impacts==5) throw new IllegalArgumentException("pillar-after-final-impact");
                pillarId=UUID.fromString(data.get("ram.pillar-id"));
                String[] xyz=data.get("ram.pillar").split(",");
                if(xyz.length!=3) throw new IllegalArgumentException("pillar-coordinate-count");
                double x=Double.parseDouble(xyz[0]),y=Double.parseDouble(xyz[1]),z=Double.parseDouble(xyz[2]);
                if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalArgumentException("pillar-nonfinite");
                pillar=new Location(ram.getWorld(),x,y,z);
                if(!safeVolume(pillar) || !buildPillar()) throw new IllegalArgumentException("pillar-site-invalid");
            }
            hold(); host.visualsChanged();
        } catch(RuntimeException failure) { fail("restore-refused:"+failure.getMessage()); }
    }
    private void fail(String reason) {
        // Preserve the last authoritative identity before presentation cleanup.
        if(ram!=null) host.checkpoint();
        close(); host.fail("RIFT_RAM:"+reason);
    }
}
