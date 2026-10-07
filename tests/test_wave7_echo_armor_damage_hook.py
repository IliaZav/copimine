"""Execute the current carrier observer with pre-armor amounts and original source."""
from pathlib import Path
import subprocess

import pytest
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def armor_hook(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-armor-hook")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    signature = "void onEchoProbeArmorDamage(EntityDamageEvent event)"
    callback = extract(source, signature) if signature in source else "public void onEchoProbeArmorDamage(EntityDamageEvent event) {}"
    harness = directory / "EchoArmorHookChecks.java"
    harness.write_text(r'''
import java.util.*;import java.util.logging.Level;
public class EchoArmorHookChecks {
 static final UUID ACTOR=new UUID(0,1),OWNER=new UUID(0,2),OTHER=new UUID(0,3);
 static class Entity {UUID id;Entity(UUID id){this.id=id;}UUID getUniqueId(){return id;}}
 static class Player extends Entity {Player(UUID id){super(id);}}
 static class Bukkit {static Player owner=new Player(OWNER);static Player getPlayer(UUID id){return owner;}}
 static class DamageSource {}
 static class EntityDamageEvent {
  enum DamageModifier {BASE,BLOCKING,HARD_HAT}
  Entity entity=new Entity(ACTOR);boolean cancelled,modifiers=true;DamageSource source=new DamageSource();
  Entity getEntity(){return entity;}boolean isCancelled(){return cancelled;}DamageSource getDamageSource(){return source;}
  double getDamage(){return 12;}double getFinalDamage(){return 0;}
  boolean isApplicable(DamageModifier modifier){return modifier==DamageModifier.BASE||modifiers;}
  double getDamage(DamageModifier modifier){return switch(modifier){case BASE->12;case BLOCKING->-4;case HARD_HAT->-3;};}
  double getOriginalDamage(DamageModifier modifier){return 18;}
 }
 static class Probe {
  Entity carrier=new Entity(ACTOR);int calls;double original,armor;Object receipt;DamageSource source;
  Entity carrier(){return carrier;}UUID owner(){return OWNER;}
  boolean acceptedArmorDamage(Object receipt,Player owner,UUID event,long generation,long tick,boolean capable,
                              DamageSource source,double original,double armor){
   if(owner!=Bukkit.owner||!event.equals(new UUID(0,4))||generation!=7||tick!=110||!capable)throw new AssertionError("armor context changed");
   calls++;this.receipt=receipt;this.source=source;this.original=original;this.armor=armor;return true;
  }
 }
 Probe echoPresentationProbe=new Probe();String eventId=new UUID(0,4).toString();long generation=7,eventTickCounter=110;
 Set<UUID> echoPresentationCapablePlayers=new HashSet<>(Set.of(OWNER));
 UUID parseUuidOrNull(String value){return UUID.fromString(value);}
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
 boolean clearEchoPresentationProbe(){echoPresentationProbe=null;return true;}
''' + callback + r'''
 public static void main(String[] args){
  var adapter=new EchoArmorHookChecks();var event=new EntityDamageEvent();
  switch(args[0]){case "normal":break;case "no-modifiers":event.modifiers=false;break;
   case "foreign":event.entity=new Entity(OTHER);break;case "cancelled":event.cancelled=true;break;default:throw new AssertionError(args[0]);}
  adapter.onEchoProbeArmorDamage(event);var probe=adapter.echoPresentationProbe;
  boolean accepted=Set.of("normal","no-modifiers").contains(args[0]);
  if(probe.calls!=(accepted?1:0))throw new AssertionError("accepted carrier armor event missed or foreign/cancelled hit admitted: "+probe.calls);
  if(accepted&&(probe.receipt!=event||probe.source!=event.source||probe.original!=18||probe.armor!=(event.modifiers?5:12)))
   throw new AssertionError("armor wear used mitigated final damage or changed source; original="+probe.original+", armor="+probe.armor);
 }
}
''', encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(harness)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["normal", "no-modifiers", "foreign", "cancelled"])
def test_accepted_armor_observer(armor_hook, scenario):
    result = subprocess.run(["java", "-cp", str(armor_hook), "EchoArmorHookChecks", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
