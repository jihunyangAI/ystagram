package com.yangstagram.seed;

import com.yangstagram.seed.SeedService.Persona;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 진짜 사람이 쓴 것 같은 캡션·댓글 (기획서 §8.3).
 * 사진마다 "무엇을 찍었는지"(item, 예: 수제버거·라떼·댕댕이)가 있어서 글이 사진과 맞는다.
 * 틀 안의 자리: {item} 사진 대상, {place} 장소, {friend} 같이 간 친구(@멘션), {n} 작은 숫자, {price} 가격, {shop} 가게 이름.
 */
public final class SeedCaptions {

    private SeedCaptions() {
    }

    /** 캡션 한 개에 필요한 정보 */
    record Ctx(Topic topic, String item, String place, String friend, Persona persona, String shop) {
    }

    // ------------------------------------------------------------ 캡션

    private static final Map<Topic, List<String>> POSTS = Map.ofEntries(
            Map.entry(Topic.FOOD, List.of(
                    "{place} {item} 맛집 드디어 와봤다 🤤\n웨이팅 40분 했는데 후회 1도 없음\n다음엔 다른 메뉴도 먹어볼 예정",
                    "오늘 점심은 {item}\n회사 근처에 이런 데가 있었다니… 왜 이제 알았지",
                    "{item} 먹으러 왕복 2시간 운전한 사람 = 나\n근데 그럴 만했다 진짜로",
                    "{friend} 가 데려간 {item} 집\n둘 다 말없이 먹기만 함ㅋㅋ 그게 정답",
                    "비 오는 날엔 역시 {item}\n배불러서 움직일 수가 없다",
                    "재방문 의사 💯\n{item} 시켰는데 양이 미쳤음\n가격도 착하고 사장님도 친절하심",
                    "다이어트는 내일부터 하기로 함 🙃\n오늘의 {item}",
                    "생일 기념으로 {item} 플렉스 🎂\n이 맛에 일하지",
                    "인생 {item} 찾았다…\n여기는 진짜 저만 알고 싶은데 올림",
                    "{item} 사진만 보면 배고파지는 병 있음\n오늘 저녁 메뉴 고민 끝",
                    "솔직 후기)\n{item} 맛은 있는데 웨이팅이 너무 길다\n평일 오픈런 추천합니다",
                    "퇴근하고 혼밥 🍽\n{item} 한 그릇이면 하루 피로 끝")),
            Map.entry(Topic.CAFE, List.of(
                    "주말 카페 투어 ☕️\n{place} 신상 카페 다녀왔어요\n{item} 진짜 맛있었음",
                    "오늘의 {item}\n창가 자리 잡아서 2시간 멍때림",
                    "{item} 맛집 발견 📍{place}\n분위기도 좋고 디저트도 다 맛있음",
                    "카공하러 왔다가 {item}만 두 개 먹고 감\n공부는 내일의 내가…",
                    "{friend} 랑 수다 떨다 보니 4시간 순삭\n{item} 리필하고 싶었다",
                    "아아 말고 {item} 마시는 계절이 왔다 🍂",
                    "여기 {item} 먹으려고 오픈런함\n10시 반에 갔는데 벌써 줄 서 있었음",
                    "카페인 충전 완료 ⚡️\n오늘도 버텨보자",
                    "사장님이 직접 만드신다는 {item}\n단 거 싫어하는 사람도 좋아할 맛",
                    "비 오는 날 카페 = 국룰\n{item} 한 입 하고 빗소리 듣는 중")),
            Map.entry(Topic.TRAVEL, List.of(
                    "{place}에 와 있어요 ✈️\n하루 종일 걸었더니 다리가 후들후들\n그래도 너무 좋다",
                    "여행 사진 정리하다가 다시 가고 싶어서 올림\n{place} 또 갈 거야",
                    "D+{n}\n아무 계획 없이 걷기만 하는 중\n이게 진짜 여행이지",
                    "공항 가는 길이 제일 설레는 거 나만 그래? 🧳",
                    "{friend} 랑 {n}년 만의 여행\n사진 300장 찍었는데 건질 건 이거 하나ㅋㅋ",
                    "{place} 날씨 요정 등장 ☀️\n어제까지 비 왔다는데 운 좋았다",
                    "숙소 뷰가 다 했다\n체크아웃 하기 싫어…",
                    "여행 마지막 날\n벌써 그리운 거 실화냐",
                    "길 잃어서 들어간 골목이 제일 예뻤음\n#계획없는여행 의 묘미",
                    "연차 쓰고 떠난 {place}\n회사 단톡 알림은 잠시 꺼둘게요 🙏")),
            Map.entry(Topic.FASHION, List.of(
                    "오늘 착장 🖤\n요즘 이 조합에 꽂혔어요",
                    "새로 산 {item} 개시 ✨\n생각보다 훨씬 잘 어울려서 기분 좋음",
                    "꾸안꾸 데일리룩\n정보는 댓글로 물어봐 주세요 (택 다 뗌ㅠ)",
                    "날씨 좋아서 꺼낸 {item}\n가을엔 역시 레이어드",
                    "출근룩 기록 📁\n편하면서 멀쩡해 보이는 게 목표",
                    "{item} 하나로 분위기 달라지는 거 신기함",
                    "주말 {place} 나들이 룩\n{friend} 가 찍어줬는데 사진 잘 나왔다",
                    "세일 때 건진 {item} 💸\n이 가격 실화?",
                    "요즘 자주 입는 {item}\n어디 거냐고 많이 물어보셔서 올려요 (협찬 아님)")),
            Map.entry(Topic.FITNESS, List.of(
                    "오운완 💪\n오늘은 하체 날… 계단 내려갈 때 무서움",
                    "아침 {item} 기록\n5km 27분, 지난주보다 1분 줄었다 🏃",
                    "{n}일 연속 운동 성공\n꾸준함이 답이라는 말 이제 좀 알겠다",
                    "운동 가기 싫은 날 그냥 일단 나옴\n막상 하면 개운한 거 알잖아요",
                    "{item} 시작한 지 {n}개월\n체력이 확실히 달라졌어요",
                    "PT 쌤이 오늘 칭찬해 줌 🥹\n이 맛에 운동한다",
                    "땀 흘린 만큼 개운한 하루\n프로틴 한 잔 하고 귀가",
                    "주말 아침 {item}\n공기 좋고 사람 없고 최고")),
            Map.entry(Topic.PETS, List.of(
                    "우리 집 {item} 자는 모습 보세요 🥹\n코 고는 소리까지 귀여움",
                    "산책 가자고 현관 앞에서 시위 중\n누가 이기나 보자",
                    "간식 봉지 소리 듣고 0.1초 만에 달려옴 🐾",
                    "오늘도 집사는 행복합니다\n월급의 반은 얘 꺼",
                    "{item} 생일 🎂\n벌써 {n}살이라니… 아프지 말고 오래오래 같이 살자",
                    "목욕 직후 표정\n세상 억울함",
                    "택배 상자는 내 거라고 주장하는 중 📦",
                    "{friend} 네 {item} 놀러 옴\n둘이 너무 잘 놀아서 감동",
                    "이 눈빛에 안 넘어가는 사람 없음\n결국 간식 또 줌")),
            Map.entry(Topic.PHOTO, List.of(
                    "빛이 좋았던 날 📷\n이 시간대 {item:은} 못 참지",
                    "필름 현상 맡긴 거 찾아옴\n기다리는 맛이 있다",
                    "보정 거의 안 했어요\n{place}의 {item}",
                    "오늘의 한 컷\n셔터 누르는 순간 이건 건졌다 싶었음",
                    "카메라 들고 {place} 산책\n{n}시간 걸었는데 마음에 드는 건 이거",
                    "색감 마음에 들어서 올려요 🎞")),
            Map.entry(Topic.MUSIC, List.of(
                    "요즘 무한 반복 중인 노래 🎧\n가사가 너무 내 얘기",
                    "{item} 다녀왔어요 🎤\n아직도 귀에서 소리가 나는 중… 최고였다",
                    "LP 한 장 추가 💿\n월급날마다 하나씩 사는 게 소확행",
                    "{item} 연습 {n}개월 차\n드디어 한 곡 완주했다",
                    "비 오는 날 플레이리스트\n추천곡 있으면 댓글로 알려주세요 🙏",
                    "{friend} 랑 갑자기 간 공연인데 인생 공연 됨")),
            Map.entry(Topic.DEV, List.of(
                    "오늘도 배포 성공 🚀\n금요일 배포는 하지 말자고 했잖아…",
                    "{item} 세팅 바꿨어요\n모니터 암 달았더니 책상이 넓어짐",
                    "버그 3시간 잡았는데 원인이 오타 하나였음\n개발자의 삶이란",
                    "새 사이드 프로젝트 시작\n이번엔 진짜 끝까지 간다 (아마도)",
                    "커피와 코드 ☕️💻\n주말인데 왜 일하고 있지",
                    "키보드 새로 들였어요 ⌨️\n타건감 때문에 일하고 싶어짐 (거짓말)")),
            Map.entry(Topic.PARENTING, List.of(
                    "하루하루 크는 게 보여요 🥹\n오늘 처음으로 \"엄마\" 했어요",
                    "주말 나들이\n{place} 갔는데 우리보다 애가 더 신남",
                    "육아는 체력전…\n커피 세 잔째",
                    "처음 해 본 것들 기록 📒\n오늘은 혼자 숟가락질 성공",
                    "{n}개월 기념 사진\n이 작은 손이 언제 이렇게 컸지",
                    "잠든 모습 보면 하루 피로가 다 풀림\n(깨어 있을 땐… 할 말 하않)")),
            Map.entry(Topic.BEAUTY, List.of(
                    "요즘 쓰는 {item} 💄\n인생템 찾은 것 같아요 (내돈내산)",
                    "오늘의 메이크업\n가을엔 역시 브라운 톤",
                    "피부 관리 루틴 공유해요 ✨\n질문은 댓글로!",
                    "{item} 공병 기념\n벌써 {n}통째 재구매",
                    "드럭스토어 털어옴 🛍\n{item} 발색 미쳤어요",
                    "{friend} 가 추천해 준 {item}\n왜 이제 알았을까")),
            Map.entry(Topic.BOOKS, List.of(
                    "이번 주에 읽은 책 📚\n마지막 장 덮고 한참 멍했어요",
                    "밑줄 긋고 싶은 문장이 너무 많은 책\n올해의 책 후보",
                    "{place} 독립서점 나들이\n결국 또 세 권 사 옴",
                    "책 읽기 좋은 날씨 ☁️\n따뜻한 차 한 잔이랑",
                    "올해 목표 {n}권 중 절반 넘김 🎉",
                    "북클럽 이번 달 책\n{friend} 덕분에 끝까지 읽음")),
            Map.entry(Topic.ART, List.of(
                    "오늘의 {item} 🎨\n색 고르는 게 제일 어려워요",
                    "작업 중인 그림 조금만 공개\n완성되면 다시 올릴게요",
                    "{place} 전시 보고 왔어요\n한 작품 앞에서 20분 서 있었음",
                    "{item} 연습 {n}일 차\n손이 조금씩 따라오는 느낌",
                    "주말엔 그림 그리면서 쉬어요\n잡생각이 사라져서 좋음")),
            Map.entry(Topic.NATURE, List.of(
                    "초록 충전 완료 🌿\n도시에선 못 느끼는 공기",
                    "정상에서 본 풍경 ⛰\n올라올 땐 후회했는데 내려가기 싫다",
                    "{place} 숲길 산책\n바람 소리만 들리는 게 너무 좋았다",
                    "하늘이 예뻐서 한참 서 있었음\n오늘은 사진보다 눈에 담기",
                    "{friend} 랑 새벽 등산\n일출 보려고 4시에 일어남 🌅",
                    "계절이 바뀌는 중 🍂\n{item} 색이 매일 달라요")),
            Map.entry(Topic.SPORTS, List.of(
                    "직관 다녀옴 ⚽️\n목이 다 쉬었지만 이겼으니 됐다",
                    "오늘 경기 미쳤다…\n마지막 {n}분에 역전골",
                    "주말 {item} 🏀\n끝나고 마시는 맥주 맛으로 함",
                    "{friend} 랑 처음 해 본 {item}\n다음 주에 또 하기로 함",
                    "응원하는 팀 연승 중 🔥\n이번 시즌 기대해도 되나요")),
            Map.entry(Topic.GAMING, List.of(
                    "드디어 엔딩 봤다 🎮\n플레이 타임 {n}0시간… 여운이 길다",
                    "오늘도 한 판만 하고 자려고 했는데\n해가 뜸",
                    "{item} 새로 샀어요\n손에 착 감김",
                    "{friend} 랑 밤새 협동\n우정은 지켰습니다 (아마도)",
                    "책상 세팅 바꿨어요 ✨\nRGB 없으면 성능이 안 나온다던데")),
            Map.entry(Topic.MOVIE, List.of(
                    "오늘 본 영화 🎬\n엔딩 크레딧 끝날 때까지 못 일어남",
                    "주말엔 {item} 🍿\n팝콘은 무조건 반반",
                    "여운이 너무 길게 남는 영화\n스포 없이 그냥 보세요",
                    "{friend} 랑 심야 영화\n끝나고 두 시간 동안 토론함",
                    "올해 본 영화 중 최고\n벌써 한 번 더 보고 싶다")),
            Map.entry(Topic.COOKING, List.of(
                    "오늘의 집밥 🍳\n자취 {n}년 차 실력 많이 늘었죠?",
                    "처음 만들어 본 {item}\n생각보다 잘 돼서 놀람",
                    "주말엔 {item} 🥐\n집 안에 버터 냄새 가득",
                    "레시피 물어보셔서 공유해요\n저장해 두고 해 드세요 📌",
                    "{friend} 초대해서 홈파티\n설거지는 내일의 나에게…",
                    "냉장고 파먹기 성공\n있는 재료로 만든 거 치고 훌륭함")),
            Map.entry(Topic.INTERIOR, List.of(
                    "방 구조 바꿨어요 🛋\n주말 내내 가구 옮김",
                    "우리 집 최애 공간\n여기서 커피 마실 때가 제일 행복",
                    "새로 들인 {item}\n공간이 확 살아났어요",
                    "조명 하나로 분위기 변신 💡\n전구색 추천합니다",
                    "자취방 랜선 집들이 🏠\n{n}평의 기적",
                    "식물 집사 {n}년 차 🌱\n이제 안 죽이고 잘 키워요")),
            Map.entry(Topic.CAR, List.of(
                    "주말 드라이브 🚗\n{place}까지 노래 크게 틀고",
                    "세차 완료 ✨\n비 온다는 예보 못 봄…",
                    "바다 보러 출발\n{friend} 운전 고마워",
                    "야간 드라이브 🌙\n아무 생각 없이 달리는 시간이 필요했어",
                    "이 차랑 함께한 지 {n}년\n앞으로도 잘 부탁해")));

