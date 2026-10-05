"""Cancelled Paper deaths cannot release wave controls or wipe the living roster."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_cancelled_player_death_preserves_living_attempt_and_wave_controls(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    start = source.index("    public void onPlayerDeath(PlayerDeathEvent event)")
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    adapter = source[start:end]
    probe = tmp_path / "CancelledDeathProbe.java"
    probe.write_text('''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class CancelledDeathProbe {
    String eventId="synthetic";
    long generation=43;
    int effectsReleased,wipes,overlayChanges;
    Object keyRitualPrisoner=new Object();
    UUID prisoner;
    AttemptLifecycleController attemptLifecycle=new AttemptLifecycleController();
    Set<UUID> officialRewardRoster=new HashSet<>(),clientBindingReadyPlayers=new HashSet<>();
    Map<UUID,Long> offlineRosterGraceUntilMillis=new HashMap<>();
    Map<String,UUID> padOccupants=new HashMap<>(),runeVisualOccupants=new HashMap<>();
    class Player {
        UUID id=new UUID(0,1);
        UUID getUniqueId(){return id;}
        Pdc getPersistentDataContainer(){return new Pdc();}
    }
    class Pdc {void remove(Object key){effectsReleased++;}}
    record PlayerDeathEvent(Player entity, boolean cancelled) {
        Player getEntity(){return entity;}
        boolean isCancelled(){return cancelled;}
    }
    void releaseBlackFogEffects(Player p){effectsReleased++;}
    void cancelWaveCombatTarget(UUID id){effectsReleased++;}
    void resetTransitionRuneHoldForParticipant(UUID id){effectsReleased++;}
    void restoreRitualPrisonerGravity(Player p){effectsReleased++;}
    void releaseCurrentCarrierHolder(UUID id,String reason){effectsReleased++;}
    void releaseCurrentHuntTarget(UUID id,String reason){effectsReleased++;}
    void endRitualPrisonerAbilitySessionForPlayer(UUID id,String reason){effectsReleased++;}
    UUID ritualPrisonerId(){return prisoner;}
    boolean isOfficialAttemptActive(){return true;}
    void wipeOfficialAttemptIfAllDead(String reason){wipes++;}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    void cancelShardChannel(UUID id){effectsReleased++;}
    void removeShardPassiveEffects(Player p){effectsReleased++;}
    void refreshRuneOverlayVisuals(){overlayChanges++;}
''' + adapter + '''
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        var probe=new CancelledDeathProbe();
        var player=probe.new Player();
        var id=player.id;
        probe.prisoner=id;
        probe.officialRewardRoster.add(id);
        probe.clientBindingReadyPlayers.add(id);
        probe.offlineRosterGraceUntilMillis.put(id,123L);
        probe.padOccupants.put("pad",id);
        probe.runeVisualOccupants.put("rune",id);
        probe.attemptLifecycle.begin(43,Set.of(id));
        probe.onPlayerDeath(new PlayerDeathEvent(player,true));
        require(probe.effectsReleased==0 && probe.wipes==0 && probe.overlayChanges==0,
                "cancelled death must not clear wave controls, effects or trigger a wipe");
        require(probe.attemptLifecycle.living().contains(id) && probe.attemptLifecycle.owns(43),
                "cancelled lethal callback cannot remove the last living participant");
        require(probe.clientBindingReadyPlayers.contains(id) && probe.offlineRosterGraceUntilMillis.containsKey(id)
                && probe.padOccupants.containsValue(id) && probe.runeVisualOccupants.containsValue(id),
                "cancelled death retains bridge, return grace and occupied runes");
        probe.onPlayerDeath(new PlayerDeathEvent(player,false));
        require(probe.effectsReleased>0 && probe.wipes==1 && probe.attemptLifecycle.living().isEmpty(),
                "a committed death must still follow the real death cleanup path");
        require(!probe.clientBindingReadyPlayers.contains(id) && probe.padOccupants.isEmpty()
                && probe.runeVisualOccupants.isEmpty(),"normal death releases client and rune state");
    }
}
''', encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)],
                   check=True, capture_output=True, text=True)
    result = subprocess.run(["java", "-cp", str(tmp_path), "CancelledDeathProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
