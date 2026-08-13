package com.fluorineuck.castedmaterials.network;

import com.fluorineuck.castedmaterials.material.MaterialPairValidator;
import com.fluorineuck.castedmaterials.material.MaterialPalette;
import com.fluorineuck.castedmaterials.material.MaterialValidationReport;
import com.fluorineuck.castedmaterials.material.ServerMaterialPalette;
import com.fluorineuck.castedmaterials.material.TagSnapshot;

import java.util.Arrays;

public final class ServerPaletteSynchronizer {
    public SyncDecision rebuild(TagSnapshot snapshot, SyncCause cause) {
        MaterialValidationReport report = MaterialPairValidator.validate(
                snapshot.items(), snapshot.blocks(), snapshot.itemTagIds(), snapshot.blockTagIds());
        MaterialPalette next = MaterialPalette.from(report.validIds());
        boolean changed = ServerMaterialPalette.active()
                .map(current -> !Arrays.equals(current.fingerprint(), next.fingerprint()))
                .orElse(true);
        ServerMaterialPalette.activate(next);
        return new SyncDecision(next, report, cause == SyncCause.PLAYER_JOIN || changed);
    }

    public enum SyncCause {
        RELOAD,
        PLAYER_JOIN
    }

    public record SyncDecision(
            MaterialPalette palette,
            MaterialValidationReport report,
            boolean broadcast) {
    }
}
