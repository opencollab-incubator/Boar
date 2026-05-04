package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.util.Reference;
import ac.boar.api.anticheat.model.NetworkSession;
import ac.boar.mappings.item.Item;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

public interface ItemStackProvider {

    BoarItemStack create(NetworkSession session, ItemData data);

    BoarItemStack create(NetworkSession session, Reference<Item> item, int amount);
}
