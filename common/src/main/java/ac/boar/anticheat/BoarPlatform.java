package ac.boar.anticheat;

import ac.boar.anticheat.data.block.BlockStateFactory;
import ac.boar.anticheat.data.effect.EffectProvider;
import ac.boar.anticheat.data.enchantment.EnchantmentProvider;
import ac.boar.anticheat.data.inventory.ItemStackProvider;
import ac.boar.anticheat.player.BoarPlayerManager;
import ac.boar.anticheat.util.ReferencePopulator;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.PropertyProvider;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;
import ac.boar.mappings.item.Item;
import ac.boar.mappings.item.ItemMappings;

import java.nio.file.Path;

public interface BoarPlatform {

    BlockMappings loadBlockMappings();

    BlockStateFactory blockStateFactory();

    EnchantmentProvider enchantmentProvider();

    ItemMappings loadItemMappings();

    EffectProvider effectProvider();

    ItemStackProvider itemStackProvider();

    PropertyProvider propertyProvider();

    Path dataFolder();

    BoarLogger logger();

    BoarPlayerManager<?> playerManager();

    void finalizeBlockMappings(ReferencePopulator<Block> populator);

    void finalizeEntityMappings(ReferencePopulator<EntityType> typePopulator, ReferencePopulator<EntityDefinition> definitionPopulator);

    void finalizeItemMappings(ReferencePopulator<Item> populator);
}
