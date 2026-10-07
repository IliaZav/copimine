"""Run the real typed registry aliases against both Minecraft naming eras."""
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("mode", ["modern", "legacy", "both", "missing"])
def test_attribute_aliases_preserve_real_values_and_reject_missing_registry(tmp_path, mode):
    sources = {
        "org/bukkit/NamespacedKey.java": "package org.bukkit; public record NamespacedKey(String value){public static NamespacedKey minecraft(String value){return new NamespacedKey(value);}}",
        "org/bukkit/attribute/Attribute.java": "package org.bukkit.attribute; public record Attribute(String key){}",
        "org/bukkit/Registry.java": '''package org.bukkit;
import java.util.*; import org.bukkit.attribute.Attribute;
public class Registry {
 public static final Registry ATTRIBUTE=new Registry();
 public static final Map<String,Attribute> values=new HashMap<>();
 public Attribute get(NamespacedKey key){return values.get(key.value());}
}''',
        "AttributeProbe.java": '''
import org.bukkit.Registry;
public class AttributeProbe {
 public static void main(String[] args){
   for(String key:new String[]{"max_health","attack_damage","attack_knockback","scale"}) {
     if(args[0].equals("modern")||args[0].equals("both")) Registry.values.put(key,new org.bukkit.attribute.Attribute("modern:"+key));
     if(args[0].equals("legacy")||args[0].equals("both")) Registry.values.put("generic."+key,new org.bukkit.attribute.Attribute("legacy:"+key));
   }
   try {
     var value=me.copimine.endevent.runtime.compat.Attribute.GENERIC_MAX_HEALTH;
     if(args[0].equals("missing"))throw new AssertionError("missing health registry accepted");
     String era=args[0].equals("legacy")?"legacy:":"modern:";
     if(!value.key().equals(era+"max_health"))throw new AssertionError("wrong max health registry");
     if(!me.copimine.endevent.runtime.compat.Attribute.GENERIC_ATTACK_DAMAGE.key().equals(era+"attack_damage"))throw new AssertionError("wrong damage registry");
     if(!me.copimine.endevent.runtime.compat.Attribute.GENERIC_ATTACK_KNOCKBACK.key().equals(era+"attack_knockback"))throw new AssertionError("wrong knockback registry");
     if(!me.copimine.endevent.runtime.compat.Attribute.GENERIC_SCALE.key().equals(era+"scale"))throw new AssertionError("wrong scale registry");
   } catch(ExceptionInInitializerError error){
     if(!args[0].equals("missing") || !(error.getCause() instanceof IllegalStateException)) throw error;
   }
 }
}'''}
    paths = []
    for name, source in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        paths.append(str(path))
    production = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/compat/Attribute.java"
    result = subprocess.run(["javac", "-d", str(tmp_path), str(production), *paths], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "AttributeProbe", mode], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
