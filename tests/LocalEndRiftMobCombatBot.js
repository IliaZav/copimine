/*
 * Local-only live probe for the event's real wave-mob path and attack path.
 * This is deliberately a player-side client: it samples server entity
 * positions, walks into the arena and sends actual use_entity attacks.
 */
const path = require('path')
const fs = require('fs')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))
const { Vec3 } = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'vec3'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'MobCombatProbe'
const durationMs = Number(process.argv[3] || 30000)
const arenaX = Number(process.argv[4])
const arenaY = Number(process.argv[5])
const arenaZ = Number(process.argv[6])
const arenaRadius = Number(process.argv[7])
const configuredAttackIntervalMs = Number(
  process.argv[8] || process.env.END_RIFT_ATTACK_INTERVAL_MS || 400,
)
const skipRegister = process.env.END_RIFT_BOT_SKIP_REGISTER === '1'
const botPassword = process.env.END_RIFT_BOT_PASSWORD || 'endrift-local'
const configuredBossUuid = process.env.END_RIFT_BOSS_UUID || ''
const reflectEnabled = process.env.END_RIFT_REFLECT_ENABLED === '1'
const reflectStartDelayMs = Math.max(0, Number(process.env.END_RIFT_REFLECT_START_MS || 0))
const reflectionDiagnosticsEnabled = process.env.END_RIFT_REFLECT_DIAGNOSTICS === '1'
const controlDirectory = process.argv[9] || process.env.END_RIFT_BOT_CONTROL_DIRECTORY || ''
const configuredWave7Chambers = Number(process.env.END_RIFT_WAVE7_CHAMBERS || 2)
const wave7ChamberCount = Number.isFinite(configuredWave7Chambers)
  ? Math.max(2, Math.min(4, Math.floor(configuredWave7Chambers)))
  : 2
// Leave a movement margin inside the server's 18-block room edge.  The
// client must not keep walking after a legal target is reached at the edge:
// doing so makes PlayerMoveEvent rewind the server while Mineflayer keeps a
// stale local position, and subsequent use_entity packets miss the reach
// check.  Mobs remain attackable from this band because melee reach is 2.75.
const wave7NavigationRadius = 16.5
// Diagnostic-only mode for separating server combat handling from client
// movement correction.  A player held at the server-selected chamber center
// still sends the same use_entity attack packet; it simply does not chase a
// moving mob while PlayerMoveEvent may be rewinding its position.
const wave7HoldPosition = process.env.END_RIFT_BOT_WAVE7_HOLD_POSITION === '1'
// A Wave 7 probe client must select targets from its own physical room. The
// server remains authoritative, but allowing a client to chase the nearest
// mob through a closed BARRIER makes it spend the probe pressing into a wall
// while its assigned room is left uncleared.
const wave7AutopilotDefault = process.env.END_RIFT_BOT_WAVE7_AUTOPILOT === '1'
const controlFile = controlDirectory
  ? path.join(controlDirectory, `${username}.mode`)
  : ''
const guardianProbeNames = new Set(
  String(process.env.END_RIFT_GUARDIAN_PROBE_NAMES || '')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean),
)
const guardianProbeEnabled = guardianProbeNames.has(username)
const reflectTargetDistance = 6.5
// The server validates survival interaction reach against the target hitbox,
// not the target's centre point.  Keep a small margin so a moving elite is
// never treated as attackable while the packet is still out of reach.
const meleeAttackDistance = 2.75
let obeliskTargets = []
try {
  const parsedTargets = JSON.parse(process.env.END_RIFT_OBELISK_TARGETS || '[]')
  if (Array.isArray(parsedTargets)) {
    obeliskTargets = parsedTargets
      .filter(point => point
        && Number.isFinite(Number(point.x ?? point.X))
        && Number.isFinite(Number(point.y ?? point.Y))
        && Number.isFinite(Number(point.z ?? point.Z)))
      .map(point => ({
        x: Number(point.x ?? point.X),
        y: Number(point.y ?? point.Y),
        z: Number(point.z ?? point.Z),
      }))
  }
} catch (_) {
  obeliskTargets = []
}
const attackIntervalMs = Number.isFinite(configuredAttackIntervalMs)
  ? Math.max(200, Math.min(5000, Math.floor(configuredAttackIntervalMs)))
  : 400
const bot = mineflayer.createBot({ host, port, username, version: '1.21.1', auth: 'offline' })

let joined = false
let sampleTimer = null
let attackTimer = null
let healthTimer = null
let controlTimer = null
let reflectionScanTimer = null
let lastControlMode = ''
let wave7Autopilot = false
let navigationTimer = null
let navigationTargetId = null
let meleeActionInFlight = false
let lastNavigationLogAt = 0
let navigationPath = []
let navigationPathTargetKey = ''
let navigationPathBuiltAt = 0
let lastNavigationPosition = null
let lastNavigationProgressAt = 0
let navigationRecoveryUntil = 0
let navigationRecoveryDirection = 1
let lastNavigationRecoveryLogAt = 0
let previousHealth = null
let sampleCount = 0
let movedEntities = new Map()
let attackCount = 0
let reflectionCount = 0
// AuthMe can reload a local account's inventory after spawn.  Mineflayer
// skips setQuickBarSlot(0) when slot 0 is already its local value, but Paper
// still needs one held-item packet to activate the equipment modifier for the
// probe sword.  The boundary harness explicitly signals after it has applied
// the sword, so this cannot race the configuration command. Repeating it
// would reset vanilla attack cooldowns.
let heldItemSyncSent = false
let heldItemSyncReturnTimer = null

