import Phaser from 'phaser'
import type { MapSceneData } from '../bridge/GameBridge'

/**
 * 资源加载场景（阶段 6，核心 Scene 1/2）。
 * <p>
 * 只负责加载 Tiled 地图 JSON、地块图集与占位精灵，然后启动 MapScene。
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
    this.load.image('tileset', '/assets/maps/tileset.png')
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
