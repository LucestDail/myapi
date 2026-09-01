package com.example.myapi.service.impl;

import com.example.myapi.entity.News;
import com.example.myapi.entity.NewsCompany;
import com.example.myapi.repository.news.NewsCompanyRepository;
import com.example.myapi.repository.news.NewsRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * NewsServiceImpl 단위 테스트. 리포지토리를 목으로 주입해 DB/네트워크 없이
 * 회사코드→이름 매핑, 예외시 폴백, 빈캐시 플레이스홀더, 갱신 캐시유지 를 검증한다.
 */
class NewsServiceImplTest {

    private NewsRepository newsRepository;
    private NewsCompanyRepository newsCompanyRepository;
    private NewsServiceImpl service;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        newsCompanyRepository = mock(NewsCompanyRepository.class);
        service = new NewsServiceImpl();
        ReflectionTestUtils.setField(service, "newsRepository", newsRepository);
        ReflectionTestUtils.setField(service, "newsCompanyRepository", newsCompanyRepository);
        // 초기화 완료 상태로 두어 getAllNews 가 갱신을 트리거하지 않게 한다
        ReflectionTestUtils.setField(service, "isInitialized", true);
        ReflectionTestUtils.setField(service, "lastUpdateTime", System.currentTimeMillis());
    }

    private News news(String company, String title, String content, String link, LocalDateTime dt) {
        News n = new News();
        n.setNewsCompany(company);
        n.setNewsTitle(title);
        n.setNewsContents(content);
        n.setEtc1(link);
        n.setNewsCreateDT(dt);
        n.setNewsFrom("기자");
        return n;
    }

    private void seedCache(List<News> list) {
        ReflectionTestUtils.setField(service, "cachedNews", new CopyOnWriteArrayList<>(list));
    }

    @Test
    void getNewsByCompany_정상조회() {
        List<News> list = List.of(news("001", "t", "c", "l", LocalDateTime.now()));
        when(newsRepository.findTop100ByNewsCompanyOrderByNewsCreateDTDesc("001")).thenReturn(list);
        assertEquals(1, service.getNewsByCompany("001").size());
    }

    @Test
    void getNewsByCompany_예외시_빈리스트_폴백() {
        when(newsRepository.findTop100ByNewsCompanyOrderByNewsCreateDTDesc("x"))
                .thenThrow(new RuntimeException("db down"));
        assertTrue(service.getNewsByCompany("x").isEmpty());
    }

    @Test
    void getCachedNews_회사코드를_이름으로_매핑한다() {
        seedCache(List.of(news("001", "제목", "내용", "http://n/1", LocalDateTime.of(2024, 1, 2, 3, 4, 5))));
        NewsCompany c = new NewsCompany();
        c.setNewsCompanyCode("001");
        c.setNewsCompanyName("연합뉴스");
        when(newsCompanyRepository.findByNewsCompanyCode("001")).thenReturn(Optional.of(c));

        JsonArray arr = service.getCachedNews();
        assertEquals(1, arr.size());
        JsonObject o = arr.get(0).getAsJsonObject();
        assertEquals("연합뉴스", o.get("company").getAsString());
        assertEquals("001", o.get("companyCode").getAsString());
        assertEquals("제목", o.get("title").getAsString());
        assertEquals("http://n/1", o.get("link").getAsString());
        assertEquals("2024-01-02 03:04:05", o.get("createDT").getAsString());
    }

    @Test
    void getCachedNews_회사이름_없으면_코드를_그대로_반환() {
        seedCache(List.of(news("999", "t", "c", "l", LocalDateTime.now())));
        when(newsCompanyRepository.findByNewsCompanyCode("999")).thenReturn(Optional.empty());
        JsonArray arr = service.getCachedNews();
        assertEquals("999", arr.get(0).getAsJsonObject().get("company").getAsString());
    }

    @Test
    void getCachedNews_빈캐시면_플레이스홀더_반환() {
        seedCache(List.of());
        JsonArray arr = service.getCachedNews();
        assertEquals(1, arr.size());
        assertEquals("데이터를 불러오는 중입니다...",
                arr.get(0).getAsJsonObject().get("title").getAsString());
    }

    @Test
    void updateNewsData_빈결과면_기존캐시를_유지한다() {
        List<News> existing = List.of(news("001", "old", "c", "l", LocalDateTime.now()));
        seedCache(existing);
        when(newsRepository.findTop100OrderByNewsCreateDTDesc()).thenReturn(List.of());
        service.updateNewsData();
        assertEquals(1, service.getAllNews().size()); // 기존 캐시 유지
    }

    @Test
    void updateNewsData_결과있으면_캐시갱신() {
        seedCache(List.of());
        when(newsRepository.findTop100OrderByNewsCreateDTDesc())
                .thenReturn(List.of(news("001", "new", "c", "l", LocalDateTime.now())));
        service.updateNewsData();
        assertEquals(1, service.getAllNews().size());
    }
}