    /** 덧붙이는 한 줄 (30%) */
    private static final List<String> EXTRAS = List.of(
            "다들 좋은 주말 보내세요 🙌", "기록용 📌", "오랜만에 올려요", "행복했다 💛", "저장해 두고 보세요",
            "이 날 날씨 진짜 좋았음", "사진으로 다 안 담겨서 아쉬움", "벌써 다음 주가 기다려진다", "오늘도 수고했어요 다들",
            "ㅎㅎ", "🥹🥹", "✨");

    /** 비즈니스 계정 (가게 공지 말투) */
    private static final List<String> BUSINESS = List.of(
            "[{shop}] 이번 주 신메뉴 {item} 출시 🎉\n한정 수량이라 조기 마감될 수 있어요\n\n📍 영업시간 11:00–21:00 (월 휴무)\n📩 예약·단체 문의는 DM 주세요",
            "{shop} 오늘도 오픈했습니다 ☀️\n{item} 넉넉히 준비해 두었어요\n\n📍 위치는 프로필 링크 참고",
            "🎁 팔로워 이벤트\n이 게시물 좋아요 + 친구 태그하면 추첨으로 {item} 드려요\n발표는 다음 주 금요일!",
            "단골손님들이 제일 많이 찾는 {item}\n오늘 들어온 재료로 만들었어요 💛\n\n{shop} 드림 🙌",
            "[공지] 이번 주 일요일은 임시 휴무입니다 🙏\n월요일에 {item} 들고 다시 찾아올게요");

