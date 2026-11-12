package org.gophie.ui;

import java.awt.Point;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.gophie.net.GopherItem.GopherItemType;
import org.gophie.net.GopherPage;
import org.gophie.net.GopherUrl;

/** Exercises real Swing HTML layout and deferred history position restoration. */
public class PageViewHistoryTest {
    private static PageView view;

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("gophie-page-history-test-");
        System.setProperty("user.home", home.toString());
        try {
            SwingUtilities.invokeAndWait(() -> {
                view = new PageView(null, "#ffffff", "#000000");
                view.setSize(600, 300);
            });
            GopherPage menu = page("menu", true);
            GopherPage secondMenu = page("second-menu", true);
            GopherPage text = page("text", false);
            show(menu);
            position(0, "new menu starts at the top");
            scroll(700);
            show(text);
            position(0, "new text starts at the top");
            scroll(500);
            show(menu);
            position(700, "Back from text restores the menu");
            show(text);
            position(500, "Forward restores the text");
            show(menu);
            scroll(900);
            show(secondMenu);
            position(0, "another new menu starts at the top");
            scroll(400);
            show(menu);
            position(900, "Back restores the latest menu position");
            show(secondMenu);
            position(400, "Forward restores another menu");

            // A new visit to the same URL has its own history entry.
            show(page("menu", true));
            position(0, "separate visits to one URL do not share positions");
            show(menu);
            position(900, "the earlier visit keeps its position");

            // Rapid history moves must not apply an old page's queued restoration
            // or overwrite its saved position with an intermediate layout position.
            SwingUtilities.invokeAndWait(() -> {
                render(text);
                render(secondMenu);
                render(menu);
            });
            flush();
            position(900, "rapid navigation restores only the final page");
            show(text);
            position(500, "rapid navigation preserves the text position");
            show(secondMenu);
            position(400, "rapid navigation preserves the menu position");
            System.out.println("All page history position tests passed.");
        } finally {
            Files.deleteIfExists(home.resolve("Gophie"));
            Files.deleteIfExists(home);
        }
    }

    private static GopherPage page(String selector, boolean menu) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            source.append(menu ? "1Link " : "Text line ").append(i);
            if (menu) source.append("\t/child\texample.org\t70");
            source.append("\r\n");
        }
        source.append(".\r\n");
        return new GopherPage(source.toString().getBytes(StandardCharsets.UTF_8),
            menu ? GopherItemType.GOPHERMENU : GopherItemType.TEXTFILE,
            new GopherUrl("gopher://example.org/" + selector));
    }

    private static void show(GopherPage page) throws Exception {
        SwingUtilities.invokeAndWait(() -> render(page));
        flush();
    }

    private static void render(GopherPage page) {
        if (page.getContentType() == GopherItemType.GOPHERMENU) {
            view.showGopherPage(page);
        } else {
            view.showGopherContent(page);
        }
        // Lay out the components without displaying a native window.
        view.doLayout();
        view.getViewport().doLayout();
        view.getRowHeader().doLayout();
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static void scroll(int y) throws Exception {
        SwingUtilities.invokeAndWait(() -> view.getViewport().setViewPosition(new Point(0, y)));
        position(y, "test page can scroll");
    }

    private static void position(int expected, String message) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int actual = view.getViewport().getViewPosition().y;
            if (actual != expected) {
                throw new AssertionError(message + ": expected " + expected + ", got " + actual);
            }
            int header = view.getRowHeader().getViewPosition().y;
            if (header != actual) {
                throw new AssertionError("line numbers stay aligned: " + header + " != " + actual);
            }
        });
    }
}
