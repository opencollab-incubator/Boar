package ac.boar.anticheat.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

// https://github.com/Mojang/bedrock-protocol-docs/blob/main/additional_docs/AntiCheatServer.properties
@ToString
@Setter
public final class Config {
    public static final Config DEFAULT_CONFIG = new Config();

    @JsonProperty("player-rewind-history-size-ticks")
    @JsonSetter(nulls = Nulls.SKIP)
    private int rewindHistory = 20;
    @JsonProperty("player-position-acceptance-threshold")
    @JsonSetter(nulls = Nulls.SKIP)
    private float acceptanceThreshold = 1.0E-4F;
    @JsonProperty("max-tolerance-compensated-reach")
    @JsonSetter(nulls = Nulls.SKIP)
    private float toleranceReach = 3.005F;
    @JsonProperty("differ-till-alert")
    @JsonSetter(nulls = Nulls.SKIP)
    private float alertThreshold = 0.0F;
    @JsonProperty("disabled-checks")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<String> disabledChecks = new ArrayList<>();
    @JsonProperty("ignore-ghost-block")
    @JsonSetter(nulls = Nulls.SKIP)
    private boolean ignoreGhostBlock;
    @JsonProperty("max-latency-wait")
    @JsonSetter(nulls = Nulls.SKIP)
    private long maxLatencyWait = 15000L;
    @JsonProperty("max-balance-advantage")
    @JsonSetter(nulls = Nulls.SKIP)
    private long maxBalanceAdvantage = 2000L;
    @JsonProperty("debug-mode")
    @JsonSetter(nulls = Nulls.SKIP)
    private boolean debugMode;
    @JsonProperty("prefix")
    @JsonSetter(nulls = Nulls.SKIP)
    private String prefix = "&3Boar &7>&r ";
    // Cached copy of the prefix with & converted to §. Lives on the config instance,
    // so reloading the config (which creates a new instance) resets it.
    @JsonIgnore
    @ToString.Exclude
    private String formattedPrefix;

    public int rewindHistory() {
        return rewindHistory;
    }

    public float acceptanceThreshold() {
        return acceptanceThreshold;
    }

    public float toleranceReach() {
        return Math.max(3.0001F, toleranceReach);
    }

    public float alertThreshold() {
        return alertThreshold;
    }

    public List<String> disabledChecks() {
        return disabledChecks;
    }

    public boolean ignoreGhostBlock() {
        return ignoreGhostBlock;
    }

    public long maxLatencyWait() {
        return maxLatencyWait;
    }

    public long maxBalanceAdvantage() {
        return maxBalanceAdvantage;
    }

    public boolean debugMode() {
        return debugMode;
    }

    public String prefix() {
        return prefix;
    }

    public String formattedPrefix() {
        if (formattedPrefix == null) {
            formattedPrefix = prefix.replace('&', '§');
        }

        return formattedPrefix;
    }
}
