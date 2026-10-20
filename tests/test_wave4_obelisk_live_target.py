from pathlib import Path
import re
import subprocess

from tests.test_end_rift_death_item_integrations import extract


ROOT = Path(__file__).resolve().parents[1]
EVENT = ROOT / "copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java"
CONFIG = ROOT / "copimine-end-event/config.yml"
LOAD_RUNNER = ROOT / "tests/RunEndRiftObeliskLoadLive.ps1"


def extract_powershell_function(source, name):
    start = source.index("function " + name + " {")
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def test_wave4_aim_tracks_target_until_the_telegraph_releases(tmp_path):
    source = EVENT.read_text(encoding="utf-8")
    adapter = extract(source, "boolean prepareWave4FireCast(")
    probe = tmp_path / "Wave4LiveAimProbe.java"
    probe.write_text(r'''
import java.util.UUID;
import java.util.logging.Logger;
import me.copimine.endevent.domain.ObeliskFireDirectorPolicy;
import me.copimine.endevent.runtime.WaveCombatCoordinator;

public final class Wave4LiveAimProbe {
    enum Sound { BLOCK_RESPAWN_ANCHOR_CHARGE }
    enum SoundCategory { HOSTILE }
    static final class World { void playSound(Location l, Sound s, SoundCategory c, float v, float p) {} }
    static final class Location {
        final double x, y, z;
        Location(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
        public Location clone() { return new Location(x, y, z); }
        World getWorld() { return new World(); }
        boolean isAt(Location other) { return other != null && x==other.x && y==other.y && z==other.z; }
    }
    static final class Player {
        final UUID id = new UUID(0, 51);
        Location eye;
        Player(Location eye) { this.eye=eye; }
        UUID getUniqueId() { return id; }
        Location getEyeLocation() { return eye.clone(); }
    }
    static final class Wave4ObeliskRuntimeState {
        final UUID id = new UUID(0, 77);
        ObeliskFireDirectorPolicy.FireSequence fireLease;
        Location fireAim;
        long fireReleaseTick;
        UUID id() { return id; }
        Location base() { return new Location(0, 64, 0); }
    }

    final long generation = 17;
    final String eventId = "wave4-live-aim-test";
    long eventTickCounter;
    final WaveCombatCoordinator waveCombatCoordinator = new WaveCombatCoordinator();
    Location lastCueAim;
    boolean isActiveArenaParticipant(Player player) { return true; }
    void clearWaveCombatCue(UUID id) { }
    void renderWave4ObeliskCue(Wave4ObeliskRuntimeState s, String stage, String ability,
                               Location mark, double radius) { lastCueAim=mark.clone(); }
    Logger getLogger() { return Logger.getAnonymousLogger(); }
    static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
''' + adapter + r'''
    public static void main(String[] args) {
        Wave4LiveAimProbe probe = new Wave4LiveAimProbe();
        probe.waveCombatCoordinator.begin(probe.generation);
        Wave4ObeliskRuntimeState obelisk = new Wave4ObeliskRuntimeState();
        Player target = new Player(new Location(1, 64, 1));
        require(!probe.prepareWave4FireCast(obelisk, target), "cast must honor its wind-up");
        target.eye = new Location(4, 66, -2);
        probe.eventTickCounter = 10;
        require(!probe.prepareWave4FireCast(obelisk, target), "cast must not release early");
        require(obelisk.fireAim.isAt(target.eye), "aim must track movement during the warning");
        target.eye = new Location(12, 71, -9);
        probe.eventTickCounter = obelisk.fireReleaseTick;
        require(probe.prepareWave4FireCast(obelisk, target), "cast must release at its deadline");
        require(obelisk.fireAim.isAt(target.eye), "released shot must use the target's current eye position");
        System.out.println("live target aim OK");
    }
}
''', encoding="utf-8")
    coordinator = ROOT / "copimine-end-event/src/me/copimine/endevent/runtime/WaveCombatCoordinator.java"
    domain = ROOT / "copimine-end-event/src/me/copimine/endevent/domain"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                               str(domain / "ObeliskScalingPolicy.java"),
                               str(domain / "ObeliskFireDirectorPolicy.java"),
                               str(coordinator), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave4LiveAimProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr


