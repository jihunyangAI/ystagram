# Instagram 구조 및 기능 분석

> 인스타그램의 정보 구조(IA), 화면 구성, 기능, 정책, 데이터 모델을 정리한 참고 문서입니다.
> **기준일: 2026-11-19** · 웹 검색으로 2024~2026년 변경 사항을 교차 확인했습니다.
>
> **검증 표시**
> - ✅ 공식 발표(Instagram/Meta) 또는 여러 주요 매체에서 일치하는 내용
> - ⚠️ 출처 간 내용이 다르거나, 테스트 중이거나, 2차 출처(마케팅 블로그)에만 있는 내용
> - 표시 없음: 오래 유지된 일반 기능(기본 사용 경험 기준)
>
> 인스타그램은 국가·계정·앱 버전별 A/B 테스트가 매우 많아 실제 앱 화면과 다를 수 있습니다. 출처는 문서 끝 [참고 자료](#참고-자료)에 있습니다.

---

## 목차

1. [서비스 개요와 현황](#1-서비스-개요와-현황)
2. [최근 주요 변경 타임라인 (2024~2026)](#2-최근-주요-변경-타임라인-20242026)
3. [계정 유형](#3-계정-유형)
4. [전체 정보 구조(IA)와 내비게이션](#4-전체-정보-구조ia와-내비게이션)
5. [홈(피드)](#5-홈피드)
6. [콘텐츠 유형과 사양](#6-콘텐츠-유형과-사양)
7. [게시물 작성 플로우](#7-게시물-작성-플로우)
8. [스토리](#8-스토리)
9. [릴스](#9-릴스)
10. [라이브](#10-라이브)
11. [탐색 & 검색](#11-탐색--검색)
12. [프로필](#12-프로필)
13. [소셜 그래프](#13-소셜-그래프)
14. [상호작용: 좋아요·댓글·공유·리포스트·저장](#14-상호작용-좋아요댓글공유리포스트저장)
15. [다이렉트 메시지(DM)](#15-다이렉트-메시지dm)
16. [알림](#16-알림)
17. [추천 알고리즘과 사용자 제어](#17-추천-알고리즘과-사용자-제어)
18. [크리에이터 & 비즈니스 도구](#18-크리에이터--비즈니스-도구)
19. [광고 & 커머스](#19-광고--커머스)
20. [유료 구독: Meta Verified / Meta One](#20-유료-구독-meta-verified--meta-one)
21. [AI 기능과 AI 콘텐츠 정책](#21-ai-기능과-ai-콘텐츠-정책)
22. [개인정보·보안·안전, 10대 계정](#22-개인정보보안안전-10대-계정)
23. [설정 메뉴 구조](#23-설정-메뉴-구조)
24. [회원가입·로그인·계정 관리](#24-회원가입로그인계정-관리)
25. [멀티 플랫폼 & Meta 생태계](#25-멀티-플랫폼--meta-생태계)
26. [개발자 API (Instagram Platform)](#26-개발자-api-instagram-platform)
27. [데이터 모델 (클론 구현 참고)](#27-데이터-모델-클론-구현-참고)
28. [라우트·API 설계 예시](#28-라우트api-설계-예시)
29. [기술 아키텍처 참고](#29-기술-아키텍처-참고)
30. [클론 MVP 로드맵](#30-클론-mvp-로드맵)
31. [분석 요약: 인스타그램의 방향성](#31-분석-요약-인스타그램의-방향성)

---

## 1. 서비스 개요와 현황

| 항목 | 내용 |
|---|---|
| 출시 | 2010년 10월 (iOS) |
| 운영사 | Meta Platforms (2012년 Facebook이 인수) |
| 책임자 | Adam Mosseri (Head of Instagram) |
| 월간 활성 사용자 | **30억 명** ✅ (2025년 9월 Mark Zuckerberg 발표. 직전 공개 수치는 2022년 10월 20억 명) |
| 플랫폼 | iOS, Android, 웹(instagram.com), **iPad 전용 앱** ✅ (2025년 9월 출시) |
| 핵심 축 | 공유(Create) · 소비(Feed/Reels/Stories) · 발견(Explore/Search) · 소통(DM) · 크리에이터/비즈니스 |

### 현재 전략의 중심 (공식 발언 기준)
- Meta는 "**메시지가 인스타그램에서 사진·영상을 공유하는 가장 인기 있는 방법**"이라고 밝혔습니다 ✅
- 3대 축: **릴스(영상) · DM(메시지) · 추천(Recommendations)** ✅ — 2025년 탭 개편이 이를 반영

---

## 2. 최근 주요 변경 타임라인 (2024~2026)

| 시기 | 변경 내용 | 상태 |
|---|---|---|
| 2024.03 | DM 메시지 수정(15분 이내), 채팅 고정, 읽음 표시 끄기 | ✅ |
| 2024.08 | 캐러셀 최대 10장 → **20장** | ✅ |
| 2024 | 스토리 댓글(공개 댓글, 스토리와 함께 24시간 유지) | ✅ |
| 2024.12 | **시험 릴스(Trial Reels)**: 팔로워가 아닌 사람에게 먼저 노출 | ✅ |
| 2025.01 | 프로필 그리드 썸네일 1:1 → **3:4 세로형** | ✅ |
| 2025.02 | DM 업데이트: 메시지 예약(최대 29일 후), 번역(99개 언어), 메시지 고정(최대 3개), 음악 공유 | ✅ |
| 2025.04 | **Blend**: DM 안에서 친구와 공유하는 릴스 피드 | ✅ |
| 2025.04 | 영상 편집 앱 **Edits** 출시 (iOS/Android, 무료) | ✅ |
| 2025.08 | **리포스트**, 릴스 **친구 탭**, **인스타그램 지도(Map)** (지도는 미국 먼저) | ✅ |
| 2025.09 | MAU 30억 명 달성, iPad 앱 출시 | ✅ |
| 2025.10 | **하단 탭 개편**: 홈 / 릴스 / DM / 검색 / 프로필 (만들기 `+`는 상단으로) | ✅ (국가별 적용 시점 상이 ⚠️) |
| 2025.10 | **10대 계정 13+(PG-13 기준) 콘텐츠 설정** (미국·영국·호주·캐나다 먼저, 2026년 전 세계) | ✅ |
| 2025 하반기 | 릴스 최대 길이 3분 → **20분** (3분 초과 릴스는 비팔로워 추천 대상에서 제외) | ⚠️ 시점은 출처별 상이 |
| 2025.12 | **해시태그 게시물당 최대 5개** (기존 30개) | ✅ |
| 2025.12 | **Your Algorithm**: 릴스 추천 주제를 보고 조정하는 기능 (미국 먼저) | ✅ |
| 2026.04 | **댓글 수정** (게시 후 15분 이내, 텍스트만, "수정됨" 표시) | ✅ |
| 2026.04 | Your Algorithm을 탐색으로 확대 | ⚠️ |
| 2026.05.08 | **DM 종단간 암호화(E2EE) 기능 종료** (사용률이 낮다는 이유) | ✅ |
| 2026.06.08 | **프로필 그리드 순서 재배치(Reorder Grid)** | ⚠️ 2차 출처 |
| 2026.06.10 | Your Algorithm이 피드·릴스·탐색 통합 시스템으로 확대 | ✅ (Instagram 블로그) |
| 2026.07 | 노트(Notes)에 댓글 기능 | ⚠️ |
| 2026.08.31 | "AI 크리에이터" 라벨 → **"AI 생성 프로필"** 라벨로 변경, 미표기 계정은 도달 제한 | ✅ |
| 2026.09.15 | **Meta One** 구독 출시 (Instagram Plus 포함) | ✅ |
| 2026.09~10 | 앱 안에서 **라이브 방송 홍보(Boost)** | ✅ |
| 2026.10.26 | **Features**: 최대 90분 가로 영상 (미국·브라질 일부 크리에이터 테스트) | ⚠️ |
| 2026.10.28 | 릴스 시리즈 연결 기능 종료 (별도 "시리즈" 형식 테스트 중) | ⚠️ 단일 출처 |

---

## 3. 계정 유형

| 유형 | 설명 | 주요 차이점 |
|---|---|---|
| 개인 (Personal) | 일반 사용자 | 공개/비공개 선택, 인사이트 없음 |
| 크리에이터 (Creator) | 인플루언서·아티스트 | 인사이트, 수익화, 카테고리 표시, DM 기본/일반 분리 |
| 비즈니스 (Business) | 브랜드·매장 | 인사이트, 연락처·행동 유도 버튼, 광고, 쇼핑 |
| **10대 계정** ✅ | 만 18세 미만 자동 적용 | 기본 비공개, 13+ 콘텐츠 설정, 메시지 제한, 수면 모드, 보호자 감독 ([22장](#22-개인정보보안안전-10대-계정)) |
| 인증 배지 | 파란 체크 | 유명인 진위 확인 또는 Meta Verified 구독 |
| **AI 생성 프로필** ✅ | 계정에 등장하는 인물이 AI로 생성된 경우 | 라벨 표시 의무, 미표기 시 추천 제한 ([21장](#21-ai-기능과-ai-콘텐츠-정책)) |

- **공개 계정**: 누구나 열람·팔로우
- **비공개 계정**: 팔로우 요청을 승인해야 게시물·스토리·팔로워 목록 열람 가능
- **다중 계정**: 앱 하나에서 여러 계정 전환, 계정 센터로 연결

---

## 4. 전체 정보 구조(IA)와 내비게이션

### 4.1 하단 탭 바 (2025년 10월 개편 이후) ✅

```
┌──────────────────────────────────────────────────┐
│  🏠 홈  │  🎬 릴스  │  ✈️ DM  │  🔍 검색  │ 👤 프로필 │
└──────────────────────────────────────────────────┘
```

| 구분 | 개편 전 | 개편 후 |
|---|---|---|
| 1 | 홈 | 홈 |
| 2 | 검색(탐색) | **릴스** |
| 3 (가운데) | **만들기 (+)** | **DM** |
| 4 | 릴스 | 검색(탐색) |
| 5 | 프로필 | 프로필 |

- 만들기(`+`) 버튼이 **상단 왼쪽**으로 이동 ✅
- **탭 간 좌우 스와이프** 이동 지원 ✅
- iPad 앱은 피드가 아닌 **릴스 화면으로 시작** ✅
- ⚠️ 국가별로 테스트·적용 시점이 달라 구 레이아웃 사용자도 있음

### 4.2 상단 바 (홈)
- 왼쪽: `+` 만들기
- 가운데/왼쪽: Instagram 로고 → 피드 필터(팔로잉 / 즐겨찾기)
- 오른쪽: ♡ 알림(활동)

### 4.3 사이트맵

```
Instagram
├── 홈(피드)
│   ├── 스토리 트레이(상단)
│   ├── 피드: 팔로우 게시물 · 추천 · 광고 · 리포스트
│   ├── 피드 필터: 팔로잉 / 즐겨찾기
│   └── 알림(활동)
├── 릴스
│   ├── 추천 릴스 / 친구 탭(친구가 좋아요·작성·리포스트·댓글한 릴스)
│   └── Your Algorithm (오른쪽 상단 아이콘)
├── DM
│   ├── 노트 · 인스타그램 지도(상단)
│   ├── 받은 메시지함: 기본 / 일반 / 요청
│   ├── 1:1 · 그룹 채팅 (Blend, 통화)
│   └── 브로드캐스트 채널
├── 검색·탐색
│   ├── 검색: 계정 / 키워드 / 오디오 / 태그 / 장소 / 릴스
│   └── 탐색 그리드
├── 프로필
│   ├── 헤더 · 하이라이트
│   ├── 탭: 게시물 그리드 / 릴스 / 리포스트 / 태그됨
│   └── ☰ 설정 및 활동
└── 만들기(+): 게시물 · 스토리 · 릴스 · 라이브 · 하이라이트 · 노트 · 채널 · (Features 테스트)
```

---

## 5. 홈(피드)

### 5.1 구성
| 영역 | 내용 |
|---|---|
| 스토리 트레이 | 가로 스크롤. 내 스토리(+) → 안 본 스토리 우선. 링 색상: 컬러=안 봄, 회색=봄, 초록=친한 친구 전용 |
| 팔로우 게시물 | 팔로우 계정의 게시물·릴스 |
| 리포스트 ✅ | 팔로우하는 사람이 리포스트한 게시물(원작자 표시 유지) |
| 추천 게시물 | 팔로우하지 않는 계정의 콘텐츠 |
| 광고 | "광고(Sponsored)" 표시 |
| 추천 계정 | 계정 카드 캐러셀 |

### 5.2 게시물 카드
```
┌───────────────────────────────────┐
│ (프로필) username · 협업자 · 장소 ⋯│  ← 오디오, "추천", 리포스트한 사람 표시
├───────────────────────────────────┤
│   미디어 (사진/영상/캐러셀 ●○○)    │  ← 더블탭 = 좋아요
├───────────────────────────────────┤
│ ♡  💬  🔁  ✈️                🔖   │  ← 좋아요/댓글/리포스트/공유, 저장
│ 좋아요 1,234개                     │
│ username 캡션... 더 보기           │
│ 댓글 56개 모두 보기 · 번역 보기    │
│ 3시간 전                           │
└───────────────────────────────────┘
```

### 5.3 `⋯` 메뉴
- **내 게시물**: 수정, 삭제, 보관, 프로필에 고정, 좋아요·조회수 숨기기, 댓글 끄기, 인사이트, 홍보하기, 오디오 교체(게시 후) ⚠️
- **다른 사람 게시물**: 저장, 즐겨찾기 추가, 팔로우 취소, 관심 없음, 이 게시물이 보이는 이유, 신고, 링크 복사, QR 코드

### 5.4 피드 정렬
- **홈(기본)**: 알고리즘 순위
- **팔로잉**: 팔로우 계정만 시간순
- **즐겨찾기**: 지정한 계정(최대 50개)만 시간순

---

## 6. 콘텐츠 유형과 사양

| 유형 | 형식 | 노출 위치 | 지속 기간 |
|---|---|---|---|
| 게시물 | 사진/영상 | 피드, 프로필 | 영구 |
| 캐러셀 | 사진·영상 혼합 **최대 20개** ✅ | 피드, 프로필 | 영구 |
| 릴스 | 세로 영상 **최대 20분** ⚠️ | 릴스, 피드, 탐색, 프로필 | 영구 |
| Features ⚠️ | 가로 장편 영상 최대 90분 (테스트) | — | 영구 |
| 스토리 | 세로 사진/영상 | 스토리 트레이 | 24시간 (Instagram Plus는 48시간 옵션 ⚠️) |
| 라이브 | 실시간 방송 | 스토리 트레이, 알림 | 실시간, 다시보기 |
| 노트 | 짧은 텍스트(60자)·음악·영상 | DM 상단, 프로필, 게시물/릴스 위 | 24시간 |
| 하이라이트 | 스토리 묶음 | 프로필 | 영구 |
| 리포스트 ✅ | 다른 사람의 공개 게시물/릴스 재공유 | 프로필 리포스트 탭, 팔로워 피드 | 영구 |
| 브로드캐스트 채널 | 일대다 메시지 | DM, 프로필 | 영구 |

### 사양 요약
| 항목 | 값 | 상태 |
|---|---|---|
| 캡션 | 최대 2,200자 | ✅ (Meta 개발자 문서) |
| 해시태그 | **게시물/릴스당 최대 5개** (2025.12~, 기존 30개) | ✅ |
| 캡션 @태그 | API 기준 최대 20개 | ✅ (API 문서) |
| 캐러셀 | 앱 20개 / **API 10개** | ✅ / ⚠️ |
| 캐러셀 내 영상 | 클립당 최대 60초 | ⚠️ |
| 릴스 | 최대 20분, **3분 초과는 비팔로워 추천 제외** | ⚠️ |
| 프로필 그리드 | **3:4** 썸네일 (1080×1440 권장) | ✅ |
| 피드 사진 비율 | 1:1, 4:5, 3:4, 1.91:1 | |
| 스토리/릴스 | 9:16 (1080×1920) | |
| 소개(Bio) | 150자 | ⚠️ |

---

## 7. 게시물 작성 플로우

```
[+] 만들기 (상단 왼쪽)
 → ① 미디어 선택 (갤러리/카메라, 최대 20개)
 → ② 자르기·비율
 → ③ 편집
      ├ 필터 (강도 조절)
      └ 수정: 밝기, 대비, 구조, 따뜻함, 채도, 색상, 페이드, 하이라이트, 그림자, 비네트, 틸트시프트, 선명도
 → ④ 상세 정보
      ├ 캡션 (2,200자, 해시태그 5개, @멘션)
      ├ 사람 태그 · 협업자 초대 · 위치 · 음악
      ├ 공개 대상: 전체 / 친한 친구
      ├ 제품 태그 · 유료 파트너십 라벨
      ├ AI 정보 라벨
      └ 고급 설정: 좋아요/조회수 숨기기, 댓글 끄기, 대체 텍스트, Facebook 동시 게시
 → ⑤ 공유 / 예약 / 임시저장
```

### 게시 후
- 캡션·태그·위치·대체 텍스트 수정 가능, 미디어 자체 교체는 불가 (캐러셀 일부 삭제는 가능)
- 게시 후 오디오 교체 ⚠️
- **보관**: 프로필에서 숨김, 복원 가능
- **삭제**: "최근 삭제됨"에 30일 보관 후 영구 삭제
- **그리드 재배치**: 길게 눌러 Reorder Grid → 드래그. 고정 게시물(최대 3개)은 맨 위에 유지, 앱에서만 가능 ⚠️

---

## 8. 스토리

### 8.1 시청
- 상단 진행 바, 탭=다음/이전, 길게 누르기=일시정지, 스와이프=다음 사용자
- 하단: 메시지 답장, ♡ 좋아요, 공유, **공개 댓글** ✅ (스토리와 함께 24시간 유지, 하이라이트에 넣으면 유지, 작성자가 끄거나 삭제 가능)

### 8.2 작성 도구
| 범주 | 기능 |
|---|---|
| 촬영 모드 | 일반, 부메랑, 레이아웃, 핸즈프리, 듀얼(전·후면), 만들기(텍스트 배경) |
| 효과 | AR 효과, AI 스토리 전환 효과 ⚠️ |
| 텍스트·그리기 | 폰트, 애니메이션, 펜, 형광펜, 네온 |
| 스티커 | 위치, 멘션, 해시태그, 음악, GIF, 설문, 퀴즈, 질문, 이모지 슬라이더, 카운트다운, 링크, 제품, Add Yours, 컷아웃, AI 스티커, 프레임, 음성 스티커(테스트 ⚠️) |
| 공유 대상 | 전체 / 친한 친구 / 메시지 / **여러 스토리 공개 대상**(Instagram Plus ⚠️) |

### 8.3 관리
- 조회자 목록, 스티커 응답 확인
- 특정 사용자에게 스토리 숨기기, 답장 허용 범위
- 자동 보관(스토리 아카이브), 하이라이트 추가
- 멘션된 스토리 리셰어
- 스토리 예약: Mosseri가 언급한 단계 ⚠️

### 8.4 하이라이트
- 프로필의 원형 앨범, 커버·이름 설정, 아카이브에서 선택

---

## 9. 릴스

### 9.1 시청
- 세로 전체화면, 위아래 스와이프
- 오른쪽: ♡ 좋아요, 💬 댓글, 🔁 리포스트, ✈️ 공유, ⋯, 오디오
- 하단: 작성자·팔로우, 캡션, 오디오, 협업자, AI 번역/더빙 표시
- 상단 탭: **추천 / 친구** ✅
- **Your Algorithm** 아이콘(오른쪽 상단) ✅

### 9.2 길이와 노출 ⚠️
- 최대 **20분** (한 번에 촬영하거나 여러 클립 조합)
- **3분 이하만 비팔로워 추천 대상** → 긴 릴스는 주로 기존 팔로워에게 노출
- **Features**: 최대 90분 가로 영상, 일부 크리에이터 대상 테스트, 수익 배분 미발표

### 9.3 작성 도구
- 속도, 타이머, 정렬(Align), 오디오 라이브러리·보이스오버·효과음·볼륨 믹싱
- 타임라인 편집(트림, 분할, 순서, 전환), 시간 지정 텍스트, 자동 자막, 템플릿
- **리믹스**(나란히, 그린스크린, 이어 붙이기), 템플릿 재사용
- 커버 선택, 그리드 미리보기
- **시험 릴스** ✅: 팔로워가 아닌 사람에게 24시간 먼저 노출 → 조회수·좋아요·댓글·공유 지표 확인
- **AI 번역/더빙** ✅: 한국어·일본어·프랑스어·독일어·이탈리아어 등으로 확대
- **Edits 앱 연동**: 고급 편집 후 바로 게시

### 9.4 오디오 페이지
- 해당 오디오를 쓴 릴스 목록, "오디오 사용", 저장
- 인기 오디오: 모든 공개 계정에 개방 ⚠️

---

## 10. 라이브

- 실시간 방송, 팔로워 알림
- 게스트 초대(최대 3명 공동 출연)
- 실시간 댓글, 댓글·질문 고정, 하트
- **배지**: 시청자 유료 후원
- 모더레이터, 댓글 필터, 시청자 차단
- 연습 모드(나만 보기), 라이브 예약
- **라이브 홍보(Boost)** ✅ (2026년 9~10월): 방송 직전이나 예약 시 설정. 방송 중에만 피드·스토리·릴스에 광고로 노출. 맞춤 대상 또는 추천 대상 선택. 최소 2시간 전 예약 권장
- 종료 후 다시보기 공유·다운로드

---

## 11. 탐색 & 검색

### 11.1 탐색
- 개인화 추천 그리드 (사진·영상·릴스 혼합)
- 타일을 누르면 비슷한 콘텐츠가 이어지는 피드
- AI 번역 적용 확대 ⚠️ (2026.04)
- Your Algorithm 주제 설정이 반영됨 ✅

### 11.2 검색
| 대상 | 결과 |
|---|---|
| 계정 | 사용자 이름/이름 |
| 키워드 | 관련 게시물·릴스 |
| 해시태그 | 해시태그 페이지 |
| 장소 | 장소 페이지(지도, 게시물, 매장 정보) |
| 오디오 | 오디오 페이지 |
| Meta AI | 검색창에서 AI 질문 |

- 최근 검색 기록 관리, 자동완성
- Mosseri에 따르면 해시태그는 **검색과 분류에는 도움이 되지만 알고리즘 노출을 크게 늘리지는 않음** ✅

---

## 12. 프로필

### 12.1 헤더
```
┌──────────────────────────────────────────────┐
│ username ▾ (계정 전환)             ＋   ☰    │
│ (프로필사진)    게시물    팔로워    팔로잉    │
│  +노트           128      1.2만     345      │
│ 이름 · 대명사 · 카테고리                      │
│ 소개(Bio) · 링크 · Threads 배지              │
│ "AI 생성 프로필" 라벨 (해당 시)               │
│ 함께 아는 팔로워: a, b 외 3명                 │
│ [프로필 편집] [프로필 공유]          ← 본인   │
│ [팔로우] [메시지] [⌄]               ← 타인   │
│ (○하이라이트) (○) (＋)                        │
├──────────────────────────────────────────────┤
│  ▦ 게시물 │ ▶ 릴스 │ 🔁 리포스트 │ 👤 태그됨  │
├──────────────────────────────────────────────┤
│  ▯ ▯ ▯   3열, 3:4 세로 썸네일                 │
│  ▯ ▯ ▯   고정 게시물(최대 3개) 맨 위          │
└──────────────────────────────────────────────┘
```

### 12.2 그리드 관련
- **3:4 세로 썸네일** ✅ (2025.01~, 원본은 피드에서 그대로 보임, 정사각형으로 되돌리기 불가)
- **순서 재배치** ⚠️ (2026.06~, 앱에서만, 팔로워 피드에는 영향 없음)
- 태그된 게시물을 내 그리드에 추가 ⚠️
- 고정 게시물: 기본 3개, Instagram Plus는 더 많이 ⚠️

### 12.3 프로필 편집
- 사진(아바타 가능), 이름, 사용자 이름, 대명사, 소개, 링크, 성별(비공개), 음악, Threads 배지
- 비즈니스: 카테고리, 연락처, 행동 유도 버튼, Facebook 페이지 연결
- Instagram Plus: 소개 글 맞춤 폰트 ⚠️

---

## 13. 소셜 그래프

| 개념 | 설명 |
|---|---|
| 팔로우 | 단방향 (비공개 계정은 요청 후 승인) |
| 맞팔로우 | 양방향 |
| 친한 친구 | 본인이 지정. 스토리·게시물·노트 공유 대상 제한 |
| 즐겨찾기 | 피드 우선 노출 (상대방은 모름) |
| 제한(Restrict) | 상대 댓글은 상대에게만 보임, 메시지는 요청함으로, 활동 상태 숨김 |
| 숨기기(Mute) | 게시물/스토리/노트 숨김, 팔로우는 유지 |
| 차단(Block) | 서로 열람·검색·메시지 불가, 새로 만드는 계정도 차단 옵션 |
| 팔로워 삭제 | 알림 없이 팔로워에서 제거 |
| 추천 계정 | 연락처, 함께 아는 친구, 관심사 기반 |

- 팔로워/팔로잉 목록: 검색, 정렬, "서로 팔로우", "가장 적게 소통한 계정" 등

---

## 14. 상호작용: 좋아요·댓글·공유·리포스트·저장

### 14.1 좋아요
- 하트 탭 또는 더블탭
- 좋아요 수 숨기기 (모든 게시물 보기 설정 / 내 게시물별)

### 14.2 댓글
- 텍스트, 이모지, GIF, @멘션, 사진 댓글(논의/테스트 ⚠️)
- 답글(1단계 스레드), 댓글 좋아요, 고정(최대 3개)
- **댓글 수정** ✅ (2026.04): 게시 후 15분 이내, 횟수 제한 없음, 텍스트만(사진·GIF·스티커 제외), "수정됨" 표시(이전 버전은 비공개), iOS/Android만 지원
- 작성자 관리: 삭제, 신고, 제한, 여러 개 선택
- 필터: 숨겨진 단어, 맞춤 키워드, 댓글 허용 대상, 댓글 끄기
- 댓글 끝까지 보면 다음 게시물로 이동(테스트 ⚠️)

### 14.3 리포스트 ✅ (2025.08)
- 공개 피드 게시물·릴스를 내 프로필에 재공유 (원작자 표시 유지)
- 팔로워 피드에 노출, 짧은 메모 추가 가능
- 프로필 **리포스트 탭**에 모아 보기

### 14.4 공유
- DM(개인/그룹), 스토리에 추가, 링크 복사, 외부 앱, QR
- 공유·리믹스 허용 여부 설정

### 14.5 저장
- 북마크, 컬렉션(폴더), 공유 컬렉션
- 본인만 볼 수 있음 (작성자는 저장 수만 인사이트로 확인)

### 14.6 태그 & 멘션
- 사람 태그 → 상대방 "태그됨" 탭 (수동 승인 옵션)
- 멘션 허용 범위: 모든 사람 / 팔로우하는 사람 / 아무도 없음

### 14.7 노트로 반응
- 게시물·릴스 위에 짧은 메모를 남기면 서로 아는 사람에게 보임 ⚠️

---

## 15. 다이렉트 메시지(DM)

DM은 개편 후 하단 탭 가운데에 놓인 **핵심 기능**입니다.

### 15.1 받은 메시지함
| 영역 | 설명 |
|---|---|
| 노트 | 상단 원형 말풍선 (24시간) |
| **인스타그램 지도** ✅ | 상단. 친구의 마지막 활동 위치, 위치 태그된 콘텐츠 |
| 기본 / 일반 | 크리에이터·비즈니스용 분류 |
| 요청 | 팔로우하지 않는 사람의 메시지, 숨겨진 요청 |
| 채널 | 브로드캐스트 채널 |

### 15.2 채팅 기능
| 기능 | 상세 | 상태 |
|---|---|---|
| 기본 메시지 | 텍스트, 사진·영상, 음성, GIF, 스티커, 아바타 | |
| 1회 보기 미디어 | 한 번 보기 / 다시 보기 허용 | |
| 콘텐츠 공유 | 게시물·릴스·스토리·프로필·음악 | ✅ |
| 반응·답장·전달 | 이모지 반응, 답장, 전달 | |
| **수정** | 15분 이내 | ✅ |
| 전송 취소 | — | |
| **메시지 예약** | 전송 버튼을 길게 눌러 최대 29일 후로 예약 | ✅ |
| **번역** | 길게 눌러 번역, 99개 언어 | ✅ |
| **메시지 고정** | 대화방당 최대 3개 | ✅ |
| 채팅방 고정 | 목록 상단 고정 | ✅ |
| 읽음 표시 | 끄기 가능 | ✅ |
| 통화 | 음성·영상, 화면 공유 | |
| 사라지는 메시지 | Vanish mode | |
| **Blend** | 1:1·그룹 채팅에서 함께 보는 맞춤 릴스 피드 (모두 수락해야 시작, 나가면 종료) | ✅ |
| 그룹 채팅 | 이름·사진, 관리자, 초대 링크, 설문 | |
| 맞춤 폰트 | DM·스토리 맞춤 폰트 (Instagram Plus) | ⚠️ |
| **종단간 암호화** | **2026년 5월 8일 종료** (암호화가 필요하면 WhatsApp/Messenger 안내) | ✅ |

### 15.3 인스타그램 지도 ✅ (2025.08)
- 친한 친구나 선택한 팔로워에게 **마지막 활동 위치** 공유 (사용자가 직접 켜야 함)
- 실시간 추적이 아니라 **앱을 열 때만 갱신**
- 지도 위치에 노트 남기기
- 미국 의원들의 개인정보 우려 제기 → Mosseri는 "양쪽이 모두 동의해야 하는 구조"라고 해명
- 10대가 위치 공유를 시작하면 보호자가 알 수 있고, 사용 여부를 제어 가능

### 15.4 브로드캐스트 채널
- 크리에이터 → 팔로워 일대다 메시지, 구독자는 반응·투표만 가능

### 15.5 메시지 제어
- 메시지 요청 허용 범위, 그룹 초대 허용 범위, 숨겨진 단어 필터
- 10대: 팔로우하지 않는 사람의 메시지 차단

---

## 16. 알림

### 16.1 활동 화면
- 팔로우 요청, 새 팔로워, 좋아요, 댓글, 답글, 멘션, 태그, 협업 초대, 리포스트, 라이브 시작
- 시간 그룹: 오늘 / 이번 주 / 이번 달 / 이전
- Instagram Plus: 선택한 스토리 조회 알림 ⚠️

### 16.2 푸시 설정
- 모두 일시 중지 (최대 8시간)
- 카테고리별 on/off: 게시물·스토리·댓글 / 팔로우 / 메시지 / 통화 / 라이브·릴스 / 이메일·SMS
- 계정별 구독: 게시물, 스토리, 릴스, 라이브
- 10대 계정 수면 모드: 야간 알림 무음

---

## 17. 추천 알고리즘과 사용자 제어

### 17.1 기본 원칙 (Instagram Ranking Explained, 2023) ✅
인스타그램은 하나의 알고리즘이 아니라 **피드·스토리·탐색·릴스·검색마다 따로 순위 시스템**을 둡니다.

| 영역 | 성격 | 주요 신호 |
|---|---|---|
| 피드 | 주로 팔로우 계정 | 사용자 활동, 게시물 정보(인기도, 게시 시점), 작성자 정보, 작성자와의 상호작용 이력 |
| 스토리 | 팔로우 계정 | 시청 이력, 참여 이력(반응·답장), 친밀도 |
| 탐색 | 발견(비팔로우 위주) | 게시물 인기도(좋아요·댓글·공유·저장 속도), 탐색 내 활동, 작성자와의 상호작용, 작성자 정보 |
| 릴스 | 발견(비팔로우 위주) | 시청 시간·완료율, 좋아요, **공유(DM 전송)**, 오디오 페이지 이동 등 |

- 인기도 신호는 피드·스토리보다 **탐색에서 훨씬 중요** ✅
- 커뮤니티 가이드라인 위반 콘텐츠는 **순위를 매기기 전에 제거**되고, 일부는 노출 순위가 낮아짐 ✅
- 같은 게시물이라도 릴스와 피드에서 성과가 다를 수 있음 (2025.12 Mosseri) ✅

### 17.2 추천 제외·감점 요인
- 가이드라인 위반, 추천 가이드라인 미충족
- 다른 플랫폼 워터마크, 저품질·재업로드 (원본 콘텐츠 우대)
- 3분 초과 릴스 → 비팔로워 추천 제외 ⚠️
- 라벨 없는 AI 생성 프로필 → 비팔로워 추천 제외 ✅
- 해시태그 남용 → 5개 제한으로 차단 ✅

### 17.3 Your Algorithm ✅
| 시기 | 범위 |
|---|---|
| 2025.12.10 | 릴스 (미국 먼저, 이후 전 세계 영어) |
| 2026.04 | 탐색 ⚠️ |
| 2026.06.10 | 피드·릴스·탐색 **통합 시스템** (주제를 바꾸면 세 영역 모두 반영) |

- AI가 만든 **관심사 요약**을 보여주고, 사용자가 주제를 추가하거나 뺄 수 있음
- 진입: 릴스 오른쪽 상단 아이콘 또는 설정 → 콘텐츠 기본 설정 → Your algorithm
- 관심사를 스토리로 공유 가능
- 한계: 알고리즘 전체를 사용자가 통제하는 것은 아님

### 17.4 기타 사용자 제어
- 관심 없음 / 관심 있음, 추천 콘텐츠 초기화
- 민감한 콘텐츠 설정, 추천 게시물 일시 숨김(최대 30일)
- 팔로잉·즐겨찾기 시간순 피드

### 17.5 일반적인 순위 파이프라인 (기술 참고)
```
후보 생성 (팔로잉, 유사 사용자, 임베딩 근접, 사용자 지정 주제)
 → 1차 순위 (경량 모델)
 → 2차 순위 (좋아요·댓글·공유·저장·시청 시간 확률 예측)
 → 정책 필터·다양성 조정 (같은 작성자 연속 방지, 위반·민감 콘텐츠 제외)
 → 광고 삽입 → 최종 노출
```

---

## 18. 크리에이터 & 비즈니스 도구

### 18.1 인사이트 (전문가 대시보드)
| 지표 | 내용 |
|---|---|
| 조회수(Views) | 노출·재생 통합 지표 (API에도 total views 추가 ✅ 2026.04) |
| 도달 | 고유 계정 수 (팔로워/비팔로워) |
| 참여 | 좋아요, 댓글, 공유, 저장, **리포스트** |
| 릴스 | 평균 시청 시간, 유지율, 건너뛰기율 |
| 팔로워 | 증감, 연령·성별·지역, 활동 시간 |
| 협업 게시물 | 조회수·좋아요·댓글·리포스트·저장·공유 확인 ⚠️ |
| 시험 릴스 | 비팔로워 대상 성과 |

### 18.2 수익화
- **구독**: 월정액, 구독자 전용 콘텐츠·배지·채팅
- **배지**(라이브 후원), **선물**(릴스 후원)
- **브랜드 콘텐츠**: 유료 파트너십 라벨, 크리에이터 마켓플레이스
- 제휴 커미션, 보너스 프로그램 (지역·시기 한정)
- Features 장편 영상: 수익 배분 미발표 ⚠️

### 18.3 제작 도구
- **Edits 앱** ✅: 2025.04 출시, 무료. 멀티 트랙 타임라인, 자동 자막, 배경 교체, 그린스크린, 이미지→영상 AI, 인사이트
  - 2026년 추가: AI 어시스턴트, 텔레프롬프터, 캐러셀 편집(iOS), 최대 15분 내보내기, 폰트·효과 ⚠️
- **Effect Builder** ⚠️: AR 효과 제작
- 예약 게시, Meta Business Suite

### 18.4 협업
- **협업 게시물(Collab)**: 여러 계정이 공동 작성자로 표시, 양쪽 팔로워에게 노출, 지표 통합
- 협업 캐러셀(다른 사람이 사진 추가) ✅

---

## 19. 광고 & 커머스

### 19.1 광고 노출 위치
| 위치 | 형식 |
|---|---|
| 피드 | 이미지, 영상, 캐러셀, 컬렉션 |
| 스토리 | 스토리 사이 전체 화면 |
| 릴스 | 릴스 사이 세로 영상 |
| 탐색 / 검색 결과 / 프로필 피드 | 그리드·피드 |
| **라이브** | 라이브 홍보 ✅ |

- **홍보하기(Boost)**: 앱 안에서 게시물·라이브를 광고로 (목표, 대상, 예산, 기간)
- Meta 광고 관리자: 정교한 캠페인
- "이 광고가 보이는 이유", 광고 숨기기·신고, 광고 주제 설정
- EU 등 일부 지역: 광고 없는 유료 구독
- **Instagram Plus 구독으로는 광고가 사라지지 않음** ⚠️

### 19.2 커머스
- 인스타그램 숍 / 제품 태그 (게시물·릴스·스토리) → 제품 상세 → 외부 웹사이트에서 구매
- 위시리스트, 크리에이터 제휴 태그
- 릴스 "추천 제품"(테스트 ⚠️)

---

## 20. 유료 구독: Meta Verified / Meta One

### 20.1 Meta One ✅ (2026.09.15 출시)
| 요금제 | 가격(미국) | 내용 |
|---|---|---|
| **Instagram Plus** | 월 $3.99 (Meta 요금제 페이지에는 $2.99 표기도 있음 ⚠️) | 단일 앱 기본 요금제 |
| Core | 월 $7.99 | Instagram Plus + Meta AI 이미지·영상 생성 확대 |
| Premium | 월 $19.99 | 상위 요금제 |
| 크리에이터/비즈니스 | $49.99~ ⚠️ | 인증·지원 등 (출처별 상이) |

### 20.2 Instagram Plus 기능 ⚠️
- 프로필 고정 게시물 추가
- **여러 스토리 공개 대상** 만들기
- 소개·DM·스토리 맞춤 폰트, 맞춤 아이콘
- 48시간 스토리
- 선택한 스토리 조회 알림, 추가 조회자 인사이트
- DM 미리보기
- ※ 무료 인스타그램은 그대로 유지되며, 광고도 사라지지 않음

---

## 21. AI 기능과 AI 콘텐츠 정책

### 21.1 AI 기능
| 기능 | 내용 | 상태 |
|---|---|---|
| Meta AI | 검색·DM에서 AI 어시스턴트, 이미지 생성·편집 | ✅ |
| AI 번역/더빙 | 릴스 → 탐색 → 피드로 확대, 한국어 지원 | ✅ / ⚠️ |
| Your Algorithm | AI 관심사 요약 | ✅ |
| Edits AI | 이미지→영상, AI 어시스턴트 | ✅ / ⚠️ |
| AI 스티커, AI 스토리 전환 | 스토리 꾸미기 | ⚠️ |
| AI 나이 추정 | 10대 의심 계정을 10대 계정으로 전환 | ⚠️ |

### 21.2 AI 콘텐츠 라벨 정책
- **AI 정보 라벨**: 게시물 단위. API에도 `is_ai_generated` 파라미터 추가 ✅
- **AI 생성 프로필 라벨** ✅
  - 2026년 5월 자발적 "AI 크리에이터" 라벨로 테스트 → **2026.08.31**에 "AI 생성 프로필"로 이름 변경
  - 대상: 계정에 등장하는 **인물**이 AI로 생성되었거나 상당 부분 AI로 만들어진 경우
  - AI로 사진 편집, 캡션 다듬기, 그래픽 제작만 한 경우는 해당 없음
  - 라벨 없이 걸리면: **릴스·탐색에서 비팔로워 추천 제외** (팔로워에게는 계속 보임)
  - 해결: 프로필 설정에서 라벨 켜기 또는 계정 상태(Account Status)에서 이의 제기
  - 감지 방법은 비공개

---

## 22. 개인정보·보안·안전, 10대 계정

### 22.1 개인정보
- 비공개 계정, 활동 상태 표시 on/off
- 스토리·라이브 숨기기, 태그·멘션·댓글·메시지 허용 범위
- 지도 위치 공유 제어 (기본 꺼짐)
- 리믹스·공유 허용 여부, 연락처 동기화

### 22.2 보안
- 2단계 인증 (인증 앱, SMS, WhatsApp, 보안 키), 패스키
- 로그인 활동(기기·위치 확인, 원격 로그아웃), 로그인 알림
- 인스타그램 발송 이메일 확인
- 해킹 계정 복구 (영상 셀카 인증 등)
- ⚠️ **DM 종단간 암호화는 2026.05.08 종료** → Meta가 DM 내용에 접근할 수 있게 되어 개인정보 단체의 비판 ✅

### 22.3 안전·웰빙
| 기능 | 설명 |
|---|---|
| 숨겨진 단어 | 불쾌한 댓글·메시지 요청 자동 필터 |
| 제한 / 차단 / 숨기기 | 사용자 단위 제어 |
| 제한 모드(Limits) | 일정 기간 비팔로워·최근 팔로워의 댓글·메시지 차단 |
| 민감한 콘텐츠 설정 | 추천 노출 수준 |
| 사용 시간 관리 | 일일 제한, 휴식 알림 |
| 신고 | 게시물·댓글·계정·메시지·광고 |
| 계정 상태 | 가이드라인 위반, 추천 가능 여부, 이의 제기 |

### 22.4 10대 계정 ✅
| 항목 | 내용 |
|---|---|
| 적용 대상 | 만 18세 미만 (AI 나이 추정 ⚠️ 포함) |
| 기본 설정 | 비공개 계정, 팔로우하지 않는 사람의 메시지 차단, 태그·멘션 제한, 숨겨진 단어 켜짐 |
| **13+ 콘텐츠 설정** (2025.10) | PG-13 영화 등급 기준. 성적 암시, 담배·술 판매 외에 **욕설, 위험한 행동, 마리화나 용품 등 유해 행동을 부추기는 콘텐츠**도 제한. 피드·릴스·탐색·스토리, 팔로우 계정 게시물까지 적용 |
| 해제 | 만 16세 미만은 **보호자 동의**가 있어야 완화 가능 |
| **제한 콘텐츠(Limited Content)** | 보호자가 고르는 더 엄격한 옵션. 댓글 보기·작성·받기 불가, AI 대화 제한 예정 |
| 사용 시간 | 1시간 후 알림, **수면 모드**(밤 10시~오전 7시 알림 무음) |
| 보호자 감독 | 대화 상대(내용은 비공개), 사용 시간, 위치 공유 시작 알림 |
| 적용 일정 | 미국·영국·호주·캐나다 먼저(2025.10~), 2026년 전 세계 |

---

## 23. 설정 메뉴 구조

```
설정 및 활동
├── 계정 센터 (Meta 통합: 개인정보, 비밀번호·보안, 광고 설정, 연결된 경험, Meta One/Verified)
├── 인스타그램 사용 방식
│   └── 저장됨, 보관, 내 활동, 알림, 사용 시간
├── 콘텐츠를 볼 수 있는 사람
│   └── 계정 공개 범위, 친한 친구, 차단된 계정, 스토리·라이브·위치 숨기기
├── 나와 소통할 수 있는 방법
│   └── 메시지·스토리 답장, 태그·멘션, 댓글, 공유·리믹스, 제한된 계정, 제한 모드, 숨겨진 단어
├── 내가 보는 콘텐츠
│   └── 즐겨찾기, 숨긴 계정, 콘텐츠 기본 설정(Your algorithm, 민감한 콘텐츠), 좋아요·공유 수
├── 앱 및 미디어
│   └── 기기 권한, 보관·다운로드, 접근성·자막·번역, 언어, 데이터 사용량
├── 가족 센터 (보호자 감독)
├── 전문가용 (인사이트, 광고, 브랜드 콘텐츠, AI 생성 프로필 라벨)
├── 주문·결제 / 구독
├── 추가 정보 및 지원 (도움말, 개인정보 센터, 계정 상태)
└── 로그인 (계정 추가 / 로그아웃)
```

---

## 24. 회원가입·로그인·계정 관리

### 24.1 회원가입
1. 휴대폰 번호 또는 이메일 → 인증 코드
2. 이름, 비밀번호
3. 생년월일 (만 13세 이상, 18세 미만은 10대 계정)
4. 사용자 이름 (고유, 영문·숫자·`.`·`_`, 최대 30자)
5. 약관 동의
6. 프로필 사진, 연락처 동기화, 추천 팔로우 (선택)
- Facebook 계정으로 가입 가능

### 24.2 로그인
- 사용자 이름/이메일/전화번호 + 비밀번호, Facebook 로그인, 저장된 로그인 정보, 패스키
- 비밀번호 재설정: 이메일·SMS 링크

### 24.3 계정 관리
- 사용자 이름 변경 (14일 안에 원래 이름으로 복구 가능)
- 계정 유형 전환 (개인 ↔ 크리에이터 ↔ 비즈니스)
- 내 정보 다운로드
- 일시 비활성화 / 삭제 (30일 유예 후 영구 삭제)

---

## 25. 멀티 플랫폼 & Meta 생태계

| 항목 | 내용 | 상태 |
|---|---|---|
| 모바일 앱 | iOS, Android (메인) | |
| 웹 | instagram.com (그리드 재배치, 댓글 수정 등 일부 기능 미지원) | ⚠️ |
| iPad 앱 | 2025.09 출시, 릴스로 시작, 큰 화면에 맞춘 릴스·스토리·DM | ✅ |
| TV 앱 | 릴스 중심 TV 앱 보도 | ⚠️ 미확인 |
| Edits | 공식 영상 편집 앱 | ✅ |
| Threads | 인스타그램 계정 기반 텍스트 SNS (태그는 게시물당 1개) | ✅ |
| 계정 센터 | Facebook·Messenger·Threads·Horizon 통합 | |
| 교차 게시 | Facebook 동시 게시 | |
| Meta Business Suite | 게시물·메시지·광고·인사이트 통합 관리 | |

---

## 26. 개발자 API (Instagram Platform)

| 항목 | 내용 | 상태 |
|---|---|---|
| 대상 | 비즈니스·크리에이터 계정 (개인 계정 API는 종료) | |
| 로그인 방식 | **Facebook Login**(페이지 연결 필요, `instagram_content_publish`, `instagram_manage_insights`) / **Instagram Login**(`instagram_business_content_publish`, `instagram_business_manage_messages`) | ✅ |
| 콘텐츠 게시 | **2단계**: 미디어 컨테이너 생성 → 게시 | ✅ |
| 게시 옵션 | 유료 파트너십 라벨, `is_ai_generated`(AI 라벨) | ✅ |
| 게시 한도 | 24시간당 50개 또는 100개 (문서 내 표기 상충, `content_publishing_limit`로 확인) | ⚠️ |
| 캐러셀 | API는 최대 10개 | ⚠️ |
| 인사이트 | 조회수(views), 도달, 계정 단위 지표 등 | ✅ |
| 메시지 | Messenger Platform 경유, DM 읽기·보내기 (자동 DM 시간당 제한) | ✅ / ⚠️ |
| 댓글 | 조회, 답글, 숨기기, 삭제 | |
| 기타 | 해시태그 검색, 멘션 조회, 비즈니스 디스커버리, Creator Marketplace API | |

---

## 27. 데이터 모델 (클론 구현 참고)

### 27.1 ERD 개요
```
User ─┬─< Post ─┬─< PostMedia
      │         ├─< PostCollaborator
      │         ├─< Comment ─< Comment(reply)  (edited_at)
      │         ├─< Like
      │         ├─< Repost
      │         ├─< SavedPost >── Collection
      │         ├─< PostTag (사람 태그)
      │         ├─< PostHashtag >── Hashtag   (게시물당 최대 5개)
      │         └── Location / Audio
      ├─< Story ─┬─< StoryView
      │          ├─< StoryComment
      │          ├─< StorySticker
      │          └─< HighlightItem >── Highlight
      ├─< Follow (follower_id, following_id, status, is_favorite)
      ├─< Block / Mute / Restrict / CloseFriend
      ├─< ConversationMember >── Conversation ─< Message ─< MessageReaction
      ├─< Notification
      ├─< Note
      ├─< UserLocation (지도)
      └─< UserInterestTopic (Your Algorithm)
```

### 27.2 주요 엔티티

#### User
| 필드 | 타입 | 설명 |
|---|---|---|
| id | BigInt/UUID | PK |
| username | varchar(30) unique | `[a-z0-9._]` |
| email / phone | varchar unique null | 로그인 식별자 |
| password_hash | varchar | |
| full_name | varchar(64) | |
| bio | varchar(150) | |
| profile_image_url | varchar | |
| links | json | 링크 목록 |
| pronouns | varchar | |
| account_type | enum(personal, creator, business) | |
| category | varchar null | |
| is_private | boolean | |
| is_verified | boolean | |
| is_ai_generated_profile | boolean | AI 생성 프로필 라벨 |
| birth_date | date | 10대 판정 |
| followers_count / following_count / posts_count | int | 비정규화 카운터 |
| created_at / updated_at / deleted_at | timestamp | |

#### Post
| 필드 | 설명 |
|---|---|
| id, user_id | |
| type | enum(photo, video, carousel, reel) |
| caption | text, 2,200자 |
| location_id, audio_id | null 가능 |
| audience | enum(public, close_friends) |
| hide_like_count, comments_disabled | boolean |
| is_ai_labeled | boolean (AI 정보 라벨) |
| is_paid_partnership | boolean |
| is_trial | boolean (시험 릴스: 비팔로워에게만 노출) |
| is_archived | boolean |
| pin_order | int null (프로필 고정 순서) |
| grid_position | int (그리드 재배치용, 기본은 created_at 순) |
| like/comment/share/save/repost/view_count | int |
| duration_sec | 릴스 길이 (180초 초과 시 추천 제외 로직) |
| scheduled_at, created_at, updated_at, deleted_at | |

#### PostMedia
- id, post_id, order, media_type(image/video), url, thumbnail_url, width, height, duration, filter, alt_text

#### Repost
- id, user_id, post_id, note(짧은 메모), created_at — unique(user_id, post_id)

#### Comment
- id, post_id, user_id, parent_id(null=최상위), content, media_url(사진/GIF), like_count, is_pinned, **edited_at**(15분 수정 규칙), created_at, deleted_at

#### Like
- unique(user_id, target_type[post/comment/story], target_id), created_at

#### Follow
- follower_id, following_id, status(accepted/pending), is_favorite, created_at — PK(follower_id, following_id)

#### Story / StoryView / StoryComment
- Story: id, user_id, media_url, media_type, duration, audience(all/close_friends/custom_list_id), stickers(json), comments_enabled, expires_at(=created_at+24h), created_at
- StoryView: story_id, viewer_id, viewed_at, liked
- StoryComment: id, story_id, user_id, content, created_at

#### Highlight / HighlightItem
- Highlight: id, user_id, title, cover_url, order
- HighlightItem: highlight_id, story_id, order

#### Hashtag / PostHashtag
- Hashtag: id, name(unique), post_count
- PostHashtag: post_id, hashtag_id — **서버에서 게시물당 5개 제한 검증**

#### Conversation / Member / Message
| 엔티티 | 필드 |
|---|---|
| Conversation | id, type(direct/group/broadcast), title, image_url, blend_enabled, created_at |
| ConversationMember | conversation_id, user_id, role, folder(primary/general/request), last_read_message_id, muted, pinned |
| Message | id, conversation_id, sender_id, type(text/image/video/voice/post_share/story_reply/music), content, media_url, shared_post_id, reply_to_id, view_once, **scheduled_at**(≤29일), is_pinned, edited_at, unsent_at, created_at |
| MessageReaction | message_id, user_id, emoji |

#### Notification
- id, recipient_id, actor_id, type(like/comment/reply/follow/follow_request/mention/tag/collab_invite/repost/live), target_type, target_id, is_read, created_at

#### 기타
- Block(blocker_id, blocked_id) · Mute(user_id, muted_user_id, posts, stories, notes) · Restrict · CloseFriend
- Note: id, user_id, text(60), audio_id, audience, expires_at
- UserLocation: user_id, lat, lng, updated_at, share_audience(close_friends/selected/off)
- SharedLocationAudience: user_id, viewer_id (지도 공개 대상)
- UserInterestTopic: user_id, topic, weight, source(inferred/manual), muted
- Report: id, reporter_id, target_type, target_id, reason, status

---

## 28. 라우트·API 설계 예시

### 28.1 웹 라우트 (instagram.com 구조 참고)
| 경로 | 화면 |
|---|---|
| `/` | 홈 피드 |
| `/explore/` | 탐색 |
| `/reels/` | 릴스 |
| `/direct/inbox/`, `/direct/t/{id}/` | DM 목록 / 채팅방 |
| `/{username}/` | 프로필 |
| `/{username}/reels/`, `/reposts/`, `/tagged/`, `/saved/` | 프로필 탭 |
| `/p/{shortcode}/` | 게시물 상세 |
| `/reel/{shortcode}/` | 릴스 상세 |
| `/stories/{username}/{id}/` | 스토리 뷰어 |
| `/stories/highlights/{id}/` | 하이라이트 |
| `/explore/tags/{tag}/`, `/explore/locations/{id}/` | 해시태그 / 장소 |
| `/accounts/edit/`, `/accounts/login/`, `/accounts/emailsignup/` | 계정 |

### 28.2 REST API 예시
```
# 인증
POST   /api/auth/signup | login | logout | password/reset

# 사용자·관계
GET    /api/users/me              PATCH /api/users/me
GET    /api/users/{username}
GET    /api/users/{id}/followers?cursor=   GET /api/users/{id}/following?cursor=
POST   /api/users/{id}/follow     DELETE /api/users/{id}/follow
POST   /api/follow-requests/{id}/accept | reject
POST   /api/users/{id}/block | mute | restrict
GET    /api/users/search?q=

# 게시물
GET    /api/feed?cursor=&mode=home|following|favorites
POST   /api/posts                 # presigned 업로드 후 메타데이터 등록
GET|PATCH|DELETE /api/posts/{id}
POST   /api/posts/{id}/archive
PUT    /api/users/me/grid-order   # 그리드 재배치
POST|DELETE /api/posts/{id}/like
POST|DELETE /api/posts/{id}/repost
POST   /api/posts/{id}/save

# 댓글
GET    /api/posts/{id}/comments?cursor=
POST   /api/posts/{id}/comments
PATCH  /api/comments/{id}         # 15분 이내만 허용
DELETE /api/comments/{id}
POST   /api/comments/{id}/like

# 스토리·하이라이트
GET    /api/stories/tray
POST   /api/stories
POST   /api/stories/{id}/view     GET /api/stories/{id}/viewers
POST   /api/stories/{id}/comments
GET|POST /api/highlights

# 릴스·탐색·검색·추천
GET    /api/reels?cursor=&tab=recommended|friends
GET    /api/explore?cursor=
GET    /api/search?q=&type=account|tag|place|audio
GET|PATCH /api/me/interests       # Your Algorithm

# DM
GET    /api/conversations
POST   /api/conversations
GET    /api/conversations/{id}/messages?cursor=
POST   /api/conversations/{id}/messages   # scheduled_at 선택
PATCH  /api/messages/{id}                 # 15분 이내 수정
WS     /ws                                # 실시간 메시지, 입력 중, 읽음

# 알림·지도
GET    /api/notifications?cursor=   POST /api/notifications/read
GET    /api/map/friends             PUT  /api/me/location
```
- 페이지네이션: **커서 기반**(created_at + id)
- 미디어 업로드: presigned URL → 객체 스토리지 → 비동기 처리(리사이징·트랜스코딩)

---

## 29. 기술 아키텍처 참고

### 29.1 인스타그램 (공개 자료 기준)
- 백엔드: Python/**Django** 대규모 커스터마이징, C++ 서비스
- 데이터: PostgreSQL(샤딩), Cassandra, Memcache, Redis
- 미디어: Meta CDN, 다중 해상도, 적응형 영상 스트리밍
- 클라이언트: iOS(Swift/Obj-C), Android(Kotlin/Java), 웹(React)

### 29.2 핵심 기술 과제
| 과제 | 접근 |
|---|---|
| 피드 생성 | 일반 사용자는 쓰기 시점 팬아웃(push), 팔로워가 많은 계정은 읽기 시점(pull)으로 섞는 방식 |
| 미디어 처리 | 업로드 후 비동기 리사이징·압축·썸네일·트랜스코딩 |
| 카운터 | 비정규화 + 캐시, 주기적 정합성 보정 |
| 스토리 만료 | expires_at 인덱스 + TTL, 아카이브로 이동 |
| 실시간 DM | WebSocket/MQTT, 읽음 동기화, 예약 메시지 스케줄러 |
| 검색 | Elasticsearch 등, 사용자 이름 접두어 검색 |
| 알림 | 이벤트 큐 → 묶음 처리("A님 외 10명") → APNs/FCM |
| 추천 | 임베딩 후보 생성 + ML 순위 + 주제 가중치(Your Algorithm) |
| 지도 | 위치 업데이트는 앱 실행 시에만, 공개 대상 ACL, 대략적 위치 처리 |

### 29.3 클론 추천 스택 (예시)
- 프론트: Next.js + TypeScript + Tailwind CSS
- 백엔드: Next.js Route Handlers / NestJS / Django REST
- DB: PostgreSQL + Prisma/Drizzle
- 캐시·큐: Redis (BullMQ)
- 스토리지: S3 호환 + CDN
- 실시간: WebSocket(Socket.IO) 또는 Supabase Realtime
- 인증: 세션/JWT + OAuth

---

## 30. 클론 MVP 로드맵

### Phase 1 — 핵심
- [ ] 회원가입·로그인
- [ ] 프로필 조회·편집
- [ ] 게시물 작성 (최대 20장 캐러셀, 캡션, 해시태그 5개 제한)
- [ ] 홈 피드 (팔로잉, 무한 스크롤)
- [ ] 팔로우/언팔로우, 목록
- [ ] 좋아요(더블탭), 댓글
- [ ] 프로필 그리드(3:4), 게시물 상세

### Phase 2 — 소셜
- [ ] 저장·컬렉션, 답글, 댓글 좋아요, 댓글 수정(15분)
- [ ] @멘션, #해시태그, 사람 태그, 위치
- [ ] 검색, 알림
- [ ] 비공개 계정·팔로우 요청
- [ ] 리포스트

### Phase 3 — 휘발성 & 실시간
- [ ] 스토리 (24시간, 뷰어, 조회자, 스토리 댓글)
- [ ] 하이라이트, 아카이브
- [ ] DM (1:1, 그룹, 실시간, 읽음, 수정, 고정, 예약)
- [ ] 하단 탭: 홈 / 릴스 / DM / 검색 / 프로필

### Phase 4 — 고급
- [ ] 릴스 피드 (추천/친구 탭, 자동재생)
- [ ] 탐색 그리드, 간단한 추천 알고리즘
- [ ] 관심 주제 설정 (Your Algorithm)
- [ ] 필터·편집
- [ ] 친한 친구, 숨기기, 차단, 제한
- [ ] 노트, 협업 게시물, 인사이트
- [ ] Blend, 지도 (선택)

---

## 31. 분석 요약: 인스타그램의 방향성

1. **사진 앱 → 영상·메시지 플랫폼**: 하단 탭에서 만들기 버튼을 빼고 릴스와 DM을 넣음. iPad 앱도 릴스로 시작
2. **팔로우 그래프 → 추천 그래프**: 탐색·릴스는 비팔로우 콘텐츠 중심이고, 피드에도 추천과 리포스트가 늘어남
3. **알고리즘 투명성과 사용자 제어**: Ranking Explained(2023) → Your Algorithm(2025~2026)
4. **친구 간 소통 강화**: 리포스트, 친구 탭, Blend, 지도, 노트, 스토리 댓글
5. **스팸·조작 억제**: 해시태그 5개, 3분 초과 릴스 추천 제외, AI 생성 프로필 라벨 의무화
6. **안전 규제 대응**: 10대 계정, PG-13 기준, 보호자 감독. 반면 DM 암호화 종료로 개인정보 논쟁
7. **수익 다각화**: 광고(라이브 홍보 포함) + 소비자 구독(Meta One / Instagram Plus) + 크리에이터 수익화
8. **크리에이터 도구 분리**: Edits 앱, 시험 릴스, 장편 영상(Features) 테스트

---

## 참고 자료

**공식 (Instagram / Meta)**
- [Instagram Ranking Explained](https://about.instagram.com/blog/announcements/instagram-ranking-explained)
- [Introducing a new way to see and control your algorithm](https://about.instagram.com/blog/announcements/reels-algorithm-control)
- [New Ways to Stay Close to Your Crew in IG DMs](https://about.instagram.com/blog/announcements/new-instagram-dm-update-2025)
- [New Instagram DM Features to Help You Stay Connected (Meta Newsroom)](https://about.fb.com/news/2025/02/new-instagram-dm-features-stay-connected/)
- [Introducing Meta One (Meta Newsroom)](https://about.fb.com/news/2026/09/introducing-meta-one-subscription-service-more-features-ai/)
- [Meta One Plans](https://www.meta.com/meta-one-plans/)
- [Instagram Platform Changelog](https://developers.facebook.com/documentation/instagram-platform/changelog)
- [Instagram API updates for partnerships, metrics, collaboration (2026.04)](https://developers.facebook.com/blog/post/2026/04/22/instagram-api-updates-for-partnerships-metrics-collaboration-and-engagement/)

**주요 매체**
- [Instagram reaches 3 billion monthly users — Engadget](https://www.engadget.com/social-media/instagram-reaches-3-billion-monthly-users-160554420.html)
- [Instagram hits 3 billion users — Bloomberg](https://www.bloomberg.com/news/articles/2025-09-24/instagram-hits-3-billion-users-with-focus-on-video-messaging)
- [Instagram's redesign reflects the era of video and DMs — RMIT](https://www.rmit.edu.au/news/media-releases-and-expert-comments/2025/nov/instagram-updates)
- [Instagram's new 'Your Algorithm' tool — TechCrunch](https://techcrunch.com/2025/12/10/instagrams-new-your-algorithm-tool-gives-you-more-control-over-the-reels-you-see)
- [Instagram upgrades DMs — TechCrunch](https://techcrunch.com/2025/02/19/instagram-upgrades-dms-with-music-sharing-message-scheduling-translation-and-more/)
- [Instagram puts new limits on undisclosed AI profiles — TechCrunch](https://techcrunch.com/2026/08/31/instagram-puts-new-limits-on-undisclosed-ai-profiles/)
- [Meta is killing E2EE in Instagram DMs — Engadget](https://engadget.com/social-media/meta-is-killing-end-to-end-encryption-in-instagram-dms-195207421.html)
- [Instagram Now Lets You Edit Comments — MacRumors](https://www.macrumors.com/2026/04/09/instagram-edit-comments/)
- [Instagram Announces 'Blend' — MacRumors](https://www.macrumors.com/2025/04/17/instagram-blend-shared-reels)
- [Instagram Trial Reels — MacRumors](https://www.macrumors.com/2024/12/10/instagram-trial-reels/)
- [Instagram Edits launch — 9to5Mac](https://9to5mac.com/2025/04/22/instagram-edits-launch-app-store/)
- [Instagram announces PG-13 content standards — ABC7](https://abc7.com/post/instagram-announces-new-pg-13-content-standards-teen-users/18004679/)
- [Instagram implements new limits on hashtag use — Social Media Today](https://www.socialmediatoday.com/news/instagram-implements-new-limits-on-hashtag-use/808309/)
- [Instagram adds option to boost livestreams — Social Media Today](https://www.socialmediatoday.com/news/instagram-adds-option-to-boost-livestreams/831842/)
- [Instagram expands carousels to 20 frames — Social Media Today](https://www.socialmediatoday.com/news/instagram-expands-carousels-to-20-frames/723792/)
- [Instagram launches reposts and location sharing — PPC Land](https://ppc.land/instagram-launches-reposts-feature-and-location-sharing-for-users/)

**2차 자료 (⚠️ 표시 항목의 근거, 교차 확인 권장)**
- [Instagram Updates & News 2026 — Metricool](https://metricool.com/instagram-news/)
- [The biggest Instagram updates from 2026 — HeyOrca](https://www.heyorca.com/blog/instagram-social-news)
- [The New Instagram Tabs Layout — Inro](https://www.inro.social/blog/instagram-tabs-new-layout-2025)
- [How to Reorder Your Instagram Grid — Inro](https://www.inro.social/blog/reorder-instagram-grid)
- [Instagram's New Grid Layout — Kapwing](https://www.kapwing.com/resources/instagrams-new-grid-layout-size-and-dimensions-2025/)
- [Instagram Features long videos — Upgrow](https://www.upgrow.com/blog/instagram-features-long-videos)
- [How Long Can Instagram Reels Be — ezUGC](https://www.ezugc.ai/blog/how-long-can-instagram-reels-be)
- [Meta One Subscription — Metricool](https://metricool.com/meta-one-subscription/)
- [Instagram API in 2026 — Phyllo](https://www.getphyllo.com/post/instagram-api-guide)

---

*최종 업데이트: 2026-11-19*