function syncHeldItem () {
  if (heldItemSyncSent || heldItemSyncReturnTimer !== null || !bot._client) return
  try {
    // A same-slot packet is ignored by the server's inventory listener.  A
    // short 1 -> 0 transition forces the equipment tracker to remove and
    // re-equip the sword, which activates its attack-damage modifier.
    bot._client.write('held_item_slot', { slotId: 1 })
    heldItemSyncReturnTimer = setTimeout(() => {
      try {
        if (!bot._client) return
        bot._client.write('held_item_slot', { slotId: 0 })
        heldItemSyncSent = true
        console.log(`HELD_ITEM_SYNC ${username} slot=1->0`)
      } catch (error) {
        console.error(`HELD_ITEM_SYNC_ERROR ${username} ${error.stack || error}`)
      } finally {
        heldItemSyncReturnTimer = null
      }
    }, 150)
  } catch (error) {
    console.error(`HELD_ITEM_SYNC_ERROR ${username} ${error.stack || error}`)
  }
}

// Mineflayer reuses a numeric entity id after a projectile is removed.  The
// server still gives each spawn a new UUID, so reflection state must follow
// that stable identity or later fireballs can be incorrectly treated as
// already reflected.
const reflectedProjectiles = new Set()
const reflectionTimers = new Set()
const reflectionInFlight = new Set()
const projectileOrigins = new Map()
const reflectionFailures = new Map()
const loggedReflectionFailures = new Set()

function formatPosition(point) {
  if (!point || ![point.x, point.y, point.z].every(Number.isFinite)) return 'unknown'
  return `${point.x.toFixed(2)},${point.y.toFixed(2)},${point.z.toFixed(2)}`
}

function recordReflectionFailure(projectileKey, reason, entity) {
  if (!reflectionDiagnosticsEnabled || !projectileKey) return
  reflectionFailures.set(projectileKey, {
    reason,
    entityId: entity?.id ?? 'unknown',
    uuid: entity?.uuid || 'unknown',
    projectilePos: formatPosition(entity?.position),
    playerPos: formatPosition(bot.entity?.position),
    distance: entity?.position && bot.entity?.position
      ? distance(entity.position, bot.entity.position).toFixed(2)
      : 'unknown',
  })
  const diagnosticKey = `${reason}|${formatPosition(entity?.position)}`
  if (!loggedReflectionFailures.has(diagnosticKey)) {
    loggedReflectionFailures.add(diagnosticKey)
    const failure = reflectionFailures.get(projectileKey)
    console.log(`REFLECT_SKIP ${username} reason=${failure.reason} entity=${failure.entityId}`
      + ` uuid=${failure.uuid} projectile_pos=${failure.projectilePos}`
      + ` player_pos=${failure.playerPos} distance=${failure.distance}`)
  }
}

function logReflectionGiveUp(projectileKey) {
  if (!reflectionDiagnosticsEnabled) return
  const failure = reflectionFailures.get(projectileKey)
  if (!failure) return
  console.log(`REFLECT_GIVEUP ${username} reason=${failure.reason} entity=${failure.entityId}`
    + ` uuid=${failure.uuid} projectile_pos=${failure.projectilePos}`
    + ` player_pos=${failure.playerPos} distance=${failure.distance}`)
  reflectionFailures.delete(projectileKey)
}

function enterPassiveMode () {
  if (attackTimer !== null) {
    clearInterval(attackTimer)
    attackTimer = null
  }
  // A navigation interval can outlive the attack timer when the mode changes
  // while a target is outside the 4.5 block attack radius.  Cancel it too:
  // Wave 4 uses a fixed reflection lane and the player must not walk away
  // between the server-side recovery teleports.
  stopNavigation()
  if (typeof bot.clearControlStates === 'function') bot.clearControlStates()
  wave7Autopilot = false
  console.log(`BOT_PASSIVE ${username}`)
}

function enterActiveMode (roomLocal = false) {
  wave7Autopilot = roomLocal
  if (attackTimer === null) {
    attackTimer = setInterval(attackNearest, attackIntervalMs)
  }
  console.log(`BOT_ACTIVE ${username} wave7_autopilot=${wave7Autopilot}`)
}

function pollControlMode () {
  if (!controlFile) return
  let requestedMode
  try {
    requestedMode = fs.readFileSync(controlFile, 'utf8').trim().toUpperCase()
  } catch (_) {
    return
  }
  if (!['PASSIVE', 'ACTIVE', 'ACTIVE_WAVE7'].includes(requestedMode) || requestedMode === lastControlMode) return
  lastControlMode = requestedMode
  if (requestedMode === 'PASSIVE') enterPassiveMode()
  else enterActiveMode(requestedMode === 'ACTIVE_WAVE7')
}

