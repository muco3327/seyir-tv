package tv.seyir.app;

import android.net.Uri;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Refreshes root playlists at their original URL without tearing down the player/buffer. */
@androidx.media3.common.util.UnstableApi
final class RefreshingPlaylistDataSource implements DataSource {
    private final DataSource upstream;
    private final PlaylistRoute route;

    RefreshingPlaylistDataSource(DataSource upstream, PlaylistRoute route) {
        this.upstream = upstream;
        this.route = route;
    }

    @Override public long open(DataSpec spec) throws IOException {
        String requested = spec.uri.toString();
        String target = route.resolve(requested);
        long length = upstream.open(target.equals(requested) ? spec : spec.withUri(Uri.parse(target)));
        Uri effective = upstream.getUri();
        route.opened(requested, effective == null ? null : effective.toString());
        return length;
    }

    // Expose the NEW response URL so relative HLS segments use the current session.
    @Override public Uri getUri() { return upstream.getUri(); }
    @Override public Map<String, List<String>> getResponseHeaders() { return upstream.getResponseHeaders(); }
    @Override public int read(byte[] buffer, int offset, int length) throws IOException { return upstream.read(buffer, offset, length); }
    @Override public void close() throws IOException { upstream.close(); }
    @Override public void addTransferListener(TransferListener listener) { upstream.addTransferListener(listener); }
}
