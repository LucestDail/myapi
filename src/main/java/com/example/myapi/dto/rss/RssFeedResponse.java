package com.example.myapi.dto.rss;

import java.time.Instant;
import java.util.List;

/**
 * RSS Feed Response with cache metadata
 */
public record RssFeedResponse(
        String feedUrl,
        String feedTitle,
        String source,
        int itemCount,
        List<RssItem> items,
        Instant fetchedAt,
        boolean fromCache
) {}
