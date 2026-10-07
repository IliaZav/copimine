"""Exercise the real narrow bridge to pinned native equipment wear, not HP damage."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
BRIDGE = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/wave7/EchoNativeArmorWear.java"


@pytest.fixture(scope="module")
def armor_bridge(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-native-armor")
    sources = {
        "org/bukkit/entity/LivingEntity.java": "package org.bukkit.entity; public interface LivingEntity {}",
        "org/bukkit/damage/DamageSource.java": "package org.bukkit.damage; public interface DamageSource {}",
        "net/minecraft/tags/TagKey.java": "package net.minecraft.tags;public record TagKey(String name){}",
        "net/minecraft/tags/DamageTypeTags.java": '''package net.minecraft.tags;public class DamageTypeTags {
public static final TagKey BYPASSES_ARMOR=new TagKey("bypass"),DAMAGES_HELMET=new TagKey("helmet");}''',
        "net/minecraft/world/entity/EquipmentSlot.java": "package net.minecraft.world.entity;public enum EquipmentSlot {FEET,LEGS,CHEST,HEAD}",
        "net/minecraft/world/damagesource/DamageSource.java": '''package net.minecraft.world.damagesource;
public class DamageSource {public boolean bypass,helmet;public boolean is(net.minecraft.tags.TagKey key){
return key==net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR?bypass:helmet;}}''',
        "net/minecraft/world/entity/LivingEntity.java": '''package net.minecraft.world.entity;
public class LivingEntity {
public java.util.List<String> calls=new java.util.ArrayList<>();public Object lastSource;
protected void doHurtEquipment(net.minecraft.world.damagesource.DamageSource source,float amount,EquipmentSlot... slots){
lastSource=source;calls.add(amount+":"+java.util.Arrays.toString(slots));}
}''',
        "org/bukkit/craftbukkit/damage/CraftDamageSource.java": '''package org.bukkit.craftbukkit.damage;
public class CraftDamageSource implements org.bukkit.damage.DamageSource {
public net.minecraft.world.damagesource.DamageSource nativeSource=new net.minecraft.world.damagesource.DamageSource();
public net.minecraft.world.damagesource.DamageSource getHandle(){return nativeSource;}}''',
        "ArmorBridgeChecks.java": '''import me.copimine.endevent.runtime.wave7.EchoNativeArmorWear;
public class ArmorBridgeChecks {
 public static class Carrier implements org.bukkit.entity.LivingEntity {
 public net.minecraft.world.entity.LivingEntity nativeEntity=new net.minecraft.world.entity.LivingEntity();
 public net.minecraft.world.entity.LivingEntity getHandle(){return nativeEntity;}}
 public static void main(String[] args){
 var carrier=new Carrier();var source=new org.bukkit.craftbukkit.damage.CraftDamageSource();
 var bridge=new EchoNativeArmorWear(carrier);double original=12,armor=9;
 switch(args[0]){case "bypass":source.nativeSource.bypass=true;break;
 case "helmet":source.nativeSource.helmet=true;break;
 case "helmet-bypass":source.nativeSource.helmet=true;source.nativeSource.bypass=true;break;
 case "invalid":try{bridge.wear(source,Double.NaN,9);throw new AssertionError("invalid native amount admitted");}
 catch(IllegalArgumentException expected){if(!carrier.nativeEntity.calls.isEmpty())throw new AssertionError("invalid input wore armor");return;}
 case "foreign-source":try{bridge.wear(new org.bukkit.damage.DamageSource(){},12,9);throw new AssertionError("foreign source admitted");}
 catch(IllegalArgumentException expected){if(!carrier.nativeEntity.calls.isEmpty())throw new AssertionError("foreign source wore armor");return;}
 case "normal":break;default:throw new AssertionError(args[0]);}
 bridge.wear(source,original,armor);
 var expected=switch(args[0]){
 case "bypass"->java.util.List.<String>of();
 case "helmet"->java.util.List.of("12.0:[HEAD]","9.0:[FEET, LEGS, CHEST, HEAD]");
 case "helmet-bypass"->java.util.List.of("12.0:[HEAD]");
 default->java.util.List.of("9.0:[FEET, LEGS, CHEST, HEAD]");};
 if(!carrier.nativeEntity.calls.equals(expected))throw new AssertionError("missing/wrong native equipment path: "+carrier.nativeEntity.calls);
 if(!expected.isEmpty()&&carrier.nativeEntity.lastSource!=source.nativeSource)throw new AssertionError("damage-source identity lost");
 }
}''',
    }
    if BRIDGE.exists():
        sources["me/copimine/endevent/runtime/wave7/EchoNativeArmorWear.java"] = BRIDGE.read_text(encoding="utf-8")
    else:
        # Executable baseline: the carrier currently has no native armor adapter.
        sources["me/copimine/endevent/runtime/wave7/EchoNativeArmorWear.java"] = '''package me.copimine.endevent.runtime.wave7;
public class EchoNativeArmorWear {public EchoNativeArmorWear(org.bukkit.entity.LivingEntity carrier){}
public void wear(org.bukkit.damage.DamageSource source,double original,double armor){}}'''
    paths = []
    for name, source in sources.items():
        path = directory / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        paths.append(str(path))
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), *paths], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return directory


@pytest.mark.parametrize("scenario", ["normal", "bypass", "helmet", "helmet-bypass", "invalid", "foreign-source"])
def test_native_equipment_wear_bridge(armor_bridge, scenario):
    result = subprocess.run(["java", "-cp", str(armor_bridge), "ArmorBridgeChecks", scenario], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
