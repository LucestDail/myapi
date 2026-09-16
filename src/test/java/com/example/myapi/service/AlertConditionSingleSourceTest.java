package com.example.myapi.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 구조 가드 — <b>알림 조건 판정은 {@link AlertConditions} 한 곳에만 있어야 한다.</b>
 *
 * <p>왜 필요한가: 원래 같은 {@code switch (conditionType)} 가 {@code AlertService} 와
 * {@code AlertIntegrationService} 에 두 벌로 복사돼 있었다. 그 상태에서 시간대 조건이나 새
 * 조건 타입을 한쪽에만 넣으면 <b>규칙은 저장됐는데 경로에 따라 발동이 다른</b> 상태가 되고,
 * 그건 화면에 아무 증상으로도 안 나타난다. 주석으로는 못 막으니 소스를 훑는다.</p>
 *
 * <p>판정 기준은 <b>이름이 아니라 모양</b>이다 — {@code case "above" -> "초과"} 처럼 사람이 읽을
 * 말을 고르는 표는 위반이 아니고, {@code case "above" -> value > threshold} 처럼 <b>비교해서
 * boolean 을 만드는</b> 것만 위반이다. 문구 표까지 금지하면 정당한 코드를 막는 가드가 된다.</p>
 */
class AlertConditionSingleSourceTest {

    /** 조건 판정 정본. 여기서만 비교가 일어나야 한다. */
    private static final String SINGLE_SOURCE = "AlertConditions.java";

    /** 소스가 이보다 적게 잡히면 훑기가 깨진 것으로 본다(경로를 옮기면 조용히 0건이 된다). */
    private static final int MIN_SCANNED_FILES = 50;

    private static final Pattern CONDITION_ARM =
            Pattern.compile("case\\s+\"(above|below|equals|at_least|at_most)\"\\s*->(.*)");

    /** 비교해서 boolean 을 만드는 모양. */
    private static boolean yieldsComparison(String armBody) {
        return armBody.contains(">") || armBody.contains("<") || armBody.contains("Math.abs");
    }

    private record Violation(Path file, int line, String text) {
        @Override
        public String toString() {
            return file + ":" + line + "  " + text.trim();
        }
    }

    private record ScanResult(List<Violation> violations, int scannedFiles, int singleSourceArms) {
    }

    private ScanResult scan() {
        Path root = Paths.get("src", "main", "java");
        if (!Files.isDirectory(root)) {
            // 검사 못 한 것은 통과가 아니다.
            fail("소스 루트를 찾지 못했다: " + root.toAbsolutePath());
        }
        List<Violation> violations = new ArrayList<>();
        int[] scanned = {0};
        int[] singleSourceArms = {0};

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                scanned[0]++;
                List<String> lines;
                try {
                    lines = Files.readAllLines(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                boolean isSingleSource = p.getFileName().toString().equals(SINGLE_SOURCE);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = CONDITION_ARM.matcher(lines.get(i));
                    if (!m.find() || !yieldsComparison(m.group(2))) {
                        continue;
                    }
                    if (isSingleSource) {
                        singleSourceArms[0]++;
                    } else {
                        violations.add(new Violation(p, i + 1, lines.get(i)));
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ScanResult(violations, scanned[0], singleSourceArms[0]);
    }

    @Test
    void 조건_판정은_AlertConditions_밖에_복제되어_있지_않다() {
        ScanResult result = scan();

        assertTrue(result.scannedFiles() >= MIN_SCANNED_FILES,
                "훑은 파일이 " + result.scannedFiles() + "개뿐 — 경로가 바뀌어 검사가 비었을 수 있다");

        assertTrue(result.violations().isEmpty(),
                "조건 판정 switch 가 " + SINGLE_SOURCE + " 밖에도 있다. 판정은 AlertConditions 로 모을 것:\n"
                        + String.join("\n", result.violations().stream().map(Object::toString).toList()));
    }

    /**
     * ③ 검사 대상이 실재하는가 — 탐지기 생존 확인.
     *
     * <p>위 테스트는 "위반 0건" 이면 통과한다. 그런데 탐지 정규식이 깨져도 위반은 0건이다.
     * 정본 파일에서 <b>실제로 잡히는</b> 것을 함께 확인해야 "잡을 줄 아는 자" 가 된다.</p>
     */
    @Test
    void 탐지기가_정본의_조건_분기를_실제로_찾아낸다() {
        ScanResult result = scan();
        assertTrue(result.singleSourceArms() >= 5,
                "AlertConditions 에서 조건 분기를 " + result.singleSourceArms()
                        + "개만 찾았다 — 탐지 규칙이 깨졌거나 판정 정본이 옮겨갔다");
    }

    /** ① 진짜 위반을 잡는가 — 실제 코드를 건드리지 않고 같은 판별식에 위반을 먹여 본다. */
    @Test
    void 복제된_판정을_먹이면_위반으로_잡는다() {
        List<String> mutations = List.of(
                "            case \"above\" -> currentValue > rule.getThreshold();",
                "        case \"below\" -> value < rule.getThreshold();",
                "  case \"equals\" -> Math.abs(value - rule.getThreshold()) < 0.001;",
                "case \"at_least\"    ->   v >= t;");
        for (String line : mutations) {
            Matcher m = CONDITION_ARM.matcher(line);
            assertTrue(m.find() && yieldsComparison(m.group(2)),
                    "위반을 못 잡았다: " + line);
        }
    }

    /** ② 정당한 코드를 막지 않는가 — 문구 표는 위반이 아니다. */
    @Test
    void 사람이_읽을_문구_표는_위반이_아니다() {
        List<String> legitimate = List.of(
                "            case \"above\" -> \"초과\";",
                "            case \"below\" -> \"미만\";",
                "            case \"equals\" -> \"도달\";",
                "            case \"at_least\" -> \"이상\";",
                "            case \"at_most\" -> \"이하\";");
        for (String line : legitimate) {
            Matcher m = CONDITION_ARM.matcher(line);
            boolean flagged = m.find() && yieldsComparison(m.group(2));
            assertFalse(flagged, "문구 표를 위반으로 잘못 잡았다: " + line);
        }
    }

    /** 오탐 테스트가 실제 코드를 대상으로도 성립하는지 — 문구 표는 지금 코드에 실재한다. */
    @Test
    void 지금_코드에_문구_표가_실제로_있다() throws IOException {
        Path messages = Paths.get("src", "main", "java", "com", "example", "myapi",
                "service", "AlertIntegrationService.java");
        assertTrue(Files.exists(messages), "문구 표를 가진 파일이 없다: " + messages);
        long arms = Files.readAllLines(messages).stream()
                .filter(l -> CONDITION_ARM.matcher(l).find())
                .count();
        assertEquals(5, arms,
                "AlertIntegrationService 의 조건 문구 표가 5개가 아니다 — 오탐 테스트가 허공을 재고 있을 수 있다");
    }
}
