package ac.boar.anticheat.player.accessor;

import ac.boar.mappings.entity.Entity;
import ac.boar.mappings.entity.EntityDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Accessor for accessing entity data from the server.
 */
public interface EntityAccessor {

    @Nullable
    Entity entityByRuntimeId(long runtimeId);

    @Nullable
    EntityDefinition definitionByRuntimeId(long runtimeId);
}