function distance(a, b) {
  if (!a || !b) return Number.POSITIVE_INFINITY
  const dx = a.x - b.x
  const dy = a.y - b.y
  const dz = a.z - b.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
}

function projectileIdentity(entity) {
  const uuid = String(entity?.uuid || '').trim()
  return uuid ? `uuid:${uuid}` : `id:${entity?.id ?? 'unknown'}`
}

function isCurrentProjectile(current, projectileKey) {
  return current && projectileIdentity(current) === projectileKey
}

function isConfiguredArenaMob(entity) {
  if (![arenaX, arenaY, arenaZ, arenaRadius].every(Number.isFinite)) return true
  if (!entity?.position) return false
  const dx = entity.position.x - (arenaX + 0.5)
  const dz = entity.position.z - (arenaZ + 0.5)
  return dx * dx + dz * dz <= (arenaRadius + 1) * (arenaRadius + 1)
    && Math.abs(entity.position.y - arenaY) <= 5
}

function isGuardianHitbox(entity) {
  return entity && (String(entity.name || '').toLowerCase() === 'interaction'
    || String(entity.displayName || '').toLowerCase() === 'interaction')
}

function eventMobs() {
  const entities = Object.values(bot.entities)
  if (guardianProbeEnabled) {
    const guardians = entities
      .filter(isGuardianHitbox)
      .filter(isConfiguredArenaMob)
      .filter(entity => bot.entity && distance(entity.position, bot.entity.position) <= 32)
    // One designated survival client attacks only the real Interaction
    // companions while they exist.  Once every guardian is gone it falls back
    // to the normal event-mob path, allowing the same client to finish the
    // damage-window portion of the live test.
    if (guardians.length > 0) return guardians
  }
  const visible = entities
    .filter(entity => entity && entity.uuid === configuredBossUuid)
    .filter(isConfiguredArenaMob)
  if (configuredBossUuid && visible.length > 0) return visible
  return Object.values(bot.entities)
    .filter(entity => entity && ['spider', 'enderman', 'skeleton'].includes(entity.name))
    .filter(isConfiguredArenaMob)
    .filter(entity => bot.entity && distance(entity.position, bot.entity.position) <= 32)
}

function chamberForPoint(point) {
  if (!wave7Autopilot || !point
    || ![arenaX, arenaZ].every(Number.isFinite)) return -1
  const xOffset = point.x - arenaX
  const zOffset = point.z - arenaZ
  const radius = Math.sqrt(xOffset * xOffset + zOffset * zOffset)
  if (radius < 3.5 || radius > 18.0) return -1
  const angle = Math.atan2(zOffset, xOffset)
  const halfSector = Math.max(0.05, Math.PI / wave7ChamberCount - (8.0 * Math.PI / 180.0))
  let selected = -1
  let closest = Number.POSITIVE_INFINITY
  for (let chamber = 0; chamber < wave7ChamberCount; chamber++) {
    const center = -Math.PI / 2.0 + (2.0 * Math.PI * chamber / wave7ChamberCount)
    const delta = Math.atan2(Math.sin(angle - center), Math.cos(angle - center))
    const absolute = Math.abs(delta)
    if (absolute <= halfSector && absolute < closest) {
      selected = chamber
      closest = absolute
    }
  }
  return selected
}

function sameWave7Chamber(entity) {
  return sameWave7ChamberPoint(entity?.position)
}

function sameWave7ChamberPoint(point) {
  if (!wave7Autopilot) return true
  if (!bot.entity || !point) return false
  const playerChamber = chamberForPoint(bot.entity.position)
  const targetChamber = chamberForPoint(point)
  return playerChamber >= 0 && playerChamber === targetChamber
}

function isWave7NavigationPoint(point) {
  if (!wave7Autopilot) return true
  if (!point || ![arenaX, arenaZ].every(Number.isFinite)) return false
  const dx = point.x - arenaX
  const dz = point.z - arenaZ
  const radius = Math.hypot(dx, dz)
  return radius >= 3.5 && radius <= wave7NavigationRadius
    && chamberForPoint(point) >= 0
}

function isEmptyBlock(block) {
  return Boolean(block) && (block.boundingBox === 'empty'
    || ['air', 'cave_air', 'void_air'].includes(block.name))
}

function isWalkableBlock(x, y, z) {
  if (!bot.blockAt || ![x, y, z].every(Number.isInteger)) return false
  const feet = bot.blockAt(new Vec3(x, y, z))
  const head = bot.blockAt(new Vec3(x, y + 1, z))
  const support = bot.blockAt(new Vec3(x, y - 1, z))
  return isEmptyBlock(feet) && isEmptyBlock(head) && support
    && support.name !== 'barrier' && support.boundingBox !== 'empty'
}

function navigationBounds() {
  const centerX = Number.isFinite(arenaX) ? Math.floor(arenaX) : 0
  const centerZ = Number.isFinite(arenaZ) ? Math.floor(arenaZ) : 0
  const radius = Number.isFinite(arenaRadius)
    ? Math.max(4, Math.floor(arenaRadius))
    : 20
  return {
    minX: centerX - radius,
    maxX: centerX + radius,
    minZ: centerZ - radius,
    maxZ: centerZ + radius,
  }
}

