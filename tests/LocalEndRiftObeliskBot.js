/*
 * Local-only Rift Obelisk player probe.  The client uses a real player
 * connection and real use_entity attack packets to reflect event-owned
 * LargeFireballs.  The runner positions the player on the Core ring; this
 * script never uses a server-side damage or reflection shortcut.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'ObeliskProbe'
const durationMs = Number(process.argv[3] || 35000)
const reflectStartMs = Number(process.env.END_RIFT_REFLECT_START_MS || 9000)
const reflectAfterFireballs = Math.max(0, Number(process.env.END_RIFT_REFLECT_AFTER_FIREBALLS || 0))
const reflectEnabled = process.env.END_RIFT_REFLECT_ENABLED !== '0'
const skipAuthChat = process.env.END_RIFT_SKIP_AUTH_CHAT === '1'
const targetPosition = {
  x: Number(process.env.END_RIFT_OBELISK_X),
  y: Number(process.env.END_RIFT_OBELISK_Y),
  z: Number(process.env.END_RIFT_OBELISK_Z)
}
const bot = mineflayer.createBot({ host, port, username, version: '1.21.1', auth: 'offline' })

let joined = false
let fireballsSeen = 0
let obelisksSeen = 0
let reflections = 0
let sampleTimer = null
let reflectTimer = null
const attempted = new Set()
const seenFireballIds = new Set()

function distance(first, second) {
  if (!first || !second) return Number.POSITIVE_INFINITY
  const dx = first.x - second.x
  const dy = first.y - second.y
  const dz = first.z - second.z
  return Math.sqrt(dx * dx + dy * dy + dz * dz)
}

function entityName(entity) {
  return String(entity?.name || entity?.mobType || entity?.type || '').toLowerCase()
}

function isRiftFireball(entity) {
  const name = entityName(entity)
  return name.includes('fireball')
}

function isDisplay(entity) {
  const name = entityName(entity)
  return name.includes('item_display') || name === 'display'
}

function nearbyFireballs() {
  if (!bot.entity) return []
  return Object.values(bot.entities)
    .filter(isRiftFireball)
    .filter(entity => entity.position && distance(entity.position, bot.entity.position) <= 4.8)
}

function nearbyObelisks() {
  return Object.values(bot.entities).filter(isDisplay).filter(entity => entity.position)
}

function lookAtServer(point) {
  if (!bot.entity || !point) return
  const eye = bot.entity.position.offset(0, bot.entity.eyeHeight, 0)
  const delta = point.minus(eye)
  const groundDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z)
  const yaw = Math.atan2(-delta.x, -delta.z)
  const pitch = Math.atan2(delta.y, groundDistance)
  // Mineflayer's non-forced look waits for its physics loop to emit the
  // packet. A stationary probe can leave that promise pending, while a
  // forced look changes only the local model until a later physics tick. Use
  // the same serverbound packet as Mineflayer's physics plugin, immediately.
  bot.entity.yaw = yaw
  bot.entity.pitch = pitch
  const onGround = Boolean(bot.entity.onGround)
  bot._client.write('look', {
    yaw: Math.fround((Math.PI - yaw) * 180 / Math.PI),
    pitch: Math.fround(-pitch * 180 / Math.PI),
    onGround,
    flags: { onGround, hasHorizontalCollision: undefined }
  })
}

function writeAttack(entity) {
  if (!entity || attempted.has(entity.id)) return
  attempted.add(entity.id)
  const target = nearbyObelisks()
    .sort((first, second) => distance(first.position, entity.position) - distance(second.position, entity.position))[0]
  const configuredTarget = [targetPosition.x, targetPosition.y, targetPosition.z].every(Number.isFinite)
    ? targetPosition
    : null
  const lookAt = configuredTarget
    ? entity.position.offset(configuredTarget.x - entity.position.x,
      configuredTarget.y - entity.position.y, configuredTarget.z - entity.position.z)
    : (target?.position || entity.position)
  // Wait until the real client look packet has been sent.  A forced
  // Mineflayer look only updates its local model and can leave the server's
  // Player#getEyeLocation direction unchanged when use_entity follows in the
  // same callback.
  lookAtServer(lookAt.offset(0, 0.65, 0))
  setTimeout(() => {
    const refreshed = bot.entities[entity.id]
    if (!refreshed || !bot.entity || distance(refreshed.position, bot.entity.position) > 4.8) return
    // The movement plugin may emit an interpolation packet between the first
    // aim and this callback. Repeat the real look packet immediately before
    // use_entity so the server uses this exact reflected direction.
    lookAtServer(lookAt.offset(0, 0.65, 0))
    // Sending the same use_entity + arm_animation pair as a vanilla melee
    // client keeps the reflection path independent of Mineflayer's mob-only
    // attack helper.
    bot._client.write('use_entity', {
      target: refreshed.id,
      mouse: 1,
      sneaking: false
    })
    bot._client.write('arm_animation', { hand: 0 })
    reflections += 1
    console.log(`RIFT_FIREBALL_REFLECT_ATTEMPT ${username} count=${reflections} entityId=${refreshed.id} facing=${target ? target.id : 'none'}`)
  }, 75)
}

function sample() {
  const fireballs = nearbyFireballs()
  const displays = nearbyObelisks()
  if (fireballs.length > 0) fireballsSeen += fireballs.length
  if (displays.length > 0) obelisksSeen = Math.max(obelisksSeen, displays.length)
  const now = Date.now()
  if (!reflectEnabled || now - startedAt < reflectStartMs) return
  if (seenFireballIds.size < reflectAfterFireballs) return
  for (const fireball of fireballs) writeAttack(fireball)
}

let startedAt = Date.now()

bot._client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
})

bot.once('spawn', () => {
  joined = true
  startedAt = Date.now()
  console.log(`PLAYER_JOIN ${username}`)
  if (skipAuthChat) {
    console.log(`AUTH_CHAT_SKIPPED ${username}`)
  } else {
    bot.chat('/register endrift-local endrift-local')
    // AuthMe may queue a database lookup when many real clients start at once.
    // Retry login during the normal server login window so a slow local DB does
    // not turn the obelisk load probe into a false network failure.
    for (const delay of [1000, 3000, 6000, 10000, 16000, 24000, 32000, 44000]) {
      setTimeout(() => bot.chat('/login endrift-local'), delay)
    }
  }
  sampleTimer = setInterval(sample, 100)
})

bot.on('entitySpawn', entity => {
  if (isRiftFireball(entity)) {
    seenFireballIds.add(entity.id)
    fireballsSeen += 1
    console.log(`RIFT_FIREBALL_SEEN ${username} id=${entity.id} name=${entityName(entity)} pos=${entity.position?.x},${entity.position?.y},${entity.position?.z}`)
    // The entity-spawn packet is the first authoritative client-side point
    // at which the projectile is guaranteed to be present.  Schedule one
    // immediate real attack check in addition to the sampler; this avoids
    // missing a short-lived projectile between two 100 ms samples while
    // keeping the reflection itself on the normal use_entity path.
    setTimeout(() => {
      if (!reflectEnabled || Date.now() - startedAt < reflectStartMs
        || seenFireballIds.size < reflectAfterFireballs) return
      writeAttack(entity)
    }, 50)
  } else if (isDisplay(entity)) {
    obelisksSeen += 1
    console.log(`RIFT_DISPLAY_SEEN ${username} id=${entity.id} name=${entityName(entity)} pos=${entity.position?.x},${entity.position?.y},${entity.position?.z}`)
  }
})

bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})

bot.on('end', () => {
  for (const timer of [sampleTimer, reflectTimer]) if (timer !== null) clearInterval(timer)
  if (!joined || fireballsSeen === 0 || reflectEnabled && reflections === 0) process.exitCode = 1
  console.log(`PLAYER_END ${username} fireballs=${fireballsSeen} displays=${obelisksSeen} reflections=${reflections} reflect_enabled=${reflectEnabled}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
