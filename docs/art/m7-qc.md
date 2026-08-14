# Batch 7 资源 QC

## 资源范围

- 道具：35 个 `256×256` 透明 PNG，保留 7 个底图源文件；其中 33 个为计划定义的属性或规格派生变体。
- 背景：6 张区域战斗背景与 1 张主页背景，均为 `1920×1080` PNG；6 张区域缩略图为 `256×256` 派生 PNG。
- 事件：6 张 `512×512` 随机事件插画，对应 `random-events.yml` 的全部事件 ID。

## 目检

- `m7-items-contact-sheet.png`：药剂、捕捉球、元素结晶、Boss 核心和技能书变体清晰可区分，透明底图边缘干净。
- `m7-background-contact-sheet.png`：村庄、草原、森林、水域、雷域、遗迹和主页背景色调与区域主题一致，未见文字或水印。
- `m7-event-contact-sheet.png`：六张插画均与实际事件文案对应，未见文字或水印。

## 页面接入

- 背景：`BattleView`、`HomeView`、`WorldMapView`。
- 道具：`InventoryView`、`ShopView`、`BattleView` 捕捉球选择。
- 事件：`ExploreView` 随机事件弹窗。