function navigationKey(x, y, z) {
  return `${x},${y},${z}`
}

function navigationTargetKey(target) {
  if (!target?.position) return ''
  return `${target.id}:${Math.floor(target.position.x)}:${Math.floor(target.position.y)}:${Math.floor(target.position.z)}`
}

function findWalkablePath(target) {
  if (!wave7Autopilot || !bot.entity || !target?.position) return []
  const start = {
    x: Math.floor(bot.entity.position.x),
    y: Math.floor(bot.entity.position.y),
    z: Math.floor(bot.entity.position.z),
  }
  const desired = {
    x: Math.floor(target.position.x),
    y: Math.max(start.y - 2, Math.min(start.y + 2, Math.floor(target.position.y))),
    z: Math.floor(target.position.z),
  }
  const bounds = navigationBounds()
  const goalKeys = new Set()
  for (let y = start.y - 2; y <= start.y + 2; y++) {
    for (let dx = -1; dx <= 1; dx++) {
      for (let dz = -1; dz <= 1; dz++) {
        const x = desired.x + dx
        const z = desired.z + dz
        if (x < bounds.minX || x > bounds.maxX || z < bounds.minZ || z > bounds.maxZ
          || !isWave7NavigationPoint({ x: x + 0.5, z: z + 0.5 })
          || !isWalkableBlock(x, y, z)) continue
        goalKeys.add(navigationKey(x, y, z))
      }
    }
  }
  if (goalKeys.size === 0 || !isWalkableBlock(start.x, start.y, start.z)
    || !isWave7NavigationPoint(bot.entity.position)) return []

  const startKey = navigationKey(start.x, start.y, start.z)
  const open = [{ ...start, g: 0, f: 0 }]
  const nodes = new Map([[startKey, start]])
  const cameFrom = new Map()
  const bestCost = new Map([[startKey, 0]])
  const neighbors = [
    [1, 0, 0], [-1, 0, 0], [0, 0, 1], [0, 0, -1],
    [0, 1, 0], [0, -1, 0],
  ]
  let visited = 0
  let goal = null
  while (open.length > 0 && visited++ < 900) {
    open.sort((left, right) => left.f - right.f)
    const current = open.shift()
    const currentKey = navigationKey(current.x, current.y, current.z)
    if (goalKeys.has(currentKey)) {
      goal = current
      break
    }
    for (const [dx, dy, dz] of neighbors) {
      const next = { x: current.x + dx, y: current.y + dy, z: current.z + dz }
      if (next.x < bounds.minX || next.x > bounds.maxX
        || next.z < bounds.minZ || next.z > bounds.maxZ
        || Math.abs(next.y - start.y) > 2
          || !isWave7NavigationPoint({ x: next.x + 0.5, z: next.z + 0.5 })
        || !isWalkableBlock(next.x, next.y, next.z)) continue
      const nextKey = navigationKey(next.x, next.y, next.z)
      const cost = current.g + 1 + (dy !== 0 ? 0.35 : 0)
      if (cost >= (bestCost.get(nextKey) ?? Number.POSITIVE_INFINITY)) continue
      bestCost.set(nextKey, cost)
      nodes.set(nextKey, next)
      cameFrom.set(nextKey, currentKey)
      const heuristic = Math.abs(next.x - desired.x) + Math.abs(next.z - desired.z)
        + Math.abs(next.y - desired.y)
      open.push({ ...next, g: cost, f: cost + heuristic })
    }
  }
  if (!goal) return []
  const path = []
  let cursor = navigationKey(goal.x, goal.y, goal.z)
  while (cursor !== startKey) {
    const node = nodes.get(cursor)
    if (!node) return []
    path.push(node)
    cursor = cameFrom.get(cursor)
    if (!cursor) return []
  }
  return path.reverse()
}

function stopNavigation() {
  navigationTargetId = null
  navigationPath = []
  navigationPathTargetKey = ''
  navigationPathBuiltAt = 0
  lastNavigationPosition = null
  lastNavigationProgressAt = 0
  navigationRecoveryUntil = 0
  if (navigationTimer !== null) {
    clearInterval(navigationTimer)
    navigationTimer = null
  }
  if (typeof bot.setControlState !== 'function') return
  bot.setControlState('forward', false)
  bot.setControlState('sprint', false)
  bot.setControlState('jump', false)
  bot.setControlState('left', false)
  bot.setControlState('right', false)
}

