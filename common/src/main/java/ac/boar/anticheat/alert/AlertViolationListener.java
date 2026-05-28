package ac.boar.anticheat.alert;

import ac.boar.anticheat.Boar;
import ac.boar.anticheat.check.api.Check;
import ac.boar.anticheat.violation.Violation;
import ac.boar.anticheat.violation.ViolationListener;

public final class AlertViolationListener implements ViolationListener {
    @Override
    public void onViolation(Violation violation) {
        final Check check = violation.check();

        final StringBuilder builder = new StringBuilder("§3" + violation.player().getSession().name() + "§7 failed§6 " + check.name());
        if (!check.type().isBlank()) {
            builder.append(" (").append(check.type()).append(")");
        }
        if (check.experimental()) {
            builder.append(" §a(Experimental)");
        }
        builder.append(" §7x").append(violation.vl()).append(" ").append(violation.verbose());

        Boar.getInstance().getAlertManager().alert(builder.toString());
    }
}
