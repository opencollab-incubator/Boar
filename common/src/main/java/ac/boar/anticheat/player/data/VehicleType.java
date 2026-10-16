package ac.boar.anticheat.player.data;

import java.util.Locale;

public enum VehicleType {
    BOAT,
    HORSE,
    CAMEL,
    LLAMA,
    PIG,
    STRIDER,
    MINECART,
    HAPPY_GHAST,
    OTHER;

    public static final float BOAT_OFFSET = 0.375F;

    public static VehicleType fromIdentifier(final String identifier) {
        if (isBoat(identifier)) {
            return BOAT;
        }

        return switch (identifier.toLowerCase(Locale.ROOT)) {
            case "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse" -> HORSE;
            case "minecraft:camel" -> CAMEL;
            case "minecraft:llama", "minecraft:trader_llama" -> LLAMA;
            case "minecraft:pig" -> PIG;
            case "minecraft:strider" -> STRIDER;
            case "minecraft:minecart" -> MINECART;
            case "minecraft:happy_ghast" -> HAPPY_GHAST;
            default -> OTHER;
        };
    }

    // Bedrock has minecraft:boat and minecraft:chest_boat. Java has one type per wood, like minecraft:oak_boat,
    // minecraft:birch_chest_boat and minecraft:bamboo_raft. "minecraft:boat" does not end with "_boat", so it is tested on its own.
    public static boolean isBoat(final String identifier) {
        final String id = identifier.toLowerCase(Locale.ROOT);
        return id.equals("minecraft:boat") || id.endsWith("_boat") || id.endsWith("_raft");
    }
}
