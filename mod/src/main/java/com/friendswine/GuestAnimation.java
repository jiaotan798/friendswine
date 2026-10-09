package com.friendswine;

/** Full-canvas GIF frame timing shared by all six game targets. */
public final class GuestAnimation {
    private GuestAnimation() {}

    public static int columns(boolean emma) { return emma ? 5 : 10; }
    public static int rows(boolean emma) { return emma ? 5 : 10; }
    public static float aspect(boolean emma, boolean dancing) {
        return emma ? 159F / 185F : dancing ? 1F : 169F / 200F;
    }

    /** Upright image plane: its +Z front faces the local camera, without changing entity AI yaw. */
    public static float facingYaw(double dx, double dz, float cameraYaw) {
        return dx * dx + dz * dz > 1e-12 ? (float) Math.toDegrees(Math.atan2(dx, dz)) : 180 - cameraYaw;
    }

    public static int frame(boolean emma, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return 0;
        double millis = seconds * 1000 % (emma ? 440 : 9900);
        return emma ? Math.min(20, (int) (millis / 20)) : Math.min(98, (int) (millis / 100));
    }

    public static float scale(double seconds, double min, double max) {
        if (!Double.isFinite(seconds) || !Double.isFinite(min) || !Double.isFinite(max)
                || min < 0.5 || max > 20 || min > max) throw new IllegalArgumentException("Invalid guest scale");
        return (float) (max + (min - max) * JellyAnimation.musicSquash(seconds));
    }

    /** Standalone check: compile with JellyAnimation, then run java -ea com.friendswine.GuestAnimation. */
    public static void main(String[] args) {
        assert facingYaw(0, 1, 0) == 0;
        assert facingYaw(1, 0, 0) == 90;
        assert facingYaw(-1, 0, 0) == -90;
        assert Math.abs(facingYaw(0, -1, 0)) == 180;
        assert facingYaw(0, 0, 180) == 0;
        assert facingYaw(0, 0, 90) == 90;
        assert facingYaw(1e-8, 1e-8, -90) == 270;
        assert frame(false, 0) == 0;
        assert frame(false, 0.099999) == 0;
        assert frame(false, 0.1) == 1;
        assert frame(false, 9.89999) == 98;
        assert frame(false, 9.9) == 0;
        assert frame(true, 0.019999) == 0;
        assert frame(true, 0.02) == 1;
        assert frame(true, 0.4) == 20;
        assert frame(true, 0.439999) == 20;
        assert frame(true, 0.44) == 0;
        assert frame(true, Double.NaN) == 0;
        assert frame(false, -1) == 0;
        for (double t = 0; t < 198; t += 0.001) {
            assert frame(false, t) >= 0 && frame(false, t) < 99;
            assert frame(true, t) >= 0 && frame(true, t) < 21;
            assert scale(t, 0.5, 20) >= 0.49999 && scale(t, 0.5, 20) <= 20.00001;
            assert scale(t, 2, 2) == 2;
        }
        assert Math.abs(scale(0, 0.75, 1.25) - 1.25) < 1e-6;
        assert Math.abs(scale(0.25, 0.75, 1.25) - 0.75) < 1e-6;
        System.out.println("Camera-facing cardinal/overlap checks, GIF timings and scale extremes passed.");
    }
}
