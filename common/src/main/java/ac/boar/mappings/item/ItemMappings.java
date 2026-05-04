package ac.boar.mappings.item;

import java.util.List;

public interface ItemMappings {

    List<Item> getBundleItems();

    List<Item> getCandleItems();

    List<Item> getHeadItems();

    static ItemMappings get() {
        return ItemMappingsInst.get();
    }
}
