package ac.boar.anticheat.data.vanilla;

import java.util.Locale;

public enum Attribute {
    MOVEMENT;

    public String getIdentifier() {
        return "minecraft:" + this.name().toLowerCase(Locale.ROOT);
    }
}
