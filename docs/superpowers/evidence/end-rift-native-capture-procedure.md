# End Rift native Minecraft capture procedure

Status: RUNBOOK — native exact-head capture is still required

Date prepared: 2026-09-18 (Europe/Moscow)

This runbook is the remaining release-gate procedure for the End Rift event.
It is intentionally separate from the passing Paper, contract, artifact, and
CI probes. A server-side PASS does not prove that the Java client renders the
model, UVs, animations, bossbar, Ritual Sphere, or Wave 7 barrier correctly.

The current closure records the following implementation identity:

| Field | Value |
| --- | --- |
| Repository | https://github.com/IliaZav/copimine |
| Branch | codex/end-rift-event |
| sourceImplementationSha | 042d351433cd8b2deb39142236b620a802b7a916 |
| CopiMineEndEvent.jar SHA-256 | 82020e4b212e0b85d11d07098bc4301888a11a125bbe681e1ba84a95022efda4 |
| CopiMineClient.jar SHA-256 | c975da6b9cf42cffda2d047cb1686faa3fd84404b51ca12ff162c212aa66ffce |
| CopiMineResourcePack.zip SHA-256 | 34bbed01d468f5f45821ad82dc571012f6c9c5b581cabca18fd6d1112fc143c9 |
| Purpur SHA-256 | 30403cf54f981f16e1403f172645e82d3e4a59ad6c9f1d8e98df99edb1f8ae4c |
| Local game server | 127.0.0.1:25566 |
| Local RCON | 127.0.0.1:25576 |
| Native result before capture | NOT VERIFIED |

The documentation-only commits after the implementation SHA do not authorize
reusing old screenshots or relabeling old videos. If source code or a client,
server, or resource-pack artifact changes, stop, rebuild, record new hashes,
and use the new source SHA as the capture identity.

## 1. Preconditions and refusal rules

Run all PowerShell commands from the authoritative worktree:

~~~powershell
Set-Location 'D:\Desktop\Copimine\copimine-main\.worktrees\end-rift-event'
git status --short --untracked-files=no
git branch --show-current
git rev-parse HEAD
~~~

The operator must confirm:

1. The branch is codex/end-rift-event.
2. For this closure, the code source is the recorded implementation commit
   042d351433cd8b2deb39142236b620a802b7a916. The branch HEAD may be a
   documentation-only descendant; record both captureGitHeadSha and
   sourceImplementationSha in the manifest and confirm that the artifact
   hashes below are unchanged. If source code or an artifact changed, use a
   new exact build identity instead.
3. The local server is isolated to ports 25566 and 25576; never aim this
   procedure at a production server or a public world.
4. The resource pack is accepted by the client and its SHA-256 is the hash in
   the manifest.
5. The native application bridge exposes the actual Minecraft javaw.exe
   window. A browser tab, a static appshot, a pasted image, or an old PNG/MP4
   is not a native exact-head capture.

If any precondition is false, write NOT VERIFIED in the manifest and stop the
visual acceptance claim. Do not substitute a screenshot from another commit.

Run the current static/contract gate before opening the client:

~~~powershell
.\tests\RunEndRiftEventChecks.ps1
~~~

The command must finish with End Rift current local checks passed. This is a
precondition only; it does not itself satisfy the native visual gate.

## 2. Start the isolated local session

Use the session script so the current plugins, client JAR, resource pack, and
local server are synchronized from this worktree:

~~~powershell
$sessionArgs = @{
  NoLogo = $true
  NoProfile = $true
  ExecutionPolicy = 'Bypass'
  File = '.\tests\StartEndRiftLocalUserSession.ps1'
  AdminNickname = '<your-minecraft-name>'
  ClientGameDirectory = 'D:\.minecraft\versions\ServerRP'
  LaunchClient = $true
}
powershell.exe @sessionArgs
~~~

