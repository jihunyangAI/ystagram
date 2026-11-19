package com.yangstagram.seed;

import java.util.List;

/**
 * 가짜 사용자의 관심 주제. 사용자 이름·소개 글·팔로우 군집을 만드는 데 쓴다.
 * slug는 사용자 이름에 들어가는 영문 단어 후보.
 */
public enum Topic {
    TRAVEL("여행", List.of("travel", "trip", "wander", "journey"), "✈️", List.of("여행 기록", "다음 여행지는 어디", "길 위에서")),
    FOOD("맛집", List.of("food", "eats", "yum", "foodie"), "🍜", List.of("먹으러 다니는 사람", "맛집 기록", "오늘 뭐 먹지")),
    CAFE("카페", List.of("cafe", "coffee", "latte", "brew"), "☕", List.of("카페 투어 중", "커피 없인 못 살아", "주말엔 카페")),
    FASHION("패션", List.of("style", "ootd", "look", "fit"), "👗", List.of("오늘의 착장", "데일리룩", "옷 좋아함")),
    FITNESS("운동", List.of("fit", "gym", "run", "lift"), "🏋️", List.of("운동하는 직장인", "오운완", "달리기 기록")),
    PETS("반려동물", List.of("dog", "cat", "pet", "meow"), "🐶", List.of("집사 일기", "우리 집 막내", "댕댕이 사진 계정")),
    PHOTO("사진", List.of("photo", "film", "shot", "lens"), "📷", List.of("필름 사진", "찍는 사람", "빛을 모으는 중")),
    MUSIC("음악", List.of("music", "beat", "tune", "vinyl"), "🎧", List.of("플레이리스트 공유", "음악 듣는 사람", "공연 다니는 중")),
    DEV("개발", List.of("dev", "code", "byte", "build"), "💻", List.of("개발하는 사람", "코드와 커피", "오늘도 배포")),
    PARENTING("육아", List.of("mom", "dad", "baby", "family"), "👶", List.of("육아 기록", "아이와 함께", "우리 가족 이야기")),
    BEAUTY("뷰티", List.of("beauty", "glow", "makeup", "skin"), "💄", List.of("뷰티 기록", "피부 관리 중", "화장품 리뷰")),
    BOOKS("책", List.of("book", "read", "page", "novel"), "📚", List.of("읽고 쓰는 사람", "책 한 권", "독서 기록")),
    ART("그림", List.of("art", "draw", "sketch", "paint"), "🎨", List.of("그림 그리는 사람", "드로잉 일기", "작업 기록")),
    NATURE("자연", List.of("green", "forest", "sky", "hike"), "🌿", List.of("산 좋아함", "초록이 좋아", "등산 기록")),
    SPORTS("스포츠", List.of("ball", "goal", "team", "game"), "⚽", List.of("직관 다니는 중", "응원하는 팀이 있음", "축구 좋아함")),
    GAMING("게임", List.of("game", "play", "pixel", "quest"), "🎮", List.of("게임하는 사람", "오늘도 한 판", "레벨 업 중")),
    MOVIE("영화", List.of("movie", "cine", "scene", "film"), "🎬", List.of("영화 기록", "주말엔 영화관", "영화 보는 사람")),
    COOKING("요리", List.of("cook", "kitchen", "recipe", "bake"), "🍳", List.of("집밥 기록", "베이킹 하는 중", "오늘의 요리")),
    INTERIOR("인테리어", List.of("home", "room", "deco", "space"), "🛋️", List.of("우리 집 꾸미기", "방 꾸미는 중", "집 기록")),
    CAR("자동차", List.of("car", "drive", "motor", "road"), "🚗", List.of("드라이브 좋아함", "차 좋아하는 사람", "주말 드라이브"));

    private final String label;
    private final List<String> slugs;
    private final String emoji;
    private final List<String> bioPhrases;

    Topic(String label, List<String> slugs, String emoji, List<String> bioPhrases) {
        this.label = label;
        this.slugs = slugs;
        this.emoji = emoji;
        this.bioPhrases = bioPhrases;
    }

    public String label() {
        return label;
    }

    public List<String> slugs() {
        return slugs;
    }

    public String emoji() {
        return emoji;
    }

    public List<String> bioPhrases() {
        return bioPhrases;
    }
}
