/**
 * 阶段 6 占位资源生成脚本（一次性开发工具）。
 *
 * 产物（已入库，无需重复运行；地图/资源调整后重新执行即可）：
 * - public/assets/maps/tileset.png            占位地块图集（4 格：草/路/水/树）
 * - public/assets/maps/start_village.json     起始据点 Tiled 地图
 * - public/assets/maps/meadow.json            青草原 Tiled 地图
 * - public/assets/maps/forest.json            翠树林 Tiled 地图
 * - public/assets/sprites/*.png               玩家/野怪/地图对象占位图
 *
 * 用法：node frontend/scripts/gen-placeholder-assets.mjs
 * 正式美术资源到位后直接替换对应文件，图层与对象层约定保持不变。
 */
import { deflateSync } from 'node:zlib'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const MAPS_DIR = join(ROOT, 'public', 'assets', 'maps')
const SPRITES_DIR = join(ROOT, 'public', 'assets', 'sprites')
mkdirSync(MAPS_DIR, { recursive: true })
mkdirSync(SPRITES_DIR, { recursive: true })

// ==================== 极简 PNG 编码 ====================

const CRC_TABLE = (() => {
  const table = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c
  }
  return table
})()

function crc32(buf) {
  let c = 0xffffffff
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const typeBuf = Buffer.from(type, 'ascii')
  const crcBuf = Buffer.alloc(4)
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])))
  return Buffer.concat([len, typeBuf, data, crcBuf])
}

/** pixels: Uint8Array(width*height*4) RGBA */
function encodePng(width, height, pixels) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8   // bit depth
  ihdr[9] = 6   // color type RGBA
  const raw = Buffer.alloc((width * 4 + 1) * height)
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0 // filter: none
    pixels.subarray(y * width * 4, (y + 1) * width * 4)
      .forEach((v, i) => { raw[y * (width * 4 + 1) + 1 + i] = v })
  }
  return Buffer.concat([
    signature,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

function solidTile(w, h, [r, g, b], a = 255) {
  const px = new Uint8Array(w * h * 4)
  for (let i = 0; i < w * h; i++) {
    px[i * 4] = r; px[i * 4 + 1] = g; px[i * 4 + 2] = b; px[i * 4 + 3] = a
  }
  return px
}

function circleSprite(size, [r, g, b], border) {
  const px = new Uint8Array(size * size * 4)
  const c = size / 2
  const radius = size / 2 - 2
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const d = Math.sqrt((x - c) ** 2 + (y - c) ** 2)
      if (d <= radius) {
        const i = (y * size + x) * 4
        const isBorder = border && d > radius - 2
        px[i] = isBorder ? border[0] : r
        px[i + 1] = isBorder ? border[1] : g
        px[i + 2] = isBorder ? border[2] : b
        px[i + 3] = 255
      }
    }
  }
  return px
}

// ==================== 图集与精灵 ====================

// 地块图集：128x32 = 4 格（草/路/水/树）
const tileset = new Uint8Array(128 * 32 * 4)
const tileColors = [[126, 200, 80], [201, 168, 106], [74, 144, 217], [46, 125, 50]]
tileColors.forEach((color, idx) => {
  const tile = solidTile(32, 32, color)
  // 内圈描边增加辨识度
  for (let y = 0; y < 32; y++) {
    for (let x = 0; x < 32; x++) {
      if (x === 0 || y === 0 || x === 31 || y === 31) {
        const i = (y * 32 + x) * 4
        tile[i] = Math.max(0, color[0] - 30)
        tile[i + 1] = Math.max(0, color[1] - 30)
        tile[i + 2] = Math.max(0, color[2] - 30)
      }
    }
  }
  for (let y = 0; y < 32; y++) {
    for (let x = 0; x < 32; x++) {
      const src = (y * 32 + x) * 4
      const dst = (y * 128 + idx * 32 + x) * 4
      tileset.set(tile.subarray(src, src + 4), dst)
    }
  }
})
writeFileSync(join(MAPS_DIR, 'tileset.png'), encodePng(128, 32, tileset))

const sprites = {
  player: circleSprite(32, [74, 144, 217], [30, 60, 100]),
  wild_wander: circleSprite(32, [120, 190, 90], [50, 90, 40]),
  wild_timid: circleSprite(32, [240, 210, 90], [140, 120, 40]),
  wild_aggressive: circleSprite(32, [220, 90, 80], [120, 40, 35]),
  wild_rare: circleSprite(32, [170, 110, 220], [90, 50, 130]),
  camp: solidTile(32, 32, [235, 150, 60]),
  chest: solidTile(32, 32, [150, 100, 50]),
  gather: circleSprite(32, [90, 170, 90], [40, 100, 45]),
  exit: solidTile(32, 32, [130, 140, 160]),
  boss_door: solidTile(32, 32, [110, 50, 60]),
  npc: circleSprite(32, [235, 140, 180], [150, 70, 100]),
  hidden_spot: circleSprite(32, [60, 60, 80], [30, 30, 45]),
  entry: solidTile(32, 32, [126, 200, 80], 0), // 入口为不可见逻辑对象
}
for (const [name, px] of Object.entries(sprites)) {
  writeFileSync(join(SPRITES_DIR, `${name}.png`), encodePng(32, 32, px))
}

