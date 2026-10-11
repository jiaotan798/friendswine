package com.friendswine;

/** 六版本共用的完整画布选帧、摄像机朝向与音乐缩放计算。 */
public final class GuestAnimation {
    private GuestAnimation() {}

    public static int columns(GuestVariant variant) { return variant.columns; }
    public static int rows(GuestVariant variant) { return variant.rows; }
    public static float aspect(GuestVariant variant, boolean dancing) { return dancing ? variant.danceAspect : variant.idleAspect; }

    // 保留已有两角色调用方式，旧客户端验证入口仍可调用。
    public static int columns(boolean emma) { return emma ? 5 : 10; }
    public static int rows(boolean emma) { return emma ? 5 : 10; }
    public static float aspect(boolean emma, boolean dancing) {
        return emma ? 159F / 185F : dancing ? 1F : 169F / 200F;
    }

    /** Upright image plane: its +Z front faces the local camera, without changing entity AI yaw. */
    public static float facingYaw(double dx, double dz, float cameraYaw) {
        return dx * dx + dz * dz > 1e-12 ? (float) Math.toDegrees(Math.atan2(dx, dz)) : 180 - cameraYaw;
    }

    /** 角色 GIF 保持素材原速，艾玛最后一帧的 40ms 由帧号上限覆盖。 */
    public static int frame(GuestVariant variant, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return 0;
        double millis = seconds * 1000 % variant.loopMs;
        return Math.min(variant.frames - 1, (int) (millis / (variant == GuestVariant.EMMA ? 20 : 100)));
    }
    public static int frame(boolean emma, double seconds) {
        return frame(emma ? GuestVariant.EMMA : GuestVariant.KASUMI, seconds);
    }

    public static float scale(double seconds, double min, double max) {
        if (!Double.isFinite(seconds) || !Double.isFinite(min) || !Double.isFinite(max)
                || min < 0.5 || max > 20 || min > max) throw new IllegalArgumentException("Invalid guest scale");
        return (float) (max + (min - max) * JellyAnimation.musicSquash(seconds));
    }

    /** 与 JellyAnimation、GuestVariant 一起编译，再用 -ea 运行以检查选帧、朝向及缩放边界。 */
    public static void main(String[] args) {
        assert facingYaw(0, 1, 0) == 0;
        assert facingYaw(1, 0, 0) == 90;
        assert facingYaw(-1, 0, 0) == -90;
        assert Math.abs(facingYaw(0, -1, 0)) == 180;
        assert facingYaw(0, 0, 180) == 0;
        assert facingYaw(0, 0, 90) == 90;
        assert facingYaw(1e-8, 1e-8, -90) == 270;
        assert frame(GuestVariant.AMIYA, 0.099999) == 0;
        assert frame(GuestVariant.AMIYA, 0.1) == 1;
        assert frame(GuestVariant.AMIYA, 2.699999) == 26;
        assert frame(GuestVariant.AMIYA, 2.7) == 0;
        assert columns(GuestVariant.AMIYA) == 6 && rows(GuestVariant.AMIYA) == 5;
        assert aspect(GuestVariant.AMIYA, false) == 1;
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
