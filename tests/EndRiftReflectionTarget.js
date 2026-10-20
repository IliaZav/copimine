function selectReflectionAimTarget ({ wave7Autopilot, activeSeal, sourceAnchor, wave4Target }) {
  if (activeSeal?.position) {
    return {
      kind: 'wave7-seal',
      entityId: activeSeal.id,
      position: activeSeal.position.offset(0, 1, 0),
    }
  }

  if (wave7Autopilot) return null
  if (sourceAnchor) {
    return {
      kind: 'wave4-obelisk',
      entityId: null,
      // ItemDisplay anchors sit at the obelisk base. Aim at its upper blocks
      // so a reflected fireball from the arena floor does not travel through
      // the arena floor on its way back to the source.
      position: sourceAnchor.offset(0, 4.5, 0),
    }
  }
  if (wave4Target) {
    return {
      kind: 'wave4-obelisk',
      entityId: null,
      position: wave4Target,
    }
  }
  return null
}

function preserveProjectileSourceAnchor(existing, observed) {
  return existing || observed || null
}

function isAimWithinReflectionCone ({ eyePosition, aimPosition, projectilePosition, maxDegrees = 120 }) {
  const values = [
    eyePosition?.x, eyePosition?.y, eyePosition?.z,
    aimPosition?.x, aimPosition?.y, aimPosition?.z,
    projectilePosition?.x, projectilePosition?.y, projectilePosition?.z,
    maxDegrees,
  ]
  if (!values.every(Number.isFinite) || maxDegrees < 0 || maxDegrees > 180) return false

  const aim = {
    x: aimPosition.x - eyePosition.x,
    y: aimPosition.y - eyePosition.y,
    z: aimPosition.z - eyePosition.z,
  }
  const target = {
    x: projectilePosition.x - eyePosition.x,
    y: projectilePosition.y - eyePosition.y,
    z: projectilePosition.z - eyePosition.z,
  }
  const aimLength = Math.hypot(aim.x, aim.y, aim.z)
  const targetLength = Math.hypot(target.x, target.y, target.z)
  if (aimLength < 1e-9 || targetLength < 1e-9) return false

  const cosine = (aim.x * target.x + aim.y * target.y + aim.z * target.z)
    / (aimLength * targetLength)
  return cosine + 1e-9 >= Math.cos(maxDegrees * Math.PI / 180)
}

function createReflectionActionQueue () {
  let tail = Promise.resolve()
  return function runReflectionAction (action) {
    const result = tail.then(action, action)
    tail = result.then(() => undefined, () => undefined)
    return result
  }
}

module.exports = {
  createReflectionActionQueue,
  isAimWithinReflectionCone,
  preserveProjectileSourceAnchor,
  selectReflectionAimTarget,
}