// ==================== Tiled 地图生成 ====================

const W = 25
const H = 19
const TS = 32

/** 生成基础网格：草地填充 + 树木边框（gap 为边框开口 [col,row] 集合）。 */
function baseGrid(borderGaps = []) {
  const grid = Array.from({ length: H }, () => Array.from({ length: W }, () => 1))
  const gapSet = new Set(borderGaps.map(([c, r]) => `${c},${r}`))
  for (let x = 0; x < W; x++) {
    for (let y = 0; y < H; y++) {
      if ((x === 0 || x === W - 1 || y === 0 || y === H - 1) && !gapSet.has(`${x},${y}`)) {
        grid[y][x] = 4
      }
    }
  }
  return grid
}

function setRect(grid, x0, y0, x1, y1, gid) {
  for (let y = y0; y <= y1; y++) {
    for (let x = x0; x <= x1; x++) {
      if (y >= 0 && y < H && x >= 0 && x < W) grid[y][x] = gid
    }
  }
}

let objectId = 0
function obj(name, type, tx, ty, properties = []) {
  objectId += 1
  return {
    id: objectId, name, type,
    x: tx * TS, y: ty * TS, width: TS, height: TS,
    visible: true, rotation: 0,
    properties: Object.entries(properties).map(([pname, value]) => ({ name: pname, value })),
  }
}

function buildMap(grid, objects) {
  return {
    compressionlevel: -1,
    height: H, width: W, infinite: false,
    orientation: 'orthogonal', renderorder: 'right-down',
    tiledversion: '1.10.0', type: 'map', version: '1.10',
    tilewidth: TS, tileheight: TS,
    nextlayerid: 3, nextobjectid: objectId + 100,
    tilesets: [{
      columns: 4, firstgid: 1,
      image: 'tileset.png', imageheight: 32, imagewidth: 128,
      margin: 0, spacing: 0, name: 'placeholder', tilecount: 4,
      tilewidth: TS, tileheight: TS,
    }],
    layers: [
      {
        id: 1, name: 'ground', type: 'tilelayer', visible: true,
        opacity: 1, x: 0, y: 0, width: W, height: H,
        data: grid.flat(),
      },
      {
        id: 2, name: 'objects', type: 'objectgroup', visible: true,
        opacity: 1, x: 0, y: 0, draworder: 'topdown',
        objects,
      },
    ],
  }
}

function writeMap(fileName, mapJson) {
  writeFileSync(join(MAPS_DIR, fileName), JSON.stringify(mapJson, null, 2))
}

// ---- 起始据点：晨曦村 ----
objectId = 0
{
  const gaps = []
  for (let y = 8; y <= 10; y++) gaps.push([W - 1, y])
  const grid = baseGrid(gaps)
  setRect(grid, 2, 9, W - 2, 9, 2)      // 横向主路
  setRect(grid, 4, 13, 6, 15, 3)        // 小池塘
  writeMap('start_village.json', buildMap(grid, [
    obj('SPAWN_VILLAGE', 'spawn', 3, 9),
    obj('ENTRY_VILLAGE_FROM_MEADOW', 'entry', 22, 9),
    obj('EXIT_VILLAGE_TO_MEADOW', 'exit', 23, 9, {
      exitId: 'EXIT_VILLAGE_TO_MEADOW', targetMapId: 'MAP_AREA_MEADOW',
    }),
    obj('CAMP_VILLAGE_1', 'camp', 12, 6, { campId: 'CAMP_VILLAGE_1' }),
    obj('NPC_VILLAGE_ELDER', 'npc', 8, 6, { npcId: 'NPC_VILLAGE_ELDER' }),
  ]))
}

