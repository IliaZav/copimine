"""An explicit successful reset must discard restored wave checkpoints too."""
from pathlib import Path
import subprocess
from tests.test_wave_navigation_adapter import extract

ROOT = Path(__file__).resolve().parents[1]


def test_reset_discards_stale_receipts_only_after_cleanup_succeeds(tmp_path):
    source = (ROOT/'copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java').read_text('utf-8')
    body = extract(source, 'void resetEventSafely(CommandSender sender)')
    java = '''
import java.util.*;
import me.copimine.endevent.domain.EventPhase;
public class ResetCheckpointProbe {
 static class CommandSender {}
 static class Listener { void clear() {} }
 EventPhase phase=EventPhase.RECOVERY_REQUIRED;
 boolean endUnlocked,coreCharged,bossLootCommitted;
 boolean archive=true,cleanup=true; int saves;
 int activeWave;long phaseDeadlineMillis;String recoveryReason="failed",bossRewardStatus;
 Object bossRewardRecipientUuid;
 static final String BOSS_REWARDS_PENDING="PENDING";
 Object wave3RestoredPortalCheckpoint=new Object(),wave4RestoredObeliskCheckpoint=new Object();
 Map<String,String> wave4InvalidObeliskCheckpointFields=Map.of("bad","receipt");
 Map<String,String> wave7RecoveryCheckpoint=Map.of(),blackFogRecoveryCheckpoint=Map.of(),
   ritualSphereRecoveryCheckpoint=Map.of(),wave1RecoveryCheckpoint=Map.of();
 Map<String,Integer> depositedResources=new HashMap<>(Map.of("diamond",4));
 Map<String,Object> combatProfiles=new HashMap<>(),rewardStatuses=new HashMap<>(),nightCloakRolls=new HashMap<>();
 Set<UUID> participantUuids=new HashSet<>(),officialRewardRoster=new HashSet<>(),
   nightCloakRequestsInFlight=new HashSet<>(),waveRewardsIssued=new HashSet<>();
 Listener combatProfileListener=new Listener(),realitySplitChamberController=new Listener();
 void message(CommandSender p,String s) {}
 boolean archiveWaveTwoRosterForCleanup(String s){return archive;}
 boolean clearWaveEntities(){return cleanup;}
 void forcePhase(EventPhase p,String reason){phase=p;}
 boolean saveStateSync(){saves++;return true;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 '''+body+'''
 public static void main(String[] args){
  var p=new ResetCheckpointProbe();p.cleanup=false;
  p.resetEventSafely(new CommandSender());
  check(p.wave4RestoredObeliskCheckpoint!=null && !p.wave4InvalidObeliskCheckpointFields.isEmpty());
  p.cleanup=true;p.archive=false;p.resetEventSafely(new CommandSender());
  check(p.wave3RestoredPortalCheckpoint!=null && p.wave4RestoredObeliskCheckpoint!=null);
  p.archive=true;p.resetEventSafely(new CommandSender());
  check(p.wave3RestoredPortalCheckpoint==null && p.wave4RestoredObeliskCheckpoint==null);
  check(p.wave4InvalidObeliskCheckpointFields.isEmpty());
  check(p.phase==EventPhase.COLLECTING && p.saves==1 && p.depositedResources.get("diamond")==0);
 }
}'''
    path=tmp_path/'ResetCheckpointProbe.java';path.write_text(java,encoding='utf-8')
    phase=ROOT/'copimine-end-event/src/me/copimine/endevent/domain/EventPhase.java'
    result=subprocess.run(['javac','-encoding','UTF-8','-d',str(tmp_path),str(phase),str(path)],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
    result=subprocess.run(['java','-cp',str(tmp_path),'ResetCheckpointProbe'],capture_output=True,text=True)
    assert result.returncode==0,result.stderr
