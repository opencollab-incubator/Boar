package ac.boar.geyser.mappings.item;

import ac.boar.anticheat.util.Reference;
import ac.boar.anticheat.util.ReferencePopulator;
import ac.boar.mappings.item.Item;
import ac.boar.mappings.item.ItemMappings;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.registry.Registries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GeyserItemMappings implements ItemMappings {
    private final List<Item> bundleItems = new ArrayList<>();
    private final List<Item> candleItems = new ArrayList<>();
    private final List<Item> headItems = new ArrayList<>();

    public static ItemMappings load() {
        GeyserItemMappings itemMappings = new GeyserItemMappings();

        for (Field field : Items.class.getDeclaredFields()) {
            try {
                Object object = field.get(null);

                if (object instanceof org.geysermc.geyser.item.type.Item item) {
                    final String lowercaseName = field.getName().toLowerCase(Locale.ROOT);
                    if (item == Items.BUNDLE || lowercaseName.endsWith("_bundle")) {
                        itemMappings.bundleItems.add(new GeyserItem(item));
                    } else if (item == Items.CANDLE || lowercaseName.endsWith("_candle")) {
                        itemMappings.candleItems.add(new GeyserItem(item));
                    } else if (item == Items.SKELETON_SKULL || item == Items.WITHER_SKELETON_SKULL || lowercaseName.endsWith("_head")) {
                        itemMappings.headItems.add(new GeyserItem(item));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return itemMappings;
    }

    @Override
    public List<Item> getBundleItems() {
        return Collections.unmodifiableList(this.bundleItems);
    }

    @Override
    public List<Item> getCandleItems() {
        return Collections.unmodifiableList(this.candleItems);
    }

    @Override
    public List<Item> getHeadItems() {
        return Collections.unmodifiableList(this.headItems);
    }

    public static void finalizeItemMappings(ReferencePopulator<Item> populator) {
        for (org.geysermc.geyser.item.type.Item item : Registries.JAVA_ITEMS.get()) {
            String key = item.javaIdentifier();
            Reference<Item> ref = populator.get(key);
            if (ref != null) {
                populator.populate(key, new GeyserItem(item));
            }
        }
    }
}
