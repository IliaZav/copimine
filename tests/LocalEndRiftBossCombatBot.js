/*
 * Local-only survival combat probe.  It uses Mineflayer's real use_entity
 * attack path; it is never used by the launcher or by a production server.
 * The test runner prepares the bot with RCON, while this process supplies the
 * player-side packets and records the observed boss entity.
 */
const path = require('path')
const fs = require('fs')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'BossHitProbe'
const durationMs = Number(process.argv[3] || 45000)
const attackEveryMs = Number(process.env.END_RIFT_BOSS_ATTACK_EVERY_MS || 1100)
const attackDelayMs = Number(process.env.END_RIFT_BOSS_ATTACK_DELAY_MS || 9000)
const targetUuid = process.env.END_RIFT_BOSS_UUID || ''
const rawAttackPackets = process.env.END_RIFT_RAW_ATTACK !== '0'
const attackBarrierPath = process.env.END_RIFT_BOSS_ATTACK_BARRIER || ''
const attackBarrierTimeoutMs = Number(process.env.END_RIFT_BOSS_BARRIER_TIMEOUT_MS || 90000)
const authRetryDelaysMs = [500, 2000, 5000, 9000, 13000]

const bot = mineflayer.createBot({
  host,
  port,
  username,
  version: '1.21.1',
  auth: 'offline'
})

const traceAttackPackets = process.env.END_RIFT_TRACE_ATTACK_PACKETS === '1'
const originalClientWrite = bot._client.write.bind(bot._client)
bot._client.write = (name, data) => {
  if (traceAttackPackets && name === 'use_entity') {
    console.log(`USE_ENTITY_PACKET ${username} target=${data.target} mouse=${data.mouse} hand=${data.hand ?? 'none'} sneaking=${data.sneaking}`)
  }
  return originalClientWrite(name, data)
}

let spawned = false
let attackTimer = null
let followTimer = null
let attackCount = 0
let bossSeen = false
let targetMismatchLogged = false
let attackReleaseTimer = null
let attackReleaseTimeout = null
let durationTimer = null

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

function logTargetMismatch () {
  if (targetMismatchLogged || !bot.entity || !targetUuid) return
  const candidates = Object.values(bot.entities)
    .filter(entity => entity && entity.name === 'enderman')
    .filter(entity => entity.position && entity.position.distanceTo(bot.entity.position) <= 40)
  if (candidates.length === 0) return
  targetMismatchLogged = true
  console.log(`BOSS_TARGET_MISMATCH ${username} expected=${targetUuid} candidates=${candidates.map(entity => `${entity.id}:${entity.uuid}`).join(',')}`)
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

function startFollowing () {
  if (followTimer !== null || typeof bot.setControlState !== 'function') return
  followTimer = setInterval(followBoss, 250)
  followBoss()
}

function tryAttack () {
  if (!bot.entity) return
  const boss = bossEntity()
  if (!boss) {
    logTargetMismatch()
    return
  }
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
          hand: 0,
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

function startAttacking () {
  if (attackTimer !== null) return
  armDurationTimer()
  attackTimer = setInterval(tryAttack, attackEveryMs)
  startFollowing()
  tryAttack()
  console.log(`ATTACK_RELEASED ${username}`)
}

function armDurationTimer () {
  if (durationTimer !== null) return
  // The PowerShell runner opens the barrier only after every independent
  // client has authenticated, received gear and been teleported.  Start the
  // combat window then; otherwise a five-player setup can consume the whole
  // duration before the first attack packet is intentionally released.
  durationTimer = setTimeout(() => bot.quit(), durationMs)
}

function releaseAttacksAfterBarrier () {
  if (!attackBarrierPath) {
    attackReleaseTimeout = setTimeout(startAttacking, attackDelayMs)
    return
  }

  const startedAt = Date.now()
  attackReleaseTimer = setInterval(() => {
    if (!fs.existsSync(attackBarrierPath)) {
      if (Date.now() - startedAt > attackBarrierTimeoutMs) {
        console.error(`ATTACK_BARRIER_TIMEOUT ${username} path=${attackBarrierPath}`)
        clearInterval(attackReleaseTimer)
        attackReleaseTimer = null
        process.exitCode = 1
        bot.quit()
      }
      return
    }
    clearInterval(attackReleaseTimer)
    attackReleaseTimer = null
    attackReleaseTimeout = setTimeout(startAttacking, attackDelayMs)
  }, 20)
}

bot.once('spawn', () => {
  spawned = true
  console.log(`PLAYER_JOIN ${username} entityId=${bot.entity?.id ?? 'unknown'}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of authRetryDelaysMs) {
    setTimeout(() => bot.chat('/login endrift-local'), delay)
  }
  // AuthMe may process simultaneous local handshakes on different scheduler
  // callbacks.  These sparse, bounded retries cover that race without
  // tripping the server's chat-spam guard.
  // A live runner may need to finish RCON login, inventory and teleport setup
  // after all independent clients are connected.  Do not send a combat packet
  // until the runner opens the shared barrier; this keeps the probe from
  // measuring pre-teleport packets or AuthMe's unauthenticated join window.
  releaseAttacksAfterBarrier()
})

bot.on('entitySpawn', entity => {
  if (entity?.name === 'enderman') {
    console.log(`ENTITY_SPAWN ${username} id=${entity.id} uuid=${entity.uuid} pos=${entity.position.x},${entity.position.y},${entity.position.z}`)
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
  if (attackReleaseTimer !== null) clearInterval(attackReleaseTimer)
  if (attackReleaseTimeout !== null) clearTimeout(attackReleaseTimeout)
  if (durationTimer !== null) clearTimeout(durationTimer)
  stopFollowing()
  if (!spawned || attackCount === 0) process.exitCode = 1
  console.log(`PLAYER_END ${username} attacks=${attackCount} bossSeen=${bossSeen}`)
  process.exit()
})
