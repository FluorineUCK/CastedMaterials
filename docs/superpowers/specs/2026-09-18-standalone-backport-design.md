# Casted Materials 独立项目回迁设计

日期：2026-09-18。聊天中的范围与架构已获用户确认；本文供实施前审核。
本次不 commit、不 push，不修改 Biotech 源工作树及原独立项目的其他工作树。

## 1. 目标与边界

把 Biotech 中已经有效的通用材质能力、离线制作工具及本地未提交测试，回迁为 Minecraft 1.21.1 / NeoForge 独立库。
不接管上游 Biotech 的任何渲染，不增加兼容注入、Mixin、物品注册或蜘蛛方块实体。
保留蜘蛛作为离线/Java 测试样例。库的使用者主动提供模型、目标 ID、材质 ID 和基础贴图。

选择“迁回当前精简实现并解除宿主依赖”，不选择在旧图集运行时上叠加新实现，也不整仓复制 Biotech。

## 2. 工作树与来源

- 实施：`D:/mymod/CastedMaterials/.worktrees/standalone-backport`，分支 `feature/standalone-backport`。
- 分支基点：独立项目 `feature/biotech-runtime` 的 `296e7f799e06377bce0ff21b7db75b904bd80945`。
- 最新通用能力：`D:/mymod/Create-Biotech-Official-1.21.1/.worktrees/casted-materials-test`，已提交 HEAD 为 `ada77a09f5a4211aac6ef14c776a08dec7721e83`；同时检查其未提交文件。
- 旧工具修复补充来源：`D:/mymod/CastedMaterials/.worktrees/biotech-runtime` 的未提交修改。
- 原项目根目录修改过的 Blockbench README 作为内容来源核对，不直接覆盖。
- 忽略构建输出、缓存、IDE 配置及无关本地文件。来源文件摘要放在本工作树 `build/backport/source-inventory.json`，不打包。

2026-09-18 回迁前基线：`gradlew test --offline --console=plain` 成功，61 tests / 0 failures / 0 errors；原 Blockbench Python 测试 26 passed。
这些是旧版本基线，不代表回迁后验收。

## 3. 独立库结构

生产包统一为 `com.fluorineuck.castedmaterials`：

- 根层：`CastedMaterials` 独立 Mod 入口、组件/事件注册，`CastedMaterialsApi` 与 `CastedMaterialState`。
- `palette`：材质发现、排序、验证、服务端/客户端快照及同步。
- `mapping`：定义解析、目标尺寸、统一 UV 解释器、计划编译和像素容器。
- `client`：模型捕获/绑定、资源加载、来源查询、后端选择、一次烘焙缓存、模型附件及可选 Create 桥接。

不新增独立 Gradle 子模块，不恢复旧 `atlas/cache/failure/client.definition/client.render/client.uv` 多层目录。
旧 `CastedTextureHandle`、`ClientRuntime`、分页图集及上传队列不与新运行时并存；文档给出新模型句柄接入方式，不承诺旧 Java 二进制 API 兼容。
Mod ID、数据组件和网络资源保持 `casted_materials`。不实现旧存档迁移。

## 4. 公共行为与数据流

宿主调用 `CastedMaterialsClient.resolveModel(ModelPart root, ResourceLocation targetId, ResourceLocation materialId, ResourceLocation fallbackTexture)` 得到 `CastedModelHandle`，分别调用 `render` 与 `renderEmissive`。

资源包目标定义 → 材质表/显式 slot → 专用身体或 UV 映射 → 选定眼部/有序层 → 后端计划与缓存 → 随实时部件姿态提交顶点。

- 目标定义路径：`assets/<namespace>/casted_materials/targets/<path>.json`，沿用 schema 1。
- 显式源配置、材质覆盖和资源包覆盖仍生效；本地 PNG 路径不是运行时资源 ID。
- 目标每边 1～256；源逻辑网格不套用目标上限。整数等比高清和逻辑采样约定保留。
- 数据包通过 item/block `create:casing` 配对标签影响材质发现；客户端映射与纹理由资源包提供，二者不能混称。
- 材质状态完整快照缺少字段时清空旧状态；客户端与服务端类加载隔离。

## 5. 渲染与失效

- 静态、可准确取样且存在几何收益的映射优先一次烘焙，并缓存最终贴图；每帧只提交原模型几何，不重新推断或合成。
- 专用身体无需额外复制；动画、高清、重采样或无法取得正确像素时保留直接 UV，不冻住动画或偷偷降采样。
- 上传失败或缓存容量不足时，如果直接 UV 仍合法，则退回直接 UV。
- 两种路径均不满足要求、缺失材质组件/目标/源或映射被拒绝时，统一 `FALLBACK`：仅绘制宿主基础模型/贴图，不追加通用眼睛、发光或附件。
- 默认保留现有 128 项 / 16 MiB 像素载荷缓存限制；不新增客户端后端配置。
- 缓存跨模型实例复用可复用的材质结果；资源重载立即释放动态纹理、清空相关缓存。
- 不淘汰当前批次仍可能引用的纹理；上传保持渲染线程约束。
- 使用 sprite 的实际 atlas 像素来源，不根据资源 ID 猜测可能不对应的 PNG。