function tickWave7Navigation() {
  // The same bounded navigator is also needed by the Last Seal guardian
  // probe. Tentacle throws can move the designated client away from a real
  // Interaction hitbox; without this branch the probe records a few hits and
  // then silently waits at the arena centre while the shield remains valid.
  if ((!wave7Autopilot && !guardianProbeEnabled && attackTimer === null)
    || !bot.entity || navigationTargetId === null) {
    stopNavigation()
    return
  }
  if (meleeActionInFlight) {
    stopNavigation()
    return
  }
  const target = bot.entities[navigationTargetId]
  if (!target || wave7Autopilot && !sameWave7Chamber(target)) {
    stopNavigation()
    return
  }
  const targetDistance = distance(target.position, bot.entity.position)
  if (targetDistance <= meleeAttackDistance) {
    stopNavigation()
    return
  }
  const now = Date.now()
  const currentPosition = bot.entity.position
  if (wave7Autopilot && !isWave7NavigationPoint(currentPosition)) {
    stopNavigation()
    return
  }
  if (!lastNavigationPosition) {
    lastNavigationPosition = {
      x: currentPosition.x,
      y: currentPosition.y,
      z: currentPosition.z,
    }
    lastNavigationProgressAt = now
  } else if (distance(lastNavigationPosition, currentPosition) >= 0.18) {
    lastNavigationPosition = {
      x: currentPosition.x,
      y: currentPosition.y,
      z: currentPosition.z,
    }
    lastNavigationProgressAt = now
  } else if (now - lastNavigationProgressAt >= 1_500) {
    navigationRecoveryUntil = now + 1_200
    navigationRecoveryDirection *= -1
    navigationPath = []
    navigationPathBuiltAt = 0
    lastNavigationProgressAt = now
    if (now - lastNavigationRecoveryLogAt >= 1_000) {
      lastNavigationRecoveryLogAt = now
      console.log(`NAVIGATION_RECOVERY ${username} target=${target.id} direction=${navigationRecoveryDirection}`)
    }
  }
  const targetKey = navigationTargetKey(target)
  if (navigationPathTargetKey !== targetKey || navigationPath.length === 0
    || now - navigationPathBuiltAt >= 750) {
    navigationPath = findWalkablePath(target)
    navigationPathTargetKey = targetKey
    navigationPathBuiltAt = now
  }
  while (navigationPath.length > 0) {
    const next = navigationPath[0]
    const horizontal = Math.hypot(
      currentPosition.x - (next.x + 0.5),
      currentPosition.z - (next.z + 0.5),
    )
    if (horizontal > 0.65 || Math.abs(currentPosition.y - next.y) > 1.1) break
    navigationPath.shift()
  }
  // Do not fall back to direct forward motion when the bounded path has no
  // legal waypoint.  That fallback can walk past the room radius while the
  // server is correctly rewinding the player, recreating the client/server
  // position drift this probe is meant to detect.
  if (wave7Autopilot && navigationPath.length === 0) {
    stopNavigation()
    return
  }
  const waypoint = navigationPath[0]
  const lookPoint = waypoint
    ? new Vec3(waypoint.x + 0.5, currentPosition.y + 0.8, waypoint.z + 0.5)
    : target.position.offset(0, 0.8, 0)
  // Keep the controls active on the physics cadence.  A single control call
  // after an asynchronous lookAt can be lost during a teleport/packet burst.
  bot.setControlState('forward', true)
  bot.setControlState('sprint', true)
  bot.setControlState('jump', Boolean(navigationRecoveryUntil > now
    || (waypoint && waypoint.y > Math.floor(currentPosition.y))))
  bot.setControlState('left', navigationRecoveryUntil > now && navigationRecoveryDirection < 0)
  bot.setControlState('right', navigationRecoveryUntil > now && navigationRecoveryDirection > 0)
  try {
    // Keep navigation and combat on the same synchronous view update. An
    // asynchronous Mineflayer lookAt can complete after attackNearest has
    // selected a target and overwrite the server-side aim direction.
    lookAtServer(lookPoint)
  } catch (error) {
    console.error(`NAVIGATION_ERROR ${username} ${error.stack || error}`)
  }
  if (now - lastNavigationLogAt >= 1000) {
    lastNavigationLogAt = now
    console.log(`BOT_NAVIGATING ${username} target=${target.id} distance=${targetDistance.toFixed(2)}`
      + ` path=${navigationPath.length} waypoint=${waypoint ? navigationKey(waypoint.x, waypoint.y, waypoint.z) : 'direct'}`)
  }
}

function navigateWave7(target) {
  if ((!wave7Autopilot && !guardianProbeEnabled && attackTimer === null) || !bot.entity) return
  if (wave7Autopilot && wave7HoldPosition) {
    stopNavigation()
    return
  }
  if (wave7Autopilot && !sameWave7Chamber(target)) return
  navigationTargetId = target.id
  if (navigationTimer === null) {
    navigationTimer = setInterval(tickWave7Navigation, 100)
  }
}

function sampleMobs() {
  if (!bot.entity) return
  const mobs = eventMobs()
  sampleCount += 1
  for (const mob of mobs) {
    const previous = movedEntities.get(mob.id)
    const now = { x: mob.position.x, y: mob.position.y, z: mob.position.z }
    if (previous) {
      const moved = distance(previous, now)
      if (moved >= 0.25) {
        console.log(`MOB_MOVED ${username} id=${mob.id} type=${mob.name} delta=${moved.toFixed(2)} pos=${now.x.toFixed(2)},${now.y.toFixed(2)},${now.z.toFixed(2)}`)
      }
    } else {
      console.log(`MOB_SEEN ${username} id=${mob.id} type=${mob.name} pos=${now.x.toFixed(2)},${now.y.toFixed(2)},${now.z.toFixed(2)}`)
    }
    movedEntities.set(mob.id, now)
  }
  if (sampleCount % 8 === 0) {
    const position = bot.entity.position
    console.log(`MOB_SAMPLE ${username} count=${mobs.length} player_pos=${position.x.toFixed(2)},${position.y.toFixed(2)},${position.z.toFixed(2)}`)
  }
}

