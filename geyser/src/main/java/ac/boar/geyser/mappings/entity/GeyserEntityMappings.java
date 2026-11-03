package ac.boar.geyser.mappings.entity;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.util.ReferencePopulator;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;

import java.util.Locale;
import java.util.Optional;

public final class GeyserEntityMappings {

    public static void finalizeEntityMappings(ReferencePopulator<EntityType> refPopulator,  ReferencePopulator<EntityDefinition> defPopulator) {
        refPopulator.populate((key, populator) -> {
            try {
                String stripedIdentifier = key.contains(":") ? key.substring(key.indexOf(":") + 1) : key;
                org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType type = org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType.valueOf(stripedIdentifier.toUpperCase(Locale.ROOT));
                populator.populate(new GeyserEntityType(type));

                Optional<org.geysermc.geyser.entity.EntityTypeDefinition<?>> entityDefinition =
                        org.geysermc.geyser.entity.GeyserEntityType.of(type).definition();
                if (entityDefinition.isEmpty()) {
                    // Not necessarily a problem if we have a type but not definition
                    Boar.debug("Could not find EntityDefinition with key: '" + key + "'.", Boar.DebugMessage.INFO);
                    return;
                }

                defPopulator.populate(key, new GeyserEntityDefinition(entityDefinition.get()));
            } catch (IllegalArgumentException e) {
                Boar.debug("Could not find EntityType with key: '" + key + "'.", Boar.DebugMessage.WARNING);
            }
        });

        refPopulator.ensurePopulated(false);
        defPopulator.ensurePopulated(false);
    }
}
