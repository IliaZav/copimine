"""Execute the victory adapter against authoritative WorldCore read-back."""
from pathlib import Path
import subprocess

import pytest
from tests.test_boss_phase_damage_transaction_adapter import _method

ROOT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="module")
def victory_unlock_probe(tmp_path_factory):
    out = tmp_path_factory.mktemp("victory-unlock-readback")
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    methods = "\n".join(_method(source, signature) for signature in (
        "private void unlockEnd(", "private void checkVictoryRewardCompletion(",
        "private void resumeVictorySaga("))
    java = r'''
import java.util.*;
import java.util.logging.Logger;
public class VictoryUnlockReadbackProbe {
    static final String VICTORY_BOSS_DEATH="death", VICTORY_UNLOCKED="unlocked",
        VICTORY_UNLOCK_PENDING="unlock-pending", VICTORY_COMPLETE="complete",
        VICTORY_REWARDS_DELIVERED="rewards", VICTORY_GATE_OPENED="gate";
    static final int DEFAULT_GATE_TICKS_PER_LAYER=1;
    enum EventPhase { UNLOCKED }
    interface CommandSender {}
    record WorldAccessResult(boolean success, boolean changed, String code, String message) {}
    static class Service {
        boolean enabled, accept=true, apply=true, nullResult;int calls;String key;
        boolean isEndEnabled(){return enabled;}
        WorldAccessResult setEndEnabled(boolean value,String actor,String operation){
            calls++;key=operation;
            if(nullResult)return null;
            if(accept&&apply)enabled=value;
            return new WorldAccessResult(accept,accept&&apply,accept?"OPENED":"SUPERSEDED","");
        }
    }
    Service worldAccessService=new Service();
    boolean officialBossDeathCommitted=true,bossLootCommitted=true,endUnlocked,
        rewardsAccepted=true,saveSucceeds=true,victoryGatePending;
    String eventId="current-event",victoryStep=VICTORY_REWARDS_DELIVERED;
    final Set<UUID> officialRewardRoster=Set.of(UUID.randomUUID());
    int saves,rewardCalls,announcements,restorations,visuals;
    record LayoutState(String gateStatus){}
    final LayoutState layoutState=new LayoutState("NONE");
    boolean returnStoneAccepted(){return rewardsAccepted;}
    boolean allShardRewardsAccepted(){return rewardsAccepted;}
    boolean allNightCloakRewardsAccepted(){return rewardsAccepted;}
    boolean saveStateSync(){saves++;return saveSucceeds;}
    void message(CommandSender sender,String text){}
    Logger getLogger(){return Logger.getLogger("victory-unlock-test");}
    void persistUnlockStateOrWarn(String reason){saveStateSync();}
    void issueVictoryRewards(){rewardCalls++;}
    boolean isGateConfigured(){return false;}
    void openGate(CommandSender sender,int ticks,String cause,boolean victory){throw new AssertionError("unconfigured gate");}
    void announceVictory(){announcements++;}
    void restoreCoreAndPadsForVictory(){restorations++;}
    void forcePhase(EventPhase phase,String cause){}
    void rebuildPersistedVisuals(){visuals++;}
''' + methods + r'''
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var p=new VictoryUnlockReadbackProbe();String mode=args[0];
        switch(mode){
            case "stale-local-flag" -> {
                p.endUnlocked=true;p.worldAccessService.accept=false;p.unlockEnd(null,"official-victory");
                require(!p.endUnlocked&&p.worldAccessService.calls==1&&p.rewardCalls==0,
                        "stale local unlock bypassed authoritative failed operation");
            }
            case "success-without-readback" -> {
                p.worldAccessService.apply=false;p.unlockEnd(null,"official-victory");
                require(!p.endUnlocked&&p.rewardCalls==0,"success response certified an actually closed End");
            }
            case "already-open-needs-receipt" -> {
                p.worldAccessService.enabled=true;p.unlockEnd(null,"official-victory");
                require(p.endUnlocked&&p.worldAccessService.calls==1,
                        "already-open path omitted the durable idempotency receipt");
                require("end-event:current-event:unlock".equals(p.worldAccessService.key),"unstable unlock identity");
            }
            case "save-failure" -> {
                p.saveSucceeds=false;p.unlockEnd(null,"official-victory");
                require(!p.endUnlocked&&p.worldAccessService.calls==0,"external change preceded durable intent");
            }
            case "rewards-pending" -> {
                p.rewardsAccepted=false;p.unlockEnd(null,"official-victory");
                require(p.worldAccessService.calls==0&&!p.endUnlocked,"unlock before accepted rewards");
            }
            case "death-not-committed" -> {
                p.officialBossDeathCommitted=false;p.unlockEnd(null,"official-victory");
                require(p.worldAccessService.calls==0&&!p.endUnlocked,"unlock without official death");
            }
            case "normal-unlock" -> {
                p.unlockEnd(null,"official-victory");
                require(p.worldAccessService.calls==1&&p.endUnlocked&&p.rewardCalls==1,
                        "valid persisted and observed unlock did not progress");
            }
            case "null-result" -> {
                p.worldAccessService.nullResult=true;p.unlockEnd(null,"official-victory");
                require(!p.endUnlocked&&p.rewardCalls==0,"null operation result progressed");
            }
            case "completion-stale-flag" -> {
                p.endUnlocked=true;p.worldAccessService.accept=false;p.checkVictoryRewardCompletion();
                require(p.announcements==0&&p.restorations==0&&!p.endUnlocked,
                        "victory announced while WorldCore remains closed");
            }
            case "completion-missing-service" -> {
                p.endUnlocked=true;p.worldAccessService=null;p.checkVictoryRewardCompletion();
                require(p.announcements==0&&p.restorations==0&&!p.endUnlocked,
                        "victory announced without authoritative WorldCore");
            }
            case "resume-stale-flag" -> {
                p.endUnlocked=true;p.resumeVictorySaga();
                require(!p.endUnlocked&&p.rewardCalls==1,"resume retained stale open flag");
            }
            case "resume-missing-service" -> {
                p.endUnlocked=true;p.worldAccessService=null;p.resumeVictorySaga();
                require(!p.endUnlocked,"resume certified unavailable service");
            }
            case "resume-unrecorded-open" -> {
                p.worldAccessService.enabled=true;p.resumeVictorySaga();
                require(!p.endUnlocked,"resume skipped the durable operation because End happened to be open");
            }
            case "completed-unlock-replay" -> {
                p.victoryStep=VICTORY_COMPLETE;p.endUnlocked=true;
                p.unlockEnd(null,"admin-unlock");
                require(p.worldAccessService.calls==0&&p.rewardCalls==0&&VICTORY_COMPLETE.equals(p.victoryStep),
                        "manual retry reopened an already completed victory saga");
            }
            case "completed-once" -> {
                p.endUnlocked=true;p.worldAccessService.enabled=true;p.checkVictoryRewardCompletion();
                p.checkVictoryRewardCompletion();
                require(p.announcements==1&&p.restorations==1&&VICTORY_COMPLETE.equals(p.victoryStep),
                        "valid completion missing or duplicated");
            }
            default -> throw new AssertionError(mode);
        }
        System.out.println("PASS "+mode);
    }
}
'''
    path = out / "VictoryUnlockReadbackProbe.java"
    path.write_text(java, encoding="utf-8")
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), str(path)],
                              capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=40)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return out


@pytest.mark.parametrize("mode", [
    "stale-local-flag", "success-without-readback", "already-open-needs-receipt",
    "save-failure", "rewards-pending", "death-not-committed", "normal-unlock", "null-result",
    "completion-stale-flag", "completion-missing-service", "resume-stale-flag",
    "resume-missing-service", "resume-unrecorded-open", "completed-once", "completed-unlock-replay",
])
def test_victory_requires_recorded_authoritative_world_access(victory_unlock_probe, mode):
    result = subprocess.run(["java", "-cp", str(victory_unlock_probe), "VictoryUnlockReadbackProbe", mode],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
    assert result.returncode == 0, result.stdout + result.stderr
