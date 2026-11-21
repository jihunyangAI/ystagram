package com.yangstagram.seed;

import java.util.List;
import java.util.Map;

/**
 * 가짜 게시물·댓글에 쓰는 문장 틀 (기획서 §8.3, 결정 D4: 문장 틀 조합).
 * 게시물 캡션·댓글은 SeedCaptions(사진 대상에 맞춘 문장)로 옮겼고, 여기에는 해시태그·장소·시뮬레이터용 짧은 문장이 남아 있다.
 */
public final class SeedText {

    private SeedText() {
    }

    /** 주제별 캡션 첫 문장 */
    public static final Map<Topic, List<String>> CAPTIONS = Map.ofEntries(
            Map.entry(Topic.TRAVEL, List.of("다시 가고 싶은 곳", "여행은 역시 걷는 맛", "이번 여행 사진 정리 중", "공항 가는 길이 제일 설렘", "길을 잃어도 좋았던 하루")),
            Map.entry(Topic.FOOD, List.of("여기 진짜 맛집이에요", "웨이팅 할 만했다", "오늘의 한 끼", "또 먹으러 갈 예정", "맛있는 건 0칼로리")),
            Map.entry(Topic.CAFE, List.of("오늘의 카페", "창가 자리 성공", "커피 한 잔의 여유", "디저트까지 완벽", "주말 카페 투어")),
            Map.entry(Topic.FASHION, List.of("오늘의 착장", "요즘 자주 입는 조합", "날씨 좋아서 꺼낸 옷", "데일리룩 기록", "새로 산 거 개시")),
            Map.entry(Topic.FITNESS, List.of("오운완", "오늘도 해냈다", "아침 러닝 기록", "꾸준함이 답", "땀 흘린 만큼 개운")),
            Map.entry(Topic.PETS, List.of("우리 집 막내", "산책 가자고 조르는 중", "자는 모습이 제일 귀여움", "간식 앞에선 얌전", "오늘도 집사는 행복")),
            Map.entry(Topic.PHOTO, List.of("빛이 좋았던 날", "필름으로 담은 오후", "셔터 누르는 순간", "색감이 마음에 들어서", "오늘의 한 컷")),
            Map.entry(Topic.MUSIC, List.of("요즘 무한 반복 중", "공연 다녀왔어요", "LP 한 장 추가", "오늘의 플레이리스트", "음악 없는 하루는 상상 불가")),
            Map.entry(Topic.DEV, List.of("오늘도 배포 성공", "버그 잡은 날", "새 프로젝트 시작", "책상 정리 완료", "커피와 코드")),
            Map.entry(Topic.PARENTING, List.of("하루하루 크는 게 보여요", "주말 나들이", "처음 해 본 것들", "육아는 체력전", "웃음이 끊이지 않는 하루")),
            Map.entry(Topic.BEAUTY, List.of("요즘 쓰는 템", "오늘의 메이크업", "피부 관리 루틴", "인생템 찾음", "가을 컬러 추천")),
            Map.entry(Topic.BOOKS, List.of("이번 주에 읽은 책", "밑줄 긋고 싶은 문장", "책 읽기 좋은 날씨", "서점 나들이", "올해의 책 후보")),
            Map.entry(Topic.ART, List.of("오늘의 드로잉", "작업 중인 그림", "색 고르는 게 제일 어려움", "전시 보고 왔어요", "손으로 그린 기록")),
            Map.entry(Topic.NATURE, List.of("초록 충전 완료", "정상에서 본 풍경", "숲길 산책", "하늘이 예뻤던 날", "계절이 바뀌는 중")),
            Map.entry(Topic.SPORTS, List.of("직관 다녀옴", "오늘 경기 미쳤다", "응원하는 팀 승리", "주말 풋살", "땀 흘리고 맥주 한 잔")),
            Map.entry(Topic.GAMING, List.of("드디어 클리어", "오늘도 한 판만", "새 게임 시작", "세팅 바꿨어요", "친구랑 밤새 게임")),
            Map.entry(Topic.MOVIE, List.of("오늘 본 영화", "여운이 길게 남는 영화", "주말엔 영화관", "다시 보고 싶은 장면", "올해 본 영화 중 최고")),
            Map.entry(Topic.COOKING, List.of("오늘의 집밥", "처음 만들어 본 요리", "빵 굽는 주말", "레시피 공유해요", "생각보다 잘 됐다")),
            Map.entry(Topic.INTERIOR, List.of("방 구조 바꿨어요", "새로 들인 가구", "우리 집 최애 공간", "조명 하나로 분위기 변신", "정리 정돈 완료")),
            Map.entry(Topic.CAR, List.of("주말 드라이브", "세차 완료", "바다 보러 출발", "야간 드라이브", "새 차 기념")));

    /** 캡션 뒤에 붙는 짧은 덧말 */
    static final List<String> TAILS = List.of(
            "", "", "", "😊", "✨", "🙌", "💛", "📸", "🥹", "👍", "다들 좋은 하루 보내세요",
            "기록용", "또 올게요", "추천합니다", "행복했다", "오랜만에 올려요");

