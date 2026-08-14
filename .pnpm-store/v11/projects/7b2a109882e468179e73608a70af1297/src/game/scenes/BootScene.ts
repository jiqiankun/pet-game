import Phaser from 'phaser'
import type { MapSceneData } from '../bridge/GameBridge'

const TILESET_BY_MAP: Record<string, string> = {
  start_village: 'tileset_grassland_base',
  meadow: 'tileset_grassland_base',
  forest: 'tileset_grassland_base',
  waters: 'tileset_waters',
  thunder: 'tileset_thunder',
  ruins: 'tileset_ruins',
}

/**
 * 资源加载场景（阶段 6，核心 Scene 1/2）。
 * <p>
 * 只负责加载 Tiled 地图 JSON、区域图集与地图精灵，然后启动 MapScene。
 * 所有地图共用同一个 MapScene（地图差异由 Tiled 配置解决，不新建 Scene 类）。
 */
export default class BootScene extends Phaser.Scene {
  private sceneData: MapSceneData | null = null

  constructor() {
    super('BootScene')
  }

  init(data?: MapSceneData): void {
    // Phaser 会自动启动首个 Scene（无数据）；Vue 层准备就绪后携带地图数据重新启动
    this.sceneData = data ?? null
  }

  preload(): void {
    if (!this.sceneData) return
    const tileset = TILESET_BY_MAP[this.sceneData.mapFile] ?? 'tileset_grassland_base'
    if (this.textures.exists('tileset')) this.textures.remove('tileset')
    this.load.image('tileset', `/assets/maps/tilesets/${tileset}.png`)
    this.load.tilemapTiledJSON(
      `map_${this.sceneData.mapFile}`,
      `/assets/maps/${this.sceneData.mapFile}.json`,
    )
    const sprites = [
      'player', 'wild_wander', 'wild_timid', 'wild_aggressive', 'wild_rare',
      'camp', 'chest', 'gather', 'exit', 'boss_door', 'npc', 'hidden_spot',
    ]
    for (const name of sprites) {
      this.load.image(name, `/assets/sprites/${name}.png`)
    }
  }

  create(): void {
    if (!this.sceneData) return
    this.scene.start('MapScene', this.sceneData)
  }
}
