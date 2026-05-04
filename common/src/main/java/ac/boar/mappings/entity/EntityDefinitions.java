package ac.boar.mappings.entity;

import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.ReferencePopulator;

public final class EntityDefinitions {
    static final ReferencePopulator<EntityDefinition> POPULATOR = new ReferencePopulator<>("entity_definition");

    public static final Reference<EntityDefinition> BIRCH_BOAT = create("birch_boat");
    public static final Reference<EntityDefinition> PLAYER = create("player");

    private static Reference<EntityDefinition> create(String key) {
        return POPULATOR.defer("minecraft:" + key);
    }
}
