package ac.boar.mappings.block;

import ac.boar.anticheat.util.math.Direction;

public final class Properties {
    public static final Property<Integer> AGE_3 = create("age_3");
    public static final Property<String> BELL_ATTACHMENT = create("bell_attachment");
    public static final Property<Integer> BITES = create("bites");
    public static final Property<ChestType> CHEST_TYPE = create("chest_type");
    public static final Property<String> DOOR_HINGE = create("door_hinge");
    public static final Property<Boolean> DRAG = create("drag");
    public static final Property<String> HALF = create("half");
    public static final Property<Boolean> HANGING = create("hanging");
    public static final Property<Boolean> HAS_BOOK = create("has_book");
    public static final Property<Direction> HORIZONTAL_FACING = create("horizontal_facing");
    public static final Property<Integer> LEVEL = create("level");
    public static final Property<Boolean> LIT = create("lit");
    public static final Property<Boolean> OPEN = create("open");
    public static final Property<Integer> RESPAWN_ANCHOR_CHARGES = create("respawn_anchor_charges");
    public static final Property<String> VAULT_STATE = create("vault_state");

    private static <T extends Comparable<T>> Property<T> create(String key) {
        return BlockMappingsInst.property(key);
    }
}
