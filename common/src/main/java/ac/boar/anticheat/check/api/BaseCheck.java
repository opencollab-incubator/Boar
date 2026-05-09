package ac.boar.anticheat.check.api;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.player.BoarPlayer;
import ac.boar.api.anticheat.annotations.CheckInfo;
import ac.boar.api.anticheat.annotations.Experimental;

public class BaseCheck implements Check {
    protected final BoarPlayer player;

    private final String name, type;
    private final boolean experimental;
    private int vl = 0;

    public BaseCheck(BoarPlayer player) {
        this.player = player;
        this.name = getClass().getDeclaredAnnotation(CheckInfo.class).name();
        this.type = getClass().getDeclaredAnnotation(CheckInfo.class).type();
        this.experimental = getClass().getDeclaredAnnotation(Experimental.class) != null;
    }

    public BaseCheck(BoarPlayer player, String name, String type, boolean experimental) {
        this.player = player;
        this.name = name;
        this.type = type;
        this.experimental = experimental;
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

        final StringBuilder builder = new StringBuilder("§3" + getDisplayName() + "§7 failed§6 " + name);
        if (!this.type.isBlank()) {
            builder.append(" (").append(type).append(")");
        }

        if (this.experimental) {
            builder.append(" §a(Experimental)");
        }

        builder.append(" §7x").append(vl).append(" ").append(verbose);
        Boar.getInstance().getAlertManager().alert(builder.toString());
    }

    protected final String getDisplayName() {
        return player.getSession().name();
    }
}