    /** 인플루언서 (길고 정보가 많음) */
    private static final List<String> INFLUENCER = List.of(
            "{place} {item} 솔직 후기 📝\n\n✔️ 웨이팅: 평일 30분, 주말 1시간+\n✔️ 가격: {price}\n✔️ 재방문: 무조건\n\n(내돈내산 ❌광고 아님)\n궁금한 거 댓글 남겨주세요 🙌",
            "요즘 DM으로 제일 많이 받는 질문 → {item} 어디 거예요?\n정보 정리해서 올려요 ✨\n저장해 두고 보세요 📌",
            "오늘의 {item} 🖤\n협찬 아니고 진짜 좋아서 올리는 거예요\n여러분은 어떤 게 제일 좋으세요? 댓글로 알려주세요 👇",
            "{n}만 팔로워 감사합니다 🥹🙏\n늘 {item} 이야기만 하는데도 봐주셔서 고마워요\n작은 이벤트 준비 중이니 기대해 주세요!");

    private static final List<String> PRICES = List.of("1만 2천 원", "1만 8천 원", "2만 3천 원", "9천 원", "3만 원대",
            "1인 2만 원 정도");

    /** 장소가 없을 때 쓰는 말 */
    private static final List<String> NEAR = List.of("동네", "회사 근처", "집 앞", "성수", "연남동", "을지로", "망원",
            "합정", "강남", "해운대", "제주");

