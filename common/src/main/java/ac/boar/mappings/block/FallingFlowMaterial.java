package ac.boar.mappings.block;

/** The native material result used only by the falling-flow guard. */
public enum FallingFlowMaterial {
    APPLIES_DOWNWARD_FLOW,
    NO_DOWNWARD_FLOW,
    NOT_SEARCHED,
    CUSTOM,
    MALFORMED,
    UNSUPPORTED_PROTOCOL;

    public boolean isKnown() {
        return this == APPLIES_DOWNWARD_FLOW || this == NO_DOWNWARD_FLOW;
    }

    public boolean appliesDownwardFlow() {
        return this == APPLIES_DOWNWARD_FLOW;
    }
}