function attackNearest() {
  if (!bot.entity) return
  if (meleeActionInFlight) return
  const target = eventMobs()
    .filter(sameWave7Chamber)
    .filter(entity => distance(entity.position, bot.entity.position) <= meleeAttackDistance)
    .sort((a, b) => distance(a.position, bot.entity.position) - distance(b.position, bot.entity.position))[0]
  if (!target) {
    if (wave7Autopilot && wave7HoldPosition) {
      stopNavigation()
    } else {
      const navigationTarget = eventMobs()
        .filter(sameWave7Chamber)
        .sort((a, b) => distance(a.position, bot.entity.position) - distance(b.position, bot.entity.position))[0]
      if (navigationTarget) navigateWave7(navigationTarget)
      else stopNavigation()
    }
    return
  }
  stopNavigation()
  meleeActionInFlight = true
  let selectSlot
  try {
    if (typeof bot.setQuickBarSlot === 'function') {
      // Do not resend held_item_slot for every attack. GrimAC is configured
      // to reset item usage on a slot change, so repeating the same slot can
      // make a valid second-room attack fail the vanilla cooldown gate.
      if (bot.quickBarSlot !== 0) {
        selectSlot = bot.setQuickBarSlot(0)
      }
    }
  } catch (error) {
    meleeActionInFlight = false
    console.error(`ATTACK_ERROR ${username} ${error.stack || error}`)
    return
  }
  Promise.resolve(selectSlot)
    .then(() => {
      const refreshed = bot.entities[target.id]
      if (!refreshed || distance(refreshed.position, bot.entity.position) > meleeAttackDistance) return
      const finalTarget = bot.entities[target.id]
      if (!finalTarget || distance(finalTarget.position, bot.entity.position) > meleeAttackDistance) return
      lookAtServer(finalTarget.position.offset(0, 0.8, 0))
      // Emit the same explicit packet for every target.  Mineflayer's
      // high-level bot.attack() schedules its own asynchronous lookAt and can
      // overwrite the synchronous view update above before Paper validates
      // the interaction.  That race was asymmetric: one chamber happened to
      // keep the correct yaw while the other sent a perfectly valid-looking
      // packet against the previous view direction.
      bot._client.write('use_entity', {
        target: finalTarget.id,
        mouse: 1,
        hand: 0,
        sneaking: false,
      })
      bot._client.write('arm_animation', { hand: 0 })
      attackCount += 1
      console.log(`PLAYER_ATTACK ${username} count=${attackCount} target=${finalTarget.id} uuid=${finalTarget.uuid || 'unknown'} type=${finalTarget.name} distance=${distance(finalTarget.position, bot.entity.position).toFixed(2)}`)
    })
    .catch(error => console.error(`ATTACK_ERROR ${username} ${error.stack || error}`))
    .finally(() => { meleeActionInFlight = false })
}

function isRiftFireball(entity) {
  return entity && (['fireball', 'large_fireball'].includes(entity.name)
    || entity.entityType === 62)
}

function isFireballEntity(entity) {
  return entity && (isRiftFireball(entity)
    || String(entity.name || '').toLowerCase().includes('fireball')
    || String(entity.displayName || '').toLowerCase().includes('fireball'))
}

function isDisplay(entity) {
  const name = String(entity?.name || entity?.mobType || entity?.type || '').toLowerCase()
  return name.includes('item_display') || name === 'display'
}

function nearbyObelisks() {
  return Object.values(bot.entities).filter(isDisplay).filter(entity => entity.position)
}

function isWave4ObeliskDisplay(entity) {
  if (!entity?.position || !Number.isFinite(arenaX) || !Number.isFinite(arenaZ)) return false
  const dx = entity.position.x - arenaX
  const dz = entity.position.z - arenaZ
  const horizontalSquared = dx * dx + dz * dz
  return horizontalSquared >= 7.5 * 7.5 && horizontalSquared <= 11.5 * 11.5
}

function nearestWave4ObeliskDisplay(point) {
  if (!point) return null
  return nearbyObelisks()
    .filter(isWave4ObeliskDisplay)
    .sort((first, second) => distance(first.position, point) - distance(second.position, point))[0] || null
}

function reflectionTarget() {
  if (obeliskTargets.length > 0) {
    return obeliskTargets[reflectionCount % obeliskTargets.length]
  }
  return null
}

