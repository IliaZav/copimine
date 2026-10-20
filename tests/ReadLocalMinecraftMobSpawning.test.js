'use strict'

const assert = require('node:assert/strict')
const test = require('node:test')
const { getMobSpawningValue } = require('./ReadLocalMinecraftMobSpawning')

test('uses the Minecraft default when the level data has no stored gamerule', () => {
  assert.equal(getMobSpawningValue({ Data: {} }), true)
})

test('reads legacy string gamerule values case-insensitively', () => {
  assert.equal(getMobSpawningValue({ Data: { GameRules: { doMobSpawning: 'false' } } }), false)
  assert.equal(getMobSpawningValue({ Data: { GameRules: { domobspawning: 'TRUE' } } }), true)
})

test('reads boolean gamerule values', () => {
  assert.equal(getMobSpawningValue({ Data: { GameRules: { doMobSpawning: false } } }), false)
})

test('rejects malformed stored gamerule values', () => {
  assert.throws(
    () => getMobSpawningValue({ Data: { GameRules: { doMobSpawning: 'not-a-boolean' } } }),
    /Stored doMobSpawning gamerule is not a boolean/
  )
})
