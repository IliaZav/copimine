/* Passive local players for a human-operated spell check. No attacks, login
 * credentials, resistance, teleport loop, or simulated rendering are used.
 * Mineflayer physics accepts the real server's velocity and effect packets.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))
const username = process.argv[2]
if (!/^EndRiftTarget[1-4]$/.test(username || '')) throw new Error('Use a dedicated EndRiftTarget1..4 local test account')
const durationMs = Math.min(4 * 60 * 60 * 1000, Math.max(60000, Number(process.argv[3]) || 2 * 60 * 60 * 1000))
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username, version: '1.21.1', auth: 'offline' })
function log(event, details = {}) {
  console.log(JSON.stringify({ time: new Date().toISOString(), player: username, event, ...details }))
}
function vector(value) {
  return value ? { x: Number(value.x.toFixed(4)), y: Number(value.y.toFixed(4)), z: Number(value.z.toFixed(4)) } : null
}
let lastHealth
let timer
let ended = false
bot.on('spawn', () => {
  log('SPAWN', { entity: bot.entity?.id, position: vector(bot.entity?.position) })
  if (!timer) timer = setInterval(() => {
    log('STATE', { health: bot.health, food: bot.food, position: vector(bot.entity?.position), velocity: vector(bot.entity?.velocity), effects: bot.entity?.effects || {} })
  }, 2000)
})
bot.on('health', () => {
  log('HEALTH', { before: lastHealth ?? null, after: bot.health, lost: lastHealth == null ? null : lastHealth - bot.health, food: bot.food })
  lastHealth = bot.health
})
bot.on('entityEffect', (entity, effect) => {
  if (entity?.id === bot.entity?.id) log('EFFECT_APPLIED', { effect })
})
bot.on('entityEffectEnd', (entity, effect) => {
  if (entity?.id === bot.entity?.id) log('EFFECT_ENDED', { effect })
})
bot.on('entityHurt', entity => {
  if (entity?.id === bot.entity?.id) log('HURT_FEEDBACK')
})
bot.on('death', () => log('DEATH'))
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'add_resource_pack') {
    // This protocol-only target cannot render or validate the resource pack.
    // The real HTTP download is verified by VerifyEndRiftLocalResourcePack.ps1.
    log('PACK_OFFER', { hash: data.hash, rendered: false })
    bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
  if (meta.name === 'entity_velocity' && data.entityId === bot.entity?.id) log('SERVER_VELOCITY', { packet: data })
  if (meta.name === 'damage_event' && data.entityId === bot.entity?.id) log('SERVER_DAMAGE', { packet: data })
  if (meta.name === 'custom_payload' && String(data.channel).startsWith('copimine')) log('EVENT_PAYLOAD', { channel: data.channel, bytes: data.data?.length || 0 })
  if (meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') log('SOUND_PACKET', { packet: data })
})
bot.on('kicked', reason => log('KICKED', { reason }))
bot.on('error', error => { log('ERROR', { message: error.message }); process.exitCode = 1 })
bot.on('end', reason => {
  ended = true
  clearInterval(timer)
  log('END', { reason })
  process.exit()
})
setTimeout(() => { if (!ended) bot.quit('Local spell-target session finished') }, durationMs)
