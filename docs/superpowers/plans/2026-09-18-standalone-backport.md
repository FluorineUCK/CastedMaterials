# Standalone Backport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已验证的通用材质渲染与制作能力回迁到 Casted Materials 独立库，不自动接管 Biotech。

**Architecture:** 单个 NeoForge Mod，Java 根层/palette/mapping/client；统一 UV 解释器驱动缓存烘焙与直接 UV。Create 仅通过可选客户端桥接访问。Python 通用推断独立，旧配对工具单向复用共享算法。

**Tech Stack:** Java 21、Minecraft 1.21.1、NeoForge 21.1.234、ModDevGradle 2.0.141、JUnit 5.10.3、Python 3.10+、Pillow、pytest；Create 编译桥接 6.0.10-281。

## Global Constraints

- 不 commit、不 push，不修改原 Biotech 和原独立项目其他工作树。
- 实施目录 `D:/mymod/CastedMaterials/.worktrees/standalone-backport`，分支 `feature/standalone-backport`。
- 最新来源 `D:/mymod/Create-Biotech-Official-1.21.1/.worktrees/casted-materials-test`（下称 S），包括有效未提交测试。
- 旧修复来源 `D:/mymod/CastedMaterials/.worktrees/biotech-runtime`（下称 O）。
- Java 生产根包 `com.fluorineuck.castedmaterials`（下称 J），测试根包相同（下称 T）。
- 来源材质包 `com.nobodiiiii.createbiotech.foundation.render.material` 只做定向替换，不全仓替换 `create_biotech` 资源 ID。
- 保留 Minecraft 1.21.1、Java 21、schema 1、目标每边 1～256；源网格不套用目标上限。
- 保留原项目 `init/validate/export`，通用 `infer/preview` 不依赖 Blockbench。
- 不引入 Biotech 兼容注入，不内置帽子，不复制无关 gameplay 和 Sable/JarJar 构建。
- 使用来源清单核对；并发线程若改动来源，先记录新差异，不静默混用版本。
- 同一工作树只运行一个 Gradle 命令。pytest 使用 build 下唯一临时目录。
- 每项任务执行红→绿→回归；迁移现有逻辑可先迁现有测试，以缺失新包/接口或旧行为复现为红灯，不为证明失败故意损坏用户原工作树。

---

## 文件布局及职责

```text
src/main/java/com/fluorineuck/castedmaterials/
  CastedMaterials.java             Mod 入口、组件与事件注册
  CastedMaterialsApi.java          统一材质状态 API，所属小类型内嵌
  CastedMaterialState.java         可保存/同步的材质状态
  palette/                        来自 S 的四个材质表文件
  mapping/                        来自 S 的十个定义/UV 文件
  client/                         来自 S 的客户端文件
    CreateCasingTextures.java      唯一 Create CT 桥接
src/test/java/com/fluorineuck/castedmaterials/
  client/                         运行时、缓存、来源、像素/几何回归
  mapping/                        解析/尺寸/UV/光栅化回归
  palette/                        发现、同步、排序回归
  integration/                    Python 输出互操作
  fixtures/SpiderTestModel.java    仅测试的蜘蛛模型
src/createCompatTest/java/.../client/CreateCasingTexturesTest.java
                                   可选依赖的实际 Create 注册表测试
src/main/resources/META-INF/neoforge.mods.toml
                                   只声明独立 Mod 和可选 Create
scripts/verify_standalone.py       构建产物内容校验；不是运行时模块
tools/material_mapping/           通用推断、采样、预览和共享校验
tools/blockbench/                 已配对工程编辑/导出，不含旧 infer 实现
tools/test-resources/             Java/Python 共享来源与模型夹具
tools/resourcepacks/connected_spider/  显式测试资源包，不打进生产 JAR
```

`src/createCompatTest` 是测试 source set，不是生产子模块。它使默认单元测试保持无 Create，另一个测试任务验证真实 CT 注册行为。

## 已完成的准备

- [x] 检查 `.worktrees` 已被 Git 忽略。
- [x] 从 `296e7f7` 新建隔离分支/工作树；原脏工作树未覆盖。
- [x] 旧 Java 基线：61 项通过，日志 `build/backport/baseline-java.log`。
- [x] 旧 Python 基线：26 项通过。
- [x] 写入回迁设计；任务 1～5 已实施并完成计划内验收，实际结果见 `build/backport/sdd/task-*-report.md`。

