package ac.boar.anticheat.compensated;

import ac.boar.anticheat.compensated.cache.container.ContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.ArmorContainerCache;
import ac.boar.anticheat.compensated.cache.container.impl.PlayerContainerCache;
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
import org.geysermc.geyser.inventory.item.BedrockEnchantment;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

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

    public ItemStack translate(ItemData data) {
        try {
            ItemStack stack = ItemTranslator.translateToJava(player.getSession(), data);
            if (stack == null) {
                stack = new ItemStack(Items.AIR_ID);
            }

            return stack;
        } catch (Exception ignored) {
            return new ItemStack(Items.AIR_ID);
        }
    }

    public ItemData translate(ItemStack stack) {
        return ItemTranslator.translateToBedrock(player.getSession(), stack);
    }

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
    public static Map<BedrockEnchantment, Integer> getEnchantments(final ItemData data) {
        if (data == null || data.getTag() == null || !data.getTag().containsKey("ench")) {
            return Map.of();
        }

        final Map<BedrockEnchantment, Integer> enchantmentMap = new HashMap<>();
        final List<NbtMap> enchantments = data.getTag().getList("ench", NbtType.COMPOUND);

        for (NbtMap nbtMap : enchantments) {
            if (!nbtMap.containsKey("id") || !nbtMap.containsKey("lvl")) {
                continue;
            }

            BedrockEnchantment bedrockEnchantment = BedrockEnchantment.getByBedrockId(nbtMap.getShort("id"));
            enchantmentMap.put(bedrockEnchantment, (int) nbtMap.getShort("lvl"));
        }

        return enchantmentMap;
    }
}
