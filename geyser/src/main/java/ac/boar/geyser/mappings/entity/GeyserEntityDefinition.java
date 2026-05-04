package ac.boar.geyser.mappings.entity;

import ac.boar.anticheat.util.Reference;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;

import java.util.Optional;

public record GeyserEntityDefinition(org.geysermc.geyser.entity.EntityDefinition<?> handle) implements EntityDefinition {

    @Override
    public boolean is(EntityDefinition value) {
        return ((GeyserEntityDefinition) value).handle.identifier().equals(this.handle.identifier());
    }

    @Override
    public boolean is(Reference<EntityDefinition> reference) {
        Optional<EntityDefinition> opt = reference.find();
        return opt.isPresent() ? is(opt.get()) : false;
    }

    @Override
    public EntityType type() {
        return new GeyserEntityType(this.handle.entityType());
    }

    @Override
    public String identifier() {
        return this.handle.identifier();
    }

    @Override
    public float width() {
        return this.handle.width();
    }

    @Override
    public float height() {
        return this.handle.height();
    }

    @Override
    public float offset() {
        return this.handle.offset();
    }
}