The script is expected to print a line beginning with
LOCAL_USER_SESSION_READY and to verify the loaded plugin list. If the client
launcher is already open, it leaves it untouched. In the launcher, select the
Fabric 1.21.1 ServerRP profile, connect to 127.0.0.1:25566, accept the resource
pack, and authenticate with the operator account.

If the client is already running and only the isolated Paper server needs to be
started, use the existing local startup script instead:

~~~powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\tests\StartEndRiftLocal.ps1
~~~

Do not run both startup paths concurrently. The local RCON helper refuses a
server directory outside this worktree's local-runtime directory.

## 3. Prove the native capture surface before taking evidence

Before any screenshot or video, the Computer Use bridge must report exactly one
controllable native Minecraft/javaw.exe window. Record the following in the
manifest:

~~~text
nativeWindowProcess=javaw.exe
nativeWindowTitle=<full title reported by the bridge>
nativeWindowVersion=1.21.1
nativeWindowSurface=<bridge surface identifier>
captureOperator=<name>
captureStartedAt=<local timestamp with timezone>
~~~

The title must identify the Minecraft client, not the launcher, a browser, or a
server console. If the bridge returns apps=[], exposes only browser surfaces,
or lacks native window focus/control methods, the native gate remains
NOT VERIFIED. Do not report that the plugin is fixed visually in that state.

The native client itself must be visible in every still and in the continuous
video. Use the actual game window, not a crop exported from a different client.
Keep the Minecraft F3 debug screen off for presentation captures and on only
for the dedicated hitbox/debug captures.

## 4. RCON helper for the exact local server

The repository helper authenticates to the isolated RCON port without exposing
the password in this runbook. Define this wrapper in the PowerShell session:

~~~powershell
$worktree = (Resolve-Path '.').Path
$serverDir = (Resolve-Path (Join-Path $worktree 'local-runtime\end-rift-server')).Path
$rconScript = Join-Path $worktree 'tests\InvokeEndRiftLocalRcon.ps1'

function Send-EndRiftCommand {
  param([Parameter(Mandatory = $true)][string]$CommandText)
  & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $rconScript -ServerDir $serverDir -RconPort 25576 -CommandText $CommandText
  if ($LASTEXITCODE -ne 0) {
    throw "Local RCON command failed: $CommandText"
  }
}

Send-EndRiftCommand 'cmend status'
~~~

If cmend status is empty, RCON is refused, or the server directory/ports do
not match the isolated session, stop and repair the local session before taking
evidence.

## 5. Boss model, animation, hitbox, and vanilla bossbar captures

All boss captures must use the official event boss, not a guessed vanilla mob.
Reset the isolated scene first:

~~~powershell
Send-EndRiftCommand 'cmend wave clear'
Send-EndRiftCommand 'cmend boss kill cleanup'
Send-EndRiftCommand 'minecraft:kill @e[type=minecraft:arrow]'
Send-EndRiftCommand 'cmend boss spawn official confirm'
Send-EndRiftCommand 'cmend boss freeze'
Send-EndRiftCommand 'cmend debug bosshitbox on'
Send-EndRiftCommand 'cmend debug bosshitbox status'
~~~

Use cmend status to read the current boss UUID and position. The exact-head
Paper probe observed the disposable boss at approximately 8.5 68 -44.5, but
the camera must use the position returned by the current server, not blindly
reuse that coordinate if the local map differs.

For the camera player, use spectator mode and teleport close enough to inspect
the silhouette without clipping into it. Replace the placeholders with the
current authenticated player and boss position:

~~~powershell
Send-EndRiftCommand 'gamemode spectator <camera>'
Send-EndRiftCommand 'tp <camera> <boss-x> <boss-y> <boss-z> 90 0'
~~~

Take fresh F2 screenshots with these exact roles and names:

