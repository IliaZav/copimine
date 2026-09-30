# End Rift source and JAR verification — 2026-09-29

The user confirmed that no pre-failure copy of `CopiMineEndEvent.java` is available and asked to continue recovery from the JAR.

The checked-in source exists at `copimine-end-event/src/me/copimine/endevent/CopiMineEndEvent.java`. Its Git blob is `571ee41aec1a2f1f02c1ee1df4eeaba3f02f4338`, matching the file at `HEAD` on `codex/end-rift-event` (commit `4a1362786c9737e58a65feb56de6bd7523e4163d`, `Fix End Rift wave lifecycle restoration`).

I compiled the tracked plugin sources with the repository's Paper API and dependencies into an isolated verification directory under `local-runtime/source-jar-verification-20260929`. The check did not install or copy a JAR into `minecraft/server/plugins` or change the running test server.

| Artifact | SHA-256 |
| --- | --- |
| Existing `copimine-end-event/CopiMineEndEvent.jar` | `4BBE329371E70A4C3CDF23E93553A53FAF1646869A02CD9029630ADF27FA8EA0` |
| Existing isolated test-server plugin JAR | `4BBE329371E70A4C3CDF23E93553A53FAF1646869A02CD9029630ADF27FA8EA0` |
| Freshly rebuilt verification JAR | `4BBE329371E70A4C3CDF23E93553A53FAF1646869A02CD9029630ADF27FA8EA0` |

The rebuilt JAR and existing plugin JAR matched exactly. All 393 `.class` files under `me/copimine/endevent/` also matched byte-for-byte; there were no differing or missing class files. The source compiled successfully with five deprecation warnings for Bukkit APIs marked for removal.

This confirms that the checked-in Java source reproduces the current test JAR. It does not claim a separate archived pre-failure source copy or prove the visual behavior in Minecraft; player-visible screenshots remain a separate verification step.
