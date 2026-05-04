package ac.boar.anticheat.data.inventory;

import ac.boar.anticheat.validator.inventory.ItemTransactionValidator;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

public final class BundleData {
    @Getter
    private final ItemCache[] contents = new ItemCache[64];
    @Setter
    @Getter
    private int bundleId = -1;
    public int count = 0;

    public BundleData() {
        Arrays.fill(this.contents, ItemCache.AIR);
    }

    public boolean add(final int slot, final ItemCache cache) {
        final ItemCache current = this.contents[slot];
        // Attempt to override an already occupied slot and not it is the same item... invalid!
        if (!current.getData().isNull() && !ItemTransactionValidator.validate(cache.getData(), current.getData())) {
            return false;
        }

        // Exceed the possible size.
        if (this.count + cache.count() > 64) {
            return false;
        }

        if (current.getData().isNull()) {
            this.contents[slot] = cache.clone();
        } else {
            current.count(current.count() + cache.count());
            this.contents[slot] = current;
        }

        this.count += cache.count();
        return true;
    }
}
