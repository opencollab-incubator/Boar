package ac.boar.geyser;

import ac.boar.anticheat.BoarLogger;
import ac.boar.anticheat.BoarPlatform;
import ac.boar.anticheat.data.block.BlockStateFactory;
import ac.boar.anticheat.data.effect.EffectProvider;
import ac.boar.anticheat.data.enchantment.EnchantmentProvider;
import ac.boar.anticheat.data.inventory.ItemStackProvider;
import ac.boar.anticheat.player.BoarPlayerManager;
import ac.boar.anticheat.util.ReferencePopulator;
import ac.boar.geyser.anticheat.data.block.GeyserBlockStateFactory;
import ac.boar.geyser.anticheat.data.effect.GeyserEffectProvider;
import ac.boar.geyser.anticheat.data.enchantment.GeyserEnchantmentProvider;
import ac.boar.geyser.anticheat.data.inventory.GeyserItemStackProvider;
import ac.boar.geyser.mappings.block.GeyserBlockMappings;
import ac.boar.geyser.mappings.block.GeyserPropertyProvider;
import ac.boar.geyser.mappings.entity.GeyserEntityMappings;
import ac.boar.geyser.mappings.item.GeyserItemMappings;
import ac.boar.mappings.block.Block;
import ac.boar.mappings.block.BlockMappings;
import ac.boar.mappings.block.PropertyProvider;
import ac.boar.mappings.entity.EntityDefinition;
import ac.boar.mappings.entity.EntityType;
import ac.boar.mappings.item.Item;
import ac.boar.mappings.item.ItemMappings;
import org.geysermc.geyser.session.GeyserSession;

import java.nio.file.Path;

public record GeyserBoarPlatform(Path dataFolder, BoarLogger logger, BoarPlayerManager<GeyserSession> playerManager) implements BoarPlatform {

    @Override
    public BlockMappings loadBlockMappings() {
        return GeyserBlockMappings.load();
    }

    @Override
    public BlockStateFactory blockStateFactory() {
        return GeyserBlockStateFactory.get();
    }

    @Override
    public ItemMappings loadItemMappings() {
        return GeyserItemMappings.load();
    }

    @Override
    public EnchantmentProvider enchantmentProvider() {
        return GeyserEnchantmentProvider.get();
    }

    @Override
    public EffectProvider effectProvider() {
        return GeyserEffectProvider.get();
    }

    @Override
    public ItemStackProvider itemStackProvider() {
        return GeyserItemStackProvider.get();
    }

    @Override
    public PropertyProvider propertyProvider() {
        return GeyserPropertyProvider.get();
    }

    @Override
    public void finalizeBlockMappings(ReferencePopulator<Block> populator) {
        GeyserBlockMappings.finalizeBlockMappings(populator);
    }

    @Override
    public void finalizeEntityMappings(ReferencePopulator<EntityType> typePopulator, ReferencePopulator<EntityDefinition> definitionPopulator) {
        GeyserEntityMappings.finalizeEntityMappings(typePopulator, definitionPopulator);
    }

    @Override
    public void finalizeItemMappings(ReferencePopulator<Item> populator) {
        GeyserItemMappings.finalizeItemMappings(populator);
    }
}
