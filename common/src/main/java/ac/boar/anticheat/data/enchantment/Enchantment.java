package ac.boar.anticheat.data.enchantment;

public enum Enchantment {
    DEPTH_STRIDER,
    RIPTIDE,
    SOUL_SPEED;

    public static Enchantment byId(int id) {
        return EnchantmentInst.provider().byId(id);
    }
}
