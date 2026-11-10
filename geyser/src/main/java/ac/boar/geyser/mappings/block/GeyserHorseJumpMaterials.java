package ac.boar.geyser.mappings.block;

import ac.boar.mappings.block.HorseJumpMaterial;
import ac.boar.mappings.block.HorseJumpMaterials;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;

import java.util.function.IntFunction;

/**
 * Horse jump materials (honey, carpet) for every Bedrock runtime ID in the session's block palette, by block name.
 * An ID the palette does not define stays UNKNOWN. Through BlockMappingInfo, that also marks the ID as not known.
 * Custom blocks get the default name rule (NONE). See HorseJumpMaterials.
 */
public final class GeyserHorseJumpMaterials {
    private GeyserHorseJumpMaterials() {
    }

    public static IntFunction<HorseJumpMaterial> create(BlockMappings mappings) {
        Int2ObjectOpenHashMap<HorseJumpMaterial> materials = new Int2ObjectOpenHashMap<>();
        materials.defaultReturnValue(HorseJumpMaterial.UNKNOWN);
        for (GeyserBedrockBlock definition : mappings.getStateDefinitionMap().values()) {
            NbtMap state = definition.getState();
            String name = state == null ? null : state.get("name") instanceof String value ? value : null;
            materials.put(definition.getRuntimeId(), HorseJumpMaterials.resolve(name));
        }
        return materials::get;
    }
}
