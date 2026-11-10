package ac.boar.anticheat.prediction.engine.data;

public record BounceGravityCorrection(float requestedY, float actualY) {
    private static final float FLT_EPSILON = 1.1920929E-7F;

    // RequestGravityCorrectionSystem::tick
    public static BounceGravityCorrection fromMove(final float restitutionY, final float requestedY, final float actualY) {
        return Math.abs(restitutionY) > FLT_EPSILON && Math.abs(actualY) > FLT_EPSILON ? new BounceGravityCorrection(requestedY, actualY) : null;
    }

    // MobMovementGravity::tickApplyGravityWithBounceCorrection
    public static float applyGravity(final float velocityY, final float gravity, final BounceGravityCorrection request) {
        if (request == null || Math.abs(request.actualY) <= FLT_EPSILON || Math.abs(gravity) <= FLT_EPSILON) {
            return velocityY + gravity;
        }

        // Preserve each float operation. The correction replaces this gravity application.
        final float absoluteGravity = Math.abs(gravity);
        final float square = request.requestedY * request.requestedY;
        final float twiceGravity = absoluteGravity + absoluteGravity;
        final float distanceTerm = twiceGravity * Math.abs(request.actualY);
        final float root = (float) Math.sqrt(square + distanceTerm);
        final float fraction = Math.abs((Math.abs(request.requestedY) - root) / gravity);
        final float remainder = 1.0F - fraction;
        final float sign = request.requestedY > 0.0F ? 1.0F : request.requestedY < 0.0F ? -1.0F : 0.0F;
        final float signedRemainder = sign * remainder;
        final float correction = absoluteGravity * signedRemainder;
        return correction + velocityY;
    }
}
