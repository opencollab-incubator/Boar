package ac.boar.anticheat.data;

import ac.boar.anticheat.util.math.Box;
import ac.boar.anticheat.util.math.Vec3;

public record EntityDimensions(float width, float height, float eyeHeight, boolean fixed) {
	private EntityDimensions(float width, float height, boolean fixed) {
		this(width, height, getDefaultEyeHeight(height), fixed);
	}

	private static float getDefaultEyeHeight(float height) {
		return height * 0.85F;
	}

	public Box getBoxAt(Vec3 pos) {
		return this.getBoxAt(pos.x, pos.y, pos.z);
	}

	public Box getBoxAt(float x, float y, float z) {
		float g = this.width / 2.0F;
        return new Box(x - g, y, z - g, x + g, y + this.height, z + g);
	}

    public EntityDimensions hardScaled(float ratio) {
		// Also I find it pretty funny that this doesn't seem to affect camera (for the session player) therefore don't scale the eyeHeight.
        return new EntityDimensions(this.width * ratio, this.height * ratio, this.eyeHeight, false);
    }

	public EntityDimensions scaled(float ratio) {
		return this.scaled(ratio, ratio);
	}

	public EntityDimensions scaled(float widthRatio, float heightRatio) {
		return !this.fixed && (widthRatio != 1.0F || heightRatio != 1.0F)
			? new EntityDimensions(this.width * widthRatio, this.height * heightRatio, this.eyeHeight * heightRatio, false)
			: this;
	}

	public static EntityDimensions changing(float width, float height) {
		return new EntityDimensions(width, height, false);
	}

	public static EntityDimensions fixed(float width, float height) {
		return new EntityDimensions(width, height, true);
	}

	public EntityDimensions withEyeHeight(float eyeHeight) {
		return new EntityDimensions(this.width, this.height, eyeHeight, this.fixed);
	}
}
