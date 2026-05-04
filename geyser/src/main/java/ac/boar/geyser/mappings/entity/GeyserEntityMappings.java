package ac.boar.geyser.mappings.entity;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.util.ReferencePopulator;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;
import org.geysermc.geyser.registry.Registries;

import java.util.Locale;

public final class GeyserEntityMappings {

    public static void finalizeEntityMappings(ReferencePopulator<EntityType> refPopulator,  ReferencePopulator<EntityDefinition> defPopulator) {
        refPopulator.populate((key, populator) -> {
            try {
                String stripedIdentifier = key.contains(":") ? key.substring(key.indexOf(":") + 1) : key;
                org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType type = org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType.valueOf(stripedIdentifier.toUpperCase(Locale.ROOT));
                populator.populate(new GeyserEntityType(type));

                org.geysermc.geyser.entity.EntityDefinition<?> entityDefinition = Registries.ENTITY_DEFINITIONS.get(type);
                if (entityDefinition == null) {
                    // Not necessarily a problem if we have a type but not definition
                    Boar.debug("Could not find EntityDefinition with key: '" + key + "'.", Boar.DebugMessage.INFO);
                    return;
                }

                defPopulator.populate(key, new GeyserEntityDefinition(entityDefinition));
            } catch (IllegalArgumentException e) {
                Boar.debug("Could not find EntityType with key: '" + key + "'.", Boar.DebugMessage.WARNING);
            }
        });

        refPopulator.ensurePopulated(false);
        defPopulator.ensurePopulated(false);
    }
}
