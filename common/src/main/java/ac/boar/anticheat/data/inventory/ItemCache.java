package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.compensated.CompensatedInventory;
import ac.boar.mappings.item.ItemMappings;
import lombok.Getter;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

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
        final BoarItemStack itemStack = BoarItemStack.of(inventory.getPlayer().getSession(), data);
        if (ItemMappings.get().getBundleItems().contains(itemStack.item())) {
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
