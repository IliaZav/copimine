"""Run the real bounded replica supply state; fixtures contain no player data."""
from pathlib import Path
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "copimine-end-event/src/me/copimine/endevent"


@pytest.fixture(scope="module")
def loadout_checks(tmp_path_factory):
    directory = tmp_path_factory.mktemp("echo-loadout-state")
    state = BASE / "domain/wave7/EchoLoadoutState.java"
    assert state.exists(), "Echo still has no frozen finite replica inventory"
    harness = directory / "EchoLoadoutChecks.java"
    harness.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.wave7.EchoLoadoutState;
import me.copimine.endevent.domain.wave7.EchoLoadoutState.Item;
public class EchoLoadoutChecks {
    static UUID EVENT=new UUID(0,1),OWNER=new UUID(0,2),DUEL=new UUID(0,3);
    static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    static List<Item> original(){
        var items=new ArrayList<Item>(Collections.nCopies(41,Item.empty()));
        items.set(0,new Item("IRON_SWORD",1,250,123,Map.of("minecraft:sharpness",3),"Fixture sword",false));
        items.set(1,new Item("GOLDEN_APPLE",5,0,0,Map.of(),"",false));
        items.set(2,new Item("ARROW",2,0,0,Map.of(),"",false));
        items.set(40,new Item("SHIELD",1,336,45,Map.of(),"",false));return items;
    }
    static EchoLoadoutState state(){return EchoLoadoutState.create(EVENT,7,DUEL,OWNER,original(),0);}
    static void reject(Runnable call,String reason){try{call.run();throw new AssertionError(reason);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){switch(args[0]){
    case "capture":{
        var source=original();var state=EchoLoadoutState.create(EVENT,7,DUEL,OWNER,source,0);
        check(source.get(0).damage()==123&&source.get(40).damage()==45,"source gear must not be repaired");
        check(state.item(0).damage()==0&&state.item(40).damage()==0,"only initial replica repaired");
        check(state.remaining(1)==5&&state.remaining(41)==2,"two EXTRA apples outside a full copied inventory");
        source.set(0,Item.empty());check(state.item(0).material().equals("IRON_SWORD"),"frozen snapshot aliases source");
        check(state.selectedSlot()==0&&state.item(0).enchantments().get("minecraft:sharpness")==3,"safe combat descriptors");break;}
    case "finite":{
        var state=state();check(state.consume(2,1,0),"first arrow consumed");
        check(!state.consume(2,1,0),"duplicate/stale receipt consumed again");
        check(state.remaining(2)==1&&state.revision()==1,"exact first consumption");
        check(state.consume(2,1,1)&&state.remaining(2)==0,"last arrow");
        check(!state.consume(2,1,2)&&!state.consume(1,-1,2),"cannot mint or underflow supplies");
        check(!state.consume(0,1,2),"consumption cannot erase nonconsumable gear");break;}
    case "restore":{
        var state=state();state.consume(41,1,0);check(state.damage(0,17,1),"replica damage");
        var encoded=state.encode();var restored=EchoLoadoutState.restore(encoded,EVENT,7,DUEL,OWNER,2);
        check(restored.remaining(41)==1&&restored.item(0).damage()==17,"restart refilled apples or repaired used gear");
        check(restored.revision()==2&&!restored.consume(41,1,1),"restored stale receipt accepted");
        check(restored.consume(41,1,2)&&restored.remaining(41)==0,"restored final apple");
        reject(()->EchoLoadoutState.restore(encoded,EVENT,7,DUEL,OWNER,3),"old save overwrote newer consumption");
        reject(()->EchoLoadoutState.restore(encoded,EVENT,8,DUEL,OWNER,0),"foreign attempt accepted");
        reject(()->EchoLoadoutState.restore(encoded,EVENT,7,new UUID(0,9),OWNER,0),"foreign duel accepted");break;}
    case "custom":{
        var source=original();source.set(0,new Item("IRON_SWORD",1,250,123,Map.of(),"Artifact",true));
        var state=EchoLoadoutState.create(EVENT,7,DUEL,OWNER,source,0);
        check(!state.item(0).usable()&&!state.damage(0,10,0),"privileged custom item became vanilla weapon");
        source.set(0,new Item("TNT",64,0,0,Map.of(),"",false));
        var inert=EchoLoadoutState.create(EVENT,7,DUEL,OWNER,source,0);
        check(!inert.item(0).usable()&&!inert.consume(0,1,0),"noncombat tool usable");break;}
    case "malformed":{
        var encoded=new HashMap<>(state().encode());encoded.put("slot.41.amount","3");
        reject(()->EchoLoadoutState.restore(encoded,EVENT,7,DUEL,OWNER,0),"forged allowance");
        var negative=new HashMap<>(state().encode());negative.put("slot.2.remaining","-1");
        reject(()->EchoLoadoutState.restore(negative,EVENT,7,DUEL,OWNER,0),"negative supplies");
        var unknown=new HashMap<>(state().encode());unknown.put("secret","unrecognized");
        reject(()->EchoLoadoutState.restore(unknown,EVENT,7,DUEL,OWNER,0),"unknown fields accepted");
        reject(()->new Item("IRON_SWORD",1,250,0,Map.of("plugin:execute",1),"",false),"foreign executable enchant");break;}
    case "bounds":{
        var state=state();check(state.damage(0,250,0),"break replica");
        check(state.remaining(0)==0&&!state.damage(0,1,1),"broken gear revived");
        check(!state.consume(-1,1,1)&&!state.damage(42,1,1),"slot bounds");
        reject(()->EchoLoadoutState.create(EVENT,7,DUEL,OWNER,original(),9),"selected slot outside hotbar");
        reject(()->new Item("ARROW",65,0,0,Map.of(),"",false),"oversized stack");break;}
    default:throw new AssertionError(args[0]);}}
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(directory), str(state), str(harness)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize("scenario", ["capture", "finite", "restore", "custom", "malformed", "bounds"])
def test_frozen_loadout_and_supplies(loadout_checks, scenario):
    result = subprocess.run(["java", "-cp", str(loadout_checks), "EchoLoadoutChecks", scenario],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
