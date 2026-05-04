package ac.boar.mappings.entity;

import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.ReferencePopulator;

public final class EntityTypes {
    static final ReferencePopulator<EntityType> POPULATOR = new ReferencePopulator<>("entity_type");

    public static final Reference<EntityType> BIRCH_BOAT = create("birch_boat");
    public static final Reference<EntityType> PLAYER = create("player");

    private static Reference<EntityType> create(String key) {
        return POPULATOR.defer("minecraft:" + key);
    }
}
