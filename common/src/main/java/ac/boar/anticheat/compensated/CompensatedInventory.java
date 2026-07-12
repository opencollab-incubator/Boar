package ac.boar.anticheat.compensated;

import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.ArmorContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.PlayerContainerCache;
import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.data.inventory.ItemCache;
import ac.boar.anticheat.player.BoarPlayer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.PotionMixData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CompensatedInventory {
    @Getter
    private final BoarPlayer player;

    @Getter
    @Setter
    private Map<Integer, RecipeData> craftingData = new HashMap<>();
    @Getter
    @Setter
    private Map<Integer, ItemData> creativeData = new HashMap<>();
    @Getter
    @Setter
    private List<PotionMixData> potionMixData = new ObjectArrayList<>();

    public int heldItemSlot;

    public final PlayerContainerCache inventoryContainer = new PlayerContainerCache(this);
    public final ContainerCache offhandContainer = new ContainerCache(this, (byte) ContainerId.OFFHAND, ContainerType.INVENTORY, null, -1L);
    public final ContainerCache armorContainer = new ArmorContainerCache(this);
    public final ContainerCache hudContainer = new ContainerCache(this, (byte) ContainerId.UI, ContainerType.INVENTORY, null, -1L);

    public ContainerCache openContainer = null;

    @Getter
    private final Map<Integer, ItemCache> bundleCache = new HashMap<>();

    public ContainerCache getContainer(byte id) {
        if (id == inventoryContainer.getId()) {
            return inventoryContainer;
        } else if (id == offhandContainer.getId()) {
            return offhandContainer;
        } else if (id == armorContainer.getId()) {
            return armorContainer;
        } else if (id == hudContainer.getId()) {
            return hudContainer;
        } else if (openContainer != null && id == openContainer.getId()) {
            return openContainer;
        }

        return null;
    }

    @NonNull
    public static Map<Enchantment, Integer> getEnchantments(final ItemData data) {
        if (data == null || data.getTag() == null || !data.getTag().containsKey("ench")) {
            return Map.of();
        }

        final Map<Enchantment, Integer> enchantmentMap = new HashMap<>();
        final List<NbtMap> enchantments = data.getTag().getList("ench", NbtType.COMPOUND);

        for (NbtMap nbtMap : enchantments) {
            if (!nbtMap.containsKey("id") || !nbtMap.containsKey("lvl")) {
                continue;
            }

            Enchantment bedrockEnchantment = Enchantment.byId(nbtMap.getShort("id"));
            if (bedrockEnchantment == null) {
                continue;
            }

            enchantmentMap.put(bedrockEnchantment, (int) nbtMap.getShort("lvl"));
        }

        return enchantmentMap;
    }
}
