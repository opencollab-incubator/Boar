package ac.boar.mappings.block;

/** The two BlockType property bits the client checks when a horse jumps. */
public enum HorseJumpMaterial {
    UNKNOWN(false, false, false),
    NONE(true, false, false),
    CARPET(true, true, false),
    HONEY(true, false, true);

    public final boolean known, belowProbe, preventsJump;

    HorseJumpMaterial(final boolean known, final boolean belowProbe, final boolean preventsJump) {
        this.known = known;
        this.belowProbe = belowProbe;
        this.preventsJump = preventsJump;
    }
}
