package ac.boar.anticheat.compensated.world.cache;

import ac.boar.anticheat.util.geyser.BoarChunkSection;
import com.google.common.collect.MapMaker;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.netty.buffer.ByteBuf;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * A shared, thread-safe cache of decoded chunk sections.
 * -
 * Many players receive the same chunk bytes. The cache decodes these bytes one time. All of these
 * players then share one BoarChunkSection object. This lowers memory use and decode work.
 * -
 * Sharing is safe because no code writes to a shared section. When a player changes a block,
 * CompensatedWorld#updateBlock first makes a private copy of the section for that player. This is
 * copy-on-write. The other players keep the unchanged shared section. See BoarChunkSection#isShared().
 * -
 * Cleanup is automatic. The cache holds each section through a weak reference. When no player world
 * uses a section, the garbage collector removes the section and its cache entry.
 */
public final class ChunkSectionCache {

    private final ConcurrentMap<HashCode, BoarChunkSection> sections = new MapMaker().weakValues().makeMap();
    private final ConcurrentMap<Integer, BoarChunkSection> allAirSections = new ConcurrentHashMap<>();

    public BoarChunkSection allAirSection(final int airId) {
        return this.allAirSections.computeIfAbsent(airId, id -> {
            final BoarChunkSection section = new BoarChunkSection(id);
            section.markShared();
            return section;
        });
    }

    /**
     * Returns the shared section for the given chunk bytes. If the cache has no section for these
     * bytes, this method runs {@code decoder} and caches the result. If {@code decoder} returns null,
     * the decode failed. This method then returns null and caches nothing. An all-air section decodes
     * to a normal section. The cache stores and shares it like all other sections.
     * -
     * Players can use different block palettes, for example different Minecraft versions or custom
     * blocks. Sharing is still safe because a cached section stores the raw ids from the packet, not
     * translated blocks. Each player translates a raw id to a block when the player reads it, in
     * CompensatedWorld#getBlockAt. Two players share a section only when their raw bytes are identical.
     * A player who shares a section gets the same raw ids that its own decode would produce. One rule
     * keeps this safe: the cache must hold only raw ids, never translated block data.
     */
    public BoarChunkSection getOrDecode(ByteBuf payload, int airId, Supplier<BoarChunkSection> decoder) {
        final HashCode key = key(payload, airId);
        final BoarChunkSection cached = this.sections.get(key);
        if (cached != null) {
            return cached; // The cache already has a section for these bytes.
        }

        final BoarChunkSection decoded = decoder.get();
        if (decoded == null) {
            return null; // The decode failed. The cache does not store null.
        }
        decoded.markShared(); // The first block change then makes a private copy of the section.

        // Two threads can decode the same bytes at the same time. putIfAbsent keeps the first stored
        // section. If this thread lost the race, it discards its own copy and uses the stored one.
        // All players then share one section.
        final BoarChunkSection raced = this.sections.putIfAbsent(key, decoded);
        return raced != null ? raced : decoded;
    }

    // The cache key is a 128-bit hash of the section's raw bytes plus airId. airId can change how an
    // empty layer decodes. The same key always maps to the same decoded section.
    // Guava marks the Hasher interface @Beta, unlike Hashing and HashCode. The Guava version is pinned
    // in the version catalog, so a future Guava update can only cause a compile error, not a runtime error.
    @SuppressWarnings("UnstableApiUsage")
    private static HashCode key(final ByteBuf payload, final int airId) {
        final Hasher hasher = Hashing.murmur3_128().newHasher();
        if (payload.nioBufferCount() > 0) {
            // Hash the readable bytes in place. Each ByteBuffer is a view over the buffer's memory.
            // A change to the view's position does not move the reader index of the ByteBuf.
            for (final ByteBuffer nio : payload.nioBuffers(payload.readerIndex(), payload.readableBytes())) {
                hasher.putBytes(nio);
            }
        } else {
            // Rare case: the buffer cannot expose its memory as NIO buffers. Copy the bytes instead.
            final byte[] bytes = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), bytes);
            hasher.putBytes(bytes);
        }
        return hasher.putInt(airId).hash();
    }

    /** Removes all entries. Call this only on shutdown. The garbage collector does the normal cleanup. */
    public void clear() {
        this.sections.clear();
        this.allAirSections.clear();
    }
}
