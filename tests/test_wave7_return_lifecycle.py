"""Execute the production lifecycle's personal Wave 7 return and restart fences."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_wave7_return_admission_grace_and_restart(tmp_path):
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    probe = ROOT / "tests/Wave7ReturnLifecycleTest.java"
    compiled = subprocess.run(["javac", str(ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/EncounterGenerationPolicy.java"), "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "Wave7ReturnLifecycleTest"], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr


def test_official_death_disconnect_and_return_adapters_use_personal_deadline(tmp_path):
    source = (ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java").read_text(encoding="utf-8")
    death = "private boolean markOfficialParticipantDead(UUID owner, long expectedGeneration)"
    offline = "private boolean markOfficialParticipantOffline(UUID owner, long expectedGeneration)"
    assert "markOfficialParticipantDead(uuid, generation)" in source[source.index("public void onPlayerDeath("):
                                                                     source.index("public void onPlayerRespawn(")]
    assert "markOfficialParticipantOffline(uuid, generation)" in source[source.index("public void onPlayerQuit("):
                                                                       source.index("public void onPlayerDeath(")]
    return_command = source[source.index("private void handleWave7Return("):
                            source.index("private void cancelWave7ReturnForOffense(")]
    assert "observeWave7OwnerReturnWindow(" in return_command and "owner, generation, System.nanoTime()" in return_command
    assert "beginReturn(owner, generation, eventTickCounter, 40L, System.nanoTime())" in source
    assert "observeWave7OwnerReturnWindow(owner, generation" in source[source.index("private void tickWave7Returns("):
                                                                          source.index("private void wipeOfficialAttemptIfAllDead(")]

    def extract_method(signature):
        start = source.index(signature)
        opening = source.index("{", start)
        depth = 0
        for cursor in range(opening, len(source)):
            if source[cursor] == "{":
                depth += 1
            elif source[cursor] == "}":
                depth -= 1
                if depth == 0:
                    return source[start:cursor + 1]
        raise AssertionError(f"unterminated method: {signature}")

    probe = tmp_path / "Wave7PersonalDeadlineAdapterProbe.java"
    probe.write_text(r'''
import java.util.*;
import me.copimine.endevent.runtime.AttemptLifecycleController;
public class Wave7PersonalDeadlineAdapterProbe {
    static final UUID OWNER = new UUID(0,1), OTHER = new UUID(0,2);
    AttemptLifecycleController attemptLifecycle = new AttemptLifecycleController();
    boolean wave7 = true;
    long generation = 43;
    boolean isOfficialWave7ReturnContext(){return wave7;}
    long wave7ReturnGraceMillis(){return 120_000L;}
''' + extract_method(death) + "\n" + extract_method(offline) + r'''
    public static void main(String[] args){
        var death=new Wave7PersonalDeadlineAdapterProbe();
        death.attemptLifecycle.begin(43,Set.of(OWNER,OTHER));death.attemptLifecycle.enableWave7Returns(43);
        if(!death.markOfficialParticipantDead(OWNER,43))throw new AssertionError("Wave 7 death was not recorded");
        if(death.attemptLifecycle.observeWave7OwnerReturnWindow(OWNER,43,System.nanoTime(),System.currentTimeMillis(),120_000L)
            ==AttemptLifecycleController.ReturnWindowStatus.EXPIRED)throw new AssertionError("death adapter started an expired deadline");
        var quit=new Wave7PersonalDeadlineAdapterProbe();
        quit.attemptLifecycle.begin(43,Set.of(OWNER,OTHER));quit.attemptLifecycle.enableWave7Returns(43);
        if(!quit.markOfficialParticipantOffline(OWNER,43))throw new AssertionError("Wave 7 disconnect was not recorded");
        if(quit.attemptLifecycle.observeWave7OwnerReturnWindow(OWNER,43,System.nanoTime(),System.currentTimeMillis(),120_000L)
            ==AttemptLifecycleController.ReturnWindowStatus.EXPIRED)throw new AssertionError("disconnect adapter started an expired deadline");
        var ordinary=new Wave7PersonalDeadlineAdapterProbe();ordinary.wave7=false;
        ordinary.attemptLifecycle.begin(43,Set.of(OWNER));ordinary.attemptLifecycle.enableWave7Returns(43);
        if(!ordinary.markOfficialParticipantDead(OWNER,43))throw new AssertionError("ordinary death fallback changed");
    }
}
''', encoding="utf-8")
    lifecycle = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/AttemptLifecycleController.java"
    compiled = subprocess.run(["javac", str(ROOT / "tools/minecraft-26.3/protocol/src/main/java/me/copimine/protocol/EncounterGenerationPolicy.java"), "-encoding", "UTF-8", "-d", str(tmp_path), str(lifecycle), str(probe)],
                              capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    executed = subprocess.run(["java", "-cp", str(tmp_path), "Wave7PersonalDeadlineAdapterProbe"],
                              capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