function lookAtServer(point) {
  if (!bot.entity || !point) return
  const eye = bot.entity.position.offset(0, bot.entity.eyeHeight, 0)
  const delta = point.minus(eye)
  const groundDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z)
  const yaw = Math.atan2(-delta.x, -delta.z)
  const pitch = Math.atan2(delta.y, groundDistance)
  // Mineflayer's force=true path updates its local rotation and the private
  // lastSentYaw/lastSentPitch, but deliberately waits for the physics loop to
  // emit a packet. An attack packet sent immediately afterwards would then be
  // validated by Paper against the previous server-side view direction. Send
  // the same vanilla serverbound look packet first so the following
  // use_entity is tested against the aim that selected the target.
  bot.look(yaw, pitch, true)
  bot._client.write('look', {
    yaw: Math.fround((Math.PI - yaw) * 180 / Math.PI),
    pitch: Math.fround(-pitch * 180 / Math.PI),
    onGround: bot.entity.onGround,
  })
}

async function reflectFireball(entity) {
  if (!reflectEnabled || !bot.entity || !isRiftFireball(entity)) return false
  const uuid = String(entity?.uuid || '').trim()
  if (!uuid) return false
  const projectileKey = projectileIdentity(entity)
  if (reflectedProjectiles.has(projectileKey)) return false
  const current = bot.entities[entity.id]
  if (!isCurrentProjectile(current, projectileKey)) {
    recordReflectionFailure(projectileKey, 'entity-replaced', current || entity)
    return false
  }
  if (!current.position) {
    recordReflectionFailure(projectileKey, 'no-position', current)
    return false
  }
  if (distance(current.position, bot.entity.position) > reflectTargetDistance) {
    recordReflectionFailure(projectileKey, 'too-far-before-look', current)
    return false
  }
  try {
    // The player must first acquire the projectile, then keep the return
    // target in view when the attack packet is sent.  This is how a player
    // sends an incoming fireball back toward the obelisk instead of merely
    // punching it upward into empty air.
    const sourceAnchor = projectileOrigins.get(projectileKey)
    await lookAtServer(current.position)
    await new Promise(resolve => setTimeout(resolve, 75))
    const refreshed = bot.entities[entity.id]
    if (!isCurrentProjectile(refreshed, projectileKey)) {
      recordReflectionFailure(projectileKey, 'entity-replaced-after-look', refreshed || entity)
      return false
    }
    if (!refreshed.position) {
      recordReflectionFailure(projectileKey, 'no-position-after-look', refreshed)
      return false
    }
    if (distance(refreshed.position, bot.entity.position) > reflectTargetDistance) {
      recordReflectionFailure(projectileKey, 'too-far-after-look', refreshed)
      return false
    }
    // The server's aim-cone check is defined from the player to the
    // projectile, not from the player to its source display. The source is
    // still useful for diagnostics, but using it as the final look direction
    // rejects valid diagonal reflections when the three points are not
    // perfectly collinear.
    await lookAtServer(refreshed.position)
    // Send the same serverbound attack interaction a vanilla player uses.
    // Do not call bot.attack here: Mineflayer's entity type filter can reject
    // LargeFireball before the packet is emitted.
    const target = bot.entities[entity.id]
    if (!isCurrentProjectile(target, projectileKey)) {
      recordReflectionFailure(projectileKey, 'entity-replaced-before-use', target || entity)
      return false
    }
    if (distance(target.position, bot.entity.position) > reflectTargetDistance) {
      recordReflectionFailure(projectileKey, 'too-far-before-use', target)
      return false
    }
    bot._client.write('use_entity', {
      target: target.id,
      mouse: 1,
      sneaking: false,
    })
    bot._client.write('arm_animation', { hand: 0 })
    reflectedProjectiles.add(projectileKey)
    reflectionCount += 1
    console.log(`PLAYER_REFLECT ${username} count=${reflectionCount} entity=${target.id} uuid=${target.uuid || 'unknown'} target=projectile origin=${sourceAnchor ? 'known' : 'nearest'} distance=${distance(target.position, bot.entity.position).toFixed(2)} projectile_pos=${formatPosition(target.position)} player_pos=${formatPosition(bot.entity.position)}`)
    reflectionFailures.delete(projectileKey)
    return true
  } catch (error) {
    console.error(`REFLECT_ERROR ${username} ${error.stack || error}`)
    return false
  }
}

function scheduleFireballReflection(entity) {
  if (!reflectEnabled || !isRiftFireball(entity)) return
  const uuid = String(entity?.uuid || '').trim()
  if (!uuid) return
  const projectileKey = projectileIdentity(entity)
  if (reflectedProjectiles.has(projectileKey) || reflectionInFlight.has(projectileKey)) return
  if (reflectionTimers.has(projectileKey)) return
  const timer = setTimeout(async () => {
    reflectionTimers.delete(projectileKey)
    reflectionInFlight.add(projectileKey)
    try {
      for (let attempt = 0; attempt < 25; attempt++) {
        if (reflectedProjectiles.has(projectileKey)) return
        const current = bot.entities[entity.id]
        if (!isCurrentProjectile(current, projectileKey)) return
        if (await reflectFireball(current)) return
        if (!isCurrentProjectile(bot.entities[entity.id], projectileKey)) return
        await new Promise(resolve => setTimeout(resolve, 100))
      }
      logReflectionGiveUp(projectileKey)
    } finally {
      reflectionInFlight.delete(projectileKey)
    }
  }, reflectStartDelayMs)
  reflectionTimers.add(projectileKey)
}

