import Phaser from 'phaser'
import BootScene from './scenes/BootScene'
import MapScene from './scenes/MapScene'

/** 地图画布尺寸（25×19 格 × 32px）。 */
export const GAME_WIDTH = 800
export const GAME_HEIGHT = 608

/**
 * Phaser 游戏实例工厂（阶段 6）。
 * <p>
 * 核心 Scene 固定为 BootScene（资源加载）与 MapScene（地图表现），
 * 战斗表现 Scene（BattleScene）属后续阶段接入，总数不超过 3 个。
 * Phaser 只做展示与交互，业务数据一律经 bridge 由 Vue 层处理。
 */
export function createPhaserGame(parent: HTMLElement): Phaser.Game {
  return new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    width: GAME_WIDTH,
    height: GAME_HEIGHT,
    backgroundColor: '#101820',
    pixelArt: true,
    scene: [BootScene, MapScene],
  })
}
