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
    @JsonProperty("player-position-drift-amount")
    @JsonSetter(nulls = Nulls.SKIP)
    private float positionDriftAmount = 0.0F;
    @JsonProperty("player-position-retry-max-offset")
    @JsonSetter(nulls = Nulls.SKIP)
    private float retryMaxOffset = 0.0F;
    @JsonProperty("max-tolerance-compensated-reach")
    @JsonSetter(nulls = Nulls.SKIP)
    private float toleranceReach = 2.91F;
    @JsonProperty("differ-till-alert")
    @JsonSetter(nulls = Nulls.SKIP)
    private float alertThreshold = 0.0F;
    @JsonProperty("disabled-checks")
    @JsonSetter(nulls = Nulls.SKIP)
    private List<String> disabledChecks = new ArrayList<>();
    @JsonProperty("disable-mitigations")
    @JsonSetter(nulls = Nulls.SKIP)
    private boolean disableMitigations;
    @JsonProperty("max-latency-wait")
    @JsonSetter(nulls = Nulls.SKIP)
    private long maxLatencyWait = 15000L;
    @JsonProperty("max-balance-advantage")
    @JsonSetter(nulls = Nulls.SKIP)
    private long maxBalanceAdvantage = 2000L;
    @JsonProperty("correction-flag-cooldown-ticks")
    @JsonSetter(nulls = Nulls.SKIP)
    private int correctionFlagCooldownTicks = 100;
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

    public float positionDriftAmount() {
        return Math.max(0.0F, positionDriftAmount);
    }

    public float retryMaxOffset() {
        return Math.max(0.0F, retryMaxOffset);
    }

    public float toleranceReach() {
        return Math.max(2.9F, toleranceReach);
    }

    public float alertThreshold() {
        return alertThreshold;
    }

    public List<String> disabledChecks() {
        return disabledChecks;
    }

    public boolean disableMitigations() {
        return disableMitigations;
    }

    public long maxLatencyWait() {
        return maxLatencyWait;
    }

    public long maxBalanceAdvantage() {
        return maxBalanceAdvantage;
    }

    public int correctionFlagCooldownTicks() {
        return Math.max(0, correctionFlagCooldownTicks);
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
