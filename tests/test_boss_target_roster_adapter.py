"""Execute real pursuit retargeting with late visitors inside the arena."""
from pathlib import Path
import subprocess
from tests.test_end_rift_death_item_integrations import extract

ROOT = Path(__file__).resolve().parents[1]


def test_boss_pursuit_never_admits_a_late_nonparticipant(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    method = extract(source, "void rotateBossTarget(")
    java = tmp_path / "BossTargetRosterProbe.java"
    java.write_text(r'''
import java.util.*;
import me.copimine.endevent.domain.BossTargetPolicy;
public class BossTargetRosterProbe {
    static class Player {
        final UUID id;boolean admitted;
        Player(String id,boolean admitted){this.id=UUID.fromString(id);this.admitted=admitted;}
        UUID getUniqueId(){return id;}
    }
    static class Mob {Object target;Object getTarget(){return target;}void setTarget(Object p){target=p;}UUID getUniqueId(){return new UUID(0,9);}}
    static class Bukkit {
        static final List<Player> players=new ArrayList<>();static Collection<Player> getOnlinePlayers(){return players;}
        static Player getPlayer(UUID id){return players.stream().filter(p->p.id.equals(id)).findFirst().orElse(null);}
    }
    record Config(){int bossTargetLockSeconds(){return 12;}int bossRecentTargetMemory(){return 4;}}
    final Config config=new Config();long bossTargetLockUntilMillis,nextBossTacticMillis;
    int bossTargetCursor,bossTacticCycle;final LinkedList<UUID> recentBossTargets=new LinkedList<>();
    final Set<UUID> combatHelpers=new HashSet<>();
    boolean isCombatTarget(Player p){return p!=null;}
    boolean isActiveBossParticipant(Player p){return p!=null&&p.admitted;}
    java.util.logging.Logger getLogger(){return java.util.logging.Logger.getAnonymousLogger();}
''' + method + r'''
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        var outsider=new Player("00000000-0000-0000-0000-000000000001",false);
        var member=new Player("00000000-0000-0000-0000-000000000002",true);
        Bukkit.players.add(outsider);Bukkit.players.add(member);
        var p=new BossTargetRosterProbe();var boss=new Mob();p.rotateBossTarget(boss);
        check(boss.target==member&&!p.combatHelpers.contains(outsider.id),"late visitor entered pursuit/reward participation");
        long lock=p.bossTargetLockUntilMillis;p.rotateBossTarget(boss);
        check(boss.target==member&&p.bossTargetLockUntilMillis==lock,"valid focus or its original lock was reset");
        member.admitted=false;p.rotateBossTarget(boss);
        check(boss.target==null&&p.bossTargetLockUntilMillis==0,"empty official roster retained a combat target");
    }
}
''', encoding="utf-8")
    result = subprocess.run(["javac", "-encoding", "UTF-8", "-sourcepath", str(ROOT / "copimine-end-event/src"), "-d", str(tmp_path), str(java)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "BossTargetRosterProbe"], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
