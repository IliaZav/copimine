# Wave 5 smoke chambers — latest user requirements, 2026-11-10

Supersedes earlier fade-in, visible silhouettes and open-roof safe-island tuning.
The fog is arena-local. No Blindness, Iris dependency, shaderpack selection,
physical wall, collision, resource reload or global particle/model clearing.

## Rendering

- A safe chamber has its exact 1x1, 2x2 or 3x3 published emerald footprint,
  three blocks of clear headroom and a smoke ceiling. The arena smoke layer
  is four blocks high above the actual published combat floor.
- While the camera is inside the occupied chamber, the event post effect is
  removed from the requested render effects. User-selected shaders remain
  untouched. Four rolling sides and a ceiling use normal depth-tested world
  geometry, three dark layers and ordinary translucent smoke particles.
- These surfaces are presentation only. Players freely walk through the smoke;
  no server blocks or colliders are created. Third-person cameras that move
  into the smoke are rendered as cameras in smoke.
- Outside a chamber the depth-reconstructed volume has extinction 12/block.
  Even its minimum-density eddy transmits less than 0.02% after one block.
  This is world-volume attenuation, not a Blindness potion or GUI black plate.
  There is no near-camera visibility exemption for close entities or held items.
- The first accepted ACTIVE lease immediately sets full density. A separate
  bounded critical transport reserve bypasses unsigned cosmetic backlogs,
  preserving signed-counter continuity and the client's existing rate checks.
- World smoke is local to the mod: at most 256 owned puffs, 96 initial,
  eight replacements/tick, bounded placement attempts and cached floor reads.
  Large puffs avoid safe footprints; smaller puffs form the ceiling above them.
  CLEAR, death, disconnect, dimension/arena exit and generation change only
  remove the emitter's own particles. No model bindings are touched.
- Shader ray/arena slab intersection also handles a camera outside the arena,
  parallel rays and opaque terrain before the arena entry.

## Verification boundaries

Client tests/build and shaderc compilation passed. Bridge tests exercise immediate
ACTIVE/CLEAR during a 168-body backlog, unbind cleanup, bounded critical traffic
and uninterrupted authenticated counters. Executed shader arithmetic tests cover
inside/outside/parallel/behind-camera/occluded rays. Mesh tests cover all shelter
sizes, bounded geometry, clear interiors, moving layers and opaque ceilings.
Lifecycle tests retain replay, expiry, generation, death and resume checks;
only onset expectations changed to the latest explicit instantaneous requirement.

The first CodeRabbit attempt hit its free allowance. A subsequent completed
review covered all 26 files and returned zero findings. The final near-camera
shader exception removal also passed shaderc compilation and the client suite.
Earlier boss patches have their own completed CodeRabbit results.

`nativeVerified=false`: no GUI, authentication or native Minecraft session was
operated. Density arithmetic and geometry tests do not certify the rendered
appearance, compatibility with every optional shaderpack, or player experience.
Production installation and server probes are recorded separately after release.
