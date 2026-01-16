package com.example.myapi.dto.dashboard;

import java.util.List;

/**
 * 대시보드 설정 DTO
 */
public record DashboardConfig(
        String youtubeUrl,
        List<TickerConfig> tickers
) {
    public record TickerConfig(
            String symbol,
            String name
    ) {}

    public static DashboardConfig defaultConfig() {
        return new DashboardConfig(
                "https://www.youtube.com/watch?v=jfKfPfyJRdk", // lofi hip hop radio
                List.of(
                        new TickerConfig("SPY", "S&P500"),
                        new TickerConfig("QLD", "NAS2X"),
                        new TickerConfig("NVDA", "NVIDIA"),
                        new TickerConfig("TSLA", "TESLA"),
                        new TickerConfig("SNPS", "Synop"),
                        new TickerConfig("REKR", "Rekor"),
                        new TickerConfig("SMCX", "SMC"),
                        new TickerConfig("ETHU", "ETH2X"),
                        new TickerConfig("BITX", "BTC2X"),
                        new TickerConfig("GLDM", "Gold"),
                        new TickerConfig("XXRP", "XRP"),
                        new TickerConfig("SOLT", "SOL")
                )
        );
    }
}
