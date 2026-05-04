package ac.boar.geyser.mappings.entity;

import ac.boar.anticheat.util.Reference;
import ac.boar.mappings.entity.EntityType;

import java.util.Optional;

public record GeyserEntityType(org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType handle) implements EntityType {

    @Override
    public boolean is(EntityType type) {
        return ((GeyserEntityType) type).handle() == this.handle;
    }

    @Override
    public boolean is(Reference<EntityType> reference) {
        Optional<EntityType> opt = reference.find();
        return opt.isPresent() ? is(opt.get()) : false;
    }
}