## 6. 专用贴图、眼部与层

- 有效专用 `body_<casing>` 优先于自动生成，不要求先成功解析自动来源；body 不参与编号循环。
- `eye_` 和 `body_` 严格分角色，先命名空间限定变体，再无命名空间限定变体；不匹配无前缀素材。
- 显式 JSON 选择优先。没有专用眼部时，`eye_<非负整数>` 按数字排序，按同步材质索引取模固定分配。
- 有效全透明眼部是明确选择，不继续寻找通用眼睛。选定眼部替换默认眼部，不把两张眼睛叠加。
- 保留 index 稳定升序的 cutout 层、可配置的模型附件能力；不内置帽子或其他蜘蛛装饰。

## 7. Create 是可选依赖

编译桥接针对现有来源使用的 Create `6.0.10-281`；不将 Create、Flywheel、Ponder、Registrate、Sable 或 Biotech 打包进库。

- 只有检测到 Create 已加载时，才进入隔离的客户端 CT 桥接类。
- 有 CT 注册：返回注册的 target sprite；注册存在但 target 缺失，直接认定来源缺失，不回退粒子。
- 没有 CT 注册或没有安装 Create：使用方块模型的粒子 sprite。
- 不恢复 `_connected` 文件名猜测，也不扫描“最大模型面”。
- 明确的 slot 配置优先于自动发现。
- `MaterialAttachments` 不再使用 Create `BakedQuadHelper`，按 Minecraft BLOCK 顶点格式读取坐标、UV 和有符号法线字节；使用非轴对称/负法线测试防止误读。
- 没有 Create 的客户端、专用服务端都必须能加载独立库。

## 8. Python 与原配对工具

`tools/material_mapping` 作为通用 `infer/preview` 入口，迁入：精确 RGBA、RGB 容差、模型面约束、显式草稿、候选源外接矩形优化、固定 UV 选区数量限时优化、完整 RGBA 往返校验与安全发布。

两个最优性报告分开：源外接矩形面积总和，与固定已解决映射的 `regions + pixels` 数量。超时结果只称当前最好方案，不夸称全局最优。RGB 容差不放宽 Alpha 或导出往返校验。

原项目 `tools/blockbench` 只保留手工配对 `init/validate/export` 和原有编辑器预览能力，不成为通用推断的依赖。
共用 `material_mapping` 的压缩、采样、目标尺寸、RGBA 校验和输出发布，不维护第二份算法。
保留通用 `infer --model` 最小 JSON/Cube 读取器，不要求安装 Blockbench。

回迁旧配对工具已经发现的修复：RGB/Alpha 任一变化都必须在发布前失败；256×256 通过，任一目标边 257、512、0、负数失败；源尺寸可超过 256；已有输出在校验失败时不变。
`CM_FIXED` 只按既有编辑工程规则保留，不能宣称自动成为运行时固定覆盖层；要说明实际导出边界。

## 9. 测试、资源与文档

- 通用 Java 测试迁入，包括源树未提交测试，不把原来源的测试总数当成新库必须凑齐的数量。
- Biotech 宿主行为测试不复制注册/渲染器依赖；保留可转化为句柄行为的断言，移除无关玩法断言并记录理由。
- 10 组机壳像素/几何对照、专用安山身体、眼部替换、无眼部 FALLBACK、缓存复用/释放作为独立夹具验证。
- 夹具模型只在测试源码，纹理/目标样例在 `tools/test-resources` 或明确的示例资源包；不进入生产 JAR。
- 改写 README、工具 README、样例说明，解释独立 API、可选 Create、资源包/数据包、预览与运行时缓存的区别。
- 保留迁入资源的出处及现有许可说明；不把整个 Biotech 构建/发行元数据复制过来。
- 生产 JAR 必须只有独立 Mod、通用类和必要元数据，不含 Biotech 类、蜘蛛资源、Python、测试夹具、嵌套依赖或旧运行时类。

验收：Java 测试与 build；完整 Python 测试；Python 输出可被 Java 解析；无 Create 的类加载/启动、有 Create 的注册表查询；生产 JAR 内容检查；检查两个原工作树内容未被改变。
游戏启动未执行、环境受阻或未完成的项目必须单列，不用单元测试代称游戏实测。

## 10. 自查

- 已覆盖用户确认的通用库/工具/测试范围，无 Biotech 自动适配。
- 两条渲染路径共用 UV 解释器；保留的是新精简烘焙缓存，不是恢复旧图集实现。
- Create 可选与模型附件去依赖一致。
- 配对工具保留和通用工具独立不冲突：旧工具单向依赖共享算法。
- 已区分旧基线结果与实施后验收；没有声称回迁已完成。
