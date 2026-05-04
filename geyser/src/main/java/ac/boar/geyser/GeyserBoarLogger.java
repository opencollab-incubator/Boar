package ac.boar.geyser;

import ac.boar.anticheat.BoarLogger;
import org.geysermc.geyser.api.extension.ExtensionLogger;

public record GeyserBoarLogger(ExtensionLogger logger) implements BoarLogger {

    @Override
    public void info(String message) {
        this.logger.info(message);
    }

    @Override
    public void warn(String message) {
        this.logger.warning(message);
    }

    @Override
    public void error(String message) {
        this.logger.error(message);
    }

    @Override
    public void error(String message, Throwable t) {
        this.logger.error(message, t);
    }
}
