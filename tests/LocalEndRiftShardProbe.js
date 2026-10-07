/*
 * Local-only live probe for the authenticated Rift Core Shard.
 *
 * The probe deliberately obtains the item through the existing
 * CopiMineArtifacts admin-gift queue. It never fabricates PDC values and it
 * never calls a server-side teleport/damage shortcut from the client.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'ShardProbe'
const durationMs = Number(process.argv[3] || 90000)
const adminGrant = process.argv[4] === 'admin-grant'
const bot = mineflayer.createBot({ host, port, username, version: '1.21.1', auth: 'offline' })

function offlineUuid(name) {
  const crypto = require('crypto')
  const bytes = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest()
  bytes[6] = (bytes[6] & 0x0f) | 0x30
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = bytes.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

let joined = false
let commandTimer = null
let shardActivated = false

function heldItemSummary() {
  const item = bot.heldItem
  if (!item) return 'empty'
  return `${item.slot}:${item.name}:${item.count}`
}

function chat(command) {
  if (!bot || !bot.chat) return
  console.log(`BOT_COMMAND ${username} ${command}`)
  bot.chat(command)
}

function logInventory() {
  const items = typeof bot.inventory?.items === 'function' ? bot.inventory.items() : []
  const summary = items.map(item => {
    let nbt = ''
    try { nbt = JSON.stringify(item.nbt || '') } catch (_) { nbt = '' }
    return `${item.slot}:${item.name}:${nbt}`
  }).join('|')
  console.log(`INVENTORY ${username} ${summary}`)
}

bot._client.on('packet', (data, meta) => {
  if (meta?.name === 'add_resource_pack') {
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
})

bot.once('spawn', () => {
  joined = true
  const uuid = bot.entity?.uuid || bot.player?.uuid || bot._client?.uuid || offlineUuid(username)
  console.log(`PLAYER_JOIN ${username} uuid=${uuid}`)
  chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000, 10000, 16000, 24000]) {
    setTimeout(() => chat('/login endrift-local'), delay)
  }
  // The PowerShell driver issues the admin grant from the local console. The
  // bot only claims the queued delivery after AuthMe accepts the session.
  const grantAt = 14000
  if (adminGrant) setTimeout(() => chat(`/cmartifacts admin give ${username} rift_core_shard`), grantAt)
  for (const delay of [grantAt, grantAt + 7000, grantAt + 15000, grantAt + 26000]) {
    setTimeout(() => {
      chat('/cmartifacts claim')
      setTimeout(logInventory, 2500)
    }, delay)
  }
})

bot.on('message', message => {
  const text = message?.toString?.() || ''
  if (text) console.log(`BOT_MESSAGE ${username} ${text}`)
  if (text.includes('Отложенная выдача получена') && !shardActivated) {
    shardActivated = true
    setTimeout(() => {
      try {
        // The delivered shard is in hotbar slot 4 (inventory slot 40).  Use
        // the normal player interaction path so the live probe exercises the
        // real channel teleport rather than an RCON dimension shortcut.
        bot.setQuickBarSlot(4)
        bot.updateHeldItem?.()
        console.log(`BOT_HELD_ITEM ${username} ${heldItemSummary()}`)
        // The item may arrive in the same client tick as the inventory
        // acknowledgement.  Retry only until the authoritative hotbar
        // snapshot contains the shard, then send the normal use packet.
        const activateWhenHeld = () => {
          if (shardActivated !== true || !bot.heldItem || bot.heldItem.name !== 'echo_shard') {
            setTimeout(activateWhenHeld, 250)
            return
          }
          // Force the held-slot packet even if Mineflayer already believes
          // slot 4 is selected.  Then use the real block-interaction packet
          // against the solid block under the player; this exercises the
          // server's RIGHT_CLICK_BLOCK path without a command teleport.
          bot._client.write('held_item_slot', { slotId: 4 })
          bot.quickBarSlot = 4
          bot.updateHeldItem?.()
          console.log(`BOT_ACTION ${username} activate-rift-shard held=${heldItemSummary()}`)
          const floor = bot.blockAt(bot.entity.position.floored().offset(0, -1, 0))
          if (floor) {
            bot.activateBlock(floor).then(() => {
              console.log(`BOT_ACTION ${username} shard-block-interaction-sent block=${floor.name}`)
            }).catch(error => {
              console.error(`BOT_ERROR ${username} shard block interaction failed: ${error.stack || error}`)
              bot.activateItem()
            })
          } else {
            bot.activateItem()
          }
        }
        activateWhenHeld()
      } catch (error) {
        console.error(`BOT_ERROR ${username} shard activation failed: ${error.stack || error}`)
        process.exitCode = 1
      }
    }, 1500)
  }
})

bot.on('error', error => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})

bot.on('end', () => {
  if (commandTimer !== null) clearTimeout(commandTimer)
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username}`)
  process.exit()
})

commandTimer = setTimeout(() => bot.quit(), durationMs)
