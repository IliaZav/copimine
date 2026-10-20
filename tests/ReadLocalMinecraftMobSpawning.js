'use strict'

const fs = require('node:fs')
const path = require('node:path')
const nbt = require(path.join(__dirname, '..', 'local-runtime', 'mc-bot', 'node_modules', 'prismarine-nbt'))

function findNamedValue(value, name) {
  if (value === null || typeof value !== 'object') return undefined
  for (const [key, child] of Object.entries(value)) {
    if (key.toLowerCase() === name.toLowerCase()) return child
    const nested = findNamedValue(child, name)
    if (nested !== undefined) return nested
  }
  return undefined
}

function readBoolean(value, name) {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string' && /^(true|false)$/i.test(value)) return value.toLowerCase() === 'true'
  throw new Error(`Stored ${name} gamerule is not a boolean`)
}

function getMobSpawningValue(simplifiedLevelData) {
  const gameRules = findNamedValue(simplifiedLevelData, 'GameRules') ??
    findNamedValue(simplifiedLevelData, 'game_rules')
  if (gameRules === undefined || gameRules === null) return true

  const storedValue = findNamedValue(gameRules, 'doMobSpawning')
  return storedValue === undefined ? true : readBoolean(storedValue, 'doMobSpawning')
}

async function readMobSpawning(levelDatPath) {
  const { parsed } = await nbt.parse(fs.readFileSync(levelDatPath))
  return getMobSpawningValue(nbt.simplify(parsed))
}

if (require.main === module) {
  const levelDatPath = process.argv[2]
  if (!levelDatPath) {
    process.stderr.write('Usage: node ReadLocalMinecraftMobSpawning.js <level.dat>\n')
    process.exitCode = 2
  } else {
    readMobSpawning(levelDatPath).then(value => {
      process.stdout.write(`${value}\n`)
    }).catch(error => {
      process.stderr.write(`${error.message}\n`)
      process.exitCode = 1
    })
  }
}

module.exports = { getMobSpawningValue, readMobSpawning }
