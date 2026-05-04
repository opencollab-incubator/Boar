package ac.boar.anticheat.util;

public class DimensionUtil {
    public static final int OVERWORLD_ID = 0;
    public static final int DEFAULT_NETHER_ID = 1;

    public static Dimension dimensionFromId(int id) {
        return id == OVERWORLD_ID ? Dimension.OVERWORLD : id == DEFAULT_NETHER_ID ? Dimension.THE_NETHER : Dimension.THE_END;
    }
}
