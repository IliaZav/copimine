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
const configuredBossUuid = process.env.END_RIFT_BOSS_UUID || ''
const reflectEnabled = process.env.END_RIFT_REFLECT_ENABLED === '1'
const reflectStartDelayMs = Math.max(0, Number(process.env.END_RIFT_REFLECT_START_MS || 0))
const controlDirectory = process.argv[9] || process.env.END_RIFT_BOT_CONTROL_DIRECTORY || ''
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
const reflectTargetDistance = 5.75
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
let lastControlMode = ''
let previousHealth = null
let sampleCount = 0
let movedEntities = new Map()
let attackCount = 0
let reflectionCount = 0
const reflectedEntityIds = new Set()
const reflectionTimers = new Set()
const projectileOrigins = new Map()

function enterPassiveMode () {
  if (attackTimer !== null) {
    clearInterval(attackTimer)
    attackTimer = null
  }
  if (typeof bot.clearControlStates === 'function') bot.clearControlStates()
  console.log(`BOT_PASSIVE ${username}`)
}

function enterActiveMode () {
  if (attackTimer === null) {
    attackTimer = setInterval(attackNearest, attackIntervalMs)
  }
  console.log(`BOT_ACTIVE ${username}`)
}

function pollControlMode () {
  if (!controlFile) return
  let requestedMode
  try {
    requestedMode = fs.readFileSync(controlFile, 'utf8').trim().toUpperCase()
  } catch (_) {
    return
  }
  if (!['PASSIVE', 'ACTIVE'].includes(requestedMode) || requestedMode === lastControlMode) return
  lastControlMode = requestedMode
  if (requestedMode === 'PASSIVE') enterPassiveMode()
  else enterActiveMode()
}

function distance(a, b) {
  if (!a || !b) return Number.POSITIVE_INFINITY
  const dx = a.x - b.x
  const dy = a.y - b.y
  const dz = a.z - b.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
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
  const target = eventMobs()
    .filter(entity => distance(entity.position, bot.entity.position) <= 4.5)
    .sort((a, b) => distance(a.position, bot.entity.position) - distance(b.position, bot.entity.position))[0]
  if (!target) return
  bot.lookAt(target.position.offset(0, 0.8, 0), true).then(() => {
    const refreshed = bot.entities[target.id]
    if (!refreshed || distance(refreshed.position, bot.entity.position) > 4.5) return
    const selectSlot = typeof bot.setQuickBarSlot === 'function'
      ? bot.setQuickBarSlot(0)
      : undefined
    return Promise.resolve(selectSlot).catch(() => {}).then(() => {
      const finalTarget = bot.entities[target.id]
      if (!finalTarget || distance(finalTarget.position, bot.entity.position) > 4.5) return
      if (isGuardianHitbox(finalTarget)) {
        // Mineflayer intentionally refuses some non-LivingEntity targets in
        // bot.attack().  Interaction is the server-owned guardian hitbox, so
        // emit the same vanilla attack interaction explicitly.
        bot._client.write('use_entity', {
          target: finalTarget.id,
          mouse: 1,
          sneaking: false,
        })
        bot._client.write('arm_animation', { hand: 0 })
      } else {
        bot.attack(finalTarget)
      }
      attackCount += 1
      console.log(`PLAYER_ATTACK ${username} count=${attackCount} target=${finalTarget.id} type=${finalTarget.name} distance=${distance(finalTarget.position, bot.entity.position).toFixed(2)}`)
    })
  }).catch(error => console.error(`ATTACK_ERROR ${username} ${error.stack || error}`))
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
  bot.entity.yaw = yaw
  bot.entity.pitch = pitch
  const onGround = Boolean(bot.entity.onGround)
  bot._client.write('look', {
    yaw: Math.fround((Math.PI - yaw) * 180 / Math.PI),
    pitch: Math.fround(-pitch * 180 / Math.PI),
    onGround,
    flags: { onGround, hasHorizontalCollision: undefined },
  })
}

