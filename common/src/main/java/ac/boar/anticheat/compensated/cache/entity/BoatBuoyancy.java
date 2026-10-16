package ac.boar.anticheat.compensated.cache.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public final class BoatBuoyancy {
    public enum Movement {
        NONE, WAVES, BOBBING
    }

    // The boat makes this component before its spawn metadata arrives. So it starts with these defaults and no liquids.
    private boolean enabled = true;
    private float base = 1.0F;
    private boolean applyGravity = true;
    private Movement movement = Movement.WAVES;
    private boolean autoStep;
    private final Set<String> liquids = new HashSet<>();

    public boolean enabled() {
        return this.enabled;
    }

    public float base() {
        return this.base;
    }

    public boolean appliesGravity() {
        return this.enabled && this.applyGravity;
    }

    public Movement movement() {
        return this.movement;
    }

    public boolean autoStep() {
        return this.autoStep;
    }

    public boolean hasLiquids() {
        return this.enabled && !this.liquids.isEmpty();
    }

    public boolean matches(final String identifier) {
        return this.enabled && this.liquids.contains(identifier);
    }

    public void update(final EntityDataMap update) {
        // Loop over the new entries only, so we keep the order they were sent in. The merged metadata map also holds old entries.
        update.forEach((type, value) -> {
            if (type == EntityDataTypes.IS_BUOYANT) {
                final boolean enabled = Boolean.TRUE.equals(value);
                if (enabled && !this.enabled) {
                    this.reset();
                }
                this.enabled = enabled;
            } else if (type == EntityDataTypes.BUOYANCY_DATA && this.enabled) {
                this.load((String) value);
            }
        });
    }

    private void reset() {
        this.base = 1.0F;
        this.applyGravity = true;
        this.movement = Movement.WAVES;
        this.autoStep = false;
        this.liquids.clear();
    }

    private void load(final String data) {
        // BuoyancyComponent::loadData clears the list even when the JSON cannot be parsed.
        this.liquids.clear();
        final JsonObject profile;
        try {
            profile = new JsonParser().parse(data).getAsJsonObject();
        } catch (RuntimeException invalid) {
            return;
        }

        this.base = number(profile, "base_buoyancy", this.base);
        this.applyGravity = bool(profile, "apply_gravity", this.applyGravity);
        final JsonElement mode = profile.get("movement_type");
        final String name = mode != null && mode.isJsonPrimitive() && mode.getAsJsonPrimitive().isString() ? mode.getAsString() : "";
        this.movement = switch (name) {
            case "none" -> Movement.NONE;
            case "bobbing" -> Movement.BOBBING;
            default -> Movement.WAVES;
        };
        this.autoStep = bool(profile, "can_auto_step_from_liquid", this.autoStep);

        final JsonElement blocks = profile.get("liquid_blocks");
        if (blocks != null && blocks.isJsonArray()) {
            for (final JsonElement block : blocks.getAsJsonArray()) {
                if (block.isJsonPrimitive() && block.getAsJsonPrimitive().isString()) {
                    final String identifier = block.getAsString();
                    this.liquids.add(identifier.contains(":") ? identifier : "minecraft:" + identifier);
                }
            }
        }
    }

    // Json::Value conversions use the default only for null. Other JSON types are converted (coerced) to a number or boolean.
    private static float number(final JsonObject profile, final String key, final float fallback) {
        final JsonElement value = profile.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        if (!value.isJsonPrimitive()) {
            return 0;
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean() ? 1 : 0;
        }
        if (!value.getAsJsonPrimitive().isNumber()) {
            return 0;
        }

        final String literal = value.getAsString();
        if (literal.indexOf('.') < 0 && literal.indexOf('e') < 0 && literal.indexOf('E') < 0) {
            final BigInteger integer = value.getAsBigInteger();
            if (integer.bitLength() <= (integer.signum() < 0 ? 63 : 64)) {
                return integer.floatValue();
            }
        }
        return (float) value.getAsDouble();
    }

    private static boolean bool(final JsonObject profile, final String key, final boolean fallback) {
        final JsonElement value = profile.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        if (value.isJsonArray()) {
            return value.getAsJsonArray().size() != 0;
        }
        if (value.isJsonObject()) {
            return !value.getAsJsonObject().entrySet().isEmpty();
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return value.getAsDouble() != 0;
        }
        final String string = value.getAsString();
        return !string.isEmpty() && string.charAt(0) != 0;
    }
}
