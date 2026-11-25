package com.gladbox.driftscroll;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/** Android-independent planning, with bounded recent endpoint history for unlimited sessions. */
public final class ScrollPlan {
    private final Random random;
    private final Set<Long> usedPoints = new LinkedHashSet<>();
    public ScrollPlan(Random random) { this.random = random; }

    public static void validate(int min, int max, int minutes) {
        if (min < 1 || min > max || minutes < 0)
            throw new IllegalArgumentException("Use positive delays with minimum ≤ maximum; 0 minutes means unlimited.");
    }

    public long delay(int min, int max) {
        if (min < 1 || min > max) throw new IllegalArgumentException("Invalid delay range");
        return min * 1000L + (long) (random.nextDouble() * ((max - (long) min) * 1000L + 1));
    }

    public Swipe swipe(int width, int height, float density) {
        if (width < 100 || height < 200) throw new IllegalArgumentException("Display too small");
        for (int attempt = 0; attempt < 1000; attempt++) {
            float x = width * (0.38f + random.nextFloat() * 0.24f);
            float y = height * (0.55f + random.nextFloat() * 0.17f);
            float endX = x + density * (random.nextFloat() - 0.5f) * 4;
            float endY = y - Math.min(height * 0.08f, density * (24 + random.nextFloat() * 24));
            long start = key(x, y), end = key(endX, endY);
            if (usedPoints.contains(start) || usedPoints.contains(end)) continue;
            usedPoints.add(start); usedPoints.add(end);
            while (usedPoints.size() > 16384) {
                java.util.Iterator<Long> oldest = usedPoints.iterator();
                oldest.next(); oldest.remove();
            }
            float bend = density * (2 + random.nextFloat() * 3) * (random.nextBoolean() ? 1 : -1);
            return new Swipe(x, y, x + (endX - x) / 3 + bend, y + (endY - y) / 3,
                    x + (endX - x) * 2 / 3 + bend, y + (endY - y) * 2 / 3,
                    endX, endY, 650 + random.nextInt(301));
        }
        throw new IllegalStateException("Start a new session to refresh swipe positions.");
    }

    private long key(float x, float y) { return ((long) Math.round(x) << 32) | (Math.round(y) & 0xffffffffL); }
    public record Swipe(float x, float y, float cx1, float cy1, float cx2, float cy2, float endX, float endY, long duration) {}
}
