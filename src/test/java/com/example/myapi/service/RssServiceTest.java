package com.example.myapi.service;

import com.example.myapi.dto.rss.RssFeedResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RssService XML 파싱/캐싱 검증. 외부망 대신 127.0.0.1 인프로세스 HTTP 서버로
 * 실제 fetchAndParse 경로(RSS2.0/Atom/HTML제거/절삭/비200/캐시)를 결정적으로 검증한다.
 */
class RssServiceTest {

    private static HttpServer server;
    private static String base;
    private final RssService service = new RssService();

    private static final String RSS_2_0 = "<?xml version=\"1.0\"?>"
            + "<rss version=\"2.0\"><channel><title>Feed</title>"
            + "<item><title>Item1</title><link>http://ex.com/1</link>"
            + "<description>&lt;b&gt;Hello&lt;/b&gt; world</description>"
            + "<pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate></item>"
            + "<item><title>Item2</title><link>http://ex.com/2</link>"
            + "<description>plain</description></item>"
            + "</channel></rss>";

    private static final String LONG_DESC = "x".repeat(250);
    private static final String RSS_LONG = "<?xml version=\"1.0\"?>"
            + "<rss version=\"2.0\"><channel>"
            + "<item><title>L</title><link>http://ex.com/l</link>"
            + "<description>" + LONG_DESC + "</description></item>"
            + "</channel></rss>";

    private static final String ATOM = "<?xml version=\"1.0\"?>"
            + "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>AtomFeed</title>"
            + "<entry><title>Atom1</title><link href=\"http://a.com/1\"/>"
            + "<summary>Summary text</summary><updated>2024-01-01T00:00:00Z</updated></entry>"
            + "</feed>";

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        register("/rss", 200, RSS_2_0);
        register("/rsslong", 200, RSS_LONG);
        register("/atom", 200, ATOM);
        register("/fail", 500, "server error");
        server.setExecutor(null);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void register(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void RSS2_0_아이템을_파싱하고_HTML을_제거한다() {
        RssFeedResponse r = service.getCustomFeed(base + "/rss");
        assertEquals(2, r.itemCount());
        assertFalse(r.fromCache());
        assertEquals("Item1", r.items().get(0).title());
        assertEquals("http://ex.com/1", r.items().get(0).link());
        assertEquals("Hello world", r.items().get(0).description()); // <b></b> 제거됨
        assertEquals("custom", r.items().get(0).source());
    }

    @Test
    void 긴설명은_200자로_절삭되고_말줄임표를_붙인다() {
        RssFeedResponse r = service.getCustomFeed(base + "/rsslong");
        assertEquals(1, r.itemCount());
        String desc = r.items().get(0).description();
        assertEquals(203, desc.length()); // 200 + "..."
        assertTrue(desc.endsWith("..."));
    }

    @Test
    void Atom_엔트리를_링크href와_summary로_파싱한다() {
        RssFeedResponse r = service.getCustomFeed(base + "/atom");
        assertEquals(1, r.itemCount());
        assertEquals("Atom1", r.items().get(0).title());
        assertEquals("http://a.com/1", r.items().get(0).link());
        assertEquals("Summary text", r.items().get(0).description());
    }

    @Test
    void 비200응답은_빈피드를_반환한다() {
        RssFeedResponse r = service.getCustomFeed(base + "/fail");
        assertEquals(0, r.itemCount());
        assertTrue(r.items().isEmpty());
        assertFalse(r.fromCache());
    }

    @Test
    void 잘못된URL은_예외없이_빈피드를_반환한다() {
        RssFeedResponse r = service.getCustomFeed("not-a-valid-url-no-protocol");
        assertEquals(0, r.itemCount());
        assertTrue(r.items().isEmpty());
    }

    @Test
    void 두번째호출은_캐시에서_반환된다() {
        String url = base + "/rss";
        RssService svc = new RssService();
        RssFeedResponse first = svc.getCustomFeed(url);
        RssFeedResponse second = svc.getCustomFeed(url);
        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(first.itemCount(), second.itemCount());
    }

    @Test
    void 캐시상태를_보고한다() {
        RssService svc = new RssService();
        svc.getCustomFeed(base + "/rss");
        var status = svc.getCacheStatus();
        assertEquals(1, status.get("cachedFeeds"));
        assertEquals(10L, status.get("cacheTtlMinutes"));
    }
}
