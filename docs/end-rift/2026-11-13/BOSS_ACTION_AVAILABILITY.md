# Boss action availability and post-cocoon combat

The real automatic dispatch adapter and real BossBrain reproduced two freezes:
a spent Final Strike and a configured-disabled Final Strike still won the
CHANNEL scoring. Dispatch then refused the same action repeatedly, without
recording an accepted alternative. No policy-only success could reveal this
because the phase pool still legitimately contained the one-shot spell.

Automatic selection now excludes that spell before ranking when disabled,
missing configuration, or already spent. Dispatch retains its own safety
checks. The finite one-shot remains available exactly once while legal.

The permanent post-cocoon fury profile also contained FINAL_STRIKE, whose
actual implementation is a distant locked-position explosion with Wither,
not a melee move. Fury now uses the original authored swipe and Ground Slam;
its melee-only invariant explicitly rejects the remote final strike. This
preserves the intended close pursuit and permanent incoming 3x damage.

Behavioral reproduction: tests/test_boss_spell_availability_adapter.py extracts
production automatic dispatch/pool methods and executes them against real
BossBrain, target/intent/ability policies and cast history. Spent/disabled and
melee-fury cases all failed before the repair. It now exercises 20 consecutive
choices plus a fresh one-shot that may run once, without starving valid casts.

This is source/automated evidence. Current Minecraft combat, motion, sound,
and full encounter acceptance remain NOT VERIFIED.

RAGE summon integration repair: summonServants previously returned only when
summonEchoDecoys succeeded. A refused decoy cast therefore fell through into
the ordinary spider summon. The actual adapter reproduction spawned two
spiders on the false branch. RAGE now always returns after its dedicated
summon attempt, preserving refusal and the phase's intended actor type.
Automatic ranking also excludes the summon while its existing policy reports
cooldown or a full alive cap; other attacks continue. Other phases retain their
approved ordinary summon path. Tests execute both real dispatch boundaries.
