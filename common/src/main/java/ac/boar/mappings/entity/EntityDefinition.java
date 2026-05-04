package ac.boar.mappings.entity;

import ac.boar.anticheat.util.Referenced;

public interface EntityDefinition extends Referenced<EntityDefinition> {

    EntityType type();

    String identifier();

    float width();

    float height();

    float offset();
}
