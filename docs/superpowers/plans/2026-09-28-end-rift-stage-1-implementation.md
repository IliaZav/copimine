# End Rift Stage 1 implementation plan

## Scope

Implement the attached “FINAL PROMPT 1/3 — END RIFT WAVES — ARCHITECTURE + IMPLEMENTATION v5” against the current `codex/end-rift-event` branch. This task ends at the Stage 1 handoff; it does not begin the separate boss or tentacle prompts.

Preserve existing boss/tentacle model integration and supplied source assets. Do not deploy to the production server. Keep unrelated dirty worktree changes out of Stage 1 commits.

## Execution stages

1. **Baseline and safe probes** — retain the current changes; make local live harness credentials per-run and ephemeral; pin the relevant regression contract.
2. **Lifecycle and ownership audit** — map the facade, state machine, encounter coordinator, wave runtimes, generation checks, participant roster, cleanup scope, and client bridge. Adapt existing classes rather than maintaining parallel state authorities.
3. **Seven-wave flow** — ensure each wave reports completion to the central lifecycle; add the required post-wave cleanup and 10-second rune hold after Waves 1–6; preserve Wave 4 core restoration without bypassing its rune transition; keep Wave 7 on the no-runes 40-second pre-boss handoff.
4. **Mechanics and presentation audit** — verify the seven supplied wave contracts, generation fencing, server authority, semantic client effects, music, and player-facing text against the prompt. Fix only Stage 1 requirements.
5. **Verification and evidence** — run focused regressions, the complete End Rift check script, plugin/client/resource-pack builds, lifecycle cleanup cases, and available local live probes. Keep code, build, live server, and visual results distinct.
6. **GitHub milestones** — commit only reviewed Stage 1 files in coherent, verified milestones; push normally to `origin/codex/end-rift-event`; recheck remote commit IDs and report any blocked visual evidence explicitly.

## Completion gates

- All relevant tests and builds pass, including regression tests added for discovered failures.
- The final diff contains only Stage 1 work and its necessary tests/evidence; unrelated local edits and generated diagnostics are not staged.
- Live behavior is verified on the isolated local/staging server where available, and any unavailable native Minecraft visual check is reported as incomplete rather than inferred.
- The resulting commits are present on `origin/codex/end-rift-event`.