| File | Camera/scene | Required visual check |
| --- | --- | --- |
| boss-front-bind.png | front, boss frozen | supplied guardian silhouette, horn symmetry, clean texture, vanilla bossbar |
| boss-side-bind.png | 90-degree side | depth, pivots, limbs, no floating geometry or paper-thin gaps |
| boss-rear-bind.png | rear | back texture, UV continuity, no mirrored or missing faces |
| boss-above-bind.png | above/three-quarter | head/body proportions, shoulders, legs, hitbox alignment |
| boss-hitbox-f3b.png | same scene with F3+B and debug status | model-aligned head/chest/pelvis/limb proxy coverage, no duplicate proxies |
| boss-bossbar-vanilla.png | readable HUD and boss | vanilla bossbar layout, text, health, and no custom broken overlay |

The status output must show the current generation, all expected body parts,
parent linkage, and bounded proxies. F3+B is diagnostic evidence only; it does
not replace checking the model in the normal HUD view.

Capture animation poses from the actual client, not from a server log. For each
pose, turn hitbox debug off if it obscures the model, unfreeze the boss, and use
the supported phase commands:

~~~powershell
Send-EndRiftCommand 'cmend debug bosshitbox off'
Send-EndRiftCommand 'cmend boss unfreeze'
Send-EndRiftCommand 'cmend boss phase awakening'
Send-EndRiftCommand 'cmend boss phase hunt'
Send-EndRiftCommand 'cmend boss phase rift'
Send-EndRiftCommand 'cmend boss phase overload'
Send-EndRiftCommand 'cmend boss phase rage'
Send-EndRiftCommand 'cmend boss phase last_seal'
~~~

Allow the client to render each transition, then capture at least:

~~~text
boss-idle.png
boss-movement.png
boss-melee.png
boss-chest-attack.png
boss-slam.png
boss-hurt.png
boss-death-or-cleanup.png
boss-phase-awakening.png
boss-phase-hunt.png
boss-phase-rift.png
boss-phase-overload.png
boss-phase-rage.png
boss-phase-last-seal.png
~~~

If a command is rejected or the pose cannot be seen in the native client, record
that row as NOT VERIFIED; do not infer animation success from a Java test
marker. The required visual review for every pose is: no UV stretching, no
checker/noise patches, no missing or mirrored faces, no floating cubes, no
vanilla model leakage, stable head look rotation, and every visible part
tracking its corresponding hitbox.

For rotated-limb combat evidence, keep one native observer in spectator mode,
run the exact hitbox probe in a separate PowerShell window, and capture the
boss while the probe performs its melee, miss, and projectile cases. The
server log markers prove damage routing; the screenshot must show the actual
boss pose and debug geometry at the moment of observation.

## 6. Wave 6 Ritual Sphere captures

The native client must observe the same local fixture that the server-side probe
tests. Start from a clean scene and keep the camera outside the seal initially:

~~~powershell
Send-EndRiftCommand 'cmend wave clear'
Send-EndRiftCommand 'cmend boss kill cleanup'
Send-EndRiftCommand 'cmend test wave 6'
Send-EndRiftCommand 'cmend debug objectives'
~~~

Use two authenticated players if possible: one camera/spectator and one
disposable player whose movement crosses the physical seal. If a second client
is unavailable, run the repository Wave 6 live harness for the server-side
transition and keep the native client as an observer; the native screenshot is
still required for the visual row.

Fresh screenshots must cover the following ordered states:

1. wave6-ritual-sphere-ready.png: sphere/display present, casters and guards visible, seal intact.
2. wave6-casters-guarded.png: casters have hands raised and are passive while the guardian is alive; no caster target/attack animation.
3. wave6-prisoner-captured.png: prisoner inside the seal, capture state visible, no premature drain.
4. wave6-sphere-drain.png: sphere drain at the 20-second cadence with the prisoner protected from external damage.
5. wave6-beams-and-zone.png: beams/projectile origin at the caster/sphere, 4x4 zone, Wither/Slowness, and no poison or prisoner-zone effects.
6. wave6-reverse-control-swap.png: reverse/swap effects applied only to eligible free targets; prisoner remains excluded.
7. wave6-guard-break-exposed.png: guardian removed, caster exposed but not attacking until damaged.
8. wave6-caster-awakened.png: the damaged caster has native AI/targeting and its own attack animation.
9. wave6-cleanup.png: sphere, beams, zones, controls, caster/guards, and prisoner tags gone after completion.

