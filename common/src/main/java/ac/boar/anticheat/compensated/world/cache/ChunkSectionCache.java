package ac.boar.anticheat.compensated.world.cache;

import ac.boar.anticheat.util.geyser.BoarChunkSection;
import com.google.common.collect.MapMaker;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * A shared, thread-safe cache of decoded chunk sections.
 * -
 * Lots of players get sent the exact same chunk. Rather than have every player decode and keep their
 * own copy, we decode it once and let all of them point at the same BoarChunkSection to save on memory.
 * -
 * Sharing is safe because no one ever edits a shared section in place. When a player changes a block,
 * CompensatedWorld#updateBlock first hands that player a private copy (copy-on-write), so the other
 * players who share the original are untouched. (See BoarChunkSection#isShared())
 * -
 * Cleanup is automatic. Sections are held by weak reference, so once nothing is using one anymore the
 * garbage collector throws it away and its cache entry disappears on its own.
 */
public final class ChunkSectionCache {

    private final ConcurrentMap<HashCode, BoarChunkSection> sections = new MapMaker().weakValues().makeMap();

    /**
     * Gives back the shared section for these chunk bytes. If nobody has decoded these bytes yet, the
     * given {@code decoder} runs and its result is cached for everyone else. Returns null for an
     * empty/failed decode (e.g. all-air section), which is not cached.
     * -
     * It's fine to share one section between players who might be on different block palettes
     * (different Minecraft versions, custom blocks, etc.) because cached sections store the RAW ids from the
     * packets, not real blocks. Turning a raw id into an actual block happens later, separately for each
     * player, when they read it (CompensatedWorld#getBlockAt). And two players only ever share when
     * their raw bytes are identical - so a player who shares gets exactly the ids it would have decoded
     * by itself, and just translates them with its own palette. The one rule that keeps this true:
     * this cache must only ever hold raw ids, never already-translated block data.
     */
    public BoarChunkSection getOrDecode(ByteBuf payload, int airId, Supplier<BoarChunkSection> decoder) {
        final HashCode key = key(payload, airId);
        final BoarChunkSection cached = this.sections.get(key);
        if (cached != null) {
            return cached; // someone already decoded these exact bytes - reuse it
        }

        final BoarChunkSection decoded = decoder.get();
        if (decoded == null) {
            return null; // nothing worth caching (empty/failed decode)
        }
        decoded.markShared(); // mark it so the first block edit makes a private copy instead of mutating this

        // Two threads can decode the same bytes at the same time. putIfAbsent keeps whichever landed
        // first; if we lost that race we drop our copy and use theirs, so everyone shares one section.
        final BoarChunkSection raced = this.sections.putIfAbsent(key, decoded);
        return raced != null ? raced : decoded;
    }

    // The cache key is a 128-bit hash of the section's raw bytes plus airId (airId can change how an
    // empty layer decodes). Same key == same decoded section.
    private static HashCode key(final ByteBuf payload, final int airId) {
        final byte[] bytes = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), bytes); // copy the bytes out without moving the reader
        return Hashing.murmur3_128().newHasher()
                .putBytes(bytes)
                .putInt(airId)
                .hash();
    }

    /** Empties the cache. Only needed on shutdown since the GC should handle normal cleanup. */
    public void clear() {
        this.sections.clear();
    }
}
