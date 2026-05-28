package ac.boar.anticheat.violation;

@FunctionalInterface
public interface ViolationListener {
    void onViolation(Violation violation);
}
