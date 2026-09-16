package com.example.myapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 알림 채널.
 *
 * <p>서버는 채널이 무엇이든 <b>항상 SSE 로 내려보낸다</b>. 채널 목록은 "이 알림을 받은 브라우저가
 * 무엇까지 해야 하는가" 를 말해 주는 힌트다 — {@code browser} 가 붙으면 화면은 토스트에 더해
 * OS 데스크톱 알림(Web Notifications)을 띄운다.</p>
 *
 * <p>기본값을 {@code sse} 하나로 둔 이유: 기존 규칙에는 이 칸이 없다(null). 기본을
 * {@code browser} 포함으로 두면 이미 쓰고 있던 규칙들이 어느 날 갑자기 OS 알림을 띄우기
 * 시작한다. 데스크톱 알림은 사용자가 권한까지 눌러야 하는 물건이므로 <b>규칙마다 명시적으로
 * 켜는 것</b>이 맞다.</p>
 *
 * <p>이메일 채널은 넣지 않았다 — 발송 경로(SMTP·SES)가 이 저장소에 없고, 없는 것을 있는 척
 * 목록에만 넣어 두면 켜 놓고 안 오는 상태가 된다.</p>
 */
public final class AlertChannels {

    private static final Logger log = LoggerFactory.getLogger(AlertChannels.class);

    public static final String SSE = "sse";
    public static final String BROWSER = "browser";

    private static final Set<String> KNOWN = Set.of(SSE, BROWSER);
    private static final List<String> DEFAULT = List.of(SSE);

    private AlertChannels() {
    }

    /**
     * "sse,browser" 를 정규화한다. SSE 는 전송 수단 자체라 언제나 포함된다.
     *
     * @return 최소 {@code ["sse"]}. 모르는 채널은 warn 후 버린다(조용히 무시하면 사용자는
     *         켰다고 믿는데 아무 일도 안 일어난다).
     */
    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        Set<String> parsed = new LinkedHashSet<>();
        parsed.add(SSE);
        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            String name = token.trim().toLowerCase(Locale.ROOT);
            if (!KNOWN.contains(name)) {
                log.warn("[alert] 모르는 알림 채널 '{}' 은 무시함 (가능한 값: {})", token, KNOWN);
                continue;
            }
            parsed.add(name);
        }
        return new ArrayList<>(parsed);
    }

    /** 저장용 정규화 문자열. 규칙을 다시 읽어도 같은 값이 나오도록 한 벌로 만든다. */
    public static String normalize(String raw) {
        return String.join(",", parse(raw));
    }
}
