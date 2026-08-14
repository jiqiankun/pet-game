# Batch 0 / A0 质检记录

执行日期：2026-08-14  
工具：内建 imagegen

| 锚点 | 候选 | 结果 | 结论 |
|---|---|---|---|
| 烬牙兽 `PET_FIRE_001` | 3 | 轮廓、全身构图、火元素设计通过；绿幕候选经本地去溢色后四角透明、无绿幕残留 | accepted / exported |
| 汐月灵 `PET_WATER_001` | 2 | 轮廓与水元素、月牙潮汐环设计通过；洋红幕候选经本地去溢色后四角透明、无幕色残留 | accepted / exported |
| 岩魁 `PET_EARTH_001` | 1 | 轮廓、全身构图、土晶核心、普通稀有度表现通过；绿幕去背后四角透明、无绿幕残留 | accepted / exported |

## 结论

A0 通过。正式资源已导出到 `frontend/public/assets/pets/portraits/`，后续 B01~B08 将以此三张资源为画风参考。抠图流程为绿幕/洋红幕 + 本地 alpha 蒙版、去溢色和透明角校验，不需要 API Key。