## Task 1: 通用 Python 推断与夹具回迁

**Files:**
- 从 S 复制 `tools/material_mapping/`、`tools/tests/`、`tools/test-resources/`、`tools/__init__.py`（若来源存在）、`tools/requirements.txt`。
- 保留原 `tools/blockbench`，不在本任务删除它。
- 修改 `tools/tests/test_tool_layout.py`，新预期为“通用模块不依赖 Blockbench”，而不是“仓库不得存在 Blockbench”。

**Interfaces:**
- `tools.material_mapping.cli.main(argv)` 是通用 CLI。
- `compression.MappedPixel`、`compression.compress_pixels`、`sampling.render_pixels`、`sampling.render_definition` 为任务 2 复用入口。

- [x] 先复制通用 Python 测试与测试资源，运行测试，确认新模块缺失是失败原因。

```powershell
python -B -m pytest -q -p no:cacheprovider --basetemp build/backport/red-infer tools/material_mapping/tests tools/tests
```

- [x] 复制对应实现，排除缓存。复制规则可直接使用以下脚本主体：

```python
from pathlib import Path
import shutil
S = Path(r'D:/mymod/Create-Biotech-Official-1.21.1/.worktrees/casted-materials-test')
W = Path(r'D:/mymod/CastedMaterials/.worktrees/standalone-backport')
for relative in ('tools/material_mapping', 'tools/tests', 'tools/test-resources'):
    shutil.copytree(S / relative, W / relative, dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('__pycache__', '*.pyc', '.pytest_cache'))
shutil.copy2(S / 'tools/requirements.txt', W / 'tools/requirements.txt')
```

- [x] 将布局测试中禁止整个 `tools/blockbench` 存在的断言改成依赖方向检查；保留模块方式和绝对路径脚本方式、任意 cwd 的 CLI 测试。

```python
import ast
from pathlib import Path

def test_generic_mapping_has_no_blockbench_dependency():
    folder = Path(__file__).resolve().parents[1] / 'material_mapping'
    for path in folder.glob('*.py'):
        tree = ast.parse(path.read_text(encoding='utf-8'))
        for node in ast.walk(tree):
            names = ([node.module or ''] if isinstance(node, ast.ImportFrom)
                     else [alias.name for alias in node.names] if isinstance(node, ast.Import) else [])
            assert not any('blockbench' in name or 'casted_bb' in name for name in names), path
```

- [x] 跑通模糊匹配、草稿、外接框、限时选区、模型约束、眼/body 预览及 RGBA/路径安全回归。

```powershell
python -B -m pytest -q -p no:cacheprovider --basetemp build/backport/green-infer tools/material_mapping/tests tools/tests
```

- [x] 确认报告仍分别输出 `source_bounds_optimization` 和 `selection_optimization`，退出码 0/3/2 未变。记录通过数量，不预设为来源总数。

## Task 2: 旧配对工具复用共享算法及确定修复

**Files:**
- 修改 `tools/blockbench/casted_bb/commands.py`、`pairing.py`、`preview.py`。
- 删除 `tools/blockbench/casted_bb/compression.py` 的重复算法并更新所有导入；不能留第二份算法实现。
- 修改 `tools/blockbench/cli.py` 与 `generate_spider_example.py` 的直接脚本导入路径。
- 从 O 合并 `tests/test_export.py`、`test_init.py`、`test_validate.py`、`test_spider_example.py`、`test_mapping_contract.py`、`test_preview_sampling.py` 及所需 helpers/fixtures；不复制 O 的旧 infer 实现。

**Interfaces:**
- 原 `cli.py init/validate/export` 参数继续可用。
- `expand_pairs` 留在 Blockbench；压缩、变换和采样从通用包直接导入。
- 在 `sampling.py` 新增并共用 `validate_rgba_equal(actual, expected)`。

- [x] 先导入 O 的失败回归：同 Alpha 不同 RGB、同 RGB 不同 Alpha、旧输出不变、目标上限以及大源图；在旧实现上确认预期失败。
- [x] 提取 O 配对工具的修复，不复制其 `_infer`、推断参数和推断模块。把通用算法导入改为：

```python
from tools.material_mapping.compression import MappedPixel, compress_pixels
from tools.material_mapping.sampling import (
    inverse_transform, render_definition, render_pixels,
    validate_target_size, validate_rgba_equal,
)
from tools.material_mapping.cli_support import (
    _computed_cost, _json, _parse_grids, _parse_size,
    _parse_sources, _png_bytes, _publish_outputs, _same_path,
)
```

