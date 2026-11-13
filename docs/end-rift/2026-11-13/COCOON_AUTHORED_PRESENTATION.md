# Cocoon descent: source animation, real tick owner and resume clock

The old fall translated the entity without an authored body clip. It also
incremented its 40/20-tick counter from the 5-tick strategic heartbeat, making
the sequence five times slower than its policy. A per-call policy test did not
exercise that production scheduling boundary.

Implemented:

* Two generated Bedrock clips on the original bones: 2-second descent/brace and
  1-second landing/compression/recovery. The last fall pose exactly equals the
  first landing pose; recovery returns all authored deltas to bind pose.
* Original geometry, pivots, UVs, textures and supplied clips remain unchanged.
  `CopiMineClient/tools/generate_guardian_cocoon_animations.py` owns the new JSON.
  The existing server pose generator consumes those exact clips too, so the
  animated hitboxes follow the same hierarchy as Fabric.
* The existing per-tick containment watchdog owns descent motion and pose
  updates. Strategic AI remains at its existing 5-tick budget. A server-tick
  receipt prevents a main-heartbeat call from advancing the fall a second time.
* The watchdog also updates hitboxes for the disposable boss even when the
  durable event is outside a numbered combat phase. It does not run numbered
  wave containment merely because the diagnostic boss exists.
* Signed phase cues use the remaining authored stage duration; the client
  resumes that frame on rejoin/heartbeat instead of restarting the fall.
  Ordinary attack heartbeats retain their existing no-restart behavior.
* New IDs are explicitly catalogued by server, both client trees and the strict
  shared codec. No new packet fields, unsigned fallback or client-owned damage.
* Old cast reset callbacks are invalidated before descent, and routine hurt
  clips cannot replace a fall/landing pose. Accepted damage feedback remains
  separate from animation priority. Melee fury releases AI/gravity immediately.

Verification includes real adapter execution with strategic callbacks every
five server ticks and the actual motion watchdog every server tick, duplicate
same-tick calls, canceled/redirected movement, exact 40/60 boundaries, one impact,
client stage-clock resume, malformed duration refusal, strict codec round trips,
and signed sender output. Source animation and hitbox seam tests also execute.
Focused owner/wire/pose groups passed; modern server, client and bridge builds
passed. Final candidate hashes and broader gates are recorded separately.

Native animated frames, sound balance, gameplay feel, lag/performance matrix
and complete official encounter acceptance remain NOT VERIFIED. Historical F2
images are references, not screenshots of these new clips.

Client compatibility and rollout:

The new clips require CopiMineClient 0.1.4+26.3. The shared protocol keeps the
reviewed 0.1.3 heartbeat negotiation compatible; it does not accept arbitrary
version prefixes. The sender maps the two new visual tokens to known fallback
clips for a verified older client, avoiding a strict V4 decode failure that
would tear down its entire model stream. This fallback is compatibility only,
not acceptance of the intended new animation.

The default client-mod requirement, pinned profile and isolated bot harness
now name 0.1.4+26.3. An existing runtime config is preserved on upgrade: change
only client_bridge.required_client_mod_version to 0.1.4+26.3 together with the
new client after approval; do not overwrite unrelated configuration or secrets.
The EndEvent plugin and the bridge-bearing Narcotics plugin must be installed
as the tested pair, with the manifest's exact hashes. No runtime or Prism
installation was performed by this change.
