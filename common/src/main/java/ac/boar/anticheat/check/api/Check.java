package ac.boar.anticheat.check.api;

public interface Check {

    String name();

    String type();

    boolean experimental();

    /**
     * The check class that owns this flag. A typed sub-check keeps the class of its parent check.
     * Use this value to find which integration registered the check. Two integrations can register
     * checks with the same name, so the name alone does not identify the owner.
     */
    default Class<?> origin() {
        return this.getClass();
    }

    void fail();

    void fail(String verbose);
}
