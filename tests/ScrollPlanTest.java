import com.gladbox.driftscroll.ScrollPlan;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ScrollPlanTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int[] display : new int[][] {{1080, 2400, 3}, {2400, 1080, 3}, {480, 800, 1}}) {
            ScrollPlan plan = new ScrollPlan(new Random(42));
            Set<String> points = new HashSet<>();
            for (int i = 0; i < 7200; i++) {
                ScrollPlan.Swipe s = plan.swipe(display[0], display[1], display[2]);
                check(s.y() > s.endY(), "Swipe must move upward to read downward");
                check(s.y() - s.endY() <= Math.min(48 * display[2], display[1] * 0.08) + 0.01, "Swipe too long");
                check(s.endY() > display[1] * 0.4 && s.y() < display[1] * 0.73, "Outside reading area");
                check(s.cx1() > 0 && s.cx2() < display[0], "Curve out of bounds");
                double cross = (s.cx1() - s.x()) * (s.endY() - s.y()) - (s.cy1() - s.y()) * (s.endX() - s.x());
                check(Math.abs(cross) > 1, "Straight path");
                check(points.add(Math.round(s.x()) + ":" + Math.round(s.y())), "Repeated start");
                check(points.add(Math.round(s.endX()) + ":" + Math.round(s.endY())), "Repeated end");
                check(s.duration() >= 650 && s.duration() <= 950, "Bad duration");
                long delay = plan.delay(1, 60);
                check(delay >= 1000 && delay <= 60000, "Delay outside limit");
            }
        }
        ScrollPlan plan = new ScrollPlan(new Random(7));
        check(plan.delay(60, 60) == 60000, "Fixed upper limit");
        ScrollPlan.validate(61, 3600, 0);
        ScrollPlan.validate(1, Integer.MAX_VALUE, Integer.MAX_VALUE);
        check(plan.delay(Integer.MAX_VALUE, Integer.MAX_VALUE) == Integer.MAX_VALUE * 1000L, "Large delay overflow");
        for (int i = 0; i < 30000; i++) {
            long largeDelay = plan.delay(61, Integer.MAX_VALUE);
            check(largeDelay >= 61000 && largeDelay <= Integer.MAX_VALUE * 1000L, "Large random delay overflow");
            plan.swipe(480, 800, 1);
        }
        for (int[] bad : new int[][] {{0, 60, 10}, {20, 10, 10}, {1, 5, -1}}) {
            boolean rejected = false;
            try { ScrollPlan.validate(bad[0], bad[1], bad[2]); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid settings accepted");
        }
        System.out.println("PASS: 51,600 swipes; short curved paths, bounded recent history, unlimited-session validation, large delays without overflow, invalid inputs.");
    }
}
