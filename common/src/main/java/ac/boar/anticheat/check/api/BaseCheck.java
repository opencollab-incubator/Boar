package ac.boar.anticheat.check.api;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.anticheat.violation.Violation;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.api.anticheat.annotations.Experimental;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BaseCheck implements Check {
    protected final BoarPlayer player;

    private final String name, type;
    private final boolean experimental;
    private final Class<?> origin;
    private final Map<String, BaseCheck> typedChecks = new ConcurrentHashMap<>();
    private int vl = 0;

    public BaseCheck(BoarPlayer player) {
        this.player = player;
        this.name = getClass().getDeclaredAnnotation(CheckInfo.class).name();
        this.type = getClass().getDeclaredAnnotation(CheckInfo.class).type();
        this.experimental = getClass().getDeclaredAnnotation(Experimental.class) != null;
        this.origin = getClass();
    }

    public BaseCheck(BoarPlayer player, String name, String type, boolean experimental) {
        this(player, name, type, experimental, null);
    }

    /**
     * Makes a check that reports {@code origin} as its owner. A typed sub-check uses this
     * constructor to keep the class of the parent check that made it.
     *
     * @param origin the class that owns the flag, or null to use the class of this check
     */
    public BaseCheck(BoarPlayer player, String name, String type, boolean experimental, Class<?> origin) {
        this.player = player;
        this.name = name;
        this.type = type;
        this.experimental = experimental;
        this.origin = origin == null ? getClass() : origin;
    }

    @Override
    public Class<?> origin() {
        return this.origin;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String type() {
        return this.type;
    }

    @Override
    public boolean experimental() {
        return this.experimental;
    }

    public int vl() {
        return this.vl;
    }

    @Override
    public void fail() {
        fail("");
    }

    @Override
    public void fail(String verbose) {
        this.vl++;
        Boar.getInstance().getViolationRegistry().dispatch(new Violation(this.player, this, this.vl, verbose));
    }

    public void fail(Enum<?> type, String verbose) {
        fail(type == null ? null : type.name(), verbose);
    }

    public void fail(String type, String verbose) {
        String typedKey = type == null ? "" : type;
        String disabledName = typedKey.isEmpty() ? this.name : this.name + "-" + typedKey;
        if (Boar.getConfig().disabledChecks().contains(disabledName)) {
            return;
        }

        typedChecks.computeIfAbsent(typedKey, key -> new BaseCheck(this.player, this.name, key, this.experimental, this.origin)).fail(verbose);
    }

    protected final String getDisplayName() {
        return player.getSession().name();
    }
}
