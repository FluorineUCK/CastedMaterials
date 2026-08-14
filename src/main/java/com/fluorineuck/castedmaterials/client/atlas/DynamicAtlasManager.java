package com.fluorineuck.castedmaterials.client.atlas;

import com.fluorineuck.castedmaterials.client.cache.RenderKey;
import com.fluorineuck.castedmaterials.client.cache.UploadQueue;
import com.fluorineuck.castedmaterials.client.cache.UploadResult;
import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

public final class DynamicAtlasManager implements UploadQueue, AutoCloseable {
    public static final int PAGE_SIZE = 512;
    public static final int MAX_PAGES = 16;
    private static final int SOFT_PAGE_BUDGET = 4;
    private static final Logger LOGGER = Logger.getLogger(DynamicAtlasManager.class.getName());

    private final TextureBackend backend;
    private final LongSupplier clock;
    private final ConcurrentLinkedQueue<PendingUpload> pendingUploads = new ConcurrentLinkedQueue<>();
    private final List<DynamicAtlasPage> pages = new ArrayList<>();
    private long generation;
    private boolean softBudgetWarningIssued;
    private boolean closed;

    public DynamicAtlasManager(long generation, TextureBackend backend) {
        this(generation, backend, System::nanoTime);
    }

    public DynamicAtlasManager(long generation, TextureBackend backend, LongSupplier clock) {
        this.generation = generation;
        this.backend = Objects.requireNonNull(backend, "backend");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void enqueue(RenderKey key, PixelImage image, Consumer<UploadResult> completion) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(completion, "completion");
        pendingUploads.add(new PendingUpload(key,
                new PixelImage(image.width(), image.height(), image.pixels()), completion));
    }

    public int drain(long budgetNanos) {
        if (budgetNanos < 0) {
            throw new IllegalArgumentException("budgetNanos must not be negative");
        }
        long startedAt = clock.getAsLong();
        int completed = 0;
        if (clock.getAsLong() - startedAt >= budgetNanos) {
            return completed;
        }
        while (true) {
            PendingUpload pending = pendingUploads.poll();
            if (pending == null) {
                return completed;
            }
            completed++;
            if (pending.key.generation() != generation || closed) {
                pending.completeFailure(RenderFailureCode.STALE_GENERATION);
            } else {
                upload(pending);
            }
            if (clock.getAsLong() - startedAt >= budgetNanos) {
                return completed;
            }
        }
    }

    public void switchGeneration(long generation) {
        this.generation = generation;
        closePages();
        softBudgetWarningIssued = false;
        PendingUpload pending;
        while ((pending = pendingUploads.poll()) != null) {
            pending.completeFailure(RenderFailureCode.STALE_GENERATION);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            closePages();
            PendingUpload pending;
            while ((pending = pendingUploads.poll()) != null) {
                pending.completeFailure(RenderFailureCode.STALE_GENERATION);
            }
        }
    }

    private void upload(PendingUpload pending) {
        Optional<PageAllocation> allocated = allocate(pending.image.width(), pending.image.height());
        if (allocated.isEmpty()) {
            pending.completeFailure(RenderFailureCode.ATLAS_BUDGET_EXHAUSTED);
            return;
        }
        PageAllocation pageAllocation = allocated.orElseThrow();
        try {
            backend.upload(pageAllocation.page.id(), pageAllocation.allocation, toAbgr(pending.image));
            pending.completion.accept(new UploadResult(true,
                    Optional.of(new AtlasRegion(pageAllocation.page.id(), pageAllocation.allocation,
                            PAGE_SIZE, PAGE_SIZE)), Optional.empty()));
        } catch (RuntimeException error) {
            pending.completeFailure(RenderFailureCode.GPU_UPLOAD_FAILED);
        }
    }

    private Optional<PageAllocation> allocate(int width, int height) {
        for (DynamicAtlasPage page : pages) {
            Optional<AtlasAllocation> allocation = page.allocate(width, height);
            if (allocation.isPresent()) {
                return Optional.of(new PageAllocation(page, allocation.orElseThrow()));
            }
        }
        if (pages.size() >= MAX_PAGES) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("casted_materials",
                "dynamic/g" + generation + "/page_" + pages.size());
        DynamicAtlasPage page = new DynamicAtlasPage(id);
        Optional<AtlasAllocation> allocation = page.allocate(width, height);
        if (allocation.isEmpty()) {
            return Optional.empty();
        }
        backend.createPage(id, PAGE_SIZE, PAGE_SIZE);
        pages.add(page);
        if (pages.size() >= SOFT_PAGE_BUDGET && !softBudgetWarningIssued) {
            softBudgetWarningIssued = true;
            LOGGER.warning(() -> "Casted Materials dynamic atlas reached " + SOFT_PAGE_BUDGET
                    + " pages in generation " + generation);
        }
        return Optional.of(new PageAllocation(page, allocation.orElseThrow()));
    }

    private void closePages() {
        for (DynamicAtlasPage page : pages) {
            backend.closePage(page.id());
        }
        pages.clear();
    }

    private static int[] toAbgr(PixelImage image) {
        int[] argb = image.pixels();
        int[] abgr = new int[argb.length];
        for (int index = 0; index < argb.length; index++) {
            int pixel = argb[index];
            abgr[index] = pixel & 0xff00ff00
                    | (pixel & 0x00ff0000) >>> 16
                    | (pixel & 0x000000ff) << 16;
        }
        return abgr;
    }

    private record PendingUpload(RenderKey key, PixelImage image, Consumer<UploadResult> completion) {
        private void completeFailure(RenderFailureCode failure) {
            completion.accept(new UploadResult(false, Optional.empty(), Optional.of(failure)));
        }
    }

    private record PageAllocation(DynamicAtlasPage page, AtlasAllocation allocation) { }
}
