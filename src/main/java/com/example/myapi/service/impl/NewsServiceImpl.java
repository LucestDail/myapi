package com.example.myapi.service.impl;

import com.example.myapi.entity.News;
import com.example.myapi.entity.NewsCompany;
import com.example.myapi.repository.news.NewsRepository;
import com.example.myapi.repository.news.NewsCompanyRepository;
import com.example.myapi.service.NewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Slf4j
@Service
public class NewsServiceImpl implements NewsService {

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private NewsCompanyRepository newsCompanyRepository;

    private List<News> cachedNews = new CopyOnWriteArrayList<>();
    private final Map<String, String> companyCodeToNameCache = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile boolean isInitialized = false;
    private volatile long lastUpdateTime = 0;
    private static final long CACHE_DURATION_MS = 3600000; // 1시간

    @PostConstruct
    public void init() {
        cachedNews = new CopyOnWriteArrayList<>();
        executorService.submit(this::loadInitialNewsData);
    }

    private void loadInitialNewsData() {
        int retryCount = 0;
        final int maxRetries = 5;
        final long retryDelayMs = 10000; // 10초

        while (retryCount < maxRetries && !isInitialized) {
            try {
                log.info("Loading initial news data (attempt {}/{})", retryCount + 1, maxRetries);
                List<News> newsList = newsRepository.findTop100OrderByNewsCreateDTDesc();
                
                if (newsList != null && !newsList.isEmpty()) {
                    cachedNews = newsList;
                    lastUpdateTime = System.currentTimeMillis();
                    isInitialized = true;
                    log.info("Successfully loaded {} news items", newsList.size());
                } else {
                    log.warn("No news data found, keeping empty cache");
                    cachedNews = new CopyOnWriteArrayList<>();
                    isInitialized = true;
                }
            } catch (Exception e) {
                retryCount++;
                log.error("Error loading initial news data (attempt {}/{}): {}", retryCount, maxRetries, e.getMessage());
                
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Failed to load initial news data after {} attempts, keeping empty cache", maxRetries);
                    cachedNews = new CopyOnWriteArrayList<>();
                    isInitialized = true;
                }
            }
        }
    }

    @Override
    public List<News> getAllNews() {
        // 캐시가 만료되었거나 초기화되지 않은 경우 갱신
        long currentTime = System.currentTimeMillis();
        if (!isInitialized || (currentTime - lastUpdateTime >= CACHE_DURATION_MS)) {
            updateNewsData();
        }
        return cachedNews;
    }

    @Override
    public List<News> getNewsByCompany(String company) {
        try {
            return newsRepository.findTop100ByNewsCompanyOrderByNewsCreateDTDesc(company);
        } catch (Exception e) {
            log.error("Error getting news for company: {}", company, e);
            return List.of();
        }
    }

    @Override
    @Scheduled(fixedRate = 3600000) // 1시간마다 자동 갱신
    public void updateNewsData() {
        try {
            List<News> newsList = newsRepository.findTop100OrderByNewsCreateDTDesc();
            
            if (newsList != null && !newsList.isEmpty()) {
                cachedNews = newsList;
                lastUpdateTime = System.currentTimeMillis();
                log.debug("Successfully updated news cache with {} items", newsList.size());
            } else {
                log.warn("No news data found during update, keeping existing cache");
            }
        } catch (Exception e) {
            log.error("Error updating news data: {}", e.getMessage());
        }
    }

    @Override
    public JsonArray getCachedNews() {
        JsonArray jsonArray = new JsonArray();
        
        try {
            // 캐시 확인 및 필요시 갱신
            getAllNews();
            
            if (cachedNews != null && !cachedNews.isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                for (News news : cachedNews) {
                    JsonObject newsObject = new JsonObject();
                    
                    // 날짜를 한국 시간대로 변환하여 포맷팅
                    String formattedDate = "";
                    if (news.getNewsCreateDT() != null) {
                        LocalDateTime localDateTime = news.getNewsCreateDT();
                        // MySQL에서 가져온 LocalDateTime을 한국 시간대로 간주하고 포맷팅
                        formattedDate = localDateTime.format(formatter);
                    }
                    newsObject.addProperty("createDT", formattedDate);
                    
                    // 회사 코드를 회사 이름으로 변환
                    String companyCode = news.getNewsCompany() != null ? news.getNewsCompany() : "";
                    String companyName = getCompanyName(companyCode);
                    newsObject.addProperty("company", companyName);
                    newsObject.addProperty("companyCode", companyCode);
                    
                    newsObject.addProperty("title", news.getNewsTitle() != null ? news.getNewsTitle() : "");
                    newsObject.addProperty("content", news.getNewsContents() != null ? news.getNewsContents() : "");
                    // ETC1에 실제 뉴스 링크 URL이 저장되어 있음
                    newsObject.addProperty("link", news.getEtc1() != null && !news.getEtc1().trim().isEmpty() ? news.getEtc1() : "");
                    newsObject.addProperty("reporter", news.getNewsFrom() != null ? news.getNewsFrom() : "");
                    jsonArray.add(newsObject);
                }
            } else {
                JsonObject emptyNewsObject = new JsonObject();
                emptyNewsObject.addProperty("createDT", "");
                emptyNewsObject.addProperty("company", "");
                emptyNewsObject.addProperty("title", "데이터를 불러오는 중입니다...");
                emptyNewsObject.addProperty("content", "뉴스 데이터가 준비되지 않았습니다.");
                jsonArray.add(emptyNewsObject);
            }
        } catch (Exception e) {
            log.error("Error creating JSON from cached news: {}", e.getMessage());
            JsonObject errorNewsObject = new JsonObject();
            errorNewsObject.addProperty("createDT", "");
            errorNewsObject.addProperty("company", "");
            errorNewsObject.addProperty("title", "데이터 로드 중 오류가 발생했습니다");
            errorNewsObject.addProperty("content", "잠시 후 다시 시도해주세요.");
            jsonArray.add(errorNewsObject);
        }
        
        return jsonArray;
    }

    /**
     * 회사 코드로 회사 이름 조회 (캐싱 적용)
     */
    private String getCompanyName(String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return "";
        }

        // 캐시에서 먼저 조회
        String cachedName = companyCodeToNameCache.get(companyCode);
        if (cachedName != null) {
            return cachedName;
        }

        // 캐시에 없으면 DB에서 조회
        try {
            NewsCompany company = newsCompanyRepository.findByNewsCompanyCode(companyCode).orElse(null);
            if (company != null && company.getNewsCompanyName() != null) {
                String companyName = company.getNewsCompanyName();
                // 캐시에 저장
                companyCodeToNameCache.put(companyCode, companyName);
                return companyName;
            }
        } catch (Exception e) {
            log.warn("Failed to find company name for code: {}", companyCode, e);
        }

        // 회사 이름을 찾을 수 없으면 코드 반환
        return companyCode;
    }
}
