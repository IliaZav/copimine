"""Actual fracture restore/teardown retains ownership until exact durable success."""
from pathlib import Path
import subprocess
import pytest
from tests.test_wave_navigation_adapter import extract
ROOT=Path(__file__).resolve().parents[1]
@pytest.mark.parametrize("mode",["success","already-original","world-missing","changed","write-refused","journal-refused","journal-missing"])
def test_exact_restore_keeps_failed_ownership(tmp_path,mode):
    source=(ROOT/"copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text("utf-8")
    methods="\n".join(extract(source,signature) for signature in [
        "boolean restoreCurrentRiftFractureBlocks(RiftFractureRuntimeState state)",
        "void clearCurrentRiftFractures(String reason)"])
    java=tmp_path/"FractureRestoreProbe.java"
    java.write_text(r'''
import java.util.*;
public class FractureRestoreProbe {
 record BlockCellKey(UUID worldId,int x,int y,int z){}
 static class BlockData {String value;BlockData(String s){value=s;}String getAsString(){return value;}}
 static class Block {String value="magma";boolean writes=true;String getType(){return value;}
  BlockData getBlockData(){return new BlockData(value);}}
 static class World {Block block=new Block();Block getBlockAt(int x,int y,int z){return block;}}
 static class Journal {boolean writable=true;int marks;boolean markRestored(){marks++;return writable;}
  String path(){return "test";}}
 static class RiftFractureRuntimeState {
  boolean restorationPending;UUID id=UUID.randomUUID();World world=new World();BlockCellKey key=new BlockCellKey(UUID.randomUUID(),5,64,6);
  UUID id(){return id;}World world(){return world;}
  Map<BlockCellKey,String> originals(){return Map.of(key,"original[axis=x]");}
  List<BlockCellKey> cells(){return List.of(key);}}
 Journal hazardJournal=new Journal();String eventId="test";long generation=8,nextCurrentRiftFractureBatchTick=90;
 int currentRiftFractureCursor=2;Map<UUID,RiftFractureRuntimeState> activeCurrentRiftFractures=new HashMap<>();
 Map<BlockCellKey,UUID> currentRiftFractureCells=new HashMap<>();
 java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
 boolean isRiftFractureSurface(String type){return type.equals("magma");}
 void restoreBlock(Block b,String original){if(b.writes)b.value=original;}
 void removeCurrentRiftFracture(RiftFractureRuntimeState state,String reason){activeCurrentRiftFractures.remove(state.id);currentRiftFractureCells.remove(state.key);}
'''+methods+r'''
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){String mode=args[0];var p=new FractureRestoreProbe();var s=new RiftFractureRuntimeState();
  p.activeCurrentRiftFractures.put(s.id,s);p.currentRiftFractureCells.put(s.key,s.id);
  if(mode.equals("world-missing"))s.world=null;
  if(mode.equals("changed"))s.world.block.value="player-chest";
  if(mode.equals("already-original"))s.world.block.value="original[axis=x]";
  if(mode.equals("write-refused"))s.world.block.writes=false;
  if(mode.equals("journal-refused"))p.hazardJournal.writable=false;
  if(mode.equals("journal-missing"))p.hazardJournal=null;
  p.clearCurrentRiftFractures("probe");
  boolean success=mode.equals("success")||mode.equals("already-original");
  if(success){check(p.activeCurrentRiftFractures.isEmpty()&&p.currentRiftFractureCells.isEmpty(),"restored ownership not retired");
   check(s.world.block.value.equals("original[axis=x]")&&p.hazardJournal.marks==1,"exact restoration not committed");}
  else {check(p.activeCurrentRiftFractures.containsKey(s.id)&&p.currentRiftFractureCells.containsKey(s.key),"failed restoration discarded retry ownership");
   if(p.hazardJournal!=null&&!mode.equals("journal-refused"))check(p.hazardJournal.marks==0,"incomplete world restore marked durable success");
   if(mode.equals("changed"))check(s.world.block.value.equals("player-chest"),"foreign edit overwritten");
   if(s.world==null)s.world=new World();s.world.block.writes=true;s.world.block.value="magma";
   if(p.hazardJournal==null)p.hazardJournal=new Journal();p.hazardJournal.writable=true;
   p.clearCurrentRiftFractures("retry");check(p.activeCurrentRiftFractures.isEmpty(),"retry failed");}
 }
}
''',encoding="utf-8")
    built=subprocess.run(["javac","-d",str(tmp_path),str(java)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    ran=subprocess.run(["java","-cp",str(tmp_path),"FractureRestoreProbe",mode],capture_output=True,text=True)
    assert ran.returncode==0,ran.stderr

@pytest.mark.parametrize("mode",["cracked","magma","original","foreign","write-refused","web-refused"])
def test_cold_journal_recovery_verifies_exact_blocks(tmp_path,mode):
    source=(ROOT/"copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text("utf-8")
    signature="boolean restoreMagmaJournalEntry(World world, HazardMutationJournal.Entry entry)"
    if "    private "+signature in source:
        method=extract(source,signature)
    else:
        recovery=extract(source,"void recoverHazardJournal()")
        start=recovery.rindex("            Block floor = world.getBlockAt(entry.x(), entry.floorY(), entry.z());")
        end=recovery.index("\n        }\n        if (realitySplitRecoveryBlocked)",start)
        method="boolean restoreMagmaJournalEntry(World world,HazardMutationJournal.Entry entry){int restored=0,skipped=0;"+recovery[start:end]+"return true;}"
    java=tmp_path/"ColdFractureProbe.java"
    java.write_text(r'''
public class ColdFractureProbe {
 static class Material {static String COBWEB="cobweb",MAGMA_BLOCK="magma";}
 record BlockData(String data){String getAsString(){return data;}}
 static class Block {String data="cracked";boolean writable=true;String getType(){return data;}BlockData getBlockData(){return new BlockData(data);}}
 static class World {Block floor=new Block(),web=new Block();Block getBlockAt(int x,int y,int z){return y==64?floor:web;}}
 static class HazardMutationJournal {record Entry(int x,int floorY,int z,String floorOriginal,String webOriginal){boolean hasWebMutation(){return !webOriginal.isEmpty();}}}
 boolean isRiftFractureSurface(String type){return type.equals("cracked")||type.equals("magma");}
 void restoreBlock(Block block,String data){if(block.writable)block.data=data;}
'''+method+r'''
 public static void main(String[] args){var p=new ColdFractureProbe();var w=new World();String mode=args[0];
  w.floor.data=mode.equals("magma")?"magma":mode.equals("original")?"original[axis=x]":mode.equals("foreign")?"player-chest":"cracked";
  if(mode.equals("write-refused"))w.floor.writable=false;
  String webOriginal="";if(mode.equals("web-refused")){webOriginal="air";w.web.data="cobweb";w.web.writable=false;}
  boolean success=p.restoreMagmaJournalEntry(w,new HazardMutationJournal.Entry(1,64,1,"original[axis=x]",webOriginal));
  boolean expected=mode.equals("cracked")||mode.equals("magma")||mode.equals("original");
  if(success!=expected)throw new AssertionError("unconfirmed cold restore returned "+success+" for "+mode);
  if(expected&&!w.floor.data.equals("original[axis=x]"))throw new AssertionError("exact original not restored");
  if(mode.equals("foreign")&&!w.floor.data.equals("player-chest"))throw new AssertionError("foreign edit overwritten");
 }
}
''',encoding="utf-8")
    built=subprocess.run(["javac","-d",str(tmp_path),str(java)],capture_output=True,text=True)
    assert built.returncode==0,built.stderr
    ran=subprocess.run(["java","-cp",str(tmp_path),"ColdFractureProbe",mode],capture_output=True,text=True)
    assert ran.returncode==0,ran.stderr
