package ac.boar.geyser.anticheat.player.accessor;

import ac.boar.anticheat.player.accessor.EntityAccessor;
import ac.boar.geyser.mappings.entity.GeyserEntity;
import ac.boar.geyser.mappings.entity.GeyserEntityDefinition;
import ac.boar.mappings.entity.Entity;
import ac.boar.mappings.entity.EntityDefinition;
import org.geysermc.geyser.session.GeyserSession;
import org.jetbrains.annotations.Nullable;

public record GeyserEntityAccessor(GeyserSession session) implements EntityAccessor {

    @Override
    public @Nullable Entity entityByRuntimeId(long runtimeId) {
        org.geysermc.geyser.entity.type.Entity entity = this.session.getEntityCache().getEntityByGeyserId(runtimeId);
        if (entity == null) {
            return null;
        }

        return new GeyserEntity(entity);
    }

    @Override
    public EntityDefinition definitionByRuntimeId(long runtimeId) {
        org.geysermc.geyser.entity.type.Entity entity = this.session.getEntityCache().getEntityByGeyserId(runtimeId);
        if (entity == null || entity.getJavaDefinition() == null) {
            return null;
        }

        return new GeyserEntityDefinition(entity.getJavaDefinition());
    }
}
