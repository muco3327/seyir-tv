package tv.seyir.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveRecoveryTest {
    @Test public void stopsAfterThreeAttempts() {
        LiveRecovery policy = new LiveRecovery();
        assertEquals(1000, policy.nextDelay());
        assertEquals(2000, policy.nextDelay());
        assertEquals(4000, policy.nextDelay());
        assertEquals(-1, policy.nextDelay());
    }
    @Test public void briefPlaybackDoesNotResetBudget() {
        LiveRecovery policy = new LiveRecovery();
        for (int i = 0; i < 3; i++) {
            assertTrue(policy.nextDelay() > 0);
            policy.playing(true, i * 50000L);
            policy.playing(false, i * 50000L + 5000);
        }
        assertEquals(-1, policy.nextDelay());
    }
    @Test public void stablePlaybackRestoresBudget() {
        LiveRecovery policy = new LiveRecovery();
        policy.nextDelay(); policy.nextDelay(); policy.nextDelay();
        policy.playing(true, 0);
        policy.playing(false, 20000);
        assertEquals(1000, policy.nextDelay());
    }
    @Test public void permanentClientErrorsAreNotRetried() {
        assertFalse(LiveRecovery.retryHttp(400));
        assertFalse(LiveRecovery.retryHttp(401));
        assertTrue(LiveRecovery.retryHttp(404));
        assertTrue(LiveRecovery.retryHttp(503));
    }
}
