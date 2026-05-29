package ac.boar.anticheat.util.geyser;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.cloudburstmc.protocol.common.util.VarInts;

public final class ChunkDecoder {
    private ChunkDecoder() {
    }

    public record DecodedSubChunk(BoarChunkSection section, int sectionY) {
    }

    public static BlockStorage readLayer(final ByteBuf buf, final int initialId) {
        final int version = buf.readUnsignedByte() >> 1;
        if (version == 127) { // 127 = Same values as previous palette
            return null;
        }

        final BitArray bitArray;
        if (version == 0) {
            bitArray = BitArrayVersion.get(version, true).createArray(4096, null);
        } else {
            bitArray = BitArrayVersion.get(version, true).createArray(4096);
        }

        if (!(bitArray instanceof SingletonBitArray)) {
            for (int i = 0; i < bitArray.words().length; i++) {
                bitArray.words()[i] = buf.readIntLE();
            }
        }

        final int size = bitArray instanceof SingletonBitArray ? 1 : VarInts.readInt(buf);

        final IntList palette = new IntArrayList(size);
        for (int i = 0; i < size; i++) {
            palette.add(VarInts.readInt(buf));
        }

        if (palette.isEmpty()) {
            palette.add(initialId);
        }
        return new BlockStorage(bitArray, palette);
    }

    public static DecodedSubChunk readSubChunk(final ByteBuf buf, final int airId, final int fallbackIndex, final int dimensionMinY) {
        final int version = buf.readUnsignedByte();
        int sectionY = fallbackIndex;
        final int layerCount;

        if (version == 1) {
            layerCount = 1;
        } else if (version == 8 || version == 9) {
            layerCount = buf.readUnsignedByte();
            if (version == 9) {
                // Signed Y of the sub-chunk in world space. Translate to a 0-based section index.
                final int uIndex = buf.readByte();
                sectionY = uIndex - (dimensionMinY >> 4);
            }
        } else {
            throw new IllegalStateException("Unknown sub-chunk version: " + version);
        }

        final BlockStorage[] layers = new BlockStorage[layerCount];
        for (int layer = 0; layer < layerCount; layer++) {
            layers[layer] = readLayer(buf, airId);
        }
        return new DecodedSubChunk(new BoarChunkSection(layers), sectionY);
    }
}