    /** 주제별 해시태그 후보 ('#' 없이) */
    static final Map<Topic, List<String>> TAGS = Map.ofEntries(
            Map.entry(Topic.TRAVEL, List.of("여행", "여행스타그램", "travel", "국내여행", "trip", "여행기록")),
            Map.entry(Topic.FOOD, List.of("맛집", "먹스타그램", "food", "맛집추천", "foodie", "오늘뭐먹지")),
            Map.entry(Topic.CAFE, List.of("카페", "카페투어", "cafe", "coffee", "디저트", "카페추천")),
            Map.entry(Topic.FASHION, List.of("ootd", "데일리룩", "패션", "오오티디", "style", "코디")),
            Map.entry(Topic.FITNESS, List.of("오운완", "운동", "헬스", "러닝", "fitness", "workout")),
            Map.entry(Topic.PETS, List.of("반려견", "고양이", "멍스타그램", "냥스타그램", "dog", "cat")),
            Map.entry(Topic.PHOTO, List.of("사진", "필름사진", "photography", "감성사진", "photo", "filmcamera")),
            Map.entry(Topic.MUSIC, List.of("음악", "플레이리스트", "music", "공연", "lp", "concert")),
            Map.entry(Topic.DEV, List.of("개발자", "코딩", "dev", "programming", "deskSetup", "개발일기")),
            Map.entry(Topic.PARENTING, List.of("육아", "육아스타그램", "아기", "baby", "육아일기", "family")),
            Map.entry(Topic.BEAUTY, List.of("뷰티", "메이크업", "beauty", "스킨케어", "makeup", "화장품")),
            Map.entry(Topic.BOOKS, List.of("책", "책스타그램", "독서", "bookstagram", "독서기록", "books")),
            Map.entry(Topic.ART, List.of("그림", "드로잉", "art", "illustration", "전시", "drawing")),
            Map.entry(Topic.NATURE, List.of("등산", "자연", "nature", "하늘", "산책", "hiking")),
            Map.entry(Topic.SPORTS, List.of("축구", "야구", "직관", "sports", "풋살", "football")),
            Map.entry(Topic.GAMING, List.of("게임", "gaming", "게이머", "gamer", "steam", "닌텐도")),
            Map.entry(Topic.MOVIE, List.of("영화", "영화추천", "movie", "cinema", "넷플릭스", "영화기록")),
            Map.entry(Topic.COOKING, List.of("집밥", "요리", "베이킹", "cooking", "레시피", "homecafe")),
            Map.entry(Topic.INTERIOR, List.of("인테리어", "집꾸미기", "interior", "homedeco", "자취방", "랜선집들이")),
            Map.entry(Topic.CAR, List.of("드라이브", "자동차", "car", "세차", "drive", "카스타그램")));

    /** 게시물 위치: {이름, 도시} */
    static final List<String[]> LOCATIONS = List.of(
            new String[] {"서울숲", "서울"}, new String[] {"성수동 카페거리", "서울"}, new String[] {"연남동", "서울"},
            new String[] {"익선동 한옥거리", "서울"}, new String[] {"한강공원 뚝섬", "서울"}, new String[] {"북촌 한옥마을", "서울"},
            new String[] {"을지로", "서울"}, new String[] {"남산타워", "서울"}, new String[] {"잠실 롯데월드몰", "서울"},
            new String[] {"광화문", "서울"}, new String[] {"해운대 해수욕장", "부산"}, new String[] {"광안리", "부산"},
            new String[] {"감천문화마을", "부산"}, new String[] {"전포 카페거리", "부산"}, new String[] {"애월 해안도로", "제주"},
            new String[] {"성산일출봉", "제주"}, new String[] {"협재 해수욕장", "제주"}, new String[] {"강릉 안목해변", "강릉"},
            new String[] {"경주 황리단길", "경주"}, new String[] {"전주 한옥마을", "전주"}, new String[] {"수원 행궁동", "수원"},
            new String[] {"인천 송도 센트럴파크", "인천"}, new String[] {"대구 동성로", "대구"}, new String[] {"대전 성심당", "대전"},
            new String[] {"여수 밤바다", "여수"}, new String[] {"속초 해수욕장", "속초"}, new String[] {"가평 쁘띠프랑스", "가평"},
            new String[] {"도쿄 시부야", "도쿄"}, new String[] {"오사카 도톤보리", "오사카"}, new String[] {"파리 에펠탑", "파리"});

    /** 짧은 반응 댓글 */
    public static final List<String> COMMENTS = List.of(
            "너무 예뻐요 😍", "여기 어디예요?", "와 분위기 미쳤다", "저도 가 보고 싶어요!", "사진 너무 잘 찍으셨다",
            "👏👏👏", "🔥🔥", "❤️", "좋아요 누르고 갑니다", "색감 대박", "부럽다…", "다음에 같이 가요",
            "이거 보고 저장했어요", "완전 내 스타일", "와우", "오늘도 멋지네요", "최고예요 👍", "ㅋㅋㅋㅋ 귀여워",
            "정보 감사합니다!", "분위기 너무 좋다", "어디서 사셨어요?", "보기만 해도 힐링", "와 진짜 대박이다",
            "언제 다녀오셨어요?", "ㅠㅠ 가고 싶다", "멋져요!!", "이 사진 제일 좋아요", "벌써 그리워지는 계절",
            "맛있겠다 🤤", "감성 장난 아니네요");

    /** 게시물 주인의 답글 */
    static final List<String> REPLIES = List.of(
            "감사해요 😊", "꼭 가 보세요!", "ㅎㅎ 고마워요", "다음에 같이 가요!", "🙏", "❤️", "저도 또 가고 싶어요",
            "위치는 DM 드릴게요", "칭찬 감사합니다 ✨");
}
