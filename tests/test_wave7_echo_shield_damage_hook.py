"""Execute the real native-block observer; it must never fabricate a blocked hit."""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def shield_hook(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-native-shield-hook")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signature = "void onEchoProbeShieldBlock(EntityDamageByEntityEvent event)"
    callback = extract(source, signature) if signature in source else "public void onEchoProbeShieldBlock(EntityDamageByEntityEvent event) {}"
    harness = directory / "EchoShieldHookChecks.java"
    harness.write_text(r'''
import java.util.*;import java.util.logging.Level;
public class EchoShieldHookChecks {
    static final UUID ACTOR=new UUID(0,1),OWNER=new UUID(0,2),OTHER=new UUID(0,3);
    enum Material {IRON_AXE,IRON_SWORD}
    static class Item {Material material;Item(Material value){material=value;}Material getType(){return material;}}
    static class Equipment {Item main=new Item(Material.IRON_SWORD);Item getItemInMainHand(){return main;}}
    static class Entity {UUID id;Entity(UUID value){id=value;}UUID getUniqueId(){return id;}}
    static class LivingEntity extends Entity {Equipment equipment=new Equipment();LivingEntity(UUID id){super(id);}Equipment getEquipment(){return equipment;}}
    static class Player extends LivingEntity {Player(UUID id){super(id);}}
    static class Bukkit {static Player player=new Player(OWNER);static Player getPlayer(UUID id){return player;}}
    static class EntityDamageEvent {enum DamageModifier {BLOCKING} enum DamageCause {ENTITY_ATTACK,PROJECTILE}}
    static class EntityDamageByEntityEvent extends EntityDamageEvent {
        Entity entity=new Entity(ACTOR),damager=new LivingEntity(OWNER);boolean cancelled,applicable=true;double blocked=-4.9;
        DamageCause cause=DamageCause.ENTITY_ATTACK;
        Entity getEntity(){return entity;}Entity getDamager(){return damager;}boolean isCancelled(){return cancelled;}
        boolean isApplicable(DamageModifier modifier){return applicable;}double getDamage(DamageModifier modifier){return blocked;}
        DamageCause getCause(){return cause;}
    }
    static class Probe {
        Entity carrier=new Entity(ACTOR);int hits;boolean axe,capable;double amount;long generation,tick;UUID event;Object receipt;
        Entity carrier(){return carrier;}UUID owner(){return OWNER;}
        boolean acceptedShieldBlock(Object receipt,Player player,UUID event,long generation,long tick,boolean capable,double amount,boolean axe){
            if(player!=Bukkit.player)throw new AssertionError("wrong owner object");
            hits++;this.receipt=receipt;this.axe=axe;this.capable=capable;this.amount=amount;this.generation=generation;this.tick=tick;this.event=event;return true;
        }
    }
    Probe echoPresentationProbe=new Probe();long generation=7,eventTickCounter=110;String eventId=new UUID(0,4).toString();
    Set<UUID> echoPresentationCapablePlayers=new HashSet<>(Set.of(OWNER));
    UUID parseUuidOrNull(String value){return UUID.fromString(value);}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
    boolean clearEchoPresentationProbe(){echoPresentationProbe=null;return true;}
''' + callback + r'''
    public static void main(String[] args){
        var adapter=new EchoShieldHookChecks();var hit=new EntityDamageByEntityEvent();
        switch(args[0]){
            case "own":break;
            case "axe":((LivingEntity)hit.damager).equipment.main=new Item(Material.IRON_AXE);break;
            case "projectile":hit.cause=EntityDamageEvent.DamageCause.PROJECTILE;((LivingEntity)hit.damager).equipment.main=new Item(Material.IRON_AXE);break;
            case "foreign":hit.entity=new Entity(OTHER);break;
            case "cancelled":hit.cancelled=true;break;
            case "behind":hit.blocked=0;break;
            case "no-modifier":hit.applicable=false;break;
            default:throw new AssertionError(args[0]);
        }
        adapter.onEchoProbeShieldBlock(hit);var probe=adapter.echoPresentationProbe;
        boolean accepted=Set.of("own","axe","projectile").contains(args[0]);
        if(probe.hits!=(accepted?1:0))throw new AssertionError("native block observer missed/refabricated hit; hits="+probe.hits);
        if(accepted&&(probe.receipt!=hit||probe.amount!=4.9||probe.axe!=args[0].equals("axe")
            ||!probe.capable||probe.generation!=7||probe.tick!=110||!probe.event.equals(new UUID(0,4))))throw new AssertionError("native block context/axe classification changed");
    }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(harness)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["own", "axe", "projectile", "foreign", "cancelled", "behind", "no-modifier"])
def test_native_block_observer(shield_hook, scenario):
    result = subprocess.run(["java", "-cp", str(shield_hook), "EchoShieldHookChecks", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
