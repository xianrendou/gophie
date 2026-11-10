package org.gophie.net;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.gophie.config.ConfigFile;
import org.gophie.config.ConfigurationManager;
import org.gophie.io.FileSignature;
import org.gophie.io.FileSignature.FileSignatureType;
import org.gophie.net.GopherItem.GopherItemType;
import org.gophie.net.event.GopherClientEventListener;
import org.gophie.net.event.GopherError;

/** Dependency-free regression tests. Run with build.ps1 -Test. */
public class GopherEncodingTest {
    private static final Charset GBK = Charset.forName("GBK");
    private static ConfigFile config;

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("gophie-encoding-test-");
        System.setProperty("user.home", home.toString());
        try {
            config = ConfigurationManager.getConfigFile();
            charsetSettings();
            fontSettings();
            requestEncoding();
            pageDecoding();
            fileSignatures();
            networkFetch();
            networkDownload();
            if (args.length > 0 && args[0].equals("--live")) {
                liveServer();
            }
            System.out.println("All Gopher encoding tests passed.");
        } finally {
            Files.deleteIfExists(home.resolve("Gophie"));
            Files.deleteIfExists(home);
        }
    }

    private static void charsetSettings() {
        equal(StandardCharsets.UTF_8, charset("example.org", 70), "UTF-8 default");
        config.setSetting("DEFAULT_CHARSET", "GBK", "Network");
        equal(GBK, charset("example.org", 70), "global GBK setting");
        config.setSetting("DEFAULT_CHARSET", "UTF-8", "Network");
        config.setSetting("example.org", "GBK", "Charsets");
        equal(GBK, charset("EXAMPLE.ORG", 70), "case-insensitive host lookup");
        config.setSetting("example.org:7072", "UTF-8", "Charsets");
        equal(StandardCharsets.UTF_8, charset("example.org", 7072), "port priority");
        equal(StandardCharsets.UTF_8, charset("other.org", 70), "unrelated host");
        config.setSetting("invalid.org", "not-a-charset", "Charsets");
        equal(StandardCharsets.UTF_8, charset("invalid.org", 70), "invalid charset fallback");
    }

    private static Charset charset(String host, int port) {
        return ConfigurationManager.getNetworkCharset(host, port);
    }

    private static void fontSettings() throws Exception {
        Font reference = Font.createFont(Font.TRUETYPE_FONT, new File("res/OpenSans-Regular.ttf"));
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(reference);
        config.setSetting("PAGE_FONT", reference.getPSName(), "Appearance");
        Font resolved = ConfigurationManager.getConsoleFont(17f);
        equal(reference.getPSName(), resolved.getPSName(), "PostScript font name");
        equal(17, resolved.getSize(), "configured font size");
    }

    private static void requestEncoding() {
        GopherUrl url = new GopherUrl("gopher://192.168.1.28:7072/1/wiki.pyg%7Chome/zh");
        bytes("/wiki.pyg|home/zh\r\n".getBytes(GBK), url.getRequestBytes(GBK), "pasted URL");
        bytes("/搜索\t中文+百科\r\n".getBytes(GBK), new GopherUrl(
            "localhost/搜索\t中文+百科").getRequestBytes(GBK), "GBK selector and query");
        bytes("/搜索\t中文+百科\r\n".getBytes(StandardCharsets.UTF_8), new GopherUrl(
            "localhost/搜索\t中文+百科").getRequestBytes(StandardCharsets.UTF_8), "UTF-8 request");
        bytes("/中文\r\n".getBytes(GBK), new GopherUrl(
            "localhost/%D6%D0%CE%C4").getRequestBytes(GBK), "escaped GBK octets");
        GopherItem literal = new GopherItem("0中文\t/literal%7C+中文\tlocalhost\t70");
        bytes("/literal%7C+中文\r\n".getBytes(GBK), new GopherUrl(
            literal.getUrlString()).getRequestBytes(GBK), "literal menu selector round trip");
        bytes("/100%\r\n".getBytes(GBK), new GopherUrl(
            "localhost/100%").getRequestBytes(GBK), "literal percent");
        bytes("/search\t100%20\r\n".getBytes(GBK), new GopherUrl(
            "localhost/search\t" + "100%20".replace("%", "%25")).getRequestBytes(GBK),
            "literal percent in user search text");
        for (String selector : new String[] {"/bad%0D%0Arequest", "/bad\r\nrequest"}) {
            try {
                new GopherUrl("localhost" + selector).getRequestBytes(GBK);
                throw new AssertionError("CR/LF selector accepted");
            } catch (IllegalArgumentException expected) {
                // A selector must remain a single Gopher request.
            }
        }
    }

    private static void pageDecoding() {
        String menu = "1中文维基百科\t/中文\texample.org\t70\r\n";
        GopherPage gbkPage = new GopherPage(menu.getBytes(GBK), GopherItemType.GOPHERMENU,
            new GopherUrl("example.org"));
        equal(menu, gbkPage.getSourceCode(), "GBK source");
        equal("中文维基百科", gbkPage.getItemList().get(0).getUserDisplayString(), "GBK title");
        equal("/中文", gbkPage.getItemList().get(0).getSelector(), "GBK selector");
        bytes(menu.getBytes(GBK), gbkPage.getByteArray(), "unmodified response bytes");
        GopherPage utf8Page = new GopherPage(menu.getBytes(StandardCharsets.UTF_8),
            GopherItemType.GOPHERMENU, new GopherUrl("other.org"));
        equal(menu, utf8Page.getSourceCode(), "UTF-8 source");
    }

    private static void fileSignatures() {
        for (Charset encoding : new Charset[] {GBK, StandardCharsets.UTF_8}) {
            equal(FileSignatureType.TEXT, signature("中文维基百科".getBytes(encoding)), "Chinese text");
        }
        equal(FileSignatureType.TEXT, signature(new byte[] {'1'}), "short response");
        equal(FileSignatureType.TEXT, signature(new byte[0]), "empty response");
        equal(FileSignatureType.UNKNOWN, signature(new byte[] {0, 1, 2}), "binary controls");
        equal(FileSignatureType.BINARY, signature(new byte[] {'P', 'K', 3, 4}), "ZIP signature");
        equal(FileSignatureType.IMAGE, signature(new byte[] {(byte)255, (byte)216, (byte)255}), "JPEG signature");
    }

    private static FileSignatureType signature(byte[] content) {
        return new FileSignature(content).getSignatureItemType();
    }

    private static void networkFetch() throws Exception {
        String menu = "1中文维基百科\t/中文\t127.0.0.1\t70\r\n";
        fetchFixture(menu.getBytes(GBK), GopherItemType.GOPHERMENU, GBK, "/wiki.pyg%7Chome/zh",
            "/wiki.pyg|home/zh\r\n", "中文维基百科\n");
        StringBuilder largeText = new StringBuilder();
        for (int i = 0; i < 30000; i++) { largeText.append("中文维基百科\r\n"); }
        fetchFixture(largeText.toString().getBytes(GBK), GopherItemType.TEXTFILE, GBK,
            "/搜索\t中文+百科", "/搜索\t中文+百科\r\n", largeText.toString());
        fetchFixture(largeText.toString().getBytes(StandardCharsets.UTF_8), GopherItemType.TEXTFILE,
            StandardCharsets.UTF_8, "/搜索\t中文", "/搜索\t中文\r\n", largeText.toString());
    }

    private static void fetchFixture(byte[] response, GopherItemType type, Charset encoding,
            String selector, String expectedRequest, String expectedText) throws Exception {
        try (Fixture server = new Fixture(response)) {
            config.setSetting("127.0.0.1:" + server.port(), encoding.name(), "Charsets");
            GopherPage page = new GopherClient().fetch(server.url(selector), type, null);
            bytes(expectedRequest.getBytes(encoding), server.request(), "wire request");
            equal(expectedText, page.getTextContent(), "network response");
            bytes(response, page.getByteArray(), "raw network bytes");
        }
    }

    private static void networkDownload() throws Exception {
        byte[] payload = new byte[] {0, 1, 2, (byte)255, (byte)128, 10};
        Path download = Files.createTempFile("gophie-download-test-", ".bin");
        try (Fixture server = new Fixture(payload)) {
            config.setSetting("127.0.0.1:" + server.port(), "GBK", "Charsets");
            final CountDownLatch finished = new CountDownLatch(1);
            final boolean[] succeeded = {false};
            new GopherClient().downloadAsync(server.url("/中文%7C文件"), download.toString(),
                new GopherClientEventListener() {
                    public void progress(GopherUrl url, long count) {}
                    public void pageLoaded(GopherPage page) { succeeded[0] = true; finished.countDown(); }
                    public void pageLoadFailed(GopherError error, GopherUrl url) { finished.countDown(); }
                    public void pageLoadItemMismatch(GopherItemType requested, GopherItemType detected,
                            GopherUrl url) { finished.countDown(); }
                });
            check(finished.await(10, TimeUnit.SECONDS) && succeeded[0], "download completed");
            bytes("/中文|文件\r\n".getBytes(GBK), server.request(), "download request encoding");
            bytes(payload, Files.readAllBytes(download), "binary download unchanged");
        } finally {
            Files.deleteIfExists(download);
        }
    }

    private static void liveServer() throws Exception {
        config.setSetting("192.168.1.28:7072", "GBK", "Charsets");
        GopherPage page = new GopherClient().fetch(
            "gopher://192.168.1.28:7072/1/wiki.pyg%7Chome/zh", GopherItemType.GOPHERMENU, null);
        check(page.getTextContent().contains("中文维基百科"), "live GBK homepage title");
        check(page.getItemList().size() >= 10, "live menu items");
        System.out.println("Live GBK menu passed: " + page.getItemList().size() + " items.");
    }

    private static class Fixture implements AutoCloseable {
        private final ServerSocket server;
        private final FutureTask<byte[]> exchange;

        Fixture(final byte[] response) throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server.setSoTimeout(10000);
            exchange = new FutureTask<byte[]>(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    InputStream input = socket.getInputStream();
                    ByteArrayOutputStream request = new ByteArrayOutputStream();
                    int value;
                    while ((value = input.read()) != -1) {
                        request.write(value);
                        if (value == '\n') { break; }
                    }
                    socket.getOutputStream().write(response);
                    return request.toByteArray();
                }
            });
            Thread thread = new Thread(exchange, "gopher-test-server");
            thread.setDaemon(true);
            thread.start();
        }

        int port() { return server.getLocalPort(); }
        String url(String selector) { return "gopher://127.0.0.1:" + port() + selector; }
        byte[] request() throws Exception { return exchange.get(10, TimeUnit.SECONDS); }
        public void close() throws Exception { server.close(); }
    }

    private static void check(boolean passed, String message) {
        if (!passed) { throw new AssertionError(message); }
    }

    private static void equal(Object expected, Object actual, String message) {
        check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }

    private static void bytes(byte[] expected, byte[] actual, String message) {
        check(Arrays.equals(expected, actual), message);
    }
}
