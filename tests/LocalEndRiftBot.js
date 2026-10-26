/*
 * Local-only protocol smoke client.  It talks to the isolated Paper copy on
 * 25566 and is intentionally not part of the launcher, website, or plugin
 * runtime.  The npm dependency is installed under ignored local-runtime.
 */
const path = require('path')
const { resolveLocalEndRiftEndpoint } = require('./LocalEndRiftEndpoint')
const protocolRoot = process.env.END_RIFT_BOT_PROTOCOL_ROOT ||
  path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'minecraft-protocol')
const mc = require(protocolRoot)

const { host, port } = resolveLocalEndRiftEndpoint()
const username = process.argv[2] || 'EndRiftBot'
const durationMs = Number(process.argv[3] || 15000)
const actionDelayMs = Number(process.env.END_RIFT_BOT_ACTION_DELAY_MS || 9000)
const environmentActions = (process.env.END_RIFT_BOT_ACTIONS || '')
  .split('|')
  .map(value => value.trim())
  .filter(Boolean)
const cliActions = process.argv.slice(4).map(value => value.trim()).filter(Boolean)
const actions = [...environmentActions, ...cliActions]

const client = mc.createClient({
  host,
  port,
  username,
  version: process.env.END_RIFT_BOT_VERSION || '1.21.1',
  auth: 'offline',
  disableChatSigning: true,
  // The phase harness owns the reply explicitly below.  Keeping a single
  // handler avoids duplicate keep-alive packets on long local runs.
  keepAlive: false,
  clientSettings: {
    locale: 'en_us',
    viewDistance: 8,
    chatFlags: 0,
    chatColors: true,
    skinParts: 127,
    mainHand: 1,
    enableTextFiltering: false,
    enableServerListing: true
  }
})

const sessionEpoch = Date.now()
let joined = false
const originalWrite = client.write.bind(client)
client.write = (name, params) => {
  if (process.env.END_RIFT_BOT_LOG_OUT === '1') {
    console.log(`OUT ${((Date.now() - sessionEpoch) / 1000).toFixed(2)} ${name} ${JSON.stringify(params)}`)
  }
  try {
    return originalWrite(name, params)
  } catch (error) {
    console.log(`OUT_ERROR ${name} ${error.message}`)
    throw error
  }
}
let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 }
let positionTimer = null
let sessionStarted = false

client.on('keep_alive', packet => {
  if (process.env.END_RIFT_BOT_LOG_KEEP_ALIVE === '1') {
    console.log(`KEEP_ALIVE ${username} ${packet.keepAliveId}`)
  }
  client.write('keep_alive', {
    keepAliveId: packet.keepAliveId
  })
})

function startSession () {
  if (sessionStarted) return
  sessionStarted = true
  joined = true
  console.log(`PLAYER_JOIN ${username}`)
  actions.forEach((command, index) => {
    setTimeout(() => {
      console.log(`ACTION ${username} ${command}`)
      client.chat(command)
    }, actionDelayMs + index * 1500)
  })
}

// minecraft-protocol does not move its local player state when a server
// teleport arrives. Mirror the vanilla acknowledgement and keep sending the
// accepted position so a local bot can genuinely stand on a rune.
client.on('packet', (data, meta) => {
  if (process.env.END_RIFT_BOT_LOG_PACKETS === '1') {
    console.log(`PKT ${((Date.now() - sessionEpoch) / 1000).toFixed(2)} ${meta?.state}:${meta?.name}`)
  }
  if (meta?.name === 'login') console.log(`CLIENT_LOGIN ${username}`)
  // Paper 26.3 keeps configuration open until the client answers its ping.
  if (meta?.name === 'ping') client.write('pong', { id: data.id })
  if (meta?.name === 'finish_configuration') console.log(`CLIENT_FINISH_CONFIGURATION ${username}`)
  if (meta?.name === 'disconnect') console.log(`CLIENT_DISCONNECT ${username} ${JSON.stringify(data)}`)
  if (process.env.END_RIFT_BOT_LOG_SOUND === '1'
      && ['sound_effect', 'entity_sound_effect'].includes(meta?.name)) {
    console.log(`SOUND_PACKET ${username} ${meta.name} ${JSON.stringify(data)}`)
  }
  if (process.env.END_RIFT_BOT_LOG_ALL_CHAT === '1'
      && ['system_chat', 'player_chat', 'disguised_chat', 'set_action_bar_text'].includes(meta?.name)) {
    console.log(`CHAT_PACKET ${username} ${meta.name} ${JSON.stringify(data)}`)
  }
  if (meta?.name === 'show_dialog') {
    // Native 26.3 dialogs (AuthMe pre-join register/login) block configuration
    // until the client posts a custom click action back.
    const dialog = data?.dialog?.value ?? {}
    const buttons = dialog.actions?.value?.value ?? []
    const submitId = buttons
      .map(entry => entry?.action?.value?.id?.value)
      .find(id => typeof id === 'string' && id.endsWith('/submit'))
    console.log(`DIALOG ${username} ${submitId || 'no-submit'} ${JSON.stringify(data)}`)
    const password = process.env.END_RIFT_BOT_AUTH_PASSWORD
    if (submitId && password) {
      const inputs = {}
      for (const input of dialog.inputs?.value?.value ?? []) {
        const key = input?.key?.value
        if (key) inputs[key] = { type: 'string', value: password }
      }
      client.write('custom_click_action', {
        id: submitId,
        nbt: { type: 'compound', value: inputs }
      })
      console.log(`DIALOG_SUBMIT ${username} ${submitId}`)
    }
  }
  if (meta?.name === 'add_resource_pack') {
    // The local protocol client does not render/download packs.  Report the
    // optional pack as loaded so Paper can finish configuration and let the
    // bot exercise the actual event state instead of hanging at the pack UI.
    console.log(`RESOURCE_PACK ${username} ${data.uuid} ${data.hash}`)
    client.write('resource_pack_receive', { uuid: data.uuid, result: 0 })
  }
  if (meta?.name !== 'position') return
  const relative = flag => Boolean(data.flags && data.flags[flag])
  position = {
    x: relative('x') ? position.x + data.x : data.x,
    y: relative('y') ? position.y + data.y : data.y,
    z: relative('z') ? position.z + data.z : data.z,
    yaw: relative('yaw') ? position.yaw + data.yaw : data.yaw,
    pitch: relative('pitch') ? position.pitch + data.pitch : data.pitch
  }
  // 26.3 echoes the accepted position back with the teleport id.
  client.write('teleport_confirm', {
    teleportId: data.teleportId,
    x: position.x,
    y: position.y,
    z: position.z,
    yRot: position.yaw,
    xRot: position.pitch
  })
  if (positionTimer === null && process.env.END_RIFT_BOT_HOLD_POSITION !== '1') {
    positionTimer = setInterval(() => {
      client.write('position_look', { ...position, flags: { onGround: true } })
    }, 100)
  }
  console.log(`SERVER_POSITION ${username} ${position.x},${position.y},${position.z}`)
})

client.on('login', () => console.log(`CLIENT_LOGIN_EVENT ${username}`))
// Start probe actions after the protocol enters the play state.
client.once('login', startSession)

client.on('chat', (packet) => {
  const text = JSON.stringify(packet)
  if (process.env.END_RIFT_BOT_LOG_ALL_CHAT === '1') {
    console.log(`CHAT_ALL ${text}`)
  }
})

client.on('error', (error) => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})

client.on('end', () => {
  if (positionTimer !== null) clearInterval(positionTimer)
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username}`)
  process.exit()
})

setTimeout(() => {
  client.end()
}, durationMs)
