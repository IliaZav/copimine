# Release candidate — bc2efe97

Built from `bc2efe972be7e68c50a06a0ec5aa3fa17c9c1e43` on branch
`codex/rift-guardian-polish-20261110`.

Every artifact here was hashed after being written, and `manifest.json` was
then re-read and compared against the bytes on disk field by field. All four
match. That check is worth doing separately from the one that produced the
numbers: a manifest that merely restates the build log proves nothing about the
files that ended up in the folder.

## What is verified

- **Plugin** — `CopiMineEndEvent.jar`, 2,385,635 bytes,
  sha256 `af45b9a27937a6c275b0a2f0612b4b64fd222fe8ed56a3ba3557cb1746aa1b04`.
  Identical to `tools/minecraft-26.3/server-plugins.lock.json`, and reproduced
  byte-for-byte by `endEventJar --rerun-tasks` from a clean task graph.
- **Client** — `CopiMineClient-0.1.3+26.3.jar`, 15,013,727 bytes,
  sha512 `10b49e7b…eeed6862`. Identical to
  `tools/minecraft-26.3/profile.lock.json → clientArtifact`.
- **Resource pack** and **Narcotics jar** carried over unchanged.

## What is NOT verified

This is the honest part and it is the whole gap:

- `nativeVerified: false` — nothing here has been started on a Paper server.
- `livePaperTested: false` — no wave, phase or boss has been run against a
  real server with real players.
- `clientVisuallyVerified: false` — no model, animation, spell, fog, geyser or
  shield flash has been looked at in a Minecraft client.

Compilation and 3,388 automated tests say the code is internally consistent.
They say nothing about whether the encounter looks or feels right. Sections 26
and 30 of the master contract require screenshot evidence for every visual
claim, and that evidence does not exist in this repository.

## Supersedes

`artifacts/rc-0f9b69c8` shipped `b1e95954` / 2,382,357 bytes. The lock has
since moved to `af45b9a2` / 2,385,635 because the cocoon repair added roughly
5,000 lines across 22 files after that candidate was cut. The old jar is not
corrupt; it is simply no longer what the repository pins, so it must not be
deployed as if it were.

## Deployment

Do not deploy this to production, and do not copy the client jar into the
user's Prism instance, without explicit authorization. Both are outside what
this candidate establishes. The installer at
`artifacts/guardian-polish-final/install_guardian_polish.py` still pins the
older hashes in its `OLD` dictionary and would need regenerating before it
could install this build.

## Rollback

Each deployment target keeps its previous jar. The Prism instance already holds
`CopiMineClient-0.1.3+26.3.jar` from an earlier build with its own
`.jar.bak-*` backup; restore that file and the client returns to the previous
state. On the server, restore the previous `CopiMineEndEvent.jar` and restart
`copimine-game.service`. No world data, database migration or arena state is
touched by swapping either jar, so rollback is a file copy plus a restart.
