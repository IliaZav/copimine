# Spider model visual audit — 2026-10-12

These four original F2 screenshots were copied byte-for-byte from `D:\.minecraft\versions\Copimine\screenshots` after the user reported that the End Rift spiders looked broken. SHA-256 values below match the originals.

| Capture | SHA-256 | Observation |
|---|---|---|
| `2026-10-12_14.44.08.png` | `7DEBB7706C6F5F01D7D1332D88F77FD41662164F350A86CE726921A0B830CF65` | Close front/side view; only part of the leg silhouette reads clearly. |
| `2026-10-12_14.44.12_4.png` | `A7025335F455C47C9E30411EFF8C2707DFB674A80122F3E6CAF28198B53E24D3` | The same low, compressed body and obscured legs persist at another angle. |
| `2026-10-12_14.45.10.png` | `9FE4169B6525B2EFD94B0CB3FE853ACF20E66EAD8C8511F46CCDCA223549DCC9` | One striped leg appears disproportionately prominent; the full eight-leg stance is not readable. |
| `2026-10-12_14.45.14.png` | `017BEDE45B2CD74A586B948D64AF1633F92BDA14BF025D0456D8FC85D9996911` | Confirms this is not limited to a single camera angle. |

## Assessment

The purple/yellow skin is present. The visible failure is the rendered silhouette/pose: the body reads as compressed against the arena floor and most legs are hidden or visually collapsed.

## Runtime provenance

The correct profile log directory is `D:\.minecraft\versions\Copimine\logs`, not `D:\.minecraft\logs`. Its `latest.log` was written at 14:45:14 on 2026-10-12 and records Minecraft 26.3 with Fabric Loader 0.19.5, a connection to `127.0.0.1:25568`, and screenshot messages at 14:45:10 and 14:45:14. `copimineclient.log` was written at 14:45:20; it records the CopiMine bridge hello at 14:43:24 and event-spider texture lookups at 14:43:25. The logs establish the client session and resource lookup, but do not record the SHA-512 of the JAR loaded by Fabric or the selected model instance for each spider.

## Source diagnosis and correction

The 26.3 vanilla `SpiderModel` bakes eight non-zero yaw/roll angles into the leg poses. `RiftSpiderModel` had created each leg with `PartPose.offset`, leaving all eight angles at zero; vanilla `setupAnim` then added walking deltas to that flat starting pose. A regression test failed before the fix with `right_hind_leg yaw: expected 0.7853982, actual 0.0`. The model now uses the matching 26.3 vanilla yaw/roll values for all eight legs while preserving the existing cubes, UVs, textures, and walk animation.

The current source build was run with pinned JDK 25.0.4.1+1 and Gradle 9.8.1. `test jar` succeeded: 131 tests, 0 failures, 0 errors, 0 skipped. The newly built candidate `CopiMineClient-0.1.2+26.3.jar` is 14,828,100 bytes with SHA-512 `8453b8c0452c7eac316d981808c57511678a0710129ba16a06e073ab65c697ac1c347fddc34a61c931de4b73db3dc5973bb0c124bd8e9c150b5c71b624d4d5d2`.

The candidate has since been installed into `D:\.minecraft\versions\Copimine` and `InstallMigrationProfile.ps1 -ValidateOnly` passed without mutation. The installed JAR is 14,828,100 bytes and its SHA-512 matches `tools/minecraft-26.3/profile.lock.json` exactly: `8453b8c0452c7eac316d981808c57511678a0710129ba16a06e073ab65c697ac1c347fddc34a61c931de4b73db3dc5973bb0c124bd8e9c150b5c71b624d4d5d2`.

## Post-fix Minecraft verification

The rebuilt JAR was installed and profile validation passed before launching the refreshed Minecraft 26.3 `Copimine` profile. The client connected to `127.0.0.1:25568` as `kuuso`. At 15:27:51 the server confirmed Creative mode for `kuuso`; the F2 screenshot at 15:30:31 therefore shows the player in Creative, avoiding the death risk during visual capture.

`spider-postfix-creative-15.30.31.png` is the actual Minecraft F2 capture from that live session. Its SHA-256 is `38FC15338C0C190E6DE6D4DE121507E0D4B233FCA95E6AF9286C8C94EB9EC6EC`, matching the source screenshot under `D:\.minecraft\versions\Copimine\screenshots`. Compared with the original frames, the eight legs now fan out around the body and are individually readable; the former flat, collapsed stance is gone. The existing purple/yellow texture remains intact.

This confirms the corrected resting/showroom pose in the real client. A walking animation pass from a moving live spider has not been captured, and the screenshot alone does not verify every migration feature. Overall migration acceptance remains open (`nativeVerified=false`).
