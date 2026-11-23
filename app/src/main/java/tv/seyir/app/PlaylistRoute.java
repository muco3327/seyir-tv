package tv.seyir.app;

import java.util.HashSet;
import java.util.Set;

/** Dynamic playlist route allowing warm channel transitions without rebuilding DataSource. */
final class PlaylistRoute {
    private volatile String entry;
    private final Set<String> roots = new HashSet<>();

    PlaylistRoute(String entry) {
        setEntry(entry);
    }

    synchronized void setEntry(String newEntry) {
        this.entry = newEntry;
        roots.clear();
        if (newEntry != null && !newEntry.isEmpty()) {
            roots.add(newEntry);
        }
    }

    synchronized String getEntry() {
        return entry;
    }

    synchronized String resolve(String requested) {
        return (entry != null && roots.contains(requested)) ? entry : requested;
    }

    synchronized void opened(String requested, String effective) {
        if (roots.contains(requested) && effective != null) roots.add(effective);
    }
}