function scanRiftFireballs() {
  if (!reflectEnabled) return
  for (const entity of Object.values(bot.entities)) {
    if (!isRiftFireball(entity)) continue
    const projectileKey = projectileIdentity(entity)
    if (entity.position) {
      projectileOrigins.set(projectileKey,
        nearestWave4ObeliskDisplay(entity.position)?.position || entity.position)
    }
    scheduleFireballReflection(entity)
  }
}

bot._client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
})

bot.once('spawn', () => {
  joined = true
  console.log(`PLAYER_JOIN ${username} reflect_enabled=${reflectEnabled} reflect_targets=${obeliskTargets.length} guardian_probe=${guardianProbeEnabled} wave7_hold_position=${wave7HoldPosition}`)
  if (!skipRegister) bot.chat(`/register ${botPassword} ${botPassword}`)
  for (const delay of [1000, 3000, 6000]) setTimeout(() => bot.chat(`/login ${botPassword}`), delay)
  // Mineflayer's physics plugin already acknowledges server teleports and
  // sends the matching position packet.  A second 100 ms position loop fights
  // that controller, produces invalid-packet spam after RCON sweeps, and can
  // make GrimAC disconnect the local probe before it reaches outer spawns.
  sampleTimer = setInterval(sampleMobs, 250)
  reflectionScanTimer = setInterval(scanRiftFireballs, 100)
  scanRiftFireballs()
  pollControlMode()
  controlTimer = setInterval(pollControlMode, 100)
  // Keep a survival-like attack cadence while still reacting quickly enough
  // to the tower-defense wave's moving attackers.
  enterActiveMode(wave7AutopilotDefault)
  healthTimer = setInterval(() => {
    if (previousHealth !== null && bot.health < previousHealth - 0.01) {
      console.log(`PLAYER_HURT ${username} before=${previousHealth.toFixed(2)} after=${bot.health.toFixed(2)}`)
    }
    previousHealth = bot.health
  }, 100)
})

bot.on('entitySpawn', entity => {
  scheduleFireballReflection(entity)
  if (isRiftFireball(entity) && entity.position) {
    projectileOrigins.set(projectileIdentity(entity),
      nearestWave4ObeliskDisplay(entity.position)?.position || entity.position)
  }
  if (isFireballEntity(entity) || entity?.name === 'unknown') {
    console.log(`ENTITY_PROJECTILE ${username} id=${entity?.id} name=${entity?.name} type=${entity?.type} entityType=${entity?.entityType} display=${entity?.displayName}`)
  }
  if (entity && ['spider', 'enderman', 'skeleton'].includes(entity.name)) {
    console.log(`ENTITY_SPAWN ${username} id=${entity.id} type=${entity.name}`)
  }
  if (isGuardianHitbox(entity)) {
    console.log(`GUARDIAN_HITBOX_SEEN ${username} id=${entity.id} type=${entity.name}`)
  }
})

// Keep a raw packet trace for protocol/version mismatches.  In particular,
// Paper sends LargeFireball through spawn_entity and a stale bot registry can
// label it `unknown` before the regular entity adapter gets a usable name.
bot._client.on('spawn_entity', packet => {
  const entity = bot.entities[packet.entityId]
  const registryEntity = bot.registry.entities?.[packet.type]
  const registryName = registryEntity?.name || registryEntity?.displayName || ''
  if (String(registryName).toLowerCase().includes('fireball')
    || String(entity?.name).toLowerCase().includes('fireball')) {
    const spawnPoint = new Vec3(packet.x, packet.y, packet.z)
    if (entity) {
      projectileOrigins.set(projectileIdentity(entity),
        nearestWave4ObeliskDisplay(spawnPoint)?.position || spawnPoint)
    }
    console.log(`ENTITY_PROJECTILE_PACKET ${username} id=${packet.entityId} packet_type=${packet.type} registry=${registryName} name=${entity?.name} pos=${packet.x},${packet.y},${packet.z}`)
  }
})
bot.on('message', message => {
  const text = message?.toString?.() || ''
  if (text.includes('END_RIFT_BOUNDARY_SYNC_HELD_ITEM')) syncHeldItem()
  if (text.includes('END_RIFT_PASSIVE')) enterPassiveMode()
  if (text.includes('END_RIFT_RESUME')) enterActiveMode(false)
})
bot.on('health', () => {
  if (previousHealth !== null && bot.health < previousHealth - 0.01) {
    console.log(`PLAYER_HURT ${username} before=${previousHealth.toFixed(2)} after=${bot.health.toFixed(2)}`)
  }
  previousHealth = bot.health
})
bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})
bot.on('end', () => {
  for (const timer of [sampleTimer, attackTimer, healthTimer, controlTimer, navigationTimer]) if (timer !== null) clearInterval(timer)
  if (heldItemSyncReturnTimer !== null) clearTimeout(heldItemSyncReturnTimer)
  if (reflectionScanTimer !== null) clearInterval(reflectionScanTimer)
  for (const timer of reflectionTimers) clearTimeout(timer)
  projectileOrigins.clear()
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attackCount} reflections=${reflectionCount} samples=${sampleCount}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
