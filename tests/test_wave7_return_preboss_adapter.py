"""Execute the real handoff adapter and timer when every owner is return-pending."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_all_dead_grace_cannot_be_bypassed_by_the_preboss_timer(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    adapter = extract(source, "void tickPreBossCooldown()")
    probe = tmp_path / "ReturnPreBossProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.runtime.*;
public class ReturnPreBossProbe {
    static final UUID OWNER=new UUID(0,1);
    EventPhase phase=EventPhase.PRE_BOSS_COOLDOWN;long generation=43,eventTickCounter,phaseDeadlineMillis,restoredPreBossElapsedTicks;
    int handoffs;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    PreBossTransitionController preBossTransitionController=new PreBossTransitionController();
    BossStartGateway bossStartGateway=context->{handoffs++;};
    boolean isOfficialAttempt(){return true;}
    EncounterContext currentEncounterContext(){return new EncounterContext("synthetic-event",generation,"configured-world",0,68,0,
        new EncounterContext.ArenaBounds(-20,60,-20,20,80,20),Set.of(OWNER),attemptLifecycle.activeLivingOnlineRoster(),null);}
    void forcePhase(EventPhase next,String reason){phase=next;}void saveStateAsync(){}void renderPreBossCooldownVisual(long now){}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + adapter + r'''
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        var main=new ReturnPreBossProbe();main.attemptLifecycle.begin(43,Set.of(OWNER));main.attemptLifecycle.enableWave7Returns(43);
        main.preBossTransitionController.startServerTicks(main.currentEncounterContext(),0);
        main.attemptLifecycle.markDead(OWNER,43);main.attemptLifecycle.markAlive(OWNER,43);
        main.eventTickCounter=800;main.tickPreBossCooldown();
        require(main.handoffs==0,"800-tick boss handoff bypassed the all-dead owner return grace");
        require(main.phase==EventPhase.PRE_BOSS_COOLDOWN,"pending return changed the current handoff phase");
        main.eventTickCounter=1200;main.tickPreBossCooldown();require(main.handoffs==0,"bed-alive status triggered boss handoff without admission");
        var token=main.attemptLifecycle.beginReturn(OWNER,43,1200,40);
        require(main.attemptLifecycle.completeReturn(token,1240,0),"owner completes explicit return");
        main.eventTickCounter=1240;main.tickPreBossCooldown();main.tickPreBossCooldown();
        require(main.handoffs==1,"valid return must resume the existing elapsed timer and fire its gateway exactly once");
        var stale=new ReturnPreBossProbe();stale.attemptLifecycle.begin(43,Set.of(OWNER));stale.attemptLifecycle.enableWave7Returns(43);
        stale.preBossTransitionController.startServerTicks(stale.currentEncounterContext(),0);stale.generation=44;stale.eventTickCounter=800;
        stale.tickPreBossCooldown();require(stale.handoffs==0,"old generation started a handoff");
        var ordinary=new ReturnPreBossProbe();ordinary.attemptLifecycle.begin(43,Set.of(OWNER));
        ordinary.preBossTransitionController.startServerTicks(ordinary.currentEncounterContext(),0);
        ordinary.eventTickCounter=799;ordinary.tickPreBossCooldown();require(ordinary.handoffs==0,"existing timer was shortened");
        ordinary.eventTickCounter=800;ordinary.tickPreBossCooldown();require(ordinary.handoffs==1,"ordinary gateway no longer fires at 800 ticks");
    }
}
''', encoding="utf-8")
    base = ROOT / "copimine-end-event/src/me/copimine/endevent"
    files = [base / path for path in ("runtime/AttemptLifecycleController.java", "runtime/EncounterContext.java",
             "runtime/PreBossTransitionController.java", "runtime/BossStartGateway.java", "domain/ServerTickClock.java",
             "domain/EventPhase.java", "domain/EndRiftObjective.java")]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, files), str(probe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "ReturnPreBossProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
