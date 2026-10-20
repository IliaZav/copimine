import me.copimine.endevent.domain.VisualRefreshDeadline;

public final class VisualRefreshDeadlineTest {
    public static void main(String[] args) {
        for (long offset : new long[]{0L, 50L, 149L, 150L, 200L, 249L}) {
            VisualRefreshDeadline deadline = new VisualRefreshDeadline(500L);
            for (int tick = 0; tick < 32; tick++) {
                boolean due = deadline.shouldRefresh(offset + tick * 250L);
                check(due == (tick % 2 == 0), "beam heartbeat must work at clock phase " + offset);
            }
            check(deadline.shouldRefresh(40_000L), "lagged callback must refresh once");
            check(!deadline.shouldRefresh(40_000L), "no catch-up packet burst");
            deadline.reset();
            check(deadline.shouldRefresh(offset), "new wave starts with an immediate refresh");
        }
        System.out.println("VisualRefreshDeadlineTest OK");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
