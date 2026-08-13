package com.fluorineuck.castedmaterials.client.cache;

import com.fluorineuck.castedmaterials.client.atlas.AtlasAllocation;
import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;
import com.fluorineuck.castedmaterials.mapping.CompositionResult;
import com.fluorineuck.castedmaterials.mapping.PixelImage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class RenderRepositoryTest {
    private static final Executor DIRECT = Runnable::run;

    @Test
    void isolatesFailedKeysAndRetriesThemOnlyInANewGeneration() {
        FakeUploads uploads = new FakeUploads();
        RenderRepository repository = new RenderRepository(1, DIRECT, uploads);
        RenderKey good = new RenderKey(1, id("target"), id("good"));
        RenderKey bad = new RenderKey(1, id("target"), id("bad"));

        RenderEntry goodEntry = repository.resolve(good, () -> ready(RenderableState.READY));
        uploads.succeed();
        repository.resolve(bad, () -> { throw new IllegalStateException("bad client resource"); });

        assertEquals(RenderEntry.State.READY, goodEntry.state());
        assertEquals(RenderEntry.State.QUARANTINED, repository.find(bad).orElseThrow().state());
        assertEquals(RenderEntry.State.READY, repository.find(good).orElseThrow().state());

        repository.switchGeneration(2);
        RenderKey retried = new RenderKey(2, id("target"), id("bad"));
        repository.resolve(retried, () -> ready(RenderableState.DEGRADED));
        uploads.succeed();
        assertEquals(RenderEntry.State.READY, repository.find(retried).orElseThrow().state());
        assertEquals(RenderableState.DEGRADED, repository.find(retried).orElseThrow().renderableState());
    }

    @Test
    void discardsOldGenerationUploadCompletion() {
        FakeUploads uploads = new FakeUploads();
        RenderRepository repository = new RenderRepository(1, DIRECT, uploads);
        RenderKey old = new RenderKey(1, id("target"), id("material"));
        repository.resolve(old, () -> ready(RenderableState.READY));
        repository.switchGeneration(2);
        uploads.succeed();
        assertTrue(repository.find(old).isEmpty());
    }

    private static CompositionResult ready(RenderableState state) {
        return new CompositionResult(new PixelImage(1, 1, new int[]{0xffffffff}), state, List.of());
    }

    private static final class FakeUploads implements UploadQueue {
        private java.util.function.Consumer<UploadResult> completion;

        @Override
        public void enqueue(RenderKey key, PixelImage image,
                            java.util.function.Consumer<UploadResult> completion) {
            this.completion = completion;
        }

        void succeed() {
            var callback = completion;
            completion = null;
            callback.accept(new UploadResult(true,
                    Optional.of(new AtlasAllocation(0, 0, 1, 1)), Optional.empty()));
        }
    }
}
