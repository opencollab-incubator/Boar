package ac.boar.mappings.entity;

import ac.boar.anticheat.BoarPlatform;

public final class EntityMappingsInst {

    public static void init(BoarPlatform platform) {
        platform.finalizeEntityMappings(EntityTypes.POPULATOR, EntityDefinitions.POPULATOR);
    }
}
