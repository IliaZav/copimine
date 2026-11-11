"""Execute the actual plugin reservation adapter with the real bounded admission gate."""
from pathlib import Path
import subprocess

import pytest

from tests.test_end_rift_portal_layer_runtime import declaration

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / 'copimine-end-event/src'


@pytest.fixture(scope='module')
def reservation_probe(tmp_path_factory):
    source = (SOURCE_ROOT / 'me/copimine/endevent/CopiMineEndEvent.java').read_text(encoding='utf-8')
    signature = ('private boolean reserveWaveProjectileGroup(UUID groupId, int physicalChildCount,\n'
                 '                                               boolean namedFiveArrowRain, boolean eliteVolley)')
    methods = declaration(source, signature)
    helper = 'private int waveProjectileLivingPlayers('
    if helper in source:
        methods += '\n' + declaration(source, helper)
    budget_helper = 'private int waveProjectileBudgetPlayers('
    if budget_helper in source:
        methods += '\n' + declaration(source, budget_helper)
    directory = tmp_path_factory.mktemp('wave-projectile-roster')
    probe = directory / 'ProjectileRosterProbe.java'
    probe.write_text('''
import java.util.*;
import java.util.logging.Logger;
import me.copimine.endevent.domain.*;
public class ProjectileRosterProbe {
    final WaveProjectileAdmissionController waveProjectileAdmission = new WaveProjectileAdmissionController();
    final Set<UUID> officialRewardRoster = new HashSet<>();
    boolean testCombatAiMode, testWaveFrontVisualMode;
    String eventId="probe";long generation=1;int activeWave=3;
    int wavePlayers, bossPlayers, legacyPlayers;
    class LivingEntity {} LivingEntity boss;
    LivingEntity liveBoss(){return boss;}
    boolean isBossCombatRuntimeActive(LivingEntity entity){return entity!=null;}
    List<Object> players(int count){return Collections.nCopies(count,new Object());}
    List<Object> activeLivingPlayers(){return players(legacyPlayers);}
    List<Object> activeWaveParticipants(){return players(wavePlayers);}
    List<Object> activeBossParticipants(){return players(bossPlayers);}
    Logger getLogger(){return Logger.getAnonymousLogger();}
    void admitted(int count){for(int i=0;i<count;i++)officialRewardRoster.add(new UUID(0,i+1));}
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
''' + methods + '''
    public static void main(String[] args){
        var p=new ProjectileRosterProbe();var group=UUID.randomUUID();
        switch(args[0]){
            case "diagnostic-one" -> {
                p.testWaveFrontVisualMode=true;p.wavePlayers=1;p.legacyPlayers=1;
                check(p.reserveWaveProjectileGroup(group,3,false,false),"one-player diagnostic bow volley was rejected");
                check(!p.reserveWaveProjectileGroup(UUID.randomUUID(),2,false,false),"diagnostic cap must remain bounded");
                p.waveProjectileAdmission.releaseGroup(group);
                check(p.waveProjectileAdmission.activeProjectileCount()==0,"cancelled diagnostic volley leaked slots");
            }
            case "diagnostic-stale-reward-roster" -> {
                p.testWaveFrontVisualMode=true;p.wavePlayers=1;p.legacyPlayers=0;p.admitted(20);
                check(p.reserveWaveProjectileGroup(group,1,false,false),"old reward roster erased a real arena test player");
                check(!p.reserveWaveProjectileGroup(UUID.randomUUID(),4,false,false),"stale 20-player roster widened solo test cap");
            }
            case "diagnostic-boss" -> {
                p.testCombatAiMode=true;p.boss=p.new LivingEntity();p.bossPlayers=1;
                check(p.reserveWaveProjectileGroup(group,3,false,false),"disposable boss must use its own real participant set");
            }
            case "official-last-survivor" -> {
                p.admitted(20);p.wavePlayers=1;p.legacyPlayers=1;
                check(p.reserveWaveProjectileGroup(group,3,false,false),"one survivor of a legally admitted party disabled arrows");
                check(!p.reserveWaveProjectileGroup(UUID.randomUUID(),2,false,false),"one survivor inherited the full raid projectile cap");
            }
            case "official-boss-last-survivor" -> {
                p.admitted(20);p.boss=p.new LivingEntity();p.bossPlayers=1;p.wavePlayers=20;
                check(p.reserveWaveProjectileGroup(group,3,false,false),"official boss lost its last eligible target's budget");
                check(!p.reserveWaveProjectileGroup(UUID.randomUUID(),2,false,false),"boss used the unrelated wave roster");
            }
            case "bounds" -> {
                check(WaveProjectileRosterPolicy.budgetPlayers(-1,20,true)==0,"negative population allocated slots");
                check(WaveProjectileRosterPolicy.budgetPlayers(1,0,false)==0,"missing admission allocated slots");
                check(WaveProjectileRosterPolicy.budgetPlayers(Integer.MAX_VALUE,Integer.MAX_VALUE,true)==20,"diagnostic population escaped hard clamp");
                check(WaveProjectileRosterPolicy.budgetPlayers(Integer.MAX_VALUE,Integer.MAX_VALUE,false)==20,"official population escaped hard clamp");
            }
            case "zero-player" -> {
                p.testWaveFrontVisualMode=true;p.admitted(20);
                check(!p.reserveWaveProjectileGroup(group,1,false,false),"an empty test arena allocated a projectile");
                check(!p.reserveWaveProjectileGroup(group,18,false,true),"an empty arena allocated an elite fan set");
            }
            case "invalid-official-admission" -> {
                p.wavePlayers=1;p.legacyPlayers=1;p.admitted(1);
                check(!p.reserveWaveProjectileGroup(group,1,false,false),"one player bypassed official admission");
                check(PressureBudgetController.configuredProjectileCap(1)==0,"global official minimum was weakened");
            }
            case "party-matrix" -> {
                for(int roster:new int[]{2,5,10,11,20}){
                    var q=new ProjectileRosterProbe();q.admitted(roster);q.wavePlayers=roster;q.legacyPlayers=roster;
                    int cap=PressureBudgetController.configuredProjectileCap(roster);UUID volley=UUID.randomUUID();
                    check(q.reserveWaveProjectileGroup(volley,cap,false,false),"legal whole volley rejected for roster "+roster);
                    check(!q.reserveWaveProjectileGroup(UUID.randomUUID(),1,false,false),"ordinary lane exceeded cap");
                    check(q.waveProjectileAdmission.releaseGroup(volley).isEmpty(),"unspawned reservation invented physical children");
                    check(q.waveProjectileAdmission.activeProjectileCount()==0,"release leaked reservation");
                    check(q.reserveWaveProjectileGroup(UUID.randomUUID(),18,false,true),"bounded elite lane rejected roster "+roster);
                    check(!q.reserveWaveProjectileGroup(UUID.randomUUID(),18,false,true),"two elite fan sets admitted concurrently");
                }
            }
            default -> throw new AssertionError("unknown scenario");
        }
    }
}
''', encoding='utf-8')
    result = subprocess.run(['javac', '-proc:none', '-encoding', 'UTF-8', '-sourcepath', str(SOURCE_ROOT),
                             '-d', str(directory), str(probe)], capture_output=True, text=True,
                            encoding='utf-8', errors='replace')
    assert result.returncode == 0, result.stdout + result.stderr
    return directory


@pytest.mark.parametrize('scenario', ['diagnostic-one', 'diagnostic-stale-reward-roster',
                                    'diagnostic-boss', 'official-last-survivor', 'official-boss-last-survivor', 'bounds', 'zero-player',
                                    'invalid-official-admission', 'party-matrix'])
def test_reservations_use_the_live_encounter_roster_without_widening_official_admission(reservation_probe, scenario):
    result = subprocess.run(['java', '-cp', str(reservation_probe), 'ProjectileRosterProbe', scenario],
                            capture_output=True, text=True, encoding='utf-8', errors='replace')
    assert result.returncode == 0, result.stdout + result.stderr
