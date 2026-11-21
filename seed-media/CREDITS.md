# 가짜 데이터 사진 출처

## 게시물·스토리 사진 (`photos/`)

`node seed-media/download.mjs`가 [Openverse](https://openverse.org)(WordPress 재단의 공개 라이선스 이미지 검색) API로
주제별 사진을 받는다. API 키는 필요 없다(키 없이 1분 20회, 하루 200회).

- 출처: [StockSnap](https://stocksnap.io), [rawpixel](https://www.rawpixel.com)의 사진만
- 라이선스: **CC0 / 퍼블릭 도메인만** → 출처 표시 없이 자유롭게 쓸 수 있다
- rawpixel의 박물관 소장품(옛 그림·포스터·판화)은 거른다 (스톡 사진 제목 규칙)
- 주제 20개 × 찍은 대상 102개(버거·라떼·댕댕이 등), 약 1,450장. 사진마다 대상을 기록해 두고 캡션을 사진에 맞춘다
- 사진별 제목·작가·원본 주소는 `photos/credits.json`에 있다 (사진과 함께 Git 제외)

연구용 데모의 가짜 게시물에만 쓰며, 사진 속 인물·장소는 가짜 사용자와 관계가 없다.

## 릴스 영상 (`reels/`)

`gradlew reelClips`가 위 사진 중 세로 사진에 확대·이동 효과와 주제별 글자를 얹어 만든다 (외부 영상 없음).
