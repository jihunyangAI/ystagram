package com.yangstagram.story;

import com.yangstagram.common.error.ApiException;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * 스토리 스티커 (분석 문서 §8). 사진 위에 얹히는 상호작용 요소로, 이미지에 굽지 않고 따로 저장해
 * 보는 사람이 누를 수 있게 한다. 위치 x·y는 화면 대비 0~1 (가운데 기준).
 */
public final class Stickers {

    public static final int MAX_PER_STORY = 5;
    public static final Set<String> TYPES =
            Set.of("POLL", "QUIZ", "SLIDER", "QUESTION", "MENTION", "HASHTAG", "LOCATION", "LINK", "COUNTDOWN");
    /** 응답을 받는 스티커 */
    public static final Set<String> INTERACTIVE = Set.of("POLL", "QUIZ", "SLIDER", "QUESTION");

    private Stickers() {
    }

    /**
     * 스티커 하나. 종류에 따라 쓰는 칸이 다르다.
     * POLL: question, options(2) / QUIZ: question, options(2~4), answer / SLIDER: question, emoji /
     * QUESTION: question / MENTION: username / HASHTAG: tag / LOCATION: name / LINK: url, label /
     * COUNTDOWN: question(제목), endsAt(ISO 시각)
     */
    public record Sticker(String id, String type, Double x, Double y, String question, List<String> options,
                          Integer answer, String emoji, String username, String tag, String name, String url,
                          String label, String endsAt) {
    }

    /** 응답 집계. 질문 스티커의 답변 목록(answers)은 스토리 주인에게만 */
    public record Result(String stickerId, List<Integer> counts, String myValue, Double average, int total,
                         List<Answer> answers) {
    }

    public record Answer(String username, String value) {
    }

    public static List<Sticker> validate(List<Sticker> raw) {
        if (raw == null) return List.of();
        if (raw.size() > MAX_PER_STORY) {
            throw ApiException.badRequest("TOO_MANY_STICKERS", "스티커는 " + MAX_PER_STORY + "개까지 붙일 수 있습니다.");
        }
        int[] seq = {0};
        return raw.stream().map(s -> {
            String type = s.type() == null ? "" : s.type().toUpperCase();
            if (!TYPES.contains(type)) throw ApiException.badRequest("BAD_STICKER", "알 수 없는 스티커입니다.");
            double x = clamp(s.x()), y = clamp(s.y());
            String id = "s" + (seq[0]++);
            String q = trim(s.question(), 80);
            List<String> options = s.options() == null ? List.of()
                    : s.options().stream().map(o -> trim(o, 30)).filter(o -> o != null && !o.isEmpty()).limit(4).toList();
            switch (type) {
                case "POLL" -> {
                    if (options.size() != 2) throw ApiException.badRequest("BAD_STICKER", "설문은 선택지 2개가 필요합니다.");
                    return new Sticker(id, type, x, y, q, options, null, null, null, null, null, null, null, null);
                }
                case "QUIZ" -> {
                    if (options.size() < 2) throw ApiException.badRequest("BAD_STICKER", "퀴즈는 선택지가 2개 이상 필요합니다.");
                    int answer = s.answer() == null ? 0 : s.answer();
                    if (answer < 0 || answer >= options.size()) throw ApiException.badRequest("BAD_STICKER", "정답을 골라 주세요.");
                    return new Sticker(id, type, x, y, q, options, answer, null, null, null, null, null, null, null);
                }
                case "SLIDER" -> {
                    String emoji = trim(s.emoji(), 8);
                    return new Sticker(id, type, x, y, q, null, null, emoji == null ? "😍" : emoji, null, null, null, null, null, null);
                }
                case "QUESTION" -> {
                    return new Sticker(id, type, x, y, q == null ? "무엇이든 물어보세요" : q, null, null, null, null, null, null, null, null, null);
                }
                case "MENTION" -> {
                    String u = trim(s.username(), 30);
                    if (u == null) throw ApiException.badRequest("BAD_STICKER", "언급할 사용자 이름을 입력해 주세요.");
                    return new Sticker(id, type, x, y, null, null, null, null, u.toLowerCase().replace("@", ""), null, null, null, null, null);
                }
                case "HASHTAG" -> {
                    String t = trim(s.tag(), 50);
                    if (t == null) throw ApiException.badRequest("BAD_STICKER", "해시태그를 입력해 주세요.");
                    return new Sticker(id, type, x, y, null, null, null, null, null, t.replace("#", "").toLowerCase(), null, null, null, null);
                }
                case "LOCATION" -> {
                    String n = trim(s.name(), 100);
                    if (n == null) throw ApiException.badRequest("BAD_STICKER", "위치를 입력해 주세요.");
                    return new Sticker(id, type, x, y, null, null, null, null, null, null, n, null, null, null);
                }
                case "LINK" -> {
                    String url = trim(s.url(), 300);
                    if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                        throw ApiException.badRequest("BAD_STICKER", "http(s)로 시작하는 링크를 입력해 주세요.");
                    }
                    try {
                        URI.create(url);
                    } catch (IllegalArgumentException e) {
                        throw ApiException.badRequest("BAD_STICKER", "올바른 링크가 아닙니다.");
                    }
                    String label = trim(s.label(), 30);
                    return new Sticker(id, type, x, y, null, null, null, null, null, null, null, url, label, null);
                }
                default -> { // COUNTDOWN
                    if (s.endsAt() == null) throw ApiException.badRequest("BAD_STICKER", "카운트다운 날짜를 골라 주세요.");
                    try {
                        java.time.Instant.parse(s.endsAt());
                    } catch (Exception e) {
                        throw ApiException.badRequest("BAD_STICKER", "카운트다운 날짜 형식이 잘못되었습니다.");
                    }
                    return new Sticker(id, type, x, y, q == null ? "카운트다운" : q, null, null, null, null, null, null, null, null, s.endsAt());
                }
            }
        }).toList();
    }

    private static double clamp(Double v) {
        return v == null || Double.isNaN(v) ? 0.5 : Math.max(0.05, Math.min(0.95, v));
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
