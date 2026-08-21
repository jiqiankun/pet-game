import type { MapEnterView } from '../types/map'
import type { MapSceneData } from './bridge/GameBridge'

/** 将地图接口返回的当前状态转换为 Phaser 初始化载荷，供探索页与世界地图复用。 */
export function toMapSceneData(
  view: MapEnterView | null,
  defeatedWildIds: string[],
): MapSceneData | null {
  if (!view) return null
  return {
    mapId: view.mapId,
    mapFile: view.mapFile,
    spawnObjectId: view.spawnObjectId,
    consumedChestIds: [...view.consumedChestIds],
    usedGatherIds: [...view.usedGatherIds],
    activatedCampIds: [...view.activatedCampIds],
    defeatedWildIds: [...defeatedWildIds],
  }
}
