const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '..');
const nbt = require(path.join(root, 'local-runtime/mc-bot/node_modules/prismarine-nbt'));
function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, stable(value[key])]));
  return value;
}
(async () => {
  const name = process.argv[2];
  if (!/^EndRiftTarget[1-4]$/.test(name)) throw new Error('Only dedicated local fixture accounts');
  const digest = crypto.createHash('md5').update('OfflinePlayer:' + name).digest();
  digest[6] = (digest[6] & 0x0f) | 0x30;
  digest[8] = (digest[8] & 0x3f) | 0x80;
  const hex = digest.toString('hex');
  const uuid = `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
  const file = path.join(root, 'local-runtime/end-rift-server/CopiMine/playerdata', uuid + '.dat');
  const { parsed } = await nbt.parse(fs.readFileSync(file));
  const data = nbt.simplify(parsed);
  const inventory = stable((data.Inventory || []).sort((a,b) => a.Slot - b.Slot));
  console.log(JSON.stringify({name, uuid, dimension: data.Dimension, savedAt: fs.statSync(file).mtime.toISOString(), slotCount: inventory.length,
    itemCount: inventory.reduce((n, item) => n + item.count, 0),
    inventorySha256: crypto.createHash('sha256').update(JSON.stringify(inventory)).digest('hex'), inventory,
    xp: {level: data.XpLevel, progress: data.XpP, total: data.XpTotal}}));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
