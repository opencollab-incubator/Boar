package ac.boar.geyser.anticheat.data.inventory;

import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.util.Reference;
import ac.boar.api.anticheat.model.NetworkSession;
import ac.boar.geyser.mappings.item.GeyserItem;
import ac.boar.geyser.model.GeyserNetworkSession;
import ac.boar.mappings.item.Item;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.Optional;

public record GeyserBoarItemStack(org.geysermc.geyser.inventory.GeyserItemStack handle) implements BoarItemStack {

    @Override
    public Item item() {
        return new GeyserItem(this.handle.asItem());
    }

    @Override
    public boolean is(Item item) {
        return ((GeyserItem) item).handle().javaId() == this.handle.getJavaId();
    }

    @Override
    public boolean is(Reference<Item> item) {
        Optional<Item> opt = item.find();
        return opt.isPresent() ? is(opt.get()) : false;
    }

    @Override
    public boolean isEmpty() {
        return this.handle.isEmpty();
    }

    @Override
    public ItemData toItemData(NetworkSession session) {
        return this.handle.getItemData(((GeyserNetworkSession) session).session());
    }
}
