package tv.seyir.app;

/** Finite recovery budget; briefly reaching READY must not create an infinite retry loop. */
final class LiveRecovery {
    private int attempts;
    private long playingSince = -1;

    void playing(boolean playing, long now) {
        // Some providers rotate their redirected session after ~20-40 seconds.
        // Sustained playback restores the budget; an immediate error/READY loop does not.
        if (playingSince >= 0 && now - playingSince >= 15000) attempts = 0;
        if (playing) { if (playingSince < 0) playingSince = now; }
        else playingSince = -1;
    }

    long nextDelay() {
        return attempts < 3 ? 1000L << attempts++ : -1;
    }

    void reset() { attempts = 0; playingSince = -1; }

    static boolean retryHttp(int status) {
        return status == 403 || status == 404 || status == 408 || status == 410
            || status == 429 || status >= 500 && status <= 599;
    }
}
