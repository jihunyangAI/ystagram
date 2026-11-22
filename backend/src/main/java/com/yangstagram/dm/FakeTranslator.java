package com.yangstagram.dm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DM "번역 보기" (기획서 §4 🟡: 실제 번역 엔진 대신 미리 만든 문장·단어 사전).
 * 한국어 → 영어, 영어 → 한국어. 사전에 없는 말은 그대로 두고 "번역할 수 없는 부분"이 있다고 알린다.
 */
final class FakeTranslator {

    private FakeTranslator() {
    }

    record Result(String text, String from, String to, boolean complete) {
    }

    /** 긴 표현부터 바꾸도록 순서를 지킨다 */
    private static final Map<String, String> KO_EN = new LinkedHashMap<>();
    private static final Map<String, String> EN_KO = new LinkedHashMap<>();

    static {
        String[][] pairs = {
                {"무슨 일이에요", "what's up"}, {"안녕하세요", "hello"}, {"반가워요", "nice to meet you"},
                {"고마워요", "thank you"}, {"감사합니다", "thank you"}, {"별말씀을요", "you're welcome"},
                {"천만에요", "you're welcome"}, {"좋아요", "sounds good"}, {"알겠어요", "got it"},
                {"오 진짜요", "oh really"}, {"그러게요", "right"}, {"대박", "amazing"}, {"헐", "whoa"},
                {"나중에", "later"}, {"자세히", "in detail"}, {"얘기해 줘요", "tell me"}, {"얘기해요", "let's talk"},
                {"사진", "photo"}, {"예쁘다", "is pretty"}, {"분위기", "vibe"}, {"좋네요", "is nice"},
                {"이거 어디예요", "where is this"}, {"어디예요", "where is it"}, {"같이 가요", "let's go together"},
                {"다음에", "next time"}, {"저장해 놨어요", "I saved it"}, {"공유", "sharing"},
                {"전화 못 받았어요", "I missed your call"}, {"지금", "now"}, {"메시지로", "by message"},
                {"오늘", "today"}, {"내일", "tomorrow"}, {"어제", "yesterday"}, {"점심", "lunch"}, {"저녁", "dinner"},
                {"커피", "coffee"}, {"맛있다", "delicious"}, {"맛집", "great restaurant"}, {"여행", "trip"},
                {"주말", "weekend"}, {"뭐 해요", "what are you doing"}, {"보고 싶어요", "I miss you"},
                {"축하해요", "congratulations"}, {"생일", "birthday"}, {"괜찮아요", "it's okay"}, {"네", "yes"},
                {"아니요", "no"}, {"신기하다", "fascinating"}, {"완전", "totally"}, {"진짜", "really"},
                {"ㅋㅋ", "lol"}, {"ㅎㅎ", "haha"}, {"ㅠㅠ", "(sad)"},
        };
        for (String[] p : pairs) {
            KO_EN.put(p[0], p[1]);
            EN_KO.putIfAbsent(p[1], p[0]);
        }
    }

    static Result translate(String text) {
        boolean korean = text.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3 || c >= 0x3131 && c <= 0x318E);
        Map<String, String> dict = korean ? KO_EN : EN_KO;
        String out = korean ? text : text.toLowerCase();
        boolean any = false;
        for (Map.Entry<String, String> e : dict.entrySet()) {
            if (out.contains(e.getKey())) {
                out = out.replace(e.getKey(), e.getValue());
                any = true;
            }
        }
        boolean leftover = korean
                ? out.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3)
                : out.codePoints().anyMatch(c -> c >= 'a' && c <= 'z') && !any;
        return new Result(out.strip(), korean ? "ko" : "en", korean ? "en" : "ko", any && !leftover);
    }
}
