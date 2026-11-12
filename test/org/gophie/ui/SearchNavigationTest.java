package org.gophie.ui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.gophie.net.GopherItem.GopherItemType;
import org.gophie.net.GopherPage;
import org.gophie.net.GopherUrl;
import org.gophie.net.event.GopherClientEventListener;
import org.gophie.net.event.GopherError;

/** Reproduces accumulated search handlers and search replies arriving after a result opens. */
public class SearchNavigationTest {
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("gophie-search-test-");
        System.setProperty("user.home", home.toString());
        try {
            SwingUtilities.invokeAndWait(SearchNavigationTest::searchHandlers);
            delayedReplies();
            System.out.println("All search navigation tests passed.");
        } finally {
            Files.deleteIfExists(home.resolve("Gophie"));
            Files.deleteIfExists(home);
        }
    }

    private static void searchHandlers() {
        SearchInput input = new SearchInput();
        List<String> searches = new ArrayList<String>();
        input.performSearch("Previous search", text -> searches.add("old:" + text));
        input.performSearch("Search", text -> searches.add("current:" + text));
        input.searchText.setText("香烟");
        enter(input);
        equal("[current:香烟]", searches.toString(), "reopening Search submits only once");
        check(!input.isVisible(), "submission hides Search");
        enter(input);
        equal(1, searches.size(), "a repeated Enter cannot resubmit a hidden search");
        input.performSearch("Search again", text -> searches.add("next:" + text));
        enter(input);
        equal("[current:香烟, next:香烟]", searches.toString(), "repeated searches use the new handler");
        input.performSearch("Cancel", text -> searches.add("cancelled"));
        release(input, KeyEvent.VK_ESCAPE);
        enter(input);
        equal(2, searches.size(), "Escape cancels the pending search");
        input.performSearch("Empty", text -> searches.add("empty"));
        input.searchText.setText("");
        enter(input);
        equal(2, searches.size(), "empty searches do not submit");
    }

    private static void enter(SearchInput input) {
        input.searchText.postActionEvent();
        release(input, KeyEvent.VK_ENTER);
    }

    private static void release(SearchInput input, int key) {
        KeyEvent event = new KeyEvent(input.searchText, KeyEvent.KEY_RELEASED,
            0, 0, key, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener listener : input.searchText.getKeyListeners()) listener.keyReleased(event);
    }

    private static void delayedReplies() throws Exception {
        PageLoadDispatcher dispatcher = new PageLoadDispatcher();
        List<String> events = new ArrayList<String>();
        GopherClientEventListener receiver = new GopherClientEventListener() {
            private void record(String value) {
                check(SwingUtilities.isEventDispatchThread(), "callbacks run on Swing's UI thread");
                events.add(value);
            }
            public void progress(GopherUrl url, long count) { record("progress"); }
            public void pageLoaded(GopherPage page) { record(page.getTextContent()); }
            public void pageLoadFailed(GopherError error, GopherUrl url) { record("error"); }
            public void pageLoadItemMismatch(GopherItemType requested, GopherItemType detected,
                    GopherUrl url) { record("mismatch"); }
        };
        GopherClientEventListener[] requests = new GopherClientEventListener[2];
        SwingUtilities.invokeAndWait(() -> {
            requests[0] = dispatcher.beginRequest(receiver); // slow duplicate search
            requests[1] = dispatcher.beginRequest(receiver); // clicked result
        });
        GopherPage article = page("article");
        Thread worker = new Thread(() -> requests[1].pageLoaded(article));
        worker.start();
        worker.join();
        flush();
        equal("[article]", events.toString(), "clicked result opens");

        // The earlier search finishes after the article is already displayed.
        requests[0].pageLoaded(page("search results"));
        requests[0].progress(article.getUrl(), 100);
        requests[0].pageLoadFailed(GopherError.CONNECT_FAILED, article.getUrl());
        requests[0].pageLoadItemMismatch(GopherItemType.TEXTFILE,
            GopherItemType.BINARY_FILE, article.getUrl());
        flush();
        equal("[article]", events.toString(), "late search cannot replace article or change its UI");

        SwingUtilities.invokeAndWait(() -> {
            requests[1].pageLoaded(page("queued stale reply"));
            dispatcher.invalidate(); // Back, Forward, or Stop before the reply is delivered
        });
        flush();
        equal("[article]", events.toString(), "history navigation and Stop discard queued replies");

        SwingUtilities.invokeAndWait(() -> requests[1] = dispatcher.beginRequest(receiver));
        requests[1].progress(article.getUrl(), 200);
        requests[1].pageLoadFailed(GopherError.CONNECT_FAILED, article.getUrl());
        requests[1].pageLoadItemMismatch(GopherItemType.TEXTFILE,
            GopherItemType.BINARY_FILE, article.getUrl());
        requests[1].pageLoaded(page("new page"));
        flush();
        equal("[article, progress, error, mismatch, new page]", events.toString(),
            "the current request still delivers every event type");
    }

    private static GopherPage page(String text) {
        return new GopherPage(text.getBytes(StandardCharsets.UTF_8), GopherItemType.TEXTFILE,
            new GopherUrl("example.org/" + text));
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
