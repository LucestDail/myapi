package com.example.myapi.dto.rss;

/**
 * RSS Feed Item
 */
public record RssItem(
        String title,
        String link,
        String description,
        String pubDate,
        String source
) {}
