package com.fluorineuck.castedmaterials.client.cache;

import com.fluorineuck.castedmaterials.client.atlas.AtlasRegion;
import com.fluorineuck.castedmaterials.client.failure.RenderFailureCode;
import com.fluorineuck.castedmaterials.client.failure.RenderableState;

public final class RenderEntry {
    private volatile State state = State.MISSING;
    private volatile RenderableState renderableState = RenderableState.FALLBACK_BASE;
    private volatile AtlasRegion region;
    private volatile RenderFailureCode failure;

    public State state() {
        return state;
    }

    public RenderableState renderableState() {
        return renderableState;
    }

    public AtlasRegion region() {
        return region;
    }

    public RenderFailureCode failure() {
        return failure;
    }

    void generating() {
        state = State.GENERATING;
    }

    void waitingUpload(RenderableState renderableState) {
        this.renderableState = renderableState;
        state = State.WAITING_UPLOAD;
    }

    void ready(AtlasRegion region) {
        this.region = region;
        state = State.READY;
    }

    void failed(RenderFailureCode failure, boolean quarantine) {
        this.failure = failure;
        this.renderableState = quarantine ? RenderableState.QUARANTINED : RenderableState.FALLBACK_BASE;
        state = quarantine ? State.QUARANTINED : State.FAILED;
    }

    public enum State {
        MISSING,
        GENERATING,
        WAITING_UPLOAD,
        READY,
        FAILED,
        QUARANTINED
    }
}
