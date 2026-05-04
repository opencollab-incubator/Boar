package ac.boar.geyser.anticheat.data.inventory;

import ac.boar.anticheat.data.inventory.BoarItemStack;
import ac.boar.anticheat.data.inventory.ItemStackProvider;
import ac.boar.anticheat.util.Reference;
import ac.boar.api.anticheat.model.NetworkSession;
import ac.boar.geyser.mappings.item.GeyserItem;
import ac.boar.geyser.model.GeyserNetworkSession;
import ac.boar.mappings.item.Item;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.Optional;

public class GeyserItemStackProvider implements ItemStackProvider {
    private static final GeyserItemStackProvider INSTANCE = new GeyserItemStackProvider();

    @Override
    public BoarItemStack create(NetworkSession session, ItemData data) {
        GeyserSession geyserSession = ((GeyserNetworkSession) session).session();
        ItemStack javaStack = translate(geyserSession, data);

        GeyserItemStack geyserStack = GeyserItemStack.from(
                geyserSession,
                javaStack
        );

        return new GeyserBoarItemStack(geyserStack);
    }

    @Override
    public BoarItemStack create(NetworkSession session, Reference<Item> item, int amount) {
        GeyserSession geyserSession = ((GeyserNetworkSession) session).session();
        int itemId = Items.AIR_ID;
        Optional<Item> opt = item.find();
        if (opt.isPresent()) {
            itemId = ((GeyserItem) opt.get()).handle().javaId();
        }

        GeyserItemStack geyserStack = GeyserItemStack.of(
                geyserSession,
                itemId,
                amount
        );

        return new GeyserBoarItemStack(geyserStack);
    }

    private static ItemStack translate(GeyserSession session, ItemData data) {
        ItemStack javaStack;
        try {
            javaStack = ItemTranslator.translateToJava(session, data);
            if (javaStack == null) {
                javaStack = new ItemStack(Items.AIR_ID);
            }
        } catch (Exception ignored) {
            javaStack = new ItemStack(Items.AIR_ID);
        }

        return javaStack;
    }

    public static GeyserItemStackProvider get() {
        return INSTANCE;
    }
}
