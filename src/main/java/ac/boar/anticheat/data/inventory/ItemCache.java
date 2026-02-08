package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.compensated.CompensatedInventory;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.session.cache.tags.ItemTag;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.Objects;

@Getter
@Setter
public class ItemCache {
    public final static ItemCache AIR = new ItemCache(ItemData.AIR);

    private ItemData data;
    private BundleData bundle = null;

    private ItemCache(final ItemData data) {
        this.data = data;
    }

    public ItemCache count(int count) {
        final ItemData.Builder builder = this.data.toBuilder();
        builder.count(count);
        this.data = builder.build();
        return this;
    }

    public int count() {
        return this.data.getCount();
    }

    public boolean isEmpty() {
        return this.data.getCount() <= 0 || this.data.getDefinition().getRuntimeId() == 0 || this.data.getDefinition().getRuntimeId() == -1;
    }

    @Override
    public ItemCache clone() {
        final ItemCache cache = new ItemCache(data);
        cache.setBundle(bundle);
        return cache;
    }

    public static ItemCache build(final CompensatedInventory inventory, final ItemData data) {
        final ItemCache cache = new ItemCache(data);
        final ItemStack itemStack = inventory.translate(data);

        if (GeyserItemStack.from(inventory.getPlayer().getSession(), itemStack).is(inventory.getPlayer().getSession(), ItemTag.BUNDLES)) {
            int id = -1;

            try {
                id = Objects.requireNonNull(data.getTag()).getInt("bundle_id");
            } catch (Exception ignored) {}

            if (id == -1 || inventory.getBundleCache().containsKey(id)) {
                return cache;
            }

            final BundleData bundle = new BundleData();
            bundle.setBundleId(id);
            cache.setBundle(bundle);

            inventory.getBundleCache().put(bundle.getBundleId(), cache);
        }

        return cache;
    }
}
