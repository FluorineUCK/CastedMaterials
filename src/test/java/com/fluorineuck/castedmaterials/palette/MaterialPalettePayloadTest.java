package com.fluorineuck.castedmaterials.palette;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.fluorineuck.castedmaterials.TestIds.id;
import static org.junit.jupiter.api.Assertions.*;

class MaterialPalettePayloadTest {
    @Test
    void roundTripsOrderedIdsAndFingerprint() {
        MaterialPalette palette = MaterialPalette.from(
                List.of(id("create:andesite_casing"), id("create:brass_casing")));
        MaterialPalettePayload original = MaterialPalettePayload.from(palette);
        RegistryFriendlyByteBuf buffer = buffer();

        MaterialPalettePayload.STREAM_CODEC.encode(buffer, original);
        MaterialPalettePayload decoded = MaterialPalettePayload.STREAM_CODEC.decode(buffer);

        assertEquals(original.ids(), decoded.ids());
        assertArrayEquals(original.fingerprint(), decoded.fingerprint());
        assertEquals(palette.ids(), decoded.toPalette().ids());
    }

    @Test
    void thirtyTwoNormalIdsStayWithinFourKiB() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            ids.add(id("material_" + i + "_with_a_reasonably_long_path"));
        }
        RegistryFriendlyByteBuf buffer = buffer();
        MaterialPalettePayload.STREAM_CODEC.encode(buffer,
                MaterialPalettePayload.from(MaterialPalette.from(ids)));
        assertTrue(buffer.readableBytes() <= 4096, "bytes=" + buffer.readableBytes());
    }

    @Test
    void rejectsUnsupportedProtocolAndOversizedCounts() {
        RegistryFriendlyByteBuf badProtocol = buffer();
        badProtocol.writeVarInt(2);
        assertThrows(IllegalArgumentException.class,
                () -> MaterialPalettePayload.STREAM_CODEC.decode(badProtocol));

        RegistryFriendlyByteBuf oversized = buffer();
        oversized.writeVarInt(1);
        oversized.writeBytes(new byte[32]);
        oversized.writeVarInt(16_385);
        assertThrows(IllegalArgumentException.class,
                () -> MaterialPalettePayload.STREAM_CODEC.decode(oversized));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), null, ConnectionType.NEOFORGE);
    }
}
