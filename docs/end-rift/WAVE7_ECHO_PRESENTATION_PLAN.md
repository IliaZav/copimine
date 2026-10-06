# Wave 7 Echo vanilla presentation checkpoint

Source baseline: `36775775d769e0c87dd1fc4943dbaf8c7e403fab`. This implements
the already-authorized V3 Task D design before activating personal duels.
The official four-trial layout remains explicitly legacy until Tasks A/E/F/G
are integrated. No legacy Reflection completion becomes an Echo outcome.

The server supplies a grounded health-bearing carrier and semantic state over
the existing bridge. A bounded client adapter creates an unregistered
`OtherClientPlayerEntity` view with a distinct synthetic identity, copies the
carrier's movement/equipment and delegates body, armor and held-item rendering
to the pinned vanilla player renderer. It never adds a world entity, tab entry,
connection or second damage target. Skin lookup uses the existing owner entry
and a safe vanilla fallback, with no external profile request or public skin
fixture. The player's original texture is not modified.

Ruling: use the existing bridge envelope and renderer dispatch; do not create a
second encounter engine. A local authorized presentation probe is separate
from official rewards, saved roster and combat profiles. It demonstrates
available vanilla states without granting an unfinished Echo duel success.

1. Add failing presentation-state tests for distinct identities, bounded actor
   count, classic/slim selection, semantic use progress, sequence/epoch fences,
   removal and delayed skin-result rejection.
2. Implement the state adapter and actual vanilla player view/renderer using
   the checked Minecraft 1.21.1/Yarn build. Preserve carrier selection/HP and
   leave ordinary renderers unchanged.
3. Wire bridge lifecycle, terminal cleanup and a local presentation probe.
   Document exact supported actions and carrier/body limitations.
4. Run focused and complete client tests, server/client builds, registered
   checks and diff hygiene. Review only owned public files, commit/push and
   verify that SHA's Actions.
5. Compare real other-player and replica movement/use/hurt/death in Minecraft.
   Without native access this remains BLOCKED; compilation cannot grant parity,
   hitbox agreement or completed personal-duel acceptance.

Native captures and private profiles remain outside Git. No AuthMe password,
synthetic resource-pack success or relaxed anti-cheat is part of this probe.
