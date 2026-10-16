const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')

const { selectReflectionAimTarget } = require(path.join(__dirname, 'EndRiftReflectionTarget.js'))

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

test('Wave 4 returns a reflected projectile toward its observed source obelisk', () => {
  assert.equal(typeof selectReflectionAimTarget, 'function',
    'reflection aim selection must be shared with a behavior test')

  const sourceAnchor = position(8.5, 72, -46.5)
  const target = selectReflectionAimTarget({
    wave7Autopilot: false,
    activeSeal: null,
    sourceAnchor,
  })

  assert.equal(target?.kind, 'wave4-obelisk')
  assert.equal(target?.position, sourceAnchor)
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