- [x] 两个入口共用以下严格校验；只在全部校验后发布输出：

```python
def validate_rgba_equal(actual, expected):
    if (actual.mode != 'RGBA' or expected.mode != 'RGBA'
            or actual.size != expected.size or actual.tobytes() != expected.tobytes()):
        raise ValueError('preview round-trip differs in mode, dimensions or RGBA bytes')
```

- [x] `pairing.validate_model` 调通用目标尺寸校验，把 ValueError 包装为原 `ValidationError`；不限制源宽高到 256。
- [x] 在直接脚本入口将仓库根加入 `sys.path`（按 `__file__` 找根，不依赖 cwd），模块入口保持不变。
- [x] 合并后运行两套工具全量测试，确认重复导出 JSON/PNG 一致、非对称旋转/镜像、稀疏像素和中心采样一致。

```powershell
python -B -m pytest -q -p no:cacheprovider --basetemp build/backport/paired-green tools
python -B tools/blockbench/cli.py --help
python -B -m tools.material_mapping infer --help
```

## Task 3: 精简独立 Java 运行时与可选 Create（同一可编译交付）

### 3A. 运行时、状态和通用回归

**Files:**
- 用 S 的材质包迁换 J，包前缀定向替换；`MaterialRenderingModule` 收入 `CastedMaterials` 入口。
- 删除新工作树内被替代的旧 API/atlas/cache/network/definition/material 分层文件，清单以原 Git 跟踪文件为依据。
- 迁 T 根层、mapping、palette 及不依赖宿主的 client 测试；来源 `ProjectMetadataTest`/`MaterialRenderingModuleTest` 改成独立库预期，不直接复制旧断言。
- 修改 `build.gradle` 测试资源路径，同时包含共享新夹具与需要的原配对夹具。

**Interfaces:**
- `CastedMaterials.MOD_ID = "casted_materials"`；组件资源 ID `casted_materials:material`。
- API 的所属 `CastedMaterialHolder`、`MaterialSetResult` 保持当前 S 的内嵌类型，不新建转发文件。
- `CastedMaterialsClient.resolveModel(...)` 和 `CastedModelHandle` 方法按设计保持。

- [x] 先迁测试，按缺少新包/入口确认红灯；不可通过删除状态/资源/缓存断言得到绿灯。
- [x] 生产包迁移仅做以下明确替换，再逐文件检查剩余宿主依赖：

```python
text = text.replace('com.nobodiiiii.createbiotech.foundation.render.material',
                    'com.fluorineuck.castedmaterials')
text = text.replace('com.nobodiiiii.createbiotech.CreateBiotech',
                    'com.fluorineuck.castedmaterials.CastedMaterials')
text = text.replace('MaterialRenderingModule', 'CastedMaterials')
text = text.replace('CreateBiotech.MOD_ID', 'CastedMaterials.MOD_ID')
```

- [x] 入口改为独立 `@Mod`，沿用原构造注册方式；现有注册方法的语义不丢失：

```java
@Mod(CastedMaterials.MOD_ID)
public final class CastedMaterials {
    public static final String MOD_ID = "casted_materials";
    public static final String OWNER_MOD_ID = MOD_ID;
    public static final String DATA_NAMESPACE = MOD_ID;
    // 保留来源模块的 MATERIAL、CASING_ITEMS、CASING_BLOCKS 定义及 id/register 方法。
    public CastedMaterials(IEventBus modBus) {
        register(modBus);
    }
}
```

上面的保留项从 S/MaterialRenderingModule.java 原样迁入，不重新发明注册逻辑。`CastedClientEvents` 订阅者归属改为独立 Mod。

- [x] ComposedTextureCache 动态资源归属独立 Mod。保留缓存上限、错误释放、不按帧重试、重载释放和渲染线程检查。
- [x] 保留专用身体优先、完整快照清空、眼部候选、透明眼部、所有 FALLBACK 无附件/发光等行为。
- [x] 3B 与 3A 同一验证批次完成，之后执行下列无 Create 单元测试，避免暂时引入必需依赖：

```powershell
./gradlew.bat test --offline --console=plain
```

Task 3 的两部分共同构成可编译独立运行时；期间的预期编译失败不得标成已通过。

