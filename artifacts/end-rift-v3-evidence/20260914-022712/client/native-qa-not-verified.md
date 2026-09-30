# Native Minecraft QA status

The requested native visual check was attempted with Computer Use on
2026-09-14. `cua.getState({disableDiffing:true})` returned no native apps
(`apps=[]`); it exposed only browser surfaces and Codex. The local process
inventory showed the TLauncher JVM and the Paper server JVM, but no accessible
Minecraft client `javaw.exe` window.

Consequently the following are **NOT VERIFIED**, not silently treated as
passed:

- native screenshots/video/audio/FPS;
- actual in-client model and texture selection;
- bossbar artwork and phase ticks as rendered by the client;
- portal, obelisk and core UV, bones, pivots, clipping and Z-fighting;
- tentacle animation playback and socket alignment;
- native player input through all waves and boss phases.

The server-side resource references, built client/resource-pack artifacts,
Paper event run, damage transactions, wave transitions and cleanup are covered
by the adjacent evidence files. Native Computer Use must be rerun on a host
where the Minecraft window is exposed before the visual release gate can be
called complete.
