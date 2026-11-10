package ac.boar.anticheat.util.geyser;

import java.util.List;

public class BoarChunk {

    private static final long SECTION_WARNING_COOLDOWN = 3000;

    private final BoarChunkSection[] sections;
    // True for a section whose blocks we know: decoded from a payload, or known to be all air.
    private final boolean[] knownSections;
    private final List<BlockEntityInfo> blockEntities;

    private boolean hasAllSections;
    private long lastSectionWarning = 0;

    private final long loadedTick;

    public BoarChunk(BoarChunkSection[] sections, List<BlockEntityInfo> blockEntities, boolean hasAllSections, long loadedTick) {
        this(sections, blockEntities, hasAllSections, loadedTick, new boolean[sections.length]);
    }

    public BoarChunk(BoarChunkSection[] sections, List<BlockEntityInfo> blockEntities, boolean hasAllSections, long loadedTick, boolean[] knownSections) {
        if (knownSections.length != sections.length) {
            throw new IllegalArgumentException("Section length mismatch");
        }

        this.loadedTick = loadedTick;
        this.blockEntities = blockEntities;
        this.hasAllSections = hasAllSections;
        this.sections = sections;
        this.knownSections = knownSections.clone();
    }

    public long loadedTick() {
        return this.loadedTick;
    }

    public BoarChunkSection[] sections() {
        return this.sections;
    }

    public List<BlockEntityInfo> blockEntities() {
        return this.blockEntities;
    }

    public boolean warnForMissingSections() {
        long now = System.currentTimeMillis();
        if (now - this.lastSectionWarning >= SECTION_WARNING_COOLDOWN) {
            this.lastSectionWarning = now;
            return true;
        }
        return false;
    }

    public boolean hasAllSections() {
        return this.hasAllSections;
    }

    public BoarChunkSection getSection(int index) {
        return this.sections[index];
    }

    public boolean isSectionKnown(int index) {
        return index >= 0 && index < this.sections.length && this.sections[index] != null && this.knownSections[index];
    }

    public void setSection(BoarChunkSection newSection, int index, boolean known) {
        if (newSection == null) {
            throw new IllegalArgumentException("newSection cannot be null");
        }

        this.sections[index] = newSection;
        this.knownSections[index] = known;
        this.lastSectionWarning = 0;

        if (!this.hasAllSections) {
            for (BoarChunkSection sec : this.sections) {
                if (sec == null) {
                    return;
                }
            }
            this.hasAllSections = true;
        }
    }

}
