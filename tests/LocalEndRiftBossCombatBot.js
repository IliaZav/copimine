/*
 * Local-only survival combat probe.  It uses Mineflayer's real use_entity
 * attack path; it is never used by the launcher or by a production server.
 * The test runner prepares the bot with RCON, while this process supplies the
 * player-side packets and records the observed boss entity.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'BossHitProbe'
const durationMs = Number(process.argv[3] || 45000)
const attackEveryMs = Number(process.env.END_RIFT_BOSS_ATTACK_EVERY_MS || 1100)
const attackDelayMs = Number(process.env.END_RIFT_BOSS_ATTACK_DELAY_MS || 9000)
const targetUuid = process.env.END_RIFT_BOSS_UUID || ''
const rawAttackPackets = process.env.END_RIFT_RAW_ATTACK !== '0'

const bot = mineflayer.createBot({
  host,
  port,
  username,
  version: '1.21.1',
  auth: 'offline'
})

let spawned = false
let attackTimer = null
let followTimer = null
let attackCount = 0
let bossSeen = false

bot._client.on('packet', (data, meta) => {
  if (meta?.name !== 'add_resource_pack') return
  console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
  bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
})

function bossEntity () {
  const entities = Object.values(bot.entities)
  return entities
    .filter(entity => entity && entity.name === 'enderman')
    .filter(entity => !targetUuid || entity.uuid === targetUuid)
    .filter(entity => bot.entity && entity.position.distanceTo(bot.entity.position) <= 40)
    .sort((first, second) => first.position.distanceTo(bot.entity.position)
      - second.position.distanceTo(bot.entity.position))[0]
}

function stopFollowing () {
  if (typeof bot.setControlState !== 'function') return
  for (const control of ['forward', 'back', 'left', 'right', 'sprint']) {
    bot.setControlState(control, false)
  }
}

function followBoss () {
  if (!bot.entity || typeof bot.setControlState !== 'function') return
  const boss = bossEntity()
  if (!boss) {
    stopFollowing()
    return
  }
  const distance = bot.entity.position.distanceTo(boss.position)
  if (distance > 3.0) {
    bot.setControlState('back', false)
    bot.setControlState('forward', true)
    bot.setControlState('sprint', distance > 5.0)
  } else if (distance < 1.8) {
    bot.setControlState('forward', false)
    bot.setControlState('sprint', false)
    bot.setControlState('back', true)
  } else {
    bot.setControlState('forward', false)
    bot.setControlState('back', false)
    bot.setControlState('sprint', false)
  }
  bot.lookAt(boss.position.offset(0, 1.0, 0), true).catch(error => {
    console.error(`FOLLOW_ERROR ${username} ${error.stack || error}`)
  })
}

function tryAttack () {
  if (!bot.entity) return
  const boss = bossEntity()
  if (!boss) return
  if (!bossSeen) {
    bossSeen = true
    console.log(`BOSS_ENTITY ${username} id=${boss.id} pos=${boss.position.x},${boss.position.y},${boss.position.z}`)
  }
  bot.lookAt(boss.position.offset(0, 1.2, 0), true).then(() => {
    // The boss can move during lookAt's asynchronous turn. Re-resolve the
    // entity and measure reach immediately before the use_entity packet so a
    // stale client-side position cannot turn a real player hit into a miss.
    const refreshedBoss = bossEntity()
    if (!refreshedBoss || refreshedBoss.id !== boss.id) return
    const refreshedDistance = bot.entity.position.distanceTo(refreshedBoss.position)
    if (refreshedDistance > 4.5) {
      console.log(`BOSS_OUT_OF_REACH ${username} distance=${refreshedDistance.toFixed(2)}`)
      return
    }
    const selectSlot = typeof bot.setQuickBarSlot === 'function'
      ? bot.setQuickBarSlot(0)
      : undefined
    return Promise.resolve(selectSlot).catch(() => {}).then(() => {
      const attackTarget = bossEntity()
      if (!attackTarget || attackTarget.id !== refreshedBoss.id) return
      const finalDistance = bot.entity.position.distanceTo(attackTarget.position)
      if (finalDistance > 4.5) {
        console.log(`BOSS_OUT_OF_REACH ${username} distance=${finalDistance.toFixed(2)}`)
        return
      }
      if (rawAttackPackets) {
        // Mineflayer's high-level helper is useful for ordinary probes, but
        // the multi-player contract must make the exact serverbound attack
        // packet explicit so a helper-side cooldown cannot hide a hit.
        bot._client.write('use_entity', {
          target: attackTarget.id,
          mouse: 1,
          sneaking: false
        })
        bot._client.write('arm_animation', { hand: 0 })
      } else {
        bot.attack(attackTarget)
      }
      attackCount += 1
      console.log(`PLAYER_ATTACK ${username} count=${attackCount} bossId=${attackTarget.id} distance=${finalDistance.toFixed(2)} raw=${rawAttackPackets}`)
    })
  }).catch(error => {
    console.error(`ATTACK_ERROR ${username} ${error.stack || error}`)
  })
}

bot.once('spawn', () => {
  spawned = true
  console.log(`PLAYER_JOIN ${username}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => bot.chat('/login endrift-local'), delay)
  }
  // The real boss deliberately changes position between attacks.  Keep this
  // survival client moving toward the current server entity instead of
  // turning a mobile-boss damage check into a static-coordinate check.
  followTimer = setInterval(followBoss, 250)
  followBoss()
  setTimeout(() => {
    attackTimer = setInterval(tryAttack, attackEveryMs)
    tryAttack()
  }, attackDelayMs)
})

bot.on('entitySpawn', entity => {
  if (entity?.name === 'enderman') {
    console.log(`ENTITY_SPAWN ${username} id=${entity.id} pos=${entity.position.x},${entity.position.y},${entity.position.z}`)
  }
})

bot.on('death', () => console.log(`DEATH_EVENT ${username}`))
bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})
bot.on('end', () => {
  if (attackTimer !== null) clearInterval(attackTimer)
  if (followTimer !== null) clearInterval(followTimer)
  stopFollowing()
  if (!spawned || attackCount === 0) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attackCount} bossSeen=${bossSeen}`)
  process.exit()
})

setTimeout(() => bot.quit(), durationMs)
