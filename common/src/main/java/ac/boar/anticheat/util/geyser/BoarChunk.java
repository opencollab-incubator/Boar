package ac.boar.anticheat.util.geyser;

import java.util.List;

public class BoarChunk {

    private static final long SECTION_WARNING_COOLDOWN = 3000;

    private final BoarChunkSection[] sections;
    private final List<BlockEntityInfo> blockEntities;

    private boolean hasAllSections;
    private long lastSectionWarning = 0;

    private final long loadedTick;

    public BoarChunk(BoarChunkSection[] sections, List<BlockEntityInfo> blockEntities, boolean hasAllSections, long loadedTick) {
        this.sections = sections;
        this.blockEntities = blockEntities;
        this.hasAllSections = hasAllSections;
        this.loadedTick = loadedTick;
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

    public void setSection(BoarChunkSection newSection, int index) {
        if (newSection == null) {
            throw new IllegalArgumentException("newSection cannot be null");
        }

        this.sections[index] = newSection;
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
