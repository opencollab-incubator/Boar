package ac.boar.geyser.anticheat.data.enchantment;

import ac.boar.anticheat.data.enchantment.Enchantment;
import ac.boar.anticheat.data.enchantment.EnchantmentProvider;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;

public class GeyserEnchantmentProvider implements EnchantmentProvider {
    private static final GeyserEnchantmentProvider INSTANCE = new GeyserEnchantmentProvider();

    @Override
    public Enchantment byId(int id) {
        BedrockEnchantment bedrock = BedrockEnchantment.getByBedrockId(id);
        return switch (bedrock) {
            case DEPTH_STRIDER -> Enchantment.DEPTH_STRIDER;
            case RIPTIDE -> Enchantment.RIPTIDE;
            case SOUL_SPEED -> Enchantment.SOUL_SPEED;
            default -> throw new IllegalArgumentException("Unsupported enchantment id: " + id);
        };
    }

    public static GeyserEnchantmentProvider get() {
        return INSTANCE;
    }
}
