# Cocoon owner retirement follow-up

CODE IMPLEMENTED; tests and native checks deliberately deferred to DeepSeek.

Source tracing found two separate owners surviving cocoon destruction:

* clearActiveRiftProjectiles clears the Snowball registry, while the real boss
  fireballs live in activeRiftFireballs and still pass BOSS_ACTIVE admission
  throughout falling/landing. beginCocoonFall now retires current-generation boss
  fireballs through their own cleanup path (including pending player-damage IDs).
* startCurrentLastSealVisuals checked phase only. LAST_SEAL also includes grounded
  fury, so cocoon orbit particles/ambient channel sound continued after the shield
  broke. Breaking the cocoon now cancels that task; its start and callback gates
  also reject the permanently broken-cocoon receipt, including after restoration.

The generic cast-cancellation path also clears the new fury combination so a
forced diagnostic spell, phase change or defeat cannot resume old follow-up hits.
Normal contact/recovery completion does not call this cancellation path.

DeepSeek: verify forced replacement mid-combo; live fireballs at last-defender
kill; absence of cocoon orbit/audio after break/restart; no Wave 4 or Wave 7
projectile deletion; no surviving pending impact; final melee remains mobile.
No test task/client launch/prod install was run. These latest small follow-up
changes have not been recompiled; the preceding two slices compiled successfully.
