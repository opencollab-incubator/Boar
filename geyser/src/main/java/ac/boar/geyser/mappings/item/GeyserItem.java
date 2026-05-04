package ac.boar.geyser.mappings.item;

import ac.boar.anticheat.util.Reference;
import ac.boar.mappings.item.Item;
import org.geysermc.geyser.item.type.BlockItem;

import java.util.Objects;
import java.util.Optional;

public record GeyserItem(org.geysermc.geyser.item.type.Item handle) implements Item {

    @Override
    public boolean is(Item item) {
        return ((GeyserItem) item).handle().javaId() == this.handle.javaId();
    }

    @Override
    public boolean is(Reference<Item> reference) {
        Optional<Item> opt = reference.find();
        return opt.isPresent() ? is(opt.get()) : false;
    }

    @Override
    public boolean isBlock() {
        return this.handle instanceof BlockItem;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeyserItem that = (GeyserItem) o;
        return Objects.equals(this.handle, that.handle);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.handle);
    }
}
