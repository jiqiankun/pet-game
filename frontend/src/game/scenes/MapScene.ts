import Phaser from 'phaser'
import { gameBridge, type MapSceneData } from '../bridge/GameBridge'

const TILE = 32
const PLAYER_SPEED = 160
/** 阻挡地块 gid：水（3）与树（4）。 */
const BLOCKED_GIDS = new Set([3, 4])
/** 野怪行为 → 占位精灵贴图。 */
const WILD_TEXTURES: Record<string, string> = {
  WANDER: 'wild_wander',
  TIMID: 'wild_timid',
  AGGRESSIVE: 'wild_aggressive',
  RARE_STAY: 'wild_rare',
}
/** 对象类型 → 占位贴图与中文标签。 */
const OBJECT_VISUALS: Record<string, { texture: string; label: string }> = {
  camp: { texture: 'camp', label: '营地' },
  chest: { texture: 'chest', label: '宝箱' },
  gather: { texture: 'gather', label: '采集' },
  exit: { texture: 'exit', label: '出口' },
  boss_entrance: { texture: 'boss_door', label: 'Boss入口' },
  npc: { texture: 'npc', label: 'NPC' },
  hidden_spot: { texture: 'hidden_spot', label: '?' },
}

/** 野怪行为状态（仅表现层：位置与移动由本地模拟，战斗结果一律后端裁定）。 */
interface WildActor {
  spawnId: string
  groupId: string
  behavior: string
  sprite: Phaser.GameObjects.Image
  label: Phaser.GameObjects.Text
  homeX: number
  homeY: number
  targetX: number
  targetY: number
  nextDecideAt: number
  /** 已触发遭遇（等待 Vue 处理，避免重复上报）。 */
  engaged: boolean
  /** RARE_STAY：短暂停留的隐藏/出现时间。 */
  hiddenUntil: number
  visibleUntil: number
}

/** 可交互静态对象。 */
interface InteractiveObject {
  objectType: string
  objectId: string
  x: number
  y: number
  props: Record<string, string>
  sprite?: Phaser.GameObjects.Image
}

/**
 * 地图场景（阶段 6，核心 Scene 2/2）。
 * <p>
 * 职责（Phaser 边界）：Tiled 地图渲染、玩家移动、野怪简单行为、对象交互上报。
 * 不承载任何业务数据（存档/背包/战斗公式）；所有业务结果经 bridge 由 Vue 层回传。
 */
export default class MapScene extends Phaser.Scene {
  private sceneData!: MapSceneData
  private player!: Phaser.GameObjects.Image
  private cursors!: Phaser.Types.Input.Keyboard.CursorKeys
  private wasd!: Record<string, Phaser.Input.Keyboard.Key>
  private interactKey!: Phaser.Input.Keyboard.Key
  private blockedTiles = new Set<string>()
  private mapWidthPx = 0
  private mapHeightPx = 0
  private wilds: WildActor[] = []
  private interactives: InteractiveObject[] = []
  private inputLocked = false
  private hint!: Phaser.GameObjects.Text
  private unsubscribers: Array<() => void> = []

  constructor() {
    super('MapScene')
  }

  init(data: MapSceneData): void {
    this.sceneData = data
  }