### 3B. 可选 Create 查询与原生附件几何

**Files:**
- 新增 J/client/CreateCasingTextures.java。
- 修改 J/client/MaterialTextures.java、MaterialAttachments.java。
- 修改 T/client/MaterialTextureDiscoveryTest.java、MaterialAttachmentsTest.java。
- 新增 `src/createCompatTest/java/com/fluorineuck/castedmaterials/client/CreateCasingTexturesTest.java`。
- 修改 `build.gradle`、`gradle.properties`、`neoforge.mods.toml`。

**Interfaces:**
- `MaterialTextures.CasingSource(boolean registered, TextureAtlasSprite sprite)` 区分未注册与已注册但缺失。
- `CreateCasingTextures.find(BlockState state)` 返回该记录；Create 类型只能出现在此桥接类和兼容测试中。
- `MaterialTextures.findDefault(BlockState, Function<BlockState,CasingSource>, Function<BlockState,TextureAtlasSprite>)` 是无 Create 可测入口。

- [x] 把来源查询的五个测试改成注入记录，保留所有优先级/缺失/像素来源断言；实际 Create 注册测试单独放到 createCompatTest。

```java
var registeredMissing = new MaterialTextures.CasingSource(true, null);
assertTrue(MaterialTextures.findDefault(Blocks.STONE.defaultBlockState(),
    state -> registeredMissing,
    state -> { fail("A registered missing target must not fall through"); return null; }
).isEmpty());
```

- [x] 生产侧只在 `ModList.get().isLoaded("create")` 为真时调用桥接；其他情况返回 `new CasingSource(false, null)`。桥接实现调用 `CreateClient.CASING_CONNECTIVITY.get(state)`，有 entry 就返回其 target，缺 entry 返回未注册；不捕获并伪装成“未注册”。
- [x] `findDefault` 选择 `registered ? sprite : particleLookup.apply(state)`，后续 missing sprite 和尺寸校验沿用原逻辑。
- [x] Create 使用 `compileOnly` 且 `transitive=false`，兼容测试 source set 的运行依赖才包含它及必要的 Create 测试依赖。默认 main/runtimeClasspath 与 test/runtimeClasspath 不额外并入 main/compileClasspath，以免 compileOnly 泄漏。
- [x] `createCompatTest` 复用 NeoForge 测试 bootstrap 配置但使用自身 source set/classpath；任务与默认 test 分开。Gradle 配置验收必须打印两条 runtimeClasspath 并断言 Create 只出现于兼容测试路径。
- [x] 模型附件移除 BakedQuadHelper：按 `DefaultVertexFormat.BLOCK` 的真实布局取 stride/元素偏移，读取 `Float.intBitsToFloat` 的 XYZ/UV 和有符号法线分量；不把零法线偷偷等同某轴。
- [x] 附件测试构造非对称真实 BakedQuad，验证正负 XYZ、不同 U/V、正负法线、实时部件变换及隐藏行为。原 helper 的测试填充调用一并替换，不让通用测试依赖 Create。
- [x] 默认 Java 测试与兼容测试均运行，实际 CT 使用非 `_connected` 名称、注册缺失、未注册粒子三种样例。

```powershell
./gradlew.bat test createCompatTest --offline --console=plain
```

## Task 4: 蜘蛛夹具与跨语言/后端一致性

**Files:**
- 迁 T/client/ComposedRuntimeTest.java、ProductionUvParityTest.java，T/integration 的四个互操作测试。
- 新增 T/fixtures/SpiderTestModel.java，测试资源移到 `tools/test-resources`。
- S 中 `MachineCreatureModels.spider()` 所需的模型构建代码只提取到测试类，不复制整个宿主渲染库。
- 对 Biotech item/BE/IR 相关测试，把能表达为 `CastedModelHandle` 的断言转为通用句柄测试；不为旧反射测试复制真实宿主类。

**Interfaces:**
- `SpiderTestModel.create()` 返回新的 Minecraft `ModelPart`，保持来源蜘蛛的 11 个 cube / 66 个 quad 和部件名。
- 所有生产资源读取路径改为测试夹具根，不从已删除的 Biotech src/main/resources 读取。

