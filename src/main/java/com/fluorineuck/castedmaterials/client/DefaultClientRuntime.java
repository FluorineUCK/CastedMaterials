package com.fluorineuck.castedmaterials.client;

import com.fluorineuck.castedmaterials.client.atlas.DynamicAtlasManager;
import com.fluorineuck.castedmaterials.client.atlas.MinecraftTextureBackend;
import com.fluorineuck.castedmaterials.client.cache.RenderEntry;
import com.fluorineuck.castedmaterials.client.cache.RenderKey;
import com.fluorineuck.castedmaterials.client.cache.RenderRepository;
import com.fluorineuck.castedmaterials.client.definition.CastedResourceReloadListener;
import com.fluorineuck.castedmaterials.client.definition.CastedResourceSnapshot;
import com.fluorineuck.castedmaterials.client.definition.ClientImageLoader;
import com.fluorineuck.castedmaterials.client.definition.DefaultMaterialSpriteLookup;
import com.fluorineuck.castedmaterials.client.definition.ResolvedSources;
import com.fluorineuck.castedmaterials.client.definition.SourceSlotResolver;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.client.render.CastedTextureHandle;
import com.fluorineuck.castedmaterials.definition.MaterialDefinition;
import com.fluorineuck.castedmaterials.definition.TargetDefinition;
import com.fluorineuck.castedmaterials.mapping.MappingComposer;
import com.fluorineuck.castedmaterials.material.MaterialPalette;
import com.fluorineuck.castedmaterials.network.ClientPaletteStore;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Coordinates render-thread source lookup with asynchronous pixel composition and atlas uploads. */
public final class DefaultClientRuntime implements ClientRuntime {
    static final long UPLOAD_BUDGET_NANOS = 2_000_000L;

    private final Supplier<CastedResourceSnapshot> snapshots;
    private final Supplier<MaterialPalette> palettes;
    private final SourceResolver sourceResolver;
    private final RenderRepository repository;
    private final DynamicAtlasManager atlas;
    private final Set<RenderKey> quarantinedResolvers = ConcurrentHashMap.newKeySet();
    private long generation = Long.MIN_VALUE;

    public static DefaultClientRuntime create() {
        Executor composer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Casted Materials compositor");
            thread.setDaemon(true);
            return thread;
        });
        DynamicAtlasManager atlas = new DynamicAtlasManager(0, new MinecraftTextureBackend());
        return new DefaultClientRuntime(CastedResourceReloadListener.INSTANCE::current,
                ClientPaletteStore::current, DefaultClientRuntime::resolveSources,
                new RenderRepository(0, composer, atlas), atlas);
    }

    DefaultClientRuntime(Supplier<CastedResourceSnapshot> snapshots,
                         Supplier<MaterialPalette> palettes,
                         SourceResolver sourceResolver,
                         RenderRepository repository,
                         DynamicAtlasManager atlas) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.palettes = Objects.requireNonNull(palettes, "palettes");
        this.sourceResolver = Objects.requireNonNull(sourceResolver, "sourceResolver");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.atlas = Objects.requireNonNull(atlas, "atlas");
    }

    @Override
    public CastedTextureHandle resolve(ResourceLocation targetId, ResourceLocation materialId,
                                       ResourceLocation fallbackTexture) {
        Objects.requireNonNull(fallbackTexture, "fallbackTexture");
        try {
            atlas.drain(UPLOAD_BUDGET_NANOS);
            CastedResourceSnapshot snapshot = Objects.requireNonNull(snapshots.get(), "snapshot");
            switchGeneration(snapshot.generation());
            TargetDefinition target = snapshot.targets().get(targetId);
            if (target == null || palettes.get().indexOf(materialId).isEmpty()) {
                return fallback(fallbackTexture, RenderableState.FALLBACK_BASE, snapshot.generation());
            }
            return resolveTarget(snapshot, target, materialId, fallbackTexture);
        } catch (RuntimeException ignored) {
            return fallback(fallbackTexture, RenderableState.QUARANTINED, currentGeneration());
        }
    }

    private CastedTextureHandle resolveTarget(CastedResourceSnapshot snapshot, TargetDefinition target,
                                              ResourceLocation materialId, ResourceLocation fallbackTexture) {
        RenderKey key = new RenderKey(snapshot.generation(), target.id(), materialId);
        if (quarantinedResolvers.contains(key)) {
            return fallback(fallbackTexture, RenderableState.QUARANTINED, snapshot.generation());
        }
        try {
            Optional<RenderEntry> existing = repository.find(key);
            if (existing.isPresent()) {
                return handle(existing.get(), fallbackTexture, snapshot.generation());
            }
            Optional<ResolvedSources> resolved = sourceResolver.resolve(target, materialId,
                    Optional.ofNullable(snapshot.materials().get(materialId)));
            if (resolved.isEmpty()) {
                return fallback(fallbackTexture, RenderableState.FALLBACK_BASE, snapshot.generation());
            }
            RenderEntry entry = repository.resolve(key, () -> MappingComposer.compose(target,
                    resolved.get().slots(), resolved.get().overlay().orElse(null)));
            return handle(entry, fallbackTexture, snapshot.generation());
        } catch (RuntimeException ignored) {
            quarantinedResolvers.add(key);
            return fallback(fallbackTexture, RenderableState.QUARANTINED, snapshot.generation());
        }
    }

    private void switchGeneration(long nextGeneration) {
        if (generation == nextGeneration) {
            return;
        }
        repository.switchGeneration(nextGeneration);
        generation = nextGeneration;
        atlas.switchGeneration(nextGeneration);
        quarantinedResolvers.clear();
    }

    private long currentGeneration() {
        return generation == Long.MIN_VALUE ? 0 : generation;
    }

    private static CastedTextureHandle handle(RenderEntry entry, ResourceLocation fallbackTexture, long generation) {
        if (entry.state() == RenderEntry.State.READY) {
            return new CastedTextureHandle(entry.region().texture(), entry.region().uv(),
                    entry.renderableState(), generation);
        }
        RenderableState state = entry.state() == RenderEntry.State.QUARANTINED
                ? RenderableState.QUARANTINED : RenderableState.FALLBACK_BASE;
        return fallback(fallbackTexture, state, generation);
    }

    private static CastedTextureHandle fallback(ResourceLocation texture, RenderableState state, long generation) {
        return CastedTextureHandle.fallback(texture, state, generation);
    }

    private static Optional<ResolvedSources> resolveSources(TargetDefinition target, ResourceLocation materialId,
                                                             Optional<MaterialDefinition> definition) {
        var resources = Minecraft.getInstance().getResourceManager();
        return SourceSlotResolver.resolve(target, materialId, definition,
                DefaultMaterialSpriteLookup::find,
                (sprite, grid) -> ClientImageLoader.load(resources, sprite, grid),
                overlay -> ClientImageLoader.load(resources, overlay, target.size())
                        .map(source -> source.image()));
    }

    @FunctionalInterface
    interface SourceResolver {
        Optional<ResolvedSources> resolve(TargetDefinition target, ResourceLocation materialId,
                                          Optional<MaterialDefinition> definition);
    }
}
