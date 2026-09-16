package com.example.myapi.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertChannelsTest {

    @Test
    void 비어_있으면_SSE_하나가_기본이다() {
        // 기존 규칙에는 이 칸이 없다(null). 기본이 browser 를 포함하면 쓰던 규칙들이
        // 어느 날 갑자기 OS 알림을 띄우기 시작한다.
        assertEquals(List.of("sse"), AlertChannels.parse(null));
        assertEquals(List.of("sse"), AlertChannels.parse("   "));
    }

    @Test
    void 브라우저_채널을_켜면_SSE_와_함께_나온다() {
        assertEquals(List.of("sse", "browser"), AlertChannels.parse("browser"));
        assertEquals(List.of("sse", "browser"), AlertChannels.parse("sse,browser"));
    }

    @Test
    void 대소문자와_공백을_받아준다() {
        assertEquals(List.of("sse", "browser"), AlertChannels.parse(" SSE , Browser "));
    }

    @Test
    void 중복은_한_번만_남는다() {
        assertEquals(List.of("sse", "browser"), AlertChannels.parse("browser,browser,sse"));
    }

    @Test
    void 모르는_채널은_버리되_SSE_는_남긴다() {
        // 이메일 발송 경로가 이 저장소에 없다. 목록에만 받아 두면 "켰는데 안 온다" 가 된다.
        List<String> parsed = AlertChannels.parse("email,slack");
        assertEquals(List.of("sse"), parsed);
        assertTrue(parsed.contains("sse"));
    }

    @Test
    void 정규화는_저장해_다시_읽어도_같은_값이다() {
        String once = AlertChannels.normalize("Browser, email");
        assertEquals("sse,browser", once);
        assertEquals(once, AlertChannels.normalize(once), "다시 정규화해도 값이 바뀌면 안 된다");
    }
}
