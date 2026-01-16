package com.example.myapi.service;

import com.example.myapi.dto.rss.RssFeedResponse;
import com.example.myapi.dto.rss.RssItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RSS Feed Service with 10-minute caching per URL
 */
@Service
public class RssService {

    private static final Logger log = LoggerFactory.getLogger(RssService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ITEMS = 100;
    private static final int TIMEOUT_MS = 10000;

    // Cache: URL -> (response, fetchedAt)
    private final Map<String, CachedFeed> cache = new ConcurrentHashMap<>();

    private record CachedFeed(RssFeedResponse response, Instant fetchedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(fetchedAt.plus(CACHE_TTL));
        }
    }

    // ==================== Public Feed URLs ====================

    public static final String YAHOO_MARKET = "https://finance.yahoo.com/news/rssindex";
    public static final String YAHOO_STOCK_FORMAT = "https://feeds.finance.yahoo.com/rss/2.0/headline?s=%s&region=US&lang=en-US";

    public static final String YONHAP_ALL = "https://www.yonhapnewstv.co.kr/browse/feed/";
    public static final String YONHAP_ECONOMY = "https://www.yonhapnewstv.co.kr/browse/feed/";
    public static final String YONHAP_POLITICS = "https://www.yonhapnewstv.co.kr/browse/feed/";
    public static final String YONHAP_IT = "https://www.yonhapnewstv.co.kr/browse/feed/";

    // ==================== Yahoo Finance ====================

    /**
     * Get Yahoo Finance Market News
     */
    public RssFeedResponse getYahooMarket() {
        return getFeed(YAHOO_MARKET, "Yahoo Finance Market", "yahoo");
    }

    /**
     * Get Yahoo Finance Stock News by Symbol
     */
    public RssFeedResponse getYahooStock(String symbol) {
        String url = String.format(YAHOO_STOCK_FORMAT, symbol.toUpperCase());
        return getFeed(url, "Yahoo Finance - " + symbol.toUpperCase(), "yahoo");
    }

    // ==================== Yonhap (Korean News) ====================

    /**
     * Get Yonhap News (All)
     */
    public RssFeedResponse getYonhapAll() {
        return getFeed(YONHAP_ALL, "Yonhap All", "yonhap");
    }

    /**
     * Get Yonhap Economy News
     */
    public RssFeedResponse getYonhapEconomy() {
        return getFeed(YONHAP_ECONOMY, "Yonhap Economy", "yonhap");
    }

    /**
     * Get Yonhap Politics News
     */
    public RssFeedResponse getYonhapPolitics() {
        return getFeed(YONHAP_POLITICS, "Yonhap Politics", "yonhap");
    }

    /**
     * Get Yonhap IT/Science News
     */
    public RssFeedResponse getYonhapIT() {
        return getFeed(YONHAP_IT, "Yonhap IT/Science", "yonhap");
    }

    // ==================== Custom ====================

    /**
     * Get custom RSS feed by URL
     */
    public RssFeedResponse getCustomFeed(String url) {
        return getFeed(url, "Custom Feed", "custom");
    }

    /**
     * Get cache status
     */
    public Map<String, Object> getCacheStatus() {
        return Map.of(
                "cachedFeeds", cache.size(),
                "cacheTtlMinutes", CACHE_TTL.toMinutes(),
                "cachedUrls", cache.keySet()
        );
    }

    // ==================== Core Logic ====================

    /**
     * Get feed with caching (10-minute TTL per URL)
     */
    private RssFeedResponse getFeed(String url, String title, String source) {
        CachedFeed cached = cache.get(url);

        // Return cache if valid
        if (cached != null && !cached.isExpired()) {
            log.debug("Cache hit for: {}", url);
            return new RssFeedResponse(
                    cached.response.feedUrl(),
                    cached.response.feedTitle(),
                    cached.response.source(),
                    cached.response.itemCount(),
                    cached.response.items(),
                    cached.response.fetchedAt(),
                    true  // fromCache = true
            );
        }

        // Fetch new data
        log.info("Fetching RSS: {}", url);
        try {
            List<RssItem> items = fetchAndParse(url, source);
            Instant now = Instant.now();

            RssFeedResponse response = new RssFeedResponse(
                    url,
                    title,
                    source,
                    items.size(),
                    items,
                    now,
                    false  // fromCache = false
            );

            // Update cache
            cache.put(url, new CachedFeed(response, now));
            return response;

        } catch (Exception e) {
            log.error("Failed to fetch RSS {}: {}", url, e.getMessage());

            // Return stale cache if available
            if (cached != null) {
                log.warn("Returning stale cache for: {}", url);
                return new RssFeedResponse(
                        cached.response.feedUrl(),
                        cached.response.feedTitle(),
                        cached.response.source(),
                        cached.response.itemCount(),
                        cached.response.items(),
                        cached.response.fetchedAt(),
                        true
                );
            }

            // Return empty response
            return new RssFeedResponse(url, title, source, 0, List.of(), Instant.now(), false);
        }
    }

    /**
     * Fetch and parse RSS XML
     */
    private List<RssItem> fetchAndParse(String urlStr, String source) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 MyAPI RSS Reader");

        List<RssItem> items = new ArrayList<>();

        try (InputStream is = conn.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            // Try RSS 2.0 format (<item>)
            NodeList itemNodes = doc.getElementsByTagName("item");
            if (itemNodes.getLength() == 0) {
                // Try Atom format (<entry>)
                itemNodes = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < Math.min(itemNodes.getLength(), MAX_ITEMS); i++) {
                Element item = (Element) itemNodes.item(i);

                String title = getElementText(item, "title");
                String link = getElementText(item, "link");
                if (link.isEmpty()) {
                    // Atom format
                    NodeList linkNodes = item.getElementsByTagName("link");
                    if (linkNodes.getLength() > 0) {
                        Element linkEl = (Element) linkNodes.item(0);
                        link = linkEl.getAttribute("href");
                    }
                }
                String description = getElementText(item, "description");
                if (description.isEmpty()) {
                    description = getElementText(item, "summary");
                }
                String pubDate = getElementText(item, "pubDate");
                if (pubDate.isEmpty()) {
                    pubDate = getElementText(item, "updated");
                }

                // Clean HTML from description
                description = description.replaceAll("<[^>]*>", "").trim();
                if (description.length() > 200) {
                    description = description.substring(0, 200) + "...";
                }

                items.add(new RssItem(title, link, description, pubDate, source));
            }
        }

        return items;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }
}