- [x] 先迁原像素/缓存测试，使缺失夹具、宿主依赖显式失败，再替换成独立夹具。
- [x] 完整迁入这十组来源：andesite/brass/copper/railway/shadow_steel/refined_radiance/biotech/explosion_proof/explosion_proof_side/asurine；素材名保持来源，不静默重新绘制。
- [x] 保留测试用独立 `PixelReference`，不得直接调用同一生产光栅器作为唯一期望值。
- [x] 专用 andesite 断言无需自动源；其余可合成材质断言 66 body quads、264 vertices、1 body texture batch；眼部发光仅提交残留区域。
- [x] 用直接句柄发光调用替换 Biotech 私有桥接反射，保留上传的 emission 资源与非空发光顶点断言；FALLBACK 必须没有任何发光顶点。

```java
var handle = CastedModelHandle.fallback(SpiderTestModel.create(), TestIds.id("base"));
handle.renderEmissive(new PoseStack(), type -> {
    fail("FALLBACK must not request an eye buffer");
    return null;
}, 15728640, 0, -1);
```

- [x] 跑原导出 JSON、精确推断、模糊推断的 Java 解析及逐像素比较；保持确定性导出测试，优化超时测试不依赖准确墙钟耗时。

```powershell
./gradlew.bat test --tests '*ComposedRuntimeTest' --tests '*ProductionUvParityTest' --tests '*CompatibilityTest' --offline --console=plain
python -B -m pytest -q -p no:cacheprovider --basetemp build/backport/interop tools
```

## Task 5: 打包、说明与最终验收

**Files:**
- `README.md`、`tools/README.md`、`tools/material_mapping/README.md`、`tools/blockbench/README.md`、夹具/示例 README。
- `scripts/verify_standalone.py`、构建校验任务、独立 Mod 发行元数据及必要的资源许可说明。

- [x] 编写 JAR 清单校验，挂到 Gradle check。检查一个 `casted_materials` Mod；禁止 `com/nobodiiiii/`、`com/simibubi/`、旧 atlas/cache/texture-handle 类、`META-INF/jarjar/`、`assets/create_biotech/`、Python、bbmodel、测试夹具。检查必要新类与许可条目存在。

```python
from zipfile import ZipFile
from pathlib import Path
import sys
with ZipFile(Path(sys.argv[1])) as jar:
    names = jar.namelist()
    forbidden = ('com/nobodiiiii/', 'com/simibubi/', 'META-INF/jarjar/',
                 'assets/create_biotech/', 'tools/', 'fixtures/')
    assert not any(name.startswith(forbidden) or name.endswith(('.py', '.bbmodel')) for name in names)
    assert 'com/fluorineuck/castedmaterials/CastedMaterials.class' in names
    assert 'com/fluorineuck/castedmaterials/client/ComposedTextureCache.class' in names
```

- [x] README 展示 resolveModel → render/renderEmissive 最小接入；说明旧 Java API 替换、无 Biotech 自动适配、Create 可选、运行时资源包与服务端标签区别。
- [x] 修正工具文档中“游戏只走直接 UV”“Blockbench 目录已移除”等不再符合独立库的文字；明确 CM_FIXED 实际保留范围、旧配对入口与新推断入口。
- [x] 完整执行并记录命令、退出码及 JUnit/pytest 实际数量：

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-21.0.11'
$env:PATH="$env:JAVA_HOME/bin;$env:PATH"
./gradlew.bat test createCompatTest build --offline --console=plain
python -B -m pytest -q -p no:cacheprovider --basetemp "build/backport/final-$([guid]::NewGuid().ToString('N'))" tools
```

- [x] 在不放 Create 的独立 client/server 运行目录做启动验证；只操作此工作树测试环境，不覆盖用户整合包。不自行接受新的协议提示；若环境使启动验证无法完成，明确列为未验证项，不当作成功。
- [x] 检查源码残留宿主导入、旧实现重复、意外配置项及运行时 `_connected` 猜名逻辑；允许测试输入文件本身含该文件名。
- [x] 与 `build/backport/source-inventory.json` 对照两边来源内容；用户并行变更只报告，不覆盖或回滚。
- [x] `git diff --check`，列修改清单、已删除实现的替代关系、测试结果、未验证项。保持未提交，不执行 git add/commit/push。

## 自查与执行交接

范围、尺寸、依赖、角色命名、后端回退、工具兼容、源码未提交测试和产物排除均有对应任务。
可选 Create 拆分与运行时迁移属于同一个 Task 3；各任务均有自己的验证入口。
执行前审核本计划及同日设计；执行方式可选 subagent-driven-development 或当前任务内 executing-plans。
