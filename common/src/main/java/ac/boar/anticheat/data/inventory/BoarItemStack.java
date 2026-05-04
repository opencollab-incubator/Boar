package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.util.Reference;
import ac.boar.api.anticheat.model.NetworkSession;
import ac.boar.mappings.item.Item;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

public interface BoarItemStack {

    Item item();

    boolean is(Item item);

    boolean is(Reference<Item> item);

    boolean isEmpty();

    ItemData toItemData(NetworkSession session);

    static BoarItemStack of(NetworkSession session, ItemData data) {
        return ItemStackInst.provider().create(session, data);
    }

    static BoarItemStack of(NetworkSession session, Reference<Item> item, int amount) {
        return ItemStackInst.provider().create(session, item, amount);
    }
}
