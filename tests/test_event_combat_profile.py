"""Execute the event-wide profile service without Bukkit or world threads."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_profiles_span_waves_but_never_fabricate_missing_or_artificial_observations(tmp_path):
    service = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/EventCombatProfileService.java"
    assert service.exists(), "Official W1-W6 gameplay has no event-wide combat profile collector"
    probe = tmp_path / "CombatProfileProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.runtime.EventCombatProfileService;
import me.copimine.endevent.runtime.EventCombatProfileService.*;
public class CombatProfileProbe {
    static final UUID OWNER=new UUID(0,1), OTHER=new UUID(0,2);
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static Context context(long tick,int wave){return new Context("attempt",71,OWNER,tick,wave,true,false,false,false,false);}
    public static void main(String[] args) {
        var p=new EventCombatProfileService();p.beginAttempt("attempt",71,71,Set.of(OWNER),true);
        require(p.sample(context(5,1),new Movement(3,true,-.2,false,.4,.8)),"real official W1 observation accepted");
        require(!p.sample(context(6,1),new Movement(3,true,-.2,false,.4,.8)),"movement is bounded to one sample per five ticks");
        for(int t=10;t<=300;t+=5)p.sample(context(t,1),new Movement(3,true,.2,false,.4,.8));
        require(p.snapshot(OWNER).recent().size()==32,"only 32 recent observations retained; no full trajectory");
        long before=p.snapshot(OWNER).count(Stat.MOVEMENT_SAMPLES);
        p.rotateRuntime("attempt",72);
        var six=new Context("attempt",72,OWNER,5,6,true,false,false,false,false);
        p.sample(six,new Movement(13,false,0,true,-.2,.3));
        var frozen=p.snapshot(OWNER);
        require(frozen.count(Stat.MOVEMENT_SAMPLES)==before+1 && frozen.observedWaves()==33,"W1 and W6 survive runtime rotation without inventing W2-W5");
        require(frozen.count(Stat.SHIELD_SAMPLES)==1 && frozen.count(Stat.DISTANCE_FAR)==1,"defense and actual distance aggregates recorded");
        require(!p.sample(context(400,1),new Movement(3,true,0,false,0,.8)),"stale generation rejected");
        for(int excluded=0;excluded<5;excluded++) {
            var c=new Context("attempt",72,OWNER,400,6,excluded!=0,excluded==1,excluded==2,excluded==3,excluded==4);
            require(!p.sample(c,new Movement(3,true,0,false,0,.8)),"inactive, diagnostics, forced motion, fog freeze and prisoner confinement excluded");
        }
        require(!p.sample(new Context("attempt",72,OTHER,400,6,true,false,false,false,false),new Movement(3,true,0,false,0,.8)),"foreign player cannot acquire a profile");
        require(!p.sample(new Context("attempt",72,OWNER,400,7,true,false,false,false,false),new Movement(3,true,0,false,0,.8)),"long-term persona freezes before W7");
        require(!p.sample(six,new Movement(Double.NaN,false,0,false,0,.8)),"nonfinite input cannot poison aggregates");

        var hit=new Context("attempt",72,OWNER,10,6,true,false,false,false,false);
        p.acceptedHit(hit,"impact-1",Weapon.BOW,true,true);
        p.acceptedHit(hit,"impact-1",Weapon.BOW,true,true);
        require(p.snapshot(OWNER).count(Stat.RANGED_HITS)==1 && p.snapshot(OWNER).count(Stat.JUMP_CRITICAL_HITS)==0,"one accepted receipt; critical arrows are never jump melee");
        p.acceptedHit(new Context("attempt",72,OWNER,20,6,true,false,false,false,false),"impact-2",Weapon.SWORD,true,false);
        p.acceptedHit(new Context("attempt",72,OWNER,30,6,true,false,false,false,false),"impact-3",Weapon.SWORD,true,true);
        require(p.snapshot(OWNER).count(Stat.JUMP_CRITICAL_HITS)==1,"melee critical requires native critical and eligible descent facts");
        require(frozen.count(Stat.RANGED_HITS)==0,"frozen persona remains immutable while live aggregates change");
        p.swing(hit);p.switchItem(hit);p.bowRelease(hit,Weapon.BOW,20);p.healUse(hit,.3);
        require(p.snapshot(OWNER).count(Stat.ATTEMPTED_SWINGS)==1 && p.snapshot(OWNER).count(Stat.HEAL_HEALTH_PERMILLE)==300,"attempted action and healing threshold tracked independently");

        var encoded=p.encode();require(encoded.toString().length()<5000,"compact checkpoint is bounded and contains no coordinates or item/skin data");
        var restored=new EventCombatProfileService();
        require(restored.restore(encoded,"attempt",72,Set.of(OWNER)),"valid current checkpoint restored");
        require(restored.snapshot(OWNER).count(Stat.RANGED_HITS)==1 && restored.snapshot(OWNER).observedWaves()==33,"restart retains real aggregate coverage");
        require(!new EventCombatProfileService().restore(encoded,"other-attempt",72,Set.of(OWNER)),"foreign attempt restore rejected");
        require(!new EventCombatProfileService().restore(encoded,"attempt",73,Set.of(OWNER)),"stale snapshot cannot attach to new runtime");
        var invalid=new HashMap<>(encoded);invalid.put("combat-profile."+OWNER+".stats","-1");
        require(!restored.restore(invalid,"attempt",72,Set.of(OWNER)) && restored.snapshot(OWNER).count(Stat.RANGED_HITS)==1,"malformed counters refused without clearing valid live state");
        p.beginAttempt("attempt",80,80,Set.of(OWNER),true);
        require(p.snapshot(OWNER).count(Stat.MOVEMENT_SAMPLES)==0,"new attempt starts separate observations even with same event UUID");
        p.beginAttempt("attempt",81,81,Set.of(OWNER),false);
        require(!p.snapshot(OWNER).fromEventStart() && p.snapshot(OWNER).observedWaves()==0 && p.snapshot(OWNER).confidence()==0,"mid-event instrumentation declares missing coverage and neutral confidence");
        p.clear();require(p.encode().isEmpty() && p.snapshot(OWNER)==null,"terminal cleanup removes profile ownership");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(service), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "CombatProfileProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
