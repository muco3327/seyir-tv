package tv.seyir.app;

import java.util.HashSet;
import java.util.Set;

/** Only the root playlist's known redirects may be renewed via the channel entry URL. */
final class PlaylistRoute {
    private final String entry;
    private final Set<String> roots = new HashSet<>();

    PlaylistRoute(String entry) { this.entry = entry; roots.add(entry); }

    synchronized String resolve(String requested) {
        return roots.contains(requested) ? entry : requested;
    }

    synchronized void opened(String requested, String effective) {
        if (roots.contains(requested) && effective != null) roots.add(effective);
    }
}
