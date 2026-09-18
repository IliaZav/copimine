/*
 * Local-only player-side regression probe for the operator Core-removal GUI.
 * It attacks the real Core ItemDisplay as a client would, waits for the
 * confirmation inventory, and clicks the real confirm slot.  The block-dig
 * fallback keeps the probe useful when the overlay failed to spawn.
 */
const path = require('path')
const mineflayer = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'mineflayer'))
const { Vec3 } = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'vec3'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'EndRiftGuiOpProbe'
const durationMs = Number(process.argv[3] || 45000)
const corePosition = {
  x: Number(process.argv[4] || 8),
  y: Number(process.argv[5] || 68),
  z: Number(process.argv[6] || -39)
}

const bot = mineflayer.createBot({ host, port, username, version: '1.21.1', auth: 'offline' })
let spawned = false
let digStarted = false
let digFallbackSent = false
let guiOpened = false
let confirmClicked = false
let finishTimer = null

function stopWithFailure(message) {
  console.error(`GUI_REMOVAL_ERROR ${username} ${message}`)
  process.exitCode = 1
  try { bot.quit() } catch (_) {}
}

function titleText(window) {
  if (!window) return ''
  if (typeof window.title === 'string') return window.title
  return JSON.stringify(window.title || '')
}

function isCoreDisplay(entity) {
  if (!entity || !entity.position || entity.name !== 'item_display') return false
  return entity.position.x >= corePosition.x && entity.position.x < corePosition.x + 1
    && entity.position.y >= corePosition.y + 0.5 && entity.position.y <= corePosition.y + 1.5
    && entity.position.z >= corePosition.z && entity.position.z < corePosition.z + 1
}

function findCoreDisplay() {
  return Object.values(bot.entities)
    .filter(isCoreDisplay)
    .sort((left, right) => left.position.distanceTo(bot.entity.position) - right.position.distanceTo(bot.entity.position))[0]
}

function sendCoreBlockDig(block) {
  if (!block || digFallbackSent || guiOpened || !bot.entity) return
  digFallbackSent = true
  console.log(`CORE_REMOVAL_GUI_DIG_FALLBACK ${username} block=${block.name} pos=${corePosition.x},${corePosition.y},${corePosition.z}`)
  bot._client.write('block_dig', {
    status: 0,
    location: block.position,
    face: 1,
    sequence: 0
  })
  setTimeout(() => {
    if (bot.entity && !guiOpened) {
      bot._client.write('block_dig', {
        status: 2,
        location: block.position,
        face: 1,
        sequence: 0
      })
    }
  }, 250)
}

bot._client.on('packet', (data, meta) => {
  if (meta?.name !== 'add_resource_pack') return
  console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
  bot._client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
})

bot.once('spawn', () => {
  spawned = true
  console.log(`PLAYER_JOIN ${username}`)
  bot.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => bot.chat('/login endrift-local'), delay)
  }
  setTimeout(async () => {
    try {
      if (!bot.entity || digStarted) return
      const block = bot.blockAt(new Vec3(corePosition.x, corePosition.y, corePosition.z))
      if (!block) {
        stopWithFailure(`core_block_missing ${corePosition.x},${corePosition.y},${corePosition.z}`)
        return
      }
      const coreDisplay = findCoreDisplay()
      console.log(`PLAYER_POSITION ${username} pos=${bot.entity.position.toString()} distance=${bot.entity.position.distanceTo(block.position.offset(0.5, 0.5, 0.5)).toFixed(2)} displays=${Object.values(bot.entities).filter(entity => entity.name === 'item_display').length}`)
      if (coreDisplay) {
        // force=true only changes Mineflayer's local pose.  The Paper server
        // must receive the look packet as well, otherwise its ray trace still
        // points at the old spawn direction and PlayerAnimationEvent cannot
        // identify the Core under the crosshair.
        await bot.lookAt(coreDisplay.position, false)
        digStarted = true
        console.log(`CORE_REMOVAL_GUI_ATTACK ${username} entity=${coreDisplay.id} pos=${coreDisplay.position.toString()}`)
        bot.attack(coreDisplay)
        // Some Paper builds do not dispatch entity damage for display
        // entities. A real player then continues into the protected block,
        // so exercise that same fallback instead of declaring a false pass.
        setTimeout(() => sendCoreBlockDig(block), 700)
        return
      }
      await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), false)
      await new Promise(resolve => setTimeout(resolve, 350))
      digStarted = true
      console.log(`CORE_REMOVAL_GUI_DIG ${username} block=${block.name} pos=${corePosition.x},${corePosition.y},${corePosition.z}`)
      sendCoreBlockDig(block)
    } catch (error) {
      stopWithFailure(`dig_failed ${error.stack || error}`)
    }
  }, 9000)
})

bot.on('windowOpen', window => {
  const title = titleText(window)
  if (!title.toLowerCase().includes('core') && !title.toLowerCase().includes('снят')) return
  guiOpened = true
  console.log(`CORE_REMOVAL_GUI_OPEN ${username} title=${title} type=${window.type} slots=${window.slots.length}`)
  setTimeout(() => {
    if (confirmClicked) return
    bot.clickWindow(11, 0, 0).then(() => {
      confirmClicked = true
      console.log(`CORE_REMOVAL_GUI_CLICK ${username} slot=11 action=confirm`)
    }).catch(error => stopWithFailure(`confirm_click_failed ${error.stack || error}`))
  }, 250)
})

bot.on('error', error => stopWithFailure(`bot_error ${error.stack || error}`))
bot.on('end', () => {
  if (finishTimer !== null) clearTimeout(finishTimer)
  if (!spawned || !guiOpened || !confirmClicked) process.exitCode = 1
  console.log(`PLAYER_END ${username} spawned=${spawned} guiOpened=${guiOpened} confirmClicked=${confirmClicked}`)
  process.exit()
})

finishTimer = setTimeout(() => {
  if (!guiOpened) stopWithFailure('confirmation_gui_not_opened')
  else if (!confirmClicked) stopWithFailure('confirmation_slot_not_clicked')
  else bot.quit()
}, durationMs)
