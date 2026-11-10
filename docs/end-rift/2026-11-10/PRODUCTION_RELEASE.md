# Guardian production release — 2026-11-10

Installed code: `a8f9e5100a52a4d76728599c5a68462824902633`.
Exact-code CI: https://github.com/IliaZav/copimine/actions/runs/42756970497
All three jobs succeeded: Python/contracts, PostgreSQL, Java plugin gate.
Python: 3212 passed, 10 skipped, 2 subtests; validators: 666 passed, 0 failed.
The complete local Java gate also passed and restored its temporary outputs.

## Installed and published

| Artifact | SHA-256 |
|---|---|
| CopiMineEndEvent.jar | 6e157a8e1adaf3e485fac90f91da7ce4de37be94c61998b949b743de5da0cbfa |
| CopiMineNarcotics.jar (client bridge) | 026787808eac784f1e0c2f48952c625c98a914e41f650d1b7e54521774835fb1 |
| CopiMineClient-0.1.3+26.3.jar | 92cedd12276c268786a636ebf59610ca588d5cf32adabe1e252b6e5df3b670d1 |
| CopiMineResourcePack-26.3.zip | 17aa6d3b2edf626f6d0740d2823d542500836277fbf3cecfd0de54e5e83d8eb9 |

Public HTTPS downloads were fetched independently and hashed, including from
outside the server. The user's existing 26.3 Prism instance received exactly
this client JAR, with its previous JAR backed up; other mods were preserved.

Backup: `/opt/copimine-backups/guardian-20261110T052100Z` includes the complete
runtime/world archive, PostgreSQL custom dump and globals, original JARs and
settings, archive listings and verified checksums. An earlier checked backup
also remains at `guardian-20261110T051538Z`.

The first foreground deployment lost its SSH output and returned safely to the
old runtime. The successful installation ran independently with a persistent
server-side log. No old backups were removed.

## Live server acceptance

Both `cmend spawn boss` and `cmend boss spawn` created a real disposable boss:
`hp=20000/20000 physical=20000/20000`, `mobile=1 aiEnabled=1`, inside the arena.
No players were connected, so `targeted=0` and no client bindings are expected.
Each actor was removed with `cmend boss kill cleanup`.

Final state: service active, startup Done, zero SEVERE/ERROR, event unchanged
(`6294666a-c67d-4b40-82b4-d01bcc8706a0`, generation 380), COLLECTING, wave 0,
boss none, no combat mobs, End still locked and victory NONE. Only resource-pack
URL/SHA1 changed in server.properties; all other properties were preserved.

## Additional platform defect found during acceptance

The current approved scaling policy has a 20000 HP floor and a 40000 HP cap.
The platform template and production Spigot limit were still 20000. That makes
larger-party boss setup fail closed with BOSS_CURRENT_HEALTH_UNSUPPORTED.
Raised only maxHealth.max to 40000, preserved the original config in the checked
backup and restarted successfully. JARs and public artifact hashes are unchanged.

The old test read obsolete BossHealthPolicy.MAX_HEALTH, and its integer regex
parsed `20_000` as `20`. It now reads the active BossScalingPolicy HP_CAP and
HP_FLOOR, including the full underscored numeric literal. The corrected test
first failed at 20000 < 40000, then passed after the config repair; all 33
attribute-limit/startup checks passed. This configuration-only follow-up is
separate from the immutable code/artifact commit and its green CI above.

## Evidence boundary

`nativeVerified=false`. No GUI, Minecraft client session, AuthMe login, native
model/HUD/audio screenshot or multi-player combat was operated. The original
model/texture hashes and exact head portrait are verified in the published
client; that proves packaging and identity, not appearance in a rendered frame.
The server command probes prove real actor creation, HP, enabled AI and cleanup;
they do not prove target selection or animation quality with a live group.

Detailed machine receipts remain in `artifacts/guardian-production-final/`
locally and `/home/qwerty/guardian-a8f9e510-20261110/` on the server. See
`NIGHT_REVIEW.md` for the separate overnight change audit.
