package ac.boar.geyser.mappings.block;

import ac.boar.mappings.block.PropertyProvider;
import org.geysermc.geyser.level.block.property.ChestType;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.physics.Direction;

public class GeyserPropertyProvider implements PropertyProvider {
    private static final GeyserPropertyProvider INSTANCE = new GeyserPropertyProvider();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Comparable<T>> GeyserProperty<T> get(String key) {
        return switch (key) {
            case "age_3" -> new GeyserProperty(Properties.AGE_3);
            case "bell_attachment" -> new GeyserProperty(Properties.BELL_ATTACHMENT);
            case "bites" -> new GeyserProperty(Properties.BITES);
            case "chest_type" -> new GeyserProperty<>(
                    Properties.CHEST_TYPE,
                    raw -> (T) ac.boar.mappings.block.ChestType.VALUES[((ChestType) raw).ordinal()]
            );
            case "door_hinge" -> new GeyserProperty(Properties.DOOR_HINGE);
            case "drag" -> new GeyserProperty(Properties.DRAG);
            case "half" -> new GeyserProperty(Properties.HALF);
            case "has_book" -> new GeyserProperty(Properties.HAS_BOOK);
            case "horizontal_facing" -> new GeyserProperty<>(
                    Properties.HORIZONTAL_FACING,
                    raw -> (T) ac.boar.anticheat.util.math.Direction.VALUES[((Direction) raw).ordinal()]
            );
            case "level" -> new GeyserProperty(Properties.LEVEL);
            case "lit" -> new GeyserProperty(Properties.LIT);
            case "open" -> new GeyserProperty(Properties.OPEN);
            case "respawn_anchor_charges" -> new GeyserProperty(Properties.RESPAWN_ANCHOR_CHARGES);
            case "vault_state" -> new GeyserProperty(Properties.VAULT_STATE);
            default -> throw new IllegalArgumentException("Unknown property: " + key);
        };
    }

    public static GeyserPropertyProvider get() {
        return INSTANCE;
    }
}
