package ac.boar.anticheat;

public interface BoarLogger {

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable t);
}
