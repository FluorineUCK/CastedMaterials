# 安山机壳覆盖蜘蛛装配台测试设计

## 目标

用真实的 Create 安山机壳纹理覆盖 Create Biotech 蜘蛛装配台的 64×32 蜘蛛基础贴图，证明 `MappingComposer` 能在不切分或增加蜘蛛模型顶点的前提下生成可供自定义渲染器使用的材质结果。

## 证据与夹具

- 安山机壳源：Create `create:block/andesite_casing`，16×16、完全不透明；测试夹具 SHA-256 为 `C7DA24AACFB6170EBD570E5A302A6C9D3F3C0B324314E3DF79236209667F192A`。
- 蜘蛛基础贴图：Create Biotech `create_biotech:textures/entity/spider_assembly_table/spider.png`，64×32；测试夹具 SHA-256 为 `93D330A24BDA6316DB42CD6198120F5A540F591305DB671A599CA966888A4704`。
- 蜘蛛眼睛贴图仍由 Biotech 的第二个 `RenderType.eyes` 渲染调用负责，不写入 Casted Materials 的基础输出。
- 原版 `SpiderModel#createSpiderBodyLayer` 使用 64×32 图集，包含 3 个身体 Cube 和 8 个腿部 Cube，共 11 个 Cube。

所有第三方 PNG 只进入 `src/test/resources`，不得进入生产 JAR。

## 固定坐标映射

目标定义使用 `source_grids.all = [16,16]`。根据真实蜘蛛基础贴图的非透明 UV 岛，以固定矩形覆盖 64×32 输出；宽度超过 16 的区域拆成连续矩形。矩形只复用安山机壳左上角的同尺寸像素，不创建逐像素映射。

验收时逐像素比较遮罩：输出非透明像素必须与原蜘蛛贴图非透明像素完全一致。每个输出区域的颜色必须等于其声明源矩形中的安山机壳颜色。

## 断言

1. 三张夹具的尺寸和 SHA-256 与上述证据一致。
2. 目标 JSON 经严格解析器成功加载，合成状态为 `READY`。
3. 输出为 64×32，非透明像素数为 1,344，且遮罩与原蜘蛛基础贴图完全一致。
4. 每个映射目标像素与安山机壳源像素相等；至少 1,000 个非透明像素不同于原蜘蛛颜色，证明不是原图透传。
5. `CastedTextureHandle.wrap` 处理 264 个蜘蛛 Cube 顶点后，委托消费者仍恰好收到 264 个顶点。
6. 测试在 `build/casted-materials-preview/andesite_spider.png` 写出基础结果，并写出仅供观察的 `andesite_spider_with_eyes.png`；两者不纳入生产 JAR。

## 错误处理

夹具缺失、尺寸变化、哈希变化、坐标不再覆盖全部 UV 岛、合成降级或输出路径无法写入都使测试失败。这样上游资源变化不会静默产生错误外观。

