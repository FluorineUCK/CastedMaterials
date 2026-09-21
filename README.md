# Casted Materials

Casted Materials is a standalone Minecraft 1.21.1 / NeoForge rendering library. A host mod supplies a
`ModelPart`, a material ID and data/resource-pack definitions; the library resolves a cached composite,
direct UV bindings, or the host's fallback texture. It does **not** patch or automatically adapt Create:
Biotech.

The production artifact is the `casted_materials` mod. Create 6 is an optional client dependency used only
to read its registered casing connected-texture target. Without Create, ordinary block-model particle sprites
and explicit material definitions remain available. Create classes are not bundled in the JAR.

## Minimal client integration

Call `resolveModel` again after a resource reload rather than retaining a handle indefinitely. Render the base
and emissive passes from the same handle; `renderEmissive` is a no-op when the resolved definition has no
emissive content.

```java
import com.fluorineuck.castedmaterials.client.CastedMaterialsClient;
import com.fluorineuck.castedmaterials.client.CastedModelHandle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;

CastedModelHandle handle = CastedMaterialsClient.resolveModel(
        modelRoot,
        ResourceLocation.fromNamespaceAndPath("example", "machine"),
        materialId,       // nullable: null selects the fallback
        BASE_TEXTURE);

handle.render(poseStack, buffers, RenderType::entityCutoutNoCull,
        packedLight, packedOverlay, 0xffffffff);
handle.renderEmissive(poseStack, buffers,
        LightTexture.FULL_BRIGHT, packedOverlay, 0xffffffff);
```

The target ID above reads
`assets/example/casted_materials/targets/machine.json`. Global material slot definitions read
`assets/<material namespace>/casted_materials/materials/<material path>.json`. See the
[mapping tool documentation](tools/material_mapping/README.md#接入游戏) for the schema and authoring flow.

`CastedMaterialsApi` stores a selected material on an `ItemStack` or on a host block entity implementing
`CastedMaterialsApi.CastedMaterialHolder`. Setting a material is validated against the active synchronized
palette and returns `CHANGED`, `UNCHANGED`, `UNKNOWN_MATERIAL`, or `NO_ACTIVE_PALETTE`.

## Server palette versus client resources

The server builds and synchronizes the material palette from IDs present in **both** the item and block
`#create:casing` tags. A client resource pack can provide target mappings, material slot definitions and
textures, but it cannot add an ID to that server-owned palette. Conversely, server tags do not provide the
client mapping or texture resources. A usable material needs both sides when those definitions are required.

## Migration from the old standalone API

| Old API/package | Replacement |
| --- | --- |
| `com.fluorineuck.castedmaterials.api.CastedMaterialsApi` | `com.fluorineuck.castedmaterials.CastedMaterialsApi` |
| `...material.CastedMaterialState` | `com.fluorineuck.castedmaterials.CastedMaterialState` |
| `ClientRuntime` / `DefaultClientRuntime` | `CastedMaterialsClient.resolveModel(...)` |
| `CastedTextureHandle` and dynamic-atlas APIs | `CastedModelHandle`, whose backend is selected internally |
| `definition.*` | public definition types under `mapping.*` |

There is no compatibility shim for the deleted Java packages. Host mods must update imports and rendering
calls. The library also contains no Biotech-specific renderer injection, target data, or assets.

## Authoring tools

- [Tool overview](tools/README.md)
- [Independent `infer` / `preview` mapping tools](tools/material_mapping/README.md)
- [Paired-UV Blockbench `init` / `validate` / `export` workflow](tools/blockbench/README.md)

Python, `.bbmodel` files, examples, test fixtures, Biotech fixture resources and optional Create classes are
excluded from the production JAR. `scripts/verify_standalone.py` enforces that boundary and is run by Gradle
`check` against the release JAR.

## Build and license

```powershell
./gradlew.bat test createCompatTest build --offline --console=plain
python -B -m pytest -q -p no:cacheprovider tools
```

The standalone project's own code is licensed under [MIT](LICENSE). Migrated material-library portions retain
the narrowly scoped [Nobodiiiii MIT notice](LICENSES/Nobodiiiii-MIT.txt). Test-only third-party asset provenance
is documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); those fixtures are not shipped in the
production JAR.
