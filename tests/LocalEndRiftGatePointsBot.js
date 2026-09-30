/*
 * Local-only player probe for /cmend gate pos1|pos2.  It aims at two real
 * blocks from a nearby position and sends the commands as a player, so the
 * plugin must use the server-side crosshair target rather than the player's
 * feet block.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))
const { Vec3 } = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'vec3'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'EndRiftGatePointsProbe'
const durationMs = Number(process.argv[3] || 30000)
const core = new Vec3(
  Number(process.argv[4] || 8),
  Number(process.argv[5] || 68),
  Number(process.argv[6] || -39)
)

const bot = mineflayer.createBot({ host, port, username, version: '1.21.1', auth: 'offline' })
let spawned = false
let commandsSent = false
let targetTwo = null

function fail(message) {
  console.error(`GATE_POINTS_ERROR ${username} ${message}`)
  process.exitCode = 1
  try { bot.quit() } catch (_) {}
}

function isSolid(block) {
  return Boolean(block && !block.isAir && block.boundingBox === 'block')
}

function findSecondTarget() {
  const candidates = []
  for (let x = core.x - 4; x <= core.x + 4; x += 1) {
    for (let y = core.y - 2; y <= core.y + 2; y += 1) {
      for (let z = core.z - 4; z <= core.z + 4; z += 1) {
        if (x === core.x && y === core.y && z === core.z) continue
        const block = bot.blockAt(new Vec3(x, y, z))
        if (!isSolid(block)) continue
        const distance = bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5))
        if (distance <= 7.5) candidates.push({ block, distance })
      }
    }
  }
  candidates.sort((left, right) => left.distance - right.distance)
  return candidates[0]?.block || null
}

async function aimAndCommand(block, index) {
  if (!block || !bot.entity) throw new Error(`pos${index}_target_missing`)
  const position = block.position || block
  const worldBlock = bot.blockAt(position)
  await bot.lookAt(position.offset(0.5, 0.5, 0.5), false)
  await new Promise(resolve => setTimeout(resolve, 450))
  console.log(`GATE_TARGET_${index} ${username} ${position.x},${position.y},${position.z} block=${worldBlock?.name || block.name || 'unknown'}`)
  bot.chat(`/cmend gate pos${index}`)
  await new Promise(resolve => setTimeout(resolve, 900))
}

bot._client.on('packet', (data, meta) => {
  if (meta?.name !== 'add_resource_pack') return
  console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
  bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
})

bot.on('messagestr', message => {
  const text = String(message).replace(/§./g, '')
  if (text.includes('Gate') || text.includes('gate') || text.includes('сохранена') || text.includes('Сначала')
      || text.includes('Недостаточно') || text.includes('Наведи') || text.includes('реальный')
      || text.includes('не сохран')) {
    console.log(`GATE_MESSAGE ${username} ${text}`)
  }
})

bot.once('spawn', () => {
  spawned = true
  console.log(`PLAYER_JOIN ${username}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => bot.chat('/login endrift-local'), delay)
  }
  setTimeout(async () => {
    if (!bot.entity || commandsSent) return
    try {
      targetTwo = findSecondTarget()
      if (!targetTwo) throw new Error('second_solid_target_missing')
      console.log(`GATE_TARGET_1 ${username} ${core.x},${core.y},${core.z} block=${bot.blockAt(core)?.name || 'missing'}`)
      await aimAndCommand(core, '1')
      await aimAndCommand(targetTwo, '2')
      commandsSent = true
      console.log(`GATE_POINTS_COMMANDS_SENT ${username}`)
    } catch (error) {
      fail(error.stack || error)
    }
  }, 9000)
})

bot.on('error', error => fail(`bot_error ${error.stack || error}`))
bot.on('end', () => {
  if (!spawned || !commandsSent) process.exitCode = 1
  console.log(`PLAYER_END ${username} spawned=${spawned} commandsSent=${commandsSent}`)
  process.exit()
})

setTimeout(() => {
  if (!commandsSent) fail('commands_timeout')
  else bot.quit()
}, durationMs)