The server log must be kept beside the screenshots, with the corresponding
LIVE_WAVE6_* markers. A screenshot of a sphere or particle effect without the
matching state marker is a visual observation, not a full behavior pass.

## 7. Wave 7 one-block barrier captures

Reset and start the disposable Wave 7 fixture:

~~~powershell
Send-EndRiftCommand 'cmend wave clear'
Send-EndRiftCommand 'cmend test wave 7'
~~~

Take fresh views of:

~~~text
wave7-barrier-cardinal.png
wave7-barrier-diagonal.png
wave7-barrier-corner.png
wave7-barrier-restart.png
wave7-barrier-cleanup.png
~~~

Each view must show the visible display layer and the one-block physical
minecraft:barrier collision layer as separate responsibilities. Verify a real
player cannot bypass the connected boundary from a cardinal, diagonal, or
corner approach. For the restart view, capture the rehydrated boundary before
combat begins. The local log must contain the ready/rehydrated markers with
height=5, material=barrier, collision=true, visible=true, and journaled=true;
do not call an Amethyst display alone a collision wall.

After both Wave 6 and Wave 7 visual sequences, run cmend wave clear and take
the final zero-state screenshot only if the native HUD is still visible. The
server status must also be zero state (event-mobs=0, no boss, no transient
visuals/barriers); the screenshot and status are separate evidence items.

## 8. Ordinary, elite, guardian, and ritual model matrix

Use the real wave fixtures to capture every role that appears in the current
configuration. Do not spawn a guessed vanilla replacement just to fill a row.
For each role, take front, side, idle, and attack views when the animation is
actually available:

| Role | Required views | Review points |
| --- | --- | --- |
| ordinary wave mob | front/side/idle/attack | clean silhouette, no unrelated guardian renderer |
| elite mob | front/side/idle/attack | distinct readable silhouette, horns/limbs/gear intentional, no pixel noise |
| guardian | front/side/idle/attack | supplied texture and hierarchy, head look, body/limb pivots |
| Wave 6 ritual caster | guarded/exposed/awakened | raised-hands sphere cast, passive guard state, unique attack after damage |
| Wave 6 guards | front/side/attack/shield-break | readable guard role and shield-break transition |
| ritual/enderman | front/side/attack | event renderer scoped to event role, no vanilla leakage |
| ritual/skeleton | front/side/attack | event renderer and projectile pose |
| Rift Spider | front/side/attack | leg placement, UV continuity, no floating/missing legs |

Review every image at 100% and at the normal in-game distance. The acceptance
standard is the supplied friend reference: deliberate large readable shapes,
clean dark-purple/white contrast, intentional horns and limbs, and no noisy
random pixel spray or unfilled gaps. This matrix is visual; Java model-board
tests cannot close it.

## 9. Fifteen-second flight video

Only record this after the native window identity has been proven. Use OBS or
Windows Game Bar with the Minecraft window as the capture source. The output
must be a continuous 15-second recording at approximately 30 fps; do not use a
stitched montage as the primary video.

Suggested camera path:

~~~text
0-3 s   front three-quarter view of the boss and vanilla bossbar
3-7 s   smooth orbit to the right side, keeping the full body in frame
7-11 s  orbit behind and reveal rear texture/limb alignment
11-15 s rise to an overhead three-quarter view and return the boss to center
~~~

Keep the HUD/bossbar and at least one arena landmark in frame so scale and
position are reviewable. Save as:

~~~text
native-end-rift-flight-15s-042d3514.mp4
~~~