// ---- 初始区域：青草原 ----
objectId = 0
{
  const gaps = []
  for (let y = 8; y <= 10; y++) { gaps.push([0, y]); gaps.push([W - 1, y]) }
  const grid = baseGrid(gaps)
  setRect(grid, 1, 9, W - 2, 9, 2)      // 横向主路
  setRect(grid, 16, 3, 18, 5, 3)        // 水塘
  writeMap('meadow.json', buildMap(grid, [
    obj('SPAWN_MEADOW', 'spawn', 2, 9),
    obj('ENTRY_MEADOW_FROM_VILLAGE', 'entry', 2, 10),
    obj('ENTRY_MEADOW_FROM_FOREST', 'entry', 22, 10),
    obj('EXIT_MEADOW_TO_VILLAGE', 'exit', 1, 9, {
      exitId: 'EXIT_MEADOW_TO_VILLAGE', targetMapId: 'MAP_START_VILLAGE',
    }),
    obj('EXIT_MEADOW_TO_FOREST', 'exit', 23, 9, {
      exitId: 'EXIT_MEADOW_TO_FOREST', targetMapId: 'MAP_AREA_FOREST',
    }),
    obj('CAMP_MEADOW_1', 'camp', 12, 5, { campId: 'CAMP_MEADOW_1' }),
    obj('GATHER_MEADOW_1', 'gather', 6, 4, { gatherId: 'GATHER_MEADOW_1' }),
    obj('GATHER_MEADOW_2', 'gather', 18, 13, { gatherId: 'GATHER_MEADOW_2' }),
    obj('GATHER_MEADOW_3', 'gather', 10, 14, { gatherId: 'GATHER_MEADOW_3' }),
    obj('CHEST_MEADOW_HIDDEN_1', 'chest', 20, 3, { chestId: 'CHEST_MEADOW_HIDDEN_1' }),
    obj('BOSS_MEADOW', 'boss_entrance', 12, 2, { bossId: 'BOSS_MEADOW' }),
    obj('NPC_MEADOW_SCOUT', 'npc', 5, 12, { npcId: 'NPC_MEADOW_SCOUT' }),
    obj('HIDDEN_MEADOW_1', 'hidden_spot', 21, 15, { hiddenId: 'HIDDEN_MEADOW_1' }),
    obj('WILD_MEADOW_1', 'wild_spawn', 7, 7, { spawnId: 'WILD_MEADOW_1', groupId: 'ENCOUNTER_MEADOW', behavior: 'WANDER' }),
    obj('WILD_MEADOW_2', 'wild_spawn', 15, 12, { spawnId: 'WILD_MEADOW_2', groupId: 'ENCOUNTER_MEADOW', behavior: 'TIMID' }),
    obj('WILD_MEADOW_3', 'wild_spawn', 17, 7, { spawnId: 'WILD_MEADOW_3', groupId: 'ENCOUNTER_MEADOW', behavior: 'AGGRESSIVE' }),
    obj('WILD_MEADOW_4', 'wild_spawn', 10, 12, { spawnId: 'WILD_MEADOW_4', groupId: 'ENCOUNTER_MEADOW', behavior: 'RARE_STAY' }),
  ]))
}

// ---- 森林区域：翠树林 ----
objectId = 0
{
  const gaps = []
  for (let y = 8; y <= 10; y++) gaps.push([0, y])
  const grid = baseGrid(gaps)
  setRect(grid, 1, 9, W - 2, 9, 2)      // 横向主路
  setRect(grid, 5, 5, 6, 6, 4)          // 树丛障碍
  setRect(grid, 10, 12, 11, 13, 4)
  setRect(grid, 19, 6, 20, 7, 4)
  setRect(grid, 14, 15, 15, 16, 4)
  writeMap('forest.json', buildMap(grid, [
    obj('SPAWN_FOREST', 'spawn', 2, 9),
    obj('ENTRY_FOREST_FROM_MEADOW', 'entry', 2, 10),
    obj('EXIT_FOREST_TO_MEADOW', 'exit', 1, 9, {
      exitId: 'EXIT_FOREST_TO_MEADOW', targetMapId: 'MAP_AREA_MEADOW',
    }),
    obj('CAMP_FOREST_1', 'camp', 12, 9, { campId: 'CAMP_FOREST_1' }),
    obj('GATHER_FOREST_1', 'gather', 7, 4, { gatherId: 'GATHER_FOREST_1' }),
    obj('GATHER_FOREST_2', 'gather', 17, 13, { gatherId: 'GATHER_FOREST_2' }),
    obj('CHEST_FOREST_HIDDEN_1', 'chest', 21, 4, { chestId: 'CHEST_FOREST_HIDDEN_1' }),
    obj('BOSS_FOREST', 'boss_entrance', 12, 2, { bossId: 'BOSS_FOREST' }),
    obj('NPC_FOREST_HERMIT', 'npc', 6, 13, { npcId: 'NPC_FOREST_HERMIT' }),
    obj('HIDDEN_FOREST_1', 'hidden_spot', 22, 14, { hiddenId: 'HIDDEN_FOREST_1' }),
    obj('WILD_FOREST_1', 'wild_spawn', 8, 7, { spawnId: 'WILD_FOREST_1', groupId: 'ENCOUNTER_FOREST', behavior: 'WANDER' }),
    obj('WILD_FOREST_2', 'wild_spawn', 14, 5, { spawnId: 'WILD_FOREST_2', groupId: 'ENCOUNTER_FOREST', behavior: 'AGGRESSIVE' }),
    obj('WILD_FOREST_3', 'wild_spawn', 16, 12, { spawnId: 'WILD_FOREST_3', groupId: 'ENCOUNTER_FOREST', behavior: 'TIMID' }),
    obj('WILD_FOREST_4', 'wild_spawn', 20, 10, { spawnId: 'WILD_FOREST_4', groupId: 'ENCOUNTER_FOREST', behavior: 'RARE_STAY' }),
  ]))
}

console.log('占位资源与 Tiled 地图生成完成：')
console.log('  -', join(MAPS_DIR, 'tileset.png'))
console.log('  -', join(MAPS_DIR, 'start_village.json'))
console.log('  -', join(MAPS_DIR, 'meadow.json'))
console.log('  -', join(MAPS_DIR, 'forest.json'))
console.log('  -', SPRITES_DIR, '（', Object.keys(sprites).length, '个精灵）')
