function selectReflectionAimTarget ({ wave7Autopilot, activeSeal, sourceAnchor }) {
  if (activeSeal?.position) {
    return {
      kind: 'wave7-seal',
      entityId: activeSeal.id,
      position: activeSeal.position.offset(0, 1, 0),
    }
  }

  if (wave7Autopilot || !sourceAnchor) return null
  return {
    kind: 'wave4-obelisk',
    entityId: null,
    position: sourceAnchor,
  }
}

module.exports = { selectReflectionAimTarget }