async function reflectFireball(entity) {
  if (!reflectEnabled || !bot.entity || !isRiftFireball(entity)
    || reflectedEntityIds.has(entity.id)) return false
  const current = bot.entities[entity.id]
  if (!current || !current.position
    || distance(current.position, bot.entity.position) > reflectTargetDistance) return false
  try {
    // The player must actually be looking at the projectile for the vanilla
    // attack ray to register.  Keep the first server spawn position as a
    // source anchor as well: after teleport sweeps, the projectile can be
    // observed a few frames off its original line.  Aiming at that real
    // server-provided anchor makes the reflected flight return to the source
    // obelisk without changing the packet or the server-side rule.
    const sourceAnchor = projectileOrigins.get(entity.id)
    const returnAim = sourceAnchor || current.position
    lookAtServer(current.position)
    await new Promise(resolve => setTimeout(resolve, 75))
    const refreshed = bot.entities[entity.id]
    if (!refreshed || distance(refreshed.position, bot.entity.position) > reflectTargetDistance) return false
    lookAtServer(returnAim)
    // Send the same serverbound attack interaction a vanilla player uses.
    // Do not call bot.attack here: Mineflayer's entity type filter can reject
    // LargeFireball before the packet is emitted.
    bot._client.write('use_entity', {
      target: refreshed.id,
      mouse: 1,
      sneaking: false,
    })
    bot._client.write('arm_animation', { hand: 0 })
    reflectedEntityIds.add(refreshed.id)
    reflectionCount += 1
    console.log(`PLAYER_REFLECT ${username} count=${reflectionCount} entity=${refreshed.id} target=projectile distance=${distance(refreshed.position, bot.entity.position).toFixed(2)}`)
    return true
  } catch (error) {
    console.error(`REFLECT_ERROR ${username} ${error.stack || error}`)
    return false
  }
}

function scheduleFireballReflection(entity) {
  if (!reflectEnabled || !isRiftFireball(entity) || reflectedEntityIds.has(entity.id)
    || reflectionTimers.has(entity.id)) return
  const timer = setTimeout(async () => {
    reflectionTimers.delete(entity.id)
    for (let attempt = 0; attempt < 25; attempt++) {
      if (await reflectFireball(entity)) return
      if (!bot.entities[entity.id]) return
      await new Promise(resolve => setTimeout(resolve, 100))
    }
  }, reflectStartDelayMs)
  reflectionTimers.add(entity.id)
}

bot._client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
})

bot.once('spawn', () => {
  joined = true
  console.log(`PLAYER_JOIN ${username} reflect_enabled=${reflectEnabled} reflect_targets=${obeliskTargets.length} guardian_probe=${guardianProbeEnabled}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) setTimeout(() => bot.chat('/login endrift-local'), delay)
  // Mineflayer's physics plugin already acknowledges server teleports and
  // sends the matching position packet.  A second 100 ms position loop fights
  // that controller, produces invalid-packet spam after RCON sweeps, and can
  // make GrimAC disconnect the local probe before it reaches outer spawns.
  sampleTimer = setInterval(sampleMobs, 250)
  pollControlMode()
  controlTimer = setInterval(pollControlMode, 100)
  // Keep a survival-like attack cadence while still reacting quickly enough
  // to the tower-defense wave's moving attackers.
  enterActiveMode()
  healthTimer = setInterval(() => {
    if (previousHealth !== null && bot.health < previousHealth - 0.01) {
      console.log(`PLAYER_HURT ${username} before=${previousHealth.toFixed(2)} after=${bot.health.toFixed(2)}`)
    }
    previousHealth = bot.health
  }, 100)
})

bot.on('entitySpawn', entity => {
  scheduleFireballReflection(entity)
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
    projectileOrigins.set(packet.entityId, new Vec3(packet.x, packet.y, packet.z))
    console.log(`ENTITY_PROJECTILE_PACKET ${username} id=${packet.entityId} packet_type=${packet.type} registry=${registryName} name=${entity?.name} pos=${packet.x},${packet.y},${packet.z}`)
  }
})
bot.on('message', message => {
  const text = message?.toString?.() || ''
  if (text.includes('END_RIFT_PASSIVE')) enterPassiveMode()
  if (text.includes('END_RIFT_RESUME')) enterActiveMode()
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
  for (const timer of [sampleTimer, attackTimer, healthTimer, controlTimer]) if (timer !== null) clearInterval(timer)
  for (const timer of reflectionTimers) clearTimeout(timer)
  projectileOrigins.clear()
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attackCount} reflections=${reflectionCount} samples=${sampleCount}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