def test_wave4_release_gap_includes_telegraph_for_high_accepted_config_intervals(tmp_path):
    config = CONFIG.read_text(encoding="utf-8")
    match = re.search(r"(?m)^\s+fire-interval-ticks:\s*(\d+)\s*$", config)
    assert match, "Wave 4 obelisk fire interval must be explicit in the production config"
    interval = int(match.group(1))
    assert (interval + 20) * 50 <= 3_000, "Wave 4 release cadence must include its 20-tick telegraph"

    policy = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ObeliskFireDirectorPolicy.java"
    probe = tmp_path / "Wave4CadenceProbe.java"
    probe.write_text(f'''
import me.copimine.endevent.domain.ObeliskFireDirectorPolicy;
public final class Wave4CadenceProbe {{
  public static void main(String[] args) {{
    int[] acceptedConfigIntervals = {{20, 26, 40, 41, 80, 400}};
    for (int configured : acceptedConfigIntervals) {{
      int base = ObeliskFireDirectorPolicy.fireInterval(configured, false,
          ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS);
      int channeler = ObeliskFireDirectorPolicy.fireInterval(configured, true,
          ObeliskFireDirectorPolicy.MIN_TELEGRAPH_TICKS);
      if (base + 20 > 60 || channeler + 20 > 60)
        throw new AssertionError("release interval plus warning exceeded three seconds for config="
            + configured + ": " + base + "/" + channeler);
    }}
    System.out.println("release interval including telegraph <= 60 ticks");
  }}
}}
''', encoding="utf-8")
    scaling = ROOT / "copimine-end-event/src/me/copimine/endevent/domain/ObeliskScalingPolicy.java"
    compiled = subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp_path),
                               str(scaling), str(policy), str(probe)], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    result = subprocess.run(["java", "-cp", str(tmp_path), "Wave4CadenceProbe"],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr

    event_config = (ROOT / "copimine-end-event/src/me/copimine/endevent/EventConfig.java").read_text(encoding="utf-8")
    assert "fireInterval > ObeliskFireDirectorPolicy.MAX_BASE_FIRE_INTERVAL_TICKS" in event_config
    assert "fireIntervalTicks > ObeliskFireDirectorPolicy.MAX_BASE_FIRE_INTERVAL_TICKS" in event_config


def test_wave4_load_runner_measures_per_obelisk_tick_gaps_from_launch_logs(tmp_path):
    runner = LOAD_RUNNER.read_text(encoding="utf-8")
    cadence_helper = extract_powershell_function(runner, "Get-PerObeliskReleaseCadence")
    probe = tmp_path / "Wave4CadenceRunnerProbe.ps1"
    probe.write_text(r'''$ErrorActionPreference = 'Stop'
''' + cadence_helper + r'''
$event = 'wave4-current'
$generation = 43
$ids = @(
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000004'
)
$lines = @()
foreach ($id in $ids) {
  $lines += "[INFO]: RIFT_FIREBALL_LAUNCH event=$event obelisk=$id generation=$generation tick=100"
  $lines += "[INFO]: RIFT_FIREBALL_LAUNCH event=$event obelisk=$id generation=$generation tick=160"
}
$lines += '[INFO]: RIFT_FIREBALL_LAUNCH event=old-event obelisk=00000000-0000-0000-0000-000000000001 generation=42 tick=900'
$actual = Get-PerObeliskReleaseCadence -LogLines $lines -EventId $event -Generation $generation -ExpectedObelisks 4
if ($actual.MaxGapTicks -ne 60) { throw "expected measured max gap 60, got $($actual.MaxGapTicks)" }
$bad = $lines | Where-Object { $_ -notmatch 'obelisk=00000000-0000-0000-0000-000000000004 .*tick=160' }
$bad += "[INFO]: RIFT_FIREBALL_LAUNCH event=$event obelisk=$($ids[3]) generation=$generation tick=161"
$rejected = $false
try { $null = Get-PerObeliskReleaseCadence -LogLines $bad -EventId $event -Generation $generation -ExpectedObelisks 4 }
catch { $rejected = $_.Exception.Message -match 'exceeded 60 ticks' }
if (-not $rejected) { throw 'a 61-tick per-obelisk launch gap was not rejected' }
Write-Output 'Wave 4 runner cadence parser OK'
''', encoding="utf-8")
    result = subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(probe)],
                            capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
