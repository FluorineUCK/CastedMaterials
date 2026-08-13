package com.fluorineuck.castedmaterials.client.cache;

import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.mapping.CompositionResult;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class RenderRepository {
    private final ConcurrentHashMap<RenderKey, RenderEntry> entries = new ConcurrentHashMap<>();
    private final Executor executor;
    private final UploadQueue uploadQueue;
    private volatile long generation;

    public RenderRepository(long generation, Executor executor, UploadQueue uploadQueue) {
        this.generation = generation;
        this.executor = executor;
        this.uploadQueue = uploadQueue;
    }

    public RenderEntry resolve(RenderKey key, Supplier<CompositionResult> composer) {
        if (key.generation() != generation) {
            RenderEntry stale = new RenderEntry();
            stale.failed(RenderFailureCode.STALE_GENERATION, true);
            return stale;
        }
        RenderEntry created = new RenderEntry();
        RenderEntry entry = entries.putIfAbsent(key, created);
        if (entry != null) {
            return entry;
        }
        created.generating();
        executor.execute(() -> generate(key, created, composer));
        return created;
    }

    public Optional<RenderEntry> find(RenderKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    public void switchGeneration(long nextGeneration) {
        generation = nextGeneration;
        entries.clear();
    }

    private void generate(RenderKey key, RenderEntry entry, Supplier<CompositionResult> composer) {
        try {
            CompositionResult result = composer.get();
            if (key.generation() != generation) {
                return;
            }
            if (result.state() == RenderableState.FALLBACK_BASE
                    || result.state() == RenderableState.QUARANTINED) {
                entry.failed(result.diagnostics().stream().findFirst()
                        .orElse(RenderFailureCode.COMPOSITION_FAILED), false);
                return;
            }
            entry.waitingUpload(result.state());
            uploadQueue.enqueue(key, result.image(), upload -> completeUpload(key, entry, upload));
        } catch (RuntimeException error) {
            entry.failed(RenderFailureCode.COMPOSITION_FAILED, true);
        }
    }

    private void completeUpload(RenderKey key, RenderEntry entry, UploadResult upload) {
        if (key.generation() != generation || entries.get(key) != entry) {
            return;
        }
        if (upload.uploaded() && upload.allocation().isPresent()) {
            entry.ready(upload.allocation().orElseThrow());
        } else {
            entry.failed(upload.failure().orElse(RenderFailureCode.GPU_UPLOAD_FAILED), true);
        }
    }
}
