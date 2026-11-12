package org.gophie.ui;

import javax.swing.SwingUtilities;

import org.gophie.net.GopherItem.GopherItemType;
import org.gophie.net.GopherPage;
import org.gophie.net.GopherUrl;
import org.gophie.net.event.GopherClientEventListener;
import org.gophie.net.event.GopherError;

/** Delivers only the current navigation request's events, on Swing's UI thread. */
final class PageLoadDispatcher {
    // Requests and invalidations are made on the UI thread; workers only enqueue events.
    private long generation;

    void invalidate() {
        generation++;
    }

    GopherClientEventListener beginRequest(GopherClientEventListener listener) {
        final long request = ++generation;
        return new GopherClientEventListener() {
            public void progress(GopherUrl url, long count) {
                dispatch(request, () -> listener.progress(url, count));
            }

            public void pageLoaded(GopherPage page) {
                dispatch(request, () -> listener.pageLoaded(page));
            }

            public void pageLoadFailed(GopherError error, GopherUrl url) {
                dispatch(request, () -> listener.pageLoadFailed(error, url));
            }

            public void pageLoadItemMismatch(GopherItemType requested, GopherItemType detected,
                    GopherUrl url) {
                dispatch(request, () -> listener.pageLoadItemMismatch(requested, detected, url));
            }
        };
    }

    private void dispatch(long request, Runnable callback) {
        SwingUtilities.invokeLater(() -> {
            // Check at delivery time, since navigation may change while this event is queued.
            if(request == generation) callback.run();
        });
    }
}