    /** 시뮬레이터 등에서 쓰는 간단한 판 */
    public static String simpleCaption(Topic topic, String item, Random rnd) {
        return caption(new Ctx(topic, item, null, null, Persona.REGULAR, null), rnd, List.of());
    }

    static String caption(Ctx c, Random rnd, List<String> tags) {
        if (rnd.nextDouble() < 0.06 && tags.isEmpty()) {
            return null; // 사진만
        }
        String body;
        if (c.persona() == Persona.BUSINESS && c.shop() != null && rnd.nextDouble() < 0.7) {
            body = pick(BUSINESS, rnd);
        } else if (c.persona() == Persona.INFLUENCER && rnd.nextDouble() < 0.4) {
            body = pick(INFLUENCER, rnd);
        } else {
            body = pick(POSTS.get(c.topic()), rnd);
            if (rnd.nextDouble() < 0.3) body += "\n" + pick(EXTRAS, rnd);
        }
        if (rnd.nextDouble() < 0.12) body = firstLine(body); // 짧게 한 줄만 쓰는 사람도 있다
        StringBuilder sb = new StringBuilder(fill(body, c, rnd));
        if (!tags.isEmpty()) {
            sb.append(rnd.nextDouble() < 0.6 ? "\n\n" : rnd.nextBoolean() ? "\n.\n.\n" : " ");
            sb.append(String.join(" ", tags.stream().map(t -> "#" + t).toList()));
        }
        return sb.toString();
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private static String fill(String s, Ctx c, Random rnd) {
        String place = c.place() != null ? c.place() : pick(NEAR, rnd);
        String item = c.item() != null ? c.item() : "오늘";
        // 친구 멘션은 "@이름 랑"처럼 띄우고, 멘션이 없으면 "친구랑"으로 붙인다
        s = c.friend() != null ? s.replace("{friend}", "@" + c.friend())
                : s.replaceAll("\\{friend\\} (가|랑|네|이|와|한테) ", "친구$1 ").replace("{friend}", "친구");
        return josa(s, item)
                .replace("{item}", item)
                .replace("{place}", place)
                .replace("{shop}", c.shop() != null ? c.shop() : "저희 가게")
                .replace("{price}", pick(PRICES, rnd))
                .replace("{n}", String.valueOf(2 + rnd.nextInt(8)));
    }

    /** {item:은} {item:이} {item:을} {item:와} → 받침에 맞는 조사 */
    private static String josa(String s, String word) {
        char last = word.charAt(word.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return s.replace("{item:은}", word + (batchim ? "은" : "는"))
                .replace("{item:이}", word + (batchim ? "이" : "가"))
                .replace("{item:을}", word + (batchim ? "을" : "를"))
                .replace("{item:와}", word + (batchim ? "과" : "와"));
    }

    // ------------------------------------------------------------ 댓글

    private static final Map<Topic, List<String>> COMMENTS = Map.ofEntries(
            Map.entry(Topic.FOOD, List.of("헐 {item} 맛있겠다 🤤", "여기 웨이팅 길어요?", "와 비주얼 미쳤다", "저장해 갑니다 📌",
                    "가격 얼마예요?", "나도 데려가 ㅠㅠ", "여기 진짜 맛있어요 인정", "배고픈데 이런 거 올리지 마세요ㅋㅋ")),
            Map.entry(Topic.CAFE, List.of("여기 어디예요?? 😍", "{item} 너무 맛있어 보여요", "분위기 대박", "카공 가능한가요?",
                    "주차 되나요?", "이번 주말에 가 봐야지", "디저트 맛집 인정")),
            Map.entry(Topic.TRAVEL, List.of("와 부럽다 ✈️", "여기 어디예요? 너무 예쁘다", "사진 진짜 잘 찍으셨다", "몇 박 몇 일이에요?",
                    "숙소 정보 알 수 있을까요?", "나도 떠나고 싶다…", "조심히 잘 다녀와요!")),
            Map.entry(Topic.FASHION, List.of("{item} 정보 알 수 있을까요?", "오늘 착장 찰떡 👍", "핏 너무 예뻐요", "어디 거예요??",
                    "분위기 미쳤다", "옷 잘 입는 사람 부럽다")),
            Map.entry(Topic.FITNESS, List.of("자극 받고 갑니다 💪", "오운완 👏", "대단하다 진짜", "저도 내일부터…",
                    "루틴 공유해 주세요!", "꾸준함 최고")),
            Map.entry(Topic.PETS, List.of("너무 귀여워 ㅠㅠㅠ", "이름이 뭐예요?", "심장 아파요 🥹", "간식 사 들고 놀러 갈게요",
                    "표정 무엇ㅋㅋㅋ", "몇 살이에요?", "랜선 이모 하고 갑니다")),
            Map.entry(Topic.PHOTO, List.of("색감 대박", "어떤 카메라 쓰세요?", "보정 어떻게 하셨어요?", "구도 너무 좋다", "필름 맞죠?")),
            Map.entry(Topic.MUSIC, List.of("이 노래 저도 요즘 무한 반복 중", "공연 부럽다 ㅠㅠ", "플리 공유해 주세요", "LP 컬렉션 멋져요")),
            Map.entry(Topic.DEV, List.of("책상 세팅 정보 좀요", "배포 축하해요 🎉", "키보드 뭐예요?", "금요일 배포라니… 용감하다")),
            Map.entry(Topic.PARENTING, List.of("너무 사랑스러워요 🥰", "벌써 이렇게 컸어요?", "육아 응원해요!", "볼 꼬집고 싶다")),
            Map.entry(Topic.BEAUTY, List.of("{item} 제품명 알려주세요!", "발색 예쁘다", "피부 좋으시다", "저도 써 볼게요", "호수 뭐예요?")),
            Map.entry(Topic.BOOKS, List.of("책 제목 알려주세요!", "저도 읽어 볼게요 📚", "독서 기록 멋져요", "서점 어디예요?")),
            Map.entry(Topic.ART, List.of("금손이다…", "완성작 기대돼요", "색감 너무 좋아요", "전시 저도 가 봐야겠다")),
            Map.entry(Topic.NATURE, List.of("힐링 된다 🌿", "여기 어디 산이에요?", "공기 좋아 보인다", "다음에 같이 가요!")),
            Map.entry(Topic.SPORTS, List.of("직관 부럽다", "오늘 경기 미쳤죠ㅋㅋ", "같이 해요 다음에!", "골 장면 봤어요?")),
            Map.entry(Topic.GAMING, List.of("엔딩 축하해요 🎉", "저도 하고 싶다", "세팅 멋져요", "같이 해요!")),
            Map.entry(Topic.MOVIE, List.of("저도 보고 싶었는데!", "스포 금지ㅋㅋ", "인생 영화 인정", "어디서 보셨어요?")),
            Map.entry(Topic.COOKING, List.of("레시피 공유해 주세요 🙏", "금손이네요", "맛있겠다…", "저 초대해 주세요ㅋㅋ")),
            Map.entry(Topic.INTERIOR, List.of("집 너무 예뻐요", "{item} 정보 알 수 있을까요?", "감성 미쳤다", "조명 어디 거예요?")),
            Map.entry(Topic.CAR, List.of("차 멋지다 🚗", "드라이브 코스 추천해 주세요", "세차 직후 비 국룰ㅋㅋ", "어디까지 가셨어요?")));

    private static final List<String> COMMON = List.of("😍😍", "🔥", "❤️", "👍", "와…", "대박", "너무 좋다", "멋져요!!",
            "사진 너무 예뻐요", "ㅋㅋㅋㅋㅋ", "좋아요 누르고 갑니다", "부럽다 진짜");

    /** 친구를 부르는 댓글 ({friend}) */
    private static final List<String> TAGGING = List.of("{friend} 여기 가자 우리", "{friend} 이거 봐ㅋㅋㅋ",
            "{friend} 다음 주에 여기 어때?", "{friend} 너 생각나서");

    public static String comment(Topic topic, String item, String friend, Random rnd) {
        double r = rnd.nextDouble();
        String s;
        if (friend != null && r < 0.12) {
            s = pick(TAGGING, rnd).replace("{friend}", "@" + friend);
        } else if (r < 0.75 && topic != null) {
            s = pick(COMMENTS.get(topic), rnd);
        } else {
            s = pick(COMMON, rnd);
        }
        return s.replace("{item}", item != null ? item : "이거");
    }

    private static final List<String> REPLIES = List.of("감사해요 😊", "꼭 가 보세요!", "ㅎㅎ 고마워요", "다음에 같이 가요!",
            "🙏", "❤️", "DM 드렸어요!", "칭찬 감사합니다 ✨", "ㅋㅋㅋㅋ 맞아요", "저도 또 가고 싶어요 ㅠㅠ");

    static String reply(String commenter, Random rnd) {
        return "@" + commenter + " " + pick(REPLIES, rnd);
    }

    private static <T> T pick(List<T> list, Random rnd) {
        return list.get(rnd.nextInt(list.size()));
    }
}