Extract and store three review stills (flight-first.png, flight-middle.png,
flight-last.png) plus a short description in the manifest. The description must
state what is visible at the beginning, midpoint, and end, and whether any
texture pop, animation reset, clipping, floating geometry, or bossbar failure
appears. If recording is interrupted, keep the file as a failed attempt and do
not label it as a 15-second pass.

## 10. Evidence directory and manifest

Create a new directory for this exact capture only. Never overwrite a previous
capture directory:

~~~powershell
$head = (git rev-parse HEAD).Trim()
$evidence = Join-Path $worktree "artifacts\end-rift-v3-evidence\native-exact-$head"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
~~~

Copy only the fresh native PNG/MP4 files into that directory. The directory
must contain manifest.md with:

~~~text
sourceImplementationSha=...
captureGitHeadSha=...
capturedAt=...
nativeWindowTitle=...
nativeWindowProcess=javaw.exe
clientProfile=ServerRP / Fabric 1.21.1
serverAddress=127.0.0.1:25566
resourcePackSha256=...
serverJarSha256=...
clientJarSha256=...
videoDurationSeconds=15.0 or NOT VERIFIED
visualMatrix=PASS or NOT VERIFIED
~~~

Hash every file after copying:

~~~powershell
Get-ChildItem -LiteralPath $evidence -File | Get-FileHash -Algorithm SHA256
~~~

Add the hashes, the native window title, capture timestamps, and one-sentence
descriptions of the screenshot/video contents to manifest.md. Keep raw local
server logs in local-runtime if they are ignored; reference their exact path
and SHA-256 in the manifest instead of pretending a log is a screenshot.

## 11. Acceptance matrix and publication

The visual gate can change from NOT VERIFIED only when all of these rows are
PASS with fresh files and the exact source/artifact identity:

| Gate | Result required |
| --- | --- |
| native Minecraft javaw.exe window identity | PASS |
| boss front/side/rear/above model and texture | PASS |
| boss idle/movement/attack/hurt/death/phase animation | PASS |
| bossbar and HUD | PASS |
| model-aligned hitbox/debug view | PASS |
| ordinary/elite/guardian/ritual mob matrix | PASS |
| Wave 6 Ritual Sphere state sequence | PASS |
| Wave 7 physical/visual boundary and restart | PASS |
| cleanup/zero state | PASS |
| continuous 15-second flight video and description | PASS |

If even one row is missing, set nativeMinecraftTestedSha=NOT VERIFIED and
describe the missing row. When all rows pass, update the validation ledger and
final report with the new exact SHA, evidence directory, file hashes, and
descriptions. Then commit and push the new evidence:

~~~powershell
$paths = @(
  'docs/superpowers/evidence/end-rift-native-capture-procedure.md',
  'docs/end-rift-validation.md',
  'docs/superpowers/evidence/end-rift-live-verification-2026-09-18.md',
  'docs/superpowers/reports/2026-09-18-end-rift-guardian-hitbox-ritual-wave7-final.md',
  'artifacts/end-rift-v3-evidence/native-exact-<exact-head>'
)
git add $paths
git diff --cached --check
git commit -m 'evidence(end-rift): add exact-head native acceptance captures'
git push origin HEAD:codex/end-rift-event
~~~

Run the GitHub Actions push and pull-request workflows for the resulting head
and link both runs in the final report. If GitHub rejects a large video asset,
retain its local SHA-256 and use the repository's approved release/PR asset
path; never silently omit it or replace it with an old video.

## 12. Cleanup after capture

Leave the local test world clean and verify the isolated server is stopped:

~~~powershell
Send-EndRiftCommand 'cmend wave clear'
Send-EndRiftCommand 'cmend debug bosshitbox off'
Send-EndRiftCommand 'cmend boss unfreeze'
Send-EndRiftCommand 'cmend boss kill cleanup'
Send-EndRiftCommand 'cmend status'
Send-EndRiftCommand 'stop'
~~~

Confirm ports 25566 and 25576 are closed. If a cleanup command fails, record
the failure in the manifest/report and keep the release status blocked until the
zero-state probe is rerun.
