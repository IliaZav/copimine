"""Compile and execute the actual item adapter with both public API shapes."""
from pathlib import Path
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / "copimine-end-event/src/me/copimine/endevent/presentation/ItemPresentationComponents.java"


@pytest.mark.parametrize("modern", [False, True])
def test_components_preserve_legacy_linkage_and_modern_fail_closed_semantics(tmp_path, modern):
    api = tmp_path / "org/bukkit/inventory/meta/ItemMeta.java"
    api.parent.mkdir(parents=True)
    methods = """
    void setItemModel(org.bukkit.NamespacedKey model);
    Data getCustomModelDataComponent();
    void setCustomModelDataComponent(Data data);
    """ if modern else ""
    api.write_text("""package org.bukkit.inventory.meta;
public interface ItemMeta {
    interface Data { void setFloats(java.util.List<Float> values); }
""" + methods + "}\n", encoding="utf-8")
    key = tmp_path / "org/bukkit/NamespacedKey.java"
    key.write_text("package org.bukkit; public record NamespacedKey(String value) {}", encoding="utf-8")
    probe = tmp_path / "ItemComponentProbe.java"
    probe.write_text(r'''
import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import me.copimine.endevent.presentation.ItemPresentationComponents;
public class ItemComponentProbe {
    public static class FrameData implements ItemMeta.Data {
        List<Float> values=List.of();
        public void setFloats(List<Float> values){this.values=values;}
    }
    public static class Meta implements ItemMeta {
        NamespacedKey model;FrameData data=new FrameData();boolean fail;int writes;
        public void setItemModel(NamespacedKey model){if(fail)throw new IllegalStateException("denied");this.model=model;}
        public ItemMeta.Data getCustomModelDataComponent(){return data;}
        public void setCustomModelDataComponent(ItemMeta.Data value){if(fail)throw new IllegalStateException("denied");data=(FrameData)value;writes++;}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        boolean modern=Boolean.parseBoolean(args[0]);Meta meta=new Meta();
        NamespacedKey key=new NamespacedKey("copimine:end_rift_rift_bolt");
        check(ItemPresentationComponents.setItemModel(meta,key)==modern,"API capability mismatch");
        if(!modern){
            check(meta.model==null,"legacy identity changed");
            try{ItemPresentationComponents.setFloats(meta,List.of(1F));throw new AssertionError("invisible ring admitted");}
            catch(UnsupportedOperationException expected){}
            check(meta.writes==0,"legacy data mutated");return;
        }
        check(meta.model.equals(key),"modern model identity lost");
        var source=new ArrayList<>(List.of(1F,2F));ItemPresentationComponents.setFloats(meta,source);source.set(0,9F);
        check(meta.writes==1&&meta.data.values.equals(List.of(1F,2F)),"frame not committed immutably");
        try{ItemPresentationComponents.setFloats(meta,List.of(Float.NaN));throw new AssertionError("nonfinite accepted");}
        catch(IllegalArgumentException expected){}
        check(meta.writes==1,"invalid frame wrote component");meta.fail=true;
        try{ItemPresentationComponents.setItemModel(meta,key);throw new AssertionError("model failure hidden");}
        catch(IllegalStateException expected){check(expected.getCause()!=null,"model failure lost cause");}
        try{ItemPresentationComponents.setFloats(meta,List.of(3F));throw new AssertionError("frame failure hidden");}
        catch(IllegalStateException expected){check(expected.getCause()!=null,"frame failure lost cause");}
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                             str(key), str(api), str(ADAPTER), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ItemComponentProbe", str(modern).lower()],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
