package ac.boar.geyser.mappings.block;

import ac.boar.mappings.block.FallingFlowMaterial;
import ac.boar.mappings.block.FallingFlowMaterials;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.GeyserBedrockBlock;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

public final class GeyserFallingFlowMaterials {
    private GeyserFallingFlowMaterials() {
    }

    public static IntFunction<FallingFlowMaterial> create(int protocol, BlockMappings mappings) {
        var lookup = FallingFlowMaterials.forProtocol(protocol);
        if (!lookup.isSupported()) {
            return runtimeId -> FallingFlowMaterial.UNSUPPORTED_PROTOCOL;
        }

        Set<Object> customDefinitions;
        try {
            // Geyser exposes an unrelocated Fastutil return type. Keep that type outside the shaded API boundary.
            Object value = BlockMappings.class.getMethod("getCustomBlockStateDefinitions").invoke(mappings);
            if (!(value instanceof Map<?, ?> definitions)) {
                return runtimeId -> FallingFlowMaterial.NOT_SEARCHED;
            }
            customDefinitions = new HashSet<>(definitions.values());
        } catch (ReflectiveOperationException exception) {
            return runtimeId -> FallingFlowMaterial.NOT_SEARCHED;
        }
        Int2ObjectOpenHashMap<FallingFlowMaterial> materials = new Int2ObjectOpenHashMap<>();
        materials.defaultReturnValue(FallingFlowMaterial.NOT_SEARCHED);
        for (GeyserBedrockBlock definition : mappings.getStateDefinitionMap().values()) {
            if (customDefinitions.contains(definition)) {
                materials.put(definition.getRuntimeId(), FallingFlowMaterial.CUSTOM);
                continue;
            }
            NbtMap state = definition.getState();
            if (state == null || !(state.get("name") instanceof String name)
                    || !(state.get("states") instanceof NbtMap states)) {
                materials.put(definition.getRuntimeId(), FallingFlowMaterial.MALFORMED);
                continue;
            }
            materials.put(definition.getRuntimeId(), lookup.resolve(name, states));
        }
        return materials::get;
    }

}
