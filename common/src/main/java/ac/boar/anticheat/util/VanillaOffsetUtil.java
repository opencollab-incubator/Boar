package ac.boar.anticheat.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VanillaOffsetUtil {

    // SemVersion::fromString accepts 1 to 3 unsigned 16-bit components and optional suffixes.
    private static final String COMPONENT = "(0|[1-9][0-9]{0,4})";
    private static final String PRERELEASE_PART = "(?:0|[1-9A-Za-z-][0-9A-Za-z-]*|0[0-9]*[A-Za-z-][0-9A-Za-z-]*)";
    private static final Pattern VERSION = Pattern.compile(COMPONENT + "(?:\\." + COMPONENT
            + "(?:\\." + COMPONENT + "(?:-" + PRERELEASE_PART + "(?:\\." + PRERELEASE_PART + ")*)?"
            + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?)?)?");

    private VanillaOffsetUtil() {
    }

    public static float sneakingEyeHeightReduction(final String worldVersion) {
        if (worldVersion == null) {
            return 0.35F;
        }
        final Matcher version = VERSION.matcher(worldVersion);
        if (!version.matches()) {
            // LevelData uses BaseGameVersion::ANY for empty or invalid versions. Wildcard (*) and beta also select 0.35F.
            return 0.35F;
        }
        final int major = Integer.parseInt(version.group(1));
        final int minor = version.group(2) == null ? 0 : Integer.parseInt(version.group(2));
        final int patch = version.group(3) == null ? 0 : Integer.parseInt(version.group(3));
        if (major > 0xFFFF || minor > 0xFFFF || patch > 0xFFFF) {
            return 0.35F;
        }
        // BaseGameVersion::fromString removes suffixes. VanillaOffsetSystem::tick compares against 1.20.10.
        final boolean legacy = major < 1 || major == 1 && (minor < 20 || minor == 20 && patch < 10);
        return legacy ? 0.125F : 0.35F;
    }
}
