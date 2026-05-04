package ac.boar.anticheat.util;

public record Dimension(int minY, int height) {
    public static final Dimension OVERWORLD = new Dimension(-64, 384);
    public static final Dimension THE_NETHER = new Dimension(0, 128);
    public static final Dimension THE_END = new Dimension(0, 256);

    public int maxY() {
        return this.minY + this.height;
    }
}
