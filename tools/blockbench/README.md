# Blockbench 材质映射工作流

Python 工具只用于开发和资源制作，不会进入 Casted Materials 生产 JAR。

## 直接查看安山机壳蜘蛛装配台

在 Blockbench 中打开：

```text
tools/blockbench/examples/andesite_spider_assembly_table.bbmodel
```

该文件包含 11 个蜘蛛 Cube、66 个 Face，以及内嵌的 64×32 安山机壳合成贴图，不依赖外部图片即可显示。`CM_SOURCE[all]` 默认隐藏；需要检查双 UV 时可在 Outliner 中打开它。

重新生成示例：

```powershell
python tools/blockbench/generate_spider_example.py
```

生成内容：

- `tools/blockbench/examples/spider_body.bbmodel`：可编辑的 `CM_DEST`/`CM_SOURCE[all]` 双 UV 工程；
- `tools/blockbench/examples/andesite_spider_assembly_table.bbmodel`：可直接查看的自包含材质预览；
- `build/casted-materials-preview/spider_body_andesite.json`：压缩目标定义；
- `build/casted-materials-preview/andesite_spider_blockbench.png`：64×32 合成贴图。

## 通用工程

从标准 Blockbench JSON/`.bbmodel` 创建配对工程：

```powershell
python tools/blockbench/cli.py init `
  --model model.bbmodel `
  --target example:block_or_item `
  --size 64x32 `
  --slots all `
  --source-grid all=16x16 `
  --output tools/blockbench/examples/model_paired.bbmodel
```

组约定：

- `CM_DEST`：Face UV 表示目标贴图坐标；
- `CM_SOURCE[slot]`：同名元素、同方向 Face 的 UV 表示材质源坐标；
- `CM_FIXED`：编辑器内保留的原始 Face；`init` 会克隆这些 Face 并保留原纹理引用；
- 元素几何坐标不参与映射，源副本可在三维视图中移动。

验证工程：

```powershell
python tools/blockbench/cli.py validate tools/blockbench/examples/model_paired.bbmodel
```

导出定义、PNG 和可直接打开的 Blockbench 预览：

```powershell
python tools/blockbench/cli.py export tools/blockbench/examples/model_paired.bbmodel `
  --source all=assets/example/textures/block/casing.png `
  --target-json build/casted-materials-preview/model.json `
  --preview build/casted-materials-preview/model.png `
  --blockbench-output build/casted-materials-preview/model_preview.bbmodel
```

导出使用整数像素和最近邻采样，将连续映射压缩为矩形，仅将孤立修正写入 `pixels`。验证、预览往返比较全部成功后才会原子替换目标 JSON。

`CM_FIXED` 只保证配对工程和生成的 Blockbench 预览中仍能看到原始艺术：预览仅把 `CM_DEST`
的纹理引用改到生成贴图，并让 `CM_FIXED` 保持可见及原引用。`export` 只展开配对 Face，**不会**把
`CM_FIXED` 自动烘焙为目标 JSON/PNG 的运行时 overlay。需要固定运行时图层时，应另行制作透明 PNG，
并在 target 定义中显式配置 `overlay` 或有序 texture layer。

通用自动推断入口是 `python -B -m tools.material_mapping infer`，与 Blockbench 无关；本目录保留的
`init` / `validate` / `export` 是旧配对工程入口，并单向复用 `tools.material_mapping` 中的共享算法。
