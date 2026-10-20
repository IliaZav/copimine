const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')

const {
  createReflectionActionQueue,
  isAimWithinReflectionCone,
  preserveProjectileSourceAnchor,
  selectReflectionAimTarget,
} = require(path.join(__dirname, 'EndRiftReflectionTarget.js'))

function position(x, y, z) {
  return {
    x,
    y,
    z,
    offset(dx, dy, dz) {
      return position(x + dx, y + dy, z + dz)
    },
  }
}

test('Wave 4 returns a reflected projectile toward the upper half of its source obelisk', () => {
  assert.equal(typeof selectReflectionAimTarget, 'function',
    'reflection aim selection must be shared with a behavior test')

  const sourceAnchor = position(8.5, 72, -46.5)
  const target = selectReflectionAimTarget({
    wave7Autopilot: false,
    activeSeal: null,
    sourceAnchor,
  })

  assert.equal(target?.kind, 'wave4-obelisk')
  assert.deepEqual(
    [target?.position.x, target?.position.y, target?.position.z],
    [sourceAnchor.x, sourceAnchor.y + 4.5, sourceAnchor.z],
  )
})

test('Wave 4 keeps aiming at the captured source when a round-robin target is available', () => {
  const sourceAnchor = position(8.5, 72, -46.5)
  const activeObjective = position(0.5, 72, -39.5)
  const target = selectReflectionAimTarget({
    wave7Autopilot: false,
    activeSeal: null,
    sourceAnchor,
    wave4Target: activeObjective,
  })

  assert.equal(target?.kind, 'wave4-obelisk')
  assert.deepEqual(
    [target?.position.x, target?.position.y, target?.position.z],
    [sourceAnchor.x, sourceAnchor.y + 4.5, sourceAnchor.z],
  )
})

test('Wave 4 falls back to a visible objective when its launch source is unknown', () => {
  const activeObjective = position(0.5, 72, -39.5)
  const target = selectReflectionAimTarget({
    wave7Autopilot: false,
    activeSeal: null,
    sourceAnchor: null,
    wave4Target: activeObjective,
  })

  assert.equal(target?.kind, 'wave4-obelisk')
  assert.equal(target?.position, activeObjective)
})

test('Wave 4 preserves the projectile source captured at first observation', () => {
  const firstObservation = position(8.5, 72, -46.5)
  const laterNearestDisplay = position(0.5, 72, -39.5)

  assert.equal(preserveProjectileSourceAnchor(null, firstObservation), firstObservation)
  assert.equal(
    preserveProjectileSourceAnchor(firstObservation, laterNearestDisplay),
    firstObservation,
  )
})

test('reflection bot waits until the fireball enters the source-aim cone', () => {
  const eye = position(0, 1.62, 0)
  const obeliskTop = position(0, 4.5, -10)
  const approachingFireball = position(0, 1, -3.5)
  const passedFireball = position(0, 2, 2)

  assert.equal(isAimWithinReflectionCone({
    eyePosition: eye,
    aimPosition: obeliskTop,
    projectilePosition: approachingFireball,
    maxDegrees: 120,
  }), true)
  assert.equal(isAimWithinReflectionCone({
    eyePosition: eye,
    aimPosition: obeliskTop,
    projectilePosition: passedFireball,
    maxDegrees: 120,
  }), false)
})

test('reflection bot serializes aim and attack packets across projectiles', async () => {
  const runAction = createReflectionActionQueue()
  let activeActions = 0
  let maximumConcurrentActions = 0

  await Promise.all([1, 2, 3].map(() => runAction(async () => {
    activeActions += 1
    maximumConcurrentActions = Math.max(maximumConcurrentActions, activeActions)
    await new Promise(resolve => setTimeout(resolve, 10))
    activeActions -= 1
  })))

  assert.equal(maximumConcurrentActions, 1)
})

test('Wave 7 aims at its active seal even when an obelisk anchor is also visible', () => {
  assert.equal(typeof selectReflectionAimTarget, 'function',
    'reflection aim selection must be shared with a behavior test')

  const sealPosition = position(4, 70, 3)
  const target = selectReflectionAimTarget({
    wave7Autopilot: true,
    activeSeal: { id: 42, position: sealPosition },
    sourceAnchor: position(8, 72, -46),
  })

  assert.equal(target?.kind, 'wave7-seal')
  assert.equal(target?.entityId, 42)
  assert.deepEqual(
    [target?.position.x, target?.position.y, target?.position.z],
    [4, 71, 3],
  )
})

test('Wave 7 never substitutes an obelisk for a missing active seal', () => {
  assert.equal(typeof selectReflectionAimTarget, 'function',
    'reflection aim selection must be shared with a behavior test')

  const target = selectReflectionAimTarget({
    wave7Autopilot: true,
    activeSeal: null,
    sourceAnchor: position(8, 72, -46),
  })

  assert.equal(target, null)
})
