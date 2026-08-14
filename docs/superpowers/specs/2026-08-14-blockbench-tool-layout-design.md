# Blockbench 开发工具目录整理设计

## 目标

将零安装的 Python/Blockbench 作者工具整理成一个自包含的开发工具区，使实现、入口、文档、示例、测试和共享夹具具有明确的 locality。此次整理允许破坏旧命令路径，不提供兼容转发脚本，不改变 Java 生产包、公开 API 或运行时行为。

## 目标目录

```text
tools/
└─ blockbench/
   ├─ README.md
   ├─ requirements.txt
   ├─ cli.py
   ├─ generate_spider_example.py
   ├─ casted_bb/
   │  ├─ __init__.py
   │  ├─ model.py
   │  ├─ pairing.py
   │  ├─ compression.py
   │  ├─ preview.py
   │  └─ commands.py
   ├─ examples/
   │  ├─ spider_body.bbmodel
   │  └─ andesite_spider_assembly_table.bbmodel
   ├─ test-resources/
   │  └─ fixtures/
   │     ├─ create/andesite_casing.png
   │     ├─ create_biotech/spider_assembly_table/
   │     └─ definitions/create_biotech/
   └─ tests/
      ├─ fixtures/
      │  ├─ minimal_model.json
      │  ├─ paired_valid.bbmodel
      │  └─ paired_invalid.bbmodel
      ├─ conftest.py
      ├─ helpers.py
      └─ test_*.py
```

## Module 与 seam

`tools/blockbench` 是完整作者工具 Module。删除该目录即可移除所有非生产期 Blockbench 能力，而不会把复杂度散回 Java 运行时。

- `cli.py` 是零安装命令入口，仅委托给 `casted_bb.commands.main`。
- `casted_bb` 是 Python 实现 Module；包名由 `casted_bb_lib` 简化而来。
- `commands.py` 负责命令参数、错误码和输出发布。
- `model.py` 负责标准 `.bbmodel` 数据访问。
- `pairing.py` 负责配对 UV 及验证。
- `compression.py` 负责确定性区域压缩；由原 `compress.py` 更名以避免动作名与 Module 名混淆。
- `preview.py` 负责直接映射与导出 JSON 的像素往返。
- `generate_spider_example.py` 是真实资源示例生成入口。
- `examples`、`tests`、`test-resources` 分别保存用户可打开产物、Python 专属测试数据和 Java/Python 共用夹具。

该结构维持小入口、深实现的 interface。调用者只需知道 `cli.py` 的三个命令和 `generate_spider_example.py`；内部文件移动不会扩大命令 interface。

## 共享测试资源

真实安山机壳、蜘蛛基础贴图、蜘蛛眼睛贴图和对应目标定义从 `src/test/resources/fixtures` 移入 `tools/blockbench/test-resources/fixtures`，避免 Python 工具反向依赖 Java 源码目录。

Gradle 的 `test` source set 只额外加入：

```text
tools/blockbench/test-resources
```

因此 Java 测试仍通过相同 classpath 路径 `/fixtures/...` 读取资源。生产 `jar` 不使用测试 source set，且现有 `tools/**` 排除规则继续阻止工具和夹具进入 JAR。

Python 专属的最小模型及有效/无效 `.bbmodel` 留在 `tools/blockbench/tests/fixtures`，不会与共享真实资源混合。

## 文档与示例

`docs/blockbench-authoring.md` 移为 `tools/blockbench/README.md`，命令、组约定与实际文件相邻。项目根 `README.md` 增加一个简短的作者工具链接，不复制完整说明。

根目录 `examples` 移入 `tools/blockbench/examples`。示例生成器只使用相对于 `tools/blockbench` 的路径，不再跨越到 `src/test/resources`。

新命令为：

```powershell
python tools/blockbench/cli.py init ...
python tools/blockbench/cli.py validate ...
python tools/blockbench/cli.py export ...
python tools/blockbench/generate_spider_example.py
python -m pytest tools/blockbench/tests -q
```

旧的 `tools/casted_bb.py`、`tools/generate_spider_example.py` 和根 `examples` 路径直接删除，不提供兼容入口。

## 行为保持

此次修改只移动和重命名开发文件并修正导入、资源和文档路径。以下行为保持不变：

- `.bbmodel` 初始化、验证、配对和导出规则；
- 确定性 schema-1 JSON；
- 固定坐标矩形压缩和稀疏像素输出；
- 自包含 Blockbench 预览；
- 蜘蛛示例的 11 个 Cube、66 个 Face 和 1,344 像素遮罩；
- Java 运行时类、公开 API、网络协议和生产资源。

## 验证

1. 从仓库根目录运行 `python -m pytest tools/blockbench/tests -q`。
2. 从 `tools/blockbench` 运行 `python -m pytest tests -q`，证明导入不依赖仓库根目录。
3. 运行 `python tools/blockbench/generate_spider_example.py`。
4. 用新 CLI 验证两个示例均有 66 个配对 Face。
5. 运行 `gradlew.bat clean test jar --offline`，确认全部 Java 测试通过。
6. 审计生产 JAR，不得出现 `tools/`、`.py`、`.bbmodel`、测试夹具或预览。
7. `git diff --check` 通过，且未跟踪的 `.vscode/` 保持不变。

## 非目标

- 不把 Python 工具制作成可安装包。
- 不保留旧命令或旧文件路径。
- 不调整 Java 包结构。
- 不改变映射算法、渲染逻辑或性能参数。
- 不把 Blockbench 工具或依赖加入生产 JAR。
