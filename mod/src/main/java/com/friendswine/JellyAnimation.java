package com.friendswine;

/** The five scale keys from the approved Blockbench animation, in seconds. */
public final class JellyAnimation {
    public static final double LENGTH = 0.91667;
    public static final int MUSIC_FRAMES = 2482560;
    public static final int MUSIC_SAMPLE_RATE = 44100;
    public static final double MUSIC_LENGTH = (double) MUSIC_FRAMES / MUSIC_SAMPLE_RATE;
    private static final double[] TIMES = {0, 0.25, 0.45833, 0.70833, LENGTH};
    private static final double[] VALUES = {0, 1, 0, 1, 0};

    private JellyAnimation() {}

    public static double phase(double seconds) {
        return Math.max(0, seconds) % LENGTH;
    }

    public static float angle(double seconds) {
        return (float) (-360 * phase(seconds) / LENGTH);
    }

    public static float squash(double seconds) {
        double time = phase(seconds);
        int i = 0;
        while (i < TIMES.length - 2 && time > TIMES[i + 1]) i++;
        double t = (time - TIMES[i]) / (TIMES[i + 1] - TIMES[i]);
        double p1 = VALUES[i];
        double p2 = VALUES[i + 1];
        // Alternating extrema in this looping Catmull-Rom track have zero tangents.
        return (float) (p1 + (p2 - p1) * t * t * (3 - 2 * t));
    }

    public static float heightScale(double seconds) {
        return heightScale(squash(seconds), 50);
    }

    public static float heightScale(float squash, double compressionPercent) {
        return (float) (1 - compressionPercent / 100 * squash);
    }

    public static float widthScale(float squash, double widthPercent) {
        return (float) (1 + widthPercent / 100 * squash);
    }

    public static long musicFrame(long elapsedTicks) {
        // Reduce before multiplying, so even long-running saves cannot overflow the sample clock.
        return Math.floorMod(Math.floorMod(elapsedTicks, MUSIC_FRAMES) * (MUSIC_SAMPLE_RATE / 20), MUSIC_FRAMES);
    }

    public static float musicSquash(double seconds) {
        double time = Math.max(0, seconds) % MUSIC_LENGTH;
        float value = squash(time);
        double tail = (time - (MUSIC_LENGTH - 0.1)) / 0.1;
        if (tail > 0) value *= (float) (1 - tail * tail * (3 - 2 * tail));
        return value;
    }

    public static double eyeOffset(double seconds, double eyeHeight) {
        return eyeHeight * (heightScale(seconds) - 1);
    }

    /** Standalone check: javac this file, then java -ea com.friendswine.JellyAnimation. */
    public static void main(String[] args) {
        assert Math.abs(squash(0)) < 1e-6;
        assert Math.abs(squash(0.25) - 1) < 1e-6;
        assert Math.abs(squash(0.45833)) < 1e-6;
        assert Math.abs(squash(0.70833) - 1) < 1e-6;
        assert Math.abs(squash(LENGTH)) < 1e-6;
        assert Math.abs(heightScale(0) - 1) < 1e-6;
        assert Math.abs(heightScale(0.25) - 0.5) < 1e-6;
        assert Math.abs(eyeOffset(0, 1.62)) < 1e-6;
        assert Math.abs(eyeOffset(0.25, 1.62) + 0.81) < 1e-6;
        assert Math.abs(eyeOffset(0.25, 1.27) + 0.635) < 1e-6;
        assert Math.abs(eyeOffset(0.45833, 1.62)) < 1e-6;
        // Independent samples read back from Blockbench's renderer.
        assert Math.abs(squash(0.05) - 0.104) < 1e-6;
        assert Math.abs(squash(0.15) - 0.648) < 1e-6;
        assert Math.abs(squash(0.90) - 0.0181819354) < 1e-6;
        assert Math.abs(squash(0.3166678181818182) - 0.7583220985758712) < 1e-5;
        assert Math.abs(squash(0.6500023636363637) - 0.8620985322330896) < 1e-5;
        for (double t = 0; t < MUSIC_LENGTH; t += 0.0037) {
            assert Math.abs(squash(t) - squash(t + LENGTH)) < 1e-4;
            assert squash(t) >= -0.0001 && squash(t) <= 1.0001;
            assert Math.abs(eyeOffset(t, 1.62) - eyeOffset(t + LENGTH, 1.62)) < 1e-4;
            assert Math.abs(1.62 + eyeOffset(t, 1.62) - 1.62 * heightScale(t)) < 1e-6;
            assert Math.abs(eyeOffset(t - 0.00001, 1.62) - eyeOffset(t + 0.00001, 1.62)) < 0.0002;
        }
        assert Math.abs(angle(LENGTH / 2) + 180) < 1e-4;
        for (double compression : new double[] {0, 50, 90}) {
            for (double width : new double[] {0, 50, 200}) {
                assert heightScale(1F, compression) > 0;
                assert widthScale(1F, width) >= 1;
                assert heightScale(0F, compression) == 1;
                assert widthScale(0F, width) == 1;
                for (double t = 0; t < 3 * MUSIC_LENGTH; t += 0.023) {
                    float q = musicSquash(t);
                    assert q >= -1e-5 && q <= 1.00001;
                    assert Math.abs(q - musicSquash(t + MUSIC_LENGTH)) < 1e-4;
                    double scale = heightScale(q, compression);
                    double offset = 1.62 * (scale - 1);
                    assert Math.abs(1.62 + offset - 1.62 * scale) < 1e-6;
                }
            }
        }
        assert Math.abs(musicSquash(MUSIC_LENGTH - 1e-5) - musicSquash(MUSIC_LENGTH + 1e-5)) < 1e-5;
        for (double speed : new double[] {0, 0.5, 1, 4}) {
            assert Math.abs(angle(LENGTH / 4 * speed) + 360 * phase(LENGTH / 4 * speed) / LENGTH) < 1e-4;
            double before = Math.toRadians(angle((MUSIC_LENGTH - 1e-6) * speed));
            double after = Math.toRadians(angle((MUSIC_LENGTH + 1e-6) * speed));
            assert Math.abs(Math.sin(before) - Math.sin(after)) < 1e-4;
        }
        assert musicFrame(0) == 0;
        assert musicFrame(1126) == 270;
        assert musicFrame(3378) == 810;
        assert musicFrame(Long.MAX_VALUE) >= 0 && musicFrame(Long.MAX_VALUE) < MUSIC_FRAMES;
        System.out.println("Animation, song-loop alignment, config extremes, camera phase, rotation and bounded PCM clock passed.");
    }
}
