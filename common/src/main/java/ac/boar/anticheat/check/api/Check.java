package ac.boar.anticheat.check.api;

public interface Check {

    String name();

    String type();

    boolean experimental();

    void fail();

    void fail(String verbose);
}
