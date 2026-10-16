"""Execute actual Main death eligibility against the real attempt lifecycle."""
from pathlib import Path
import subprocess

from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_only_current_active_participants_retain_items_even_after_mark_dead(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    eligibility = extract(source, "boolean isEligibleParticipantDeath(PlayerDeathEvent event)")
    phase = extract(source, "boolean isOfficialAttemptActive()")
    probe = tmp_path / "DeathEligibilityProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.EventPhase;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class DeathEligibilityProbe {
    enum GameMode { SURVIVAL, ADVENTURE, CREATIVE, SPECTATOR }
    record Player(UUID id,GameMode mode) {
        UUID getUniqueId(){return id;}
        GameMode getGameMode(){return mode;}
    }
    record PlayerDeathEvent(Player player,boolean cancelled) {
        Player getEntity(){return player;}
        boolean isCancelled(){return cancelled;}
    }
    boolean bootstrapped=true,testWaveFrontVisualMode,testCombatAiMode;
    String eventId="synthetic-attempt";
    long generation=71;
    EventPhase phase=EventPhase.WAVE_1;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Set<UUID> officialRewardRoster=new HashSet<>();
''' + eligibility + phase + r'''
    static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args) {
        var p=new DeathEligibilityProbe();
        var id=new UUID(0,1);var second=new UUID(0,2);var foreign=new UUID(0,3);
        var death=new PlayerDeathEvent(new Player(id,GameMode.SURVIVAL),false);
        p.attemptLifecycle.begin(71,Set.of(id,second));p.officialRewardRoster.addAll(Set.of(id,second));
        for(int wave=1;wave<=7;wave++){
            p.phase=EventPhase.valueOf("WAVE_"+wave);
            require(p.isEligibleParticipantDeath(death),"all actual wave phases protect active participants");
        }
        p.attemptLifecycle.markObjectiveEligible(id,71,false);
        require(p.isEligibleParticipantDeath(death),"coordinates and objective eligibility do not revoke inventory rights");
        p.attemptLifecycle.markDead(id,71);
        p.attemptLifecycle.markDead(second,71);
        require(p.isEligibleParticipantDeath(death),"last lethal event remains eligible after alive flag changes");
        require(p.isEligibleParticipantDeath(new PlayerDeathEvent(new Player(second,GameMode.ADVENTURE),false)),"two deaths in one tick protect each registered participant");
        require(!p.isEligibleParticipantDeath(new PlayerDeathEvent(new Player(foreign,GameMode.SURVIVAL),false)),"visitor is not a participant");
        require(!p.isEligibleParticipantDeath(new PlayerDeathEvent(new Player(id,GameMode.CREATIVE),false)),"creative diagnostic player is not protected");
        require(!p.isEligibleParticipantDeath(new PlayerDeathEvent(new Player(id,GameMode.SPECTATOR),false)),"spectator is not protected");
        require(!p.isEligibleParticipantDeath(new PlayerDeathEvent(death.player(),true)),"cancelled death does not grant retention");
        require(!p.isEligibleParticipantDeath(null),"null callback rejected");
        p.attemptLifecycle.setActive(id,71,false);
        require(!p.isEligibleParticipantDeath(death),"withdrawn participant rejected");
        p.attemptLifecycle.setActive(id,71,true);
        p.officialRewardRoster.remove(id);
        require(!p.isEligibleParticipantDeath(death),"lifecycle membership alone cannot expand frozen reward roster");
        p.officialRewardRoster.add(id);p.generation=72;
        require(!p.isEligibleParticipantDeath(death),"stale attempt generation rejected");
        p.generation=71;p.testWaveFrontVisualMode=true;
        require(!p.isEligibleParticipantDeath(death),"test wave roster does not grant official inventory retention");
        p.testWaveFrontVisualMode=false;p.testCombatAiMode=true;
        require(!p.isEligibleParticipantDeath(death),"diagnostic AI session rejected");
        p.testCombatAiMode=false;p.bootstrapped=false;
        require(!p.isEligibleParticipantDeath(death),"uninitialized runtime rejected");
        p.bootstrapped=true;p.eventId=" ";
        require(!p.isEligibleParticipantDeath(death),"missing attempt identity rejected");
        p.eventId="synthetic-attempt";p.phase=EventPhase.UNLOCKED;
        require(!p.isEligibleParticipantDeath(death),"terminal phase rejected");
        p.phase=EventPhase.WAVE_7;
        p.attemptLifecycle.performAttemptWipe(71,"test");
        require(!p.isEligibleParticipantDeath(death),"frozen terminal cleanup rejects new receipts");
        p.attemptLifecycle.clear();
        require(!p.isEligibleParticipantDeath(death),"cleared lifecycle never grants persisted UUID alone protection");
    }
}
''', encoding="utf-8")
    sources = [ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java",
               ROOT / "copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java", probe]
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), *map(str, sources)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "DeathEligibilityProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
