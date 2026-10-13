# COPIMINE END RIFT — EXECUTION BRIEF FOR CODEX

Read ALL files in docs/end-rift/final-26.3/ in the stated README order before making changes. The authoritative approved design is in these files; historical docs/end-rift/* ledgers are evidence of prior work, NOT a reason to revert newer user decisions.

Repository: IliaZav/copimine. Minecraft 26.3, Paper 26.3 build 169, Fabric CopiMineClient 0.1.2+26.3, Java 25. GitHub Actions mandatory; GitLab mirror secondary. Work on an isolated End Rift branch based on the CURRENT accepted migration SHA; first inspect current refs.

CRITICAL WORKTREE SAFETY: The local codex/end-rift-event branch is older and contains at least 20 dirty tracked files and untracked art/diagnostics. Never reset, clean, overwrite, checkout or force-push those changes. Inspect diffs and explicitly carry the relevant uncommitted End Rift work into a safe 26.3 development worktree.

Implementation order:
A. Preserve supplied assets and complete native 3D models/textures/animations. Repair all Waves 1–7 against approved specifications, including the Wave 3 portal rebuild, Wave 6 cyan ally hover outline and Q/W/E/R UI, and COMPLETE replacement of legacy Wave 7 Reflection/Juggernaut/Hunter with owner-only Echo, mobile Marksman and Rift Archmage.
B. Finish all six Rift Guardian phases. Grounded except Last Seal, where boss actually hovers above Core; meaningful telegraphed mechanics, proper hitbox, textures and bossbar.
C. Finish Kagune: continuous articulated model 4.5–5 blocks, temporary ambush grab/hold/throw/retract, **NEW APPROVED 90-SECOND individual defensive respawn** (NOT permanent death and NOT 40 seconds), guaranteed >=15 seconds boss vulnerability after all defenders die, 5-second pre-respawn warning, shields matched to real living slots, persisted timers, legal hit windows and intended health. Read KAGUNE_90S_ACCEPTANCE.md.
D. Whole-event replay, restart recovery, performance, regression, F2/video evidence, independent reviews and GitHub CI.

Do not implement Narcotics graphics, item naming, website changes or unrelated migration/release work.

For each BUG_LEDGER entry: establish CURRENT code state and classify as confirmed source mismatch, potential bug, already repaired but not visually checked, or genuinely native verified. Create a reproduction/regression before changing code. Document exact source method/line, root cause and result. Do NOT fabricate native acceptance. Use actual Java/Python tests, compiled JARs, clean build, isolated Paper, full 2-player official flow, 5–6-player scaling, client connected over bridge and resource pack, genuine F2 and videos.

MANDATORY FINAL VISUAL VERIFICATION: Capture overview and front/back/left/right/top/closeup of each problematic model, phases before/during/after abilities; include W1 moving carrier, W3 each gate and collapse, W4 HP/UV, W5 real fog, W6 sphere/arms/ally cyan + hostile violet outline and HUD, W7 every approved trial with walls, boss all six phases, Kagune full grab/throw and permanent death. Recheck after every visual fix. Test world restart, player death/quit/reconnect, repeated attempt, stale generation, block and deposit journal recovery, old snapshot migration, canceled damage and cleanup.

Run tests/RunEndRiftEventChecks.ps1, relevant full Python 3.13 suites, current pinned server/client/pack builds, official 2-player Live harness (stops after 3/5/7 plus full boss), Wave6–Wave7 boundary live harness, Tentacle live harness, security/code review, git diff --check, exact-SHA GitHub Actions. Tests and builds are NOT substitutes for visible Minecraft client behavior.

Do not declare DONE until implementation + automated verification + live Paper + actual Minecraft 26.3 visuals + recovery + published reviewed commits genuinely pass. When computer access is unavailable, record precise blocked native tests and continue independent engineering; do not fake screenshots.

Final output in RUSSIAN: per-wave and boss/Kagune status, every BUG_LEDGER ID disposition, exact fixed files, regression commands/results, installed hashes, F2/video links, CI, review findings, remaining issues. No endless docs-only checkpoints; deliver working behavior.

## REQUIRED UPDATED INSTRUCTIONS (2026-10-13)
NEW USER DECISION OVERRIDES OLD "defenders never respawn": each killed defensive Kagune returns after 90 active-combat seconds. This is more than a constant change: actual tickCurrentTentacles restores all guardians after 15s, hardcoded orbit draws four fake shields, config rejects values >1600 ticks, and cooldown/HP state lacks clear persistent snapshot handling. Fix root causes with failing adapter-level tests, not just policy tests. Preserve at least 15 seconds of boss vulnerability after the last guardian dies; when no defender lives, show no shield. Explain per-slot timing, 5-second telegraph, boss death cleanup and restart semantics from KAGUNE_90S_ACCEPTANCE.md.

Additional code-confirmed gaps: boss magma uses 5s safe sectors instead of approved 20s non-sector steam/ignite/armor gameplay; Wave 3 gate model rebuild may stop after one failed layer; Wave 7 named admission and actual trial runtime differ. More risks: canceled damage and closed animation window hit, client prisoner target entity cap. These require source-level reproductions AND native Minecraft 26.3 evidence.

Do not claim SUCCESS unless BUG_LEDGER items, real screenshots/videos, gameplay, persistence, dual-player and 5–6-player tests and code reviews actually verify the behavior. Do not modify unrelated plugin code or disturb the dirty End Rift worktree.
