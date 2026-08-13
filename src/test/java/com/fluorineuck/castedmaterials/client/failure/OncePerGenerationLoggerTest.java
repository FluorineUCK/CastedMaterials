package com.fluorineuck.castedmaterials.client.failure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OncePerGenerationLoggerTest {
    @Test
    void deduplicatesOnlyTheFullGenerationTargetMaterialCodeTuple() {
        List<String> messages = new ArrayList<>();
        OncePerGenerationLogger logger = new OncePerGenerationLogger(messages::add);

        logger.log(1, id("target"), id("material"), RenderFailureCode.GPU_UPLOAD_FAILED, "one");
        logger.log(1, id("target"), id("material"), RenderFailureCode.GPU_UPLOAD_FAILED, "duplicate");
        logger.log(1, id("target"), id("other"), RenderFailureCode.GPU_UPLOAD_FAILED, "other");
        logger.log(2, id("target"), id("material"), RenderFailureCode.GPU_UPLOAD_FAILED, "next");

        assertEquals(List.of("one", "other", "next"), messages);
    }
}
