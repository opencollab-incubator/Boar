package ac.boar.geyser.mappings.block;

import ac.boar.mappings.block.PropertyProvider;
import org.geysermc.geyser.level.block.property.ChestType;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.physics.Direction;

import java.util.Optional;
import java.util.function.Function;

public class GeyserPropertyProvider implements PropertyProvider {
    private static final GeyserPropertyProvider INSTANCE = new GeyserPropertyProvider();

    @Override
    public <T extends Comparable<T>> GeyserProperty<T> get(String key) {
        return switch (key) {
            case "age_3" -> new GeyserProperty(Properties.AGE_3);
            case "bell_attachment" -> new GeyserProperty(Properties.BELL_ATTACHMENT);
            case "bites" -> new GeyserProperty(Properties.BITES);
            case "chest_type" -> enumProperty(
                    Properties.CHEST_TYPE,
                    (ac.boar.mappings.block.ChestType type) -> ChestType.VALUES[type.ordinal()],
                    type -> ac.boar.mappings.block.ChestType.VALUES[type.ordinal()]
            );
            case "door_hinge" -> new GeyserProperty(Properties.DOOR_HINGE);
            case "drag" -> new GeyserProperty(Properties.DRAG);
            case "half" -> new GeyserProperty(Properties.HALF);
            case "has_book" -> new GeyserProperty(Properties.HAS_BOOK);
            case "horizontal_facing" -> enumProperty(
                    Properties.HORIZONTAL_FACING,
                    (ac.boar.anticheat.util.math.Direction d) -> Direction.VALUES[d.ordinal()],
                    d -> ac.boar.anticheat.util.math.Direction.VALUES[d.ordinal()]
            );
            case "level" -> new GeyserProperty(Properties.LEVEL);
            case "lit" -> new GeyserProperty(Properties.LIT);
            case "open" -> new GeyserProperty(Properties.OPEN);
            case "respawn_anchor_charges" -> new GeyserProperty(Properties.RESPAWN_ANCHOR_CHARGES);
            case "vault_state" -> new GeyserProperty(Properties.VAULT_STATE);
            default -> throw new IllegalArgumentException("Unknown property: " + key);
        };
    }

    @SuppressWarnings("rawtypes")
    private static <F extends Comparable<F>, T extends Comparable<T>> GeyserProperty enumProperty(org.geysermc.geyser.level.block.property.Property<F> property, Function<T, F> from, Function<F, T> to) {
        return new GeyserProperty<T>(new org.geysermc.geyser.level.block.property.Property<>(property.name()) {

            @Override
            public int valuesCount() {
                return property.valuesCount();
            }

            @Override
            public int indexOf(T value) {
                return property.indexOf(from.apply(value));
            }

            @Override
            public Optional<T> valueOf(String string) {
                return property.valueOf(string).map(to);
            }

            @Override
            public String toString() {
                return from.toString();
            }
        });
    }

    public static GeyserPropertyProvider get() {
        return INSTANCE;
    }
}
