# Final command-boss / Kagune parity and DeepSeek follow-up review

The user stopped DeepSeek and assigned final integration/deployment to this
executor. Both canonical files were reread; their SHA-256 values remain
6e1245b44a449a0ef1bab9193606398660c68f6bb2de7d7bb6082cc8cde50caf and
0c781e42e274a3f27762e0b568cc1d8ffd7dfd8623e5a0f1deb65ea73a94ae50.

## Additional work found

- c92764d0 changes only pose-generator line-ending parity and its regressions.
  It emits LF and accepts equivalent CRLF checkouts without accepting different
  content. Preserve it. Both c92764d0 and the preceding 1eb143e7 have green CI.
- Fresh runtime inspection found the exact 1eb143e7 plugin installed:
  86c5c6b200903adad86dc704ca2ee71a2b0c34a3b86a75ec9ccfae7a82737350.
  The published latest client and user's Prism copy both hash to
  e2222b4c4a1235804fed6a2d8f0c279957bc25a4a90c6f655499d73df11db555.
  Thus the prepared wave package had been installed after the earlier staging
  inspection. Server state is still COLLECTING, generation 380, no players or
  boss. These observations do not prove any player/native visual acceptance.

## Boss defects reproduced and repaired

1. The central Kagune tick required the durable BOSS_ACTIVE phase and cleared
   command-boss summons in COLLECTING. It now uses the same scoped active-boss
   predicate as the main combat controller, retaining the explicit showroom
   exception. The real extracted guardian controller now initializes defenders
   for the disposable LAST_SEAL boss. The regression was red before the fix.
2. The throw's accepted-damage gate also required BOSS_ACTIVE. A disposable
   command boss threw a valid target without its damage transaction. It now
   validates the active owned runtime. The existing cancelled-hit, missing
   carrier, one damage transaction and landing-protection cases remain green;
   the added command-boss case was red before the repair.
3. `ambush_grab` and `small_kagune` existed in the switch but not the command
   allow-list. Both now reach the production dispatcher; commands report a cast
   refusal instead of claiming success when phase/target/geometry/budget reject
   it. Executed the extracted command branch for both IDs and a rejected cast.
4. A refused hold teleport still reset player velocity and fall state and
   advanced HOLD/THROW. The hold now checks actor ownership, target eligibility,
   immunity, arena and body clearance, then honours teleport acceptance. Every
   caller enters recovery with no target if admission fails. The rejected-
   teleport regression was red before the fix; accepted/refused geometry,
   immunity and stale-actor cases now pass.
5. The independently animated under-player ambush released its initial budget
   reservation at commitment, permitting a second major cast during HOLD/THROW.
   Both automatic selection and explicit dispatch now refuse new major actions
   while this generation's ambush actor exists, through its finite cleanup.
   The real TentacleController regression distinguishes defenders and small
   summons, exercises HOLD/THROW, removal and stale generations.

6. A separate scaling audit found `bossMaxHealth` clamped frozen metadata
   against the obsolete 20000 HP constant, despite real scaling to 40000 HP.
   That desynchronised the HUD and phase thresholds for parties above ten.
   Clamp against the active BossScalingPolicy.HP_CAP. The extracted production
   getter was red at 22000 -> 20000, then passed for every roster 2–20,
   30000/40000 HP, changed live roster, missing/invalid data and the corruption
   ceiling. Eight health/phase/defeat/platform checks passed after this repair.
7. Final integration caught an unsuitable helper in the new hold admission:
   the ground-navigation clearance also required supporting floor immediately
   below the socket. A Kagune hold is airborne. Replaced it with a bounded
   full-player-volume check without ground support. The regression executes
   the real block queries (air under the socket, blocked ceiling, adjacent wall,
   liquid and non-finite position); it was red against the navigation helper.
   All seven hold/throw/guardian/scaled-health regressions passed afterward.

## Evidence

- 22 focused boss/guardian/model/pose checks passed.
- 146 broader boss contract, telegraph, defeat, accepted-damage, guardian,
  command and hold regressions passed.
- Full Paper bridge JUnit and plugin build passed.
- Exact installed-predecessor and own-plugin inventory checks: 5 passed.
- CodeRabbit reviewed the complete boss patch and tests: 0 issues.
- Original boss geometry/skin, exact UV-rendered head portrait, phase music,
  authored animations and camera-shake resources are preserved. This patch
  fixes server gameplay/admission; it does not substitute a new model.

Final CI and production command/cleanup receipts belong to the immutable final
bundle. `nativeVerified=false`: no GUI, Minecraft player session, AuthMe login,
native visual/audio capture or multiplayer combat was operated in this pass.
