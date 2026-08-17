/*
 * Local-only protocol smoke client.  It talks to the isolated Paper copy on
 * 25566 and is intentionally not part of the launcher, website, or plugin
 * runtime.  The npm dependency is installed under ignored local-runtime.
 */
const path = require('path')
const mc = require(path.resolve(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'minecraft-protocol'))

const host = process.env.END_RIFT_BOT_HOST || '127.0.0.1'
const port = Number(process.env.END_RIFT_BOT_PORT || 25566)
const username = process.argv[2] || 'EndRiftBot'
const durationMs = Number(process.argv[3] || 15000)
const actionDelayMs = Number(process.env.END_RIFT_BOT_ACTION_DELAY_MS || 9000)
const actions = (process.env.END_RIFT_BOT_ACTIONS || '')
  .split('|')
  .map(value => value.trim())
  .filter(Boolean)

const client = mc.createClient({
  host,
  port,
  username,
  version: '1.21.1',
  auth: 'offline',
  disableChatSigning: true,
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

let joined = false

client.on('playerJoin', () => {
  joined = true
  console.log(`PLAYER_JOIN ${username}`)
  client.chat('/register endrift-local endrift-local')
  for (const delay of [1000, 3000, 6000]) {
    setTimeout(() => client.chat('/login endrift-local'), delay)
  }
  actions.forEach((command, index) => {
    setTimeout(() => {
      console.log(`ACTION ${username} ${command}`)
      client.chat(command)
    }, actionDelayMs + index * 1500)
  })
})

client.on('chat', (packet) => {
  const text = JSON.stringify(packet)
  if (text.includes('EndRift') || text.includes('CopiMine') || text.includes('AuthMe')) {
    console.log(`CHAT ${text}`)
  }
})

client.on('error', (error) => {
  console.error(`BOT_ERROR ${username} ${error.stack || error}`)
  process.exitCode = 1
})

client.on('end', () => {
  if (!joined) process.exitCode = 1
  console.log(`PLAYER_END ${username}`)
  process.exit()
})

setTimeout(() => {
  client.end()
}, durationMs)