  create(): void {
    this.blockedTiles.clear()
    this.wilds = []
    this.interactives = []
    this.inputLocked = false

    const map = this.make.tilemap({ key: `map_${this.sceneData.mapFile}` })
    this.mapWidthPx = map.widthInPixels
    this.mapHeightPx = map.heightInPixels
    const tileset = map.addTilesetImage('placeholder', 'tileset')
    if (tileset) {
      const ground = map.createLayer('ground', tileset)
      if (ground) {
        for (let y = 0; y < map.height; y++) {
          for (let x = 0; x < map.width; x++) {
            const tile = ground.getTileAt(x, y)
            if (tile && BLOCKED_GIDS.has(tile.index)) {
              this.blockedTiles.add(`${x},${y}`)
            }
          }
        }
      }
    }

    // ---- 对象层 ----
    const objectLayer = map.getObjectLayer('objects')
    let spawnX = this.mapWidthPx / 2
    let spawnY = this.mapHeightPx / 2
    for (const tiledObj of objectLayer?.objects ?? []) {
      const x = tiledObj.x ?? 0
      const y = tiledObj.y ?? 0
      const cx = x + TILE / 2
      const cy = y + TILE / 2
      const type = tiledObj.type ?? ''
      const name = tiledObj.name ?? ''
      const props = this.readProps(tiledObj)

      if (type === 'spawn' || type === 'entry') {
        if (name === this.sceneData.spawnObjectId) {
          spawnX = cx
          spawnY = cy
        }
        continue
      }
      if (type === 'wild_spawn') {
        this.createWild(props, cx, cy)
        continue
      }
      const visual = OBJECT_VISUALS[type]
      if (!visual) {
        continue
      }
      // 已消耗对象不渲染
      if (type === 'chest' && this.sceneData.consumedChestIds.includes(props.chestId ?? '')) continue
      if (type === 'gather' && this.sceneData.usedGatherIds.includes(props.gatherId ?? '')) continue

      const sprite = this.add.image(cx, cy, visual.texture)
      const labelText = type === 'camp' && this.sceneData.activatedCampIds.includes(props.campId ?? '')
        ? `${visual.label}✓` : visual.label
      const label = this.add.text(cx, y + TILE + 2, labelText, {
        fontSize: '10px', color: '#e8eef7', backgroundColor: '#00000088', padding: { x: 3, y: 1 },
      }).setOrigin(0.5, 0)

      const objectId = props.campId || props.chestId || props.gatherId || props.exitId
        || props.bossId || props.npcId || props.hiddenId || name
      this.interactives.push({ objectType: type, objectId, x: cx, y: cy, props, sprite })
      if (type === 'boss_entrance') {
        label.setAlpha(0.6) // 占位提示
      }
    }
    // 未命中指定落点时退回默认出生点对象
    if (spawnX === this.mapWidthPx / 2 && spawnY === this.mapHeightPx / 2) {
      for (const tiledObj of objectLayer?.objects ?? []) {
        if (tiledObj.type === 'spawn') {
          spawnX = (tiledObj.x ?? 0) + TILE / 2
          spawnY = (tiledObj.y ?? 0) + TILE / 2
          break
        }
      }
    }

    // ---- 玩家 ----
    this.player = this.add.image(spawnX, spawnY, 'player').setDepth(10)

    // ---- 输入 ----
    if (this.input.keyboard) {
      this.cursors = this.input.keyboard.createCursorKeys()
      this.wasd = {
        W: this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.W),
        A: this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.A),
        S: this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.S),
        D: this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.D),
      }
      this.interactKey = this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.E)
    }

    this.hint = this.add.text(8, 6, '', {
      fontSize: '12px', color: '#e8eef7', backgroundColor: '#00000088', padding: { x: 6, y: 3 },
    }).setDepth(20)

    // ---- bridge 命令订阅 ----
    this.unsubscribers.push(
      gameBridge.on('cmd:restart-map', (payload) => {
        // 区域切换 / 营地休息刷新：回到 BootScene 重新加载（同一 Scene 类，地图差异由 Tiled 配置解决）
        this.scene.start('BootScene', payload)
      }),
      gameBridge.on('cmd:set-input-lock', (payload) => {
        this.inputLocked = payload.locked
      }),
      gameBridge.on('cmd:remove-wild', (payload) => {
        const idx = this.wilds.findIndex((w) => w.spawnId === payload.id)
        const wild = idx >= 0 ? this.wilds[idx] : undefined
        if (wild) {
          wild.sprite.destroy()
          wild.label.destroy()
          this.wilds.splice(idx, 1)
        }
      }),
      gameBridge.on('cmd:remove-object', (payload) => {
        const idx = this.interactives.findIndex((o) => o.objectId === payload.id)
        const obj = idx >= 0 ? this.interactives[idx] : undefined
        if (obj) {
          obj.sprite?.destroy()
          this.interactives.splice(idx, 1)
        }
      }),
    )
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      for (const unsub of this.unsubscribers) unsub()
      this.unsubscribers = []
    })
  }

  update(time: number, delta: number): void {
    if (!this.inputLocked) {
      this.movePlayer(delta)
      this.updateWilds(time, delta)
      this.checkContacts()
      this.checkInteractions()
    }
  }

  // ==================== 玩家移动 ====================

  private movePlayer(delta: number): void {
    let dx = 0
    let dy = 0
    const left = this.cursors?.left?.isDown || this.wasd?.A?.isDown
    const right = this.cursors?.right?.isDown || this.wasd?.D?.isDown
    const up = this.cursors?.up?.isDown || this.wasd?.W?.isDown
    const down = this.cursors?.down?.isDown || this.wasd?.S?.isDown
    if (left) dx -= 1
    if (right) dx += 1
    if (up) dy -= 1
    if (down) dy += 1
    if (dx === 0 && dy === 0) return

    const step = PLAYER_SPEED * (delta / 1000)
    const len = Math.sqrt(dx * dx + dy * dy)
    const nx = this.player.x + (dx / len) * step
    const ny = this.player.y + (dy / len) * step
    const half = TILE / 2 - 6 // 略小的碰撞盒，允许贴边通过

    // 分轴移动，支持沿墙滑动
    if (!this.hitsBlocked(nx, this.player.y, half)) {
      this.player.x = Phaser.Math.Clamp(nx, half, this.mapWidthPx - half)
    }
    if (!this.hitsBlocked(this.player.x, ny, half)) {
      this.player.y = Phaser.Math.Clamp(ny, half, this.mapHeightPx - half)
    }
  }

  private hitsBlocked(x: number, y: number, half: number): boolean {
    const corners: Array<[number, number]> = [
      [x - half, y - half], [x + half, y - half],
      [x - half, y + half], [x + half, y + half],
    ]
    for (const [px, py] of corners) {
      if (px < 0 || py < 0 || px >= this.mapWidthPx || py >= this.mapHeightPx) {
        return true
      }
      const tx = Math.floor(px / TILE)
      const ty = Math.floor(py / TILE)
      if (this.blockedTiles.has(`${tx},${ty}`)) {
        return true
      }
    }
    return false
  }

  // ==================== 野怪行为（表现层简单 AI） ====================

  private createWild(props: Record<string, string>, cx: number, cy: number): void {
    const spawnId = props.spawnId ?? ''
    if (this.sceneData.defeatedWildIds.includes(spawnId)) {
      return
    }
    const behavior = props.behavior ?? 'WANDER'
    const texture = WILD_TEXTURES[behavior] ?? 'wild_wander'
    const sprite = this.add.image(cx, cy, texture).setDepth(5)
    const label = this.add.text(cx, cy + TILE / 2 + 2, '野生宠物', {
      fontSize: '9px', color: '#ffe9a8', backgroundColor: '#00000077', padding: { x: 2, y: 1 },
    }).setOrigin(0.5, 0).setDepth(5)
    this.wilds.push({
      spawnId,
      groupId: props.groupId ?? '',
      behavior,
      sprite,
      label,
      homeX: cx,
      homeY: cy,
      targetX: cx,
      targetY: cy,
      nextDecideAt: 0,
      engaged: false,
      hiddenUntil: 0,
      visibleUntil: behavior === 'RARE_STAY' ? this.time.now + 12000 : Number.MAX_SAFE_INTEGER,
    })
  }

  private updateWilds(time: number, delta: number): void {
    for (const wild of this.wilds) {
      if (wild.engaged) continue

      // 稀有短暂停留：可见 12s → 消失 8s → 重新出现
      if (wild.behavior === 'RARE_STAY') {
        if (time >= wild.visibleUntil && wild.hiddenUntil === 0) {
          wild.hiddenUntil = time + 8000
          wild.sprite.setVisible(false)
          wild.label.setVisible(false)
        }
        if (wild.hiddenUntil > 0 && time >= wild.hiddenUntil) {
          wild.hiddenUntil = 0
          wild.visibleUntil = time + 12000
          wild.sprite.setVisible(true).setPosition(wild.homeX, wild.homeY)
          wild.label.setVisible(true).setPosition(wild.homeX, wild.homeY + TILE / 2 + 2)
        }
        if (!wild.sprite.visible) continue
      }

      const distToPlayer = Phaser.Math.Distance.Between(
        wild.sprite.x, wild.sprite.y, this.player.x, this.player.y)

      let speed = 0
      let moveX = wild.targetX
      let moveY = wild.targetY

      if (wild.behavior === 'TIMID' && distToPlayer < 90) {
        // 胆小逃跑：远离玩家（不离开巢穴太远）
        const away = Phaser.Math.Angle.Between(
          this.player.x, this.player.y, wild.sprite.x, wild.sprite.y)
        moveX = wild.sprite.x + Math.cos(away) * 60
        moveY = wild.sprite.y + Math.sin(away) * 60
        speed = 100
      } else if (wild.behavior === 'AGGRESSIVE' && distToPlayer < 160) {
        // 主动靠近：追击玩家
        moveX = this.player.x
        moveY = this.player.y
        speed = 70
      } else if (time >= wild.nextDecideAt) {
        // 普通游荡：周期性挑选巢穴附近随机目标
        wild.targetX = wild.homeX + Phaser.Math.Between(-96, 96)
        wild.targetY = wild.homeY + Phaser.Math.Between(-72, 72)
        wild.nextDecideAt = time + Phaser.Math.Between(1500, 3200)
      }

      if (speed === 0) {
        speed = wild.behavior === 'TIMID' ? 30 : 40
        moveX = wild.targetX
        moveY = wild.targetY
      }

      const dist = Phaser.Math.Distance.Between(wild.sprite.x, wild.sprite.y, moveX, moveY)
      if (dist > 2) {
        const angle = Phaser.Math.Angle.Between(wild.sprite.x, wild.sprite.y, moveX, moveY)
        const step = Math.min(dist, speed * (delta / 1000))
        const nx = wild.sprite.x + Math.cos(angle) * step
        const ny = wild.sprite.y + Math.sin(angle) * step
        // 限制在巢穴 180px 范围内，避免跑出地图
        if (Phaser.Math.Distance.Between(nx, ny, wild.homeX, wild.homeY) < 180) {
          wild.sprite.setPosition(nx, ny)
          wild.label.setPosition(nx, ny + TILE / 2 + 2)
        }
      }
    }
  }

  // ==================== 接触与交互 ====================

  private checkContacts(): void {
    for (const wild of this.wilds) {
      if (wild.engaged || !wild.sprite.visible) continue
      const dist = Phaser.Math.Distance.Between(
        wild.sprite.x, wild.sprite.y, this.player.x, this.player.y)
      if (dist < 26) {
        wild.engaged = true
        gameBridge.emit('encounter:touch', {
          spawnId: wild.spawnId,
          groupId: wild.groupId,
          behavior: wild.behavior,
        })
        return
      }
    }
    for (const obj of this.interactives) {
      if (obj.objectType !== 'exit') continue
      const dist = Phaser.Math.Distance.Between(obj.x, obj.y, this.player.x, this.player.y)
      if (dist < 26) {
        gameBridge.emit('exit:touch', {
          exitId: obj.props.exitId ?? obj.objectId,
          targetMapId: obj.props.targetMapId ?? '',
        })
        return
      }
    }
  }

  private checkInteractions(): void {
    if (!Phaser.Input.Keyboard.JustDown(this.interactKey)) {
      this.updateHint()
      return
    }
    // 找最近的交互对象（60px 内）
    let nearest: InteractiveObject | null = null
    let nearestDist = 60
    for (const obj of this.interactives) {
      if (obj.objectType === 'exit') continue // 出口自动触发，不走交互键
      const dist = Phaser.Math.Distance.Between(obj.x, obj.y, this.player.x, this.player.y)
      if (dist < nearestDist) {
        nearest = obj
        nearestDist = dist
      }
    }
    if (!nearest) return

    switch (nearest.objectType) {
      case 'camp':
        gameBridge.emit('camp:touch', { id: nearest.props.campId ?? nearest.objectId })
        break
      case 'gather':
        gameBridge.emit('gather:request', { id: nearest.props.gatherId ?? nearest.objectId })
        break
      case 'chest':
        gameBridge.emit('chest:request', { id: nearest.props.chestId ?? nearest.objectId })
        break
      case 'boss_entrance':
        gameBridge.emit('boss:touch', { id: nearest.props.bossId ?? nearest.objectId })
        break
      case 'npc':
        gameBridge.emit('npc:touch', { id: nearest.props.npcId ?? nearest.objectId })
        break
      case 'hidden_spot':
        gameBridge.emit('hidden:touch', { id: nearest.props.hiddenId ?? nearest.objectId })
        break
    }
  }

  private updateHint(): void {
    let text = '方向键/WASD 移动 · E 交互'
    for (const obj of this.interactives) {
      if (obj.objectType === 'exit') continue
      const dist = Phaser.Math.Distance.Between(obj.x, obj.y, this.player.x, this.player.y)
      if (dist < 60) {
        const visual = OBJECT_VISUALS[obj.objectType]
        text = `按 E 交互：${visual?.label ?? obj.objectType}`
        break
      }
    }
    if (this.hint.text !== text) {
      this.hint.setText(text)
    }
  }

  // ==================== 工具 ====================

  /** 读取 Tiled 对象属性为字符串字典。 */
  private readProps(tiledObj: Phaser.Types.Tilemaps.TiledObject): Record<string, string> {
    const props: Record<string, string> = {}
    const rawProps = tiledObj.properties as Array<{ name: string; value: unknown }> | undefined
    for (const entry of rawProps ?? []) {
      props[entry.name] = String(entry.value)
    }
    return props
  }
}
