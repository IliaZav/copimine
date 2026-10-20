# End Rift V3 evidence manifest — 2026-09-14

This folder contains the exact local Paper evidence for the current End Rift
V3 verification checkpoint. The previous source/test checkpoint was
`150a1910b4990de973332aec3e3cb1e92f64abff` on
`codex/end-rift-event`; the bossbar layout repair was tested in the working
tree immediately after that checkpoint and is included in the publication
commit containing this manifest.

## Runtime and artifacts

| Item | Value |
|---|---|
| Server | Purpur/Paper 1.21.1, local port `25566` |
| RCON | local port `25576` |
| End Rift plugin | `C61543A81FF6DD2DE42DB41FC5E2E5487AD7669F1FFC4D130701B43A69D7D519`, 661869 bytes |
| CopiMineClient post-layout-fix build | `4CF4C92F82CD201B975C57B0B88FB2A12ECD1F677D74FDD68D976704B0409895`, 9396747 bytes |
| Resource pack | `335F68A8CCF1A5BE6FECFD97B711A4684D61CC47C486C43637BD4DBCAA3BC1C1`, 24147588 bytes |
| Resource pack SHA-1 | `f5805906b94977dce2728b93a63997af7339da17` |

The End Rift plugin and resource pack were copied to the active local Paper
runtime and the server restart verified their hashes. The post-layout-fix
client hash is a fresh build artifact; no native client window was exposed to
install or launch it in this session. The website was not changed.

## Captured evidence

| Path | Status | Hash or note |
|---|---|---|
| `server/latest.log` | captured | `E5E6F94A60C687988DBBF17152B77805F3CB3DDC2FE0E2BE86A241B9FACB18C1`, 1671031 bytes |
| `official-e2e/official-current-live.log` | captured | `160A5AE78FAACD32F69E2165746D599E2971B1D14753C4D478348691CE75CA59`, 62160 bytes |
| `boss-multiplayer/FreshFiveA.log` … `FreshFiveE.log` | captured | five bot reports, 7234 bytes each |
| `performance/performance-five.csv` | captured | `5133A4A5687D29D8F447BE545991DD84991009E16642C022020DF659977670A`, 664 bytes |
| `reports/live-results.md` | tracked | summarized command output and PASS criteria |
| `reports/extended-live-results.md` | tracked | additional AI, visual-contract, scaling, spell, gate, recovery and HUD probes |
| `client/native-qa-not-verified.md` | tracked | records the Computer Use limitation |

The `.log` files are intentionally retained locally as evidence but remain
ignored by the repository's log-file rule; the tracked manifest and report
identify their hashes and paths. No fake screenshots or video are included.

## Native client gate

`cua.getState({disableDiffing:true})` returned `apps=[]`; only Brave and the
Codex in-app browser were exposed. A native Minecraft window, client process,
screenshots, video, audio and client FPS were therefore **NOT VERIFIED**.
The Paper runtime and Mineflayer probes are server-side evidence and do not
replace native visual QA.

See [`reports/live-results.md`](reports/live-results.md) and
[`client/native-qa-not-verified.md`](client/native-qa-not-verified.md).
