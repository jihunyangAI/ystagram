# Yangstagram

인스타그램의 구조와 기능을 참고해 만드는 **연구용** 사진·영상 SNS입니다.
실제 서비스로 운영하지 않으며, **모든 사용자와 콘텐츠는 가짜 데이터**입니다.

- 기획 문서: [yangstagram.md](yangstagram.md)
- 인스타그램 분석 문서: [reference/instagram.md](reference/instagram.md)
- 현재 단계: **M2 (살 붙이기 ①)** 완료 — 스토리·DM(실시간)·알림·검색·팔로우 요청·답글·저장·리포스트·시간 가속
  (M1: 피드·게시물 작성·좋아요·댓글·팔로우·프로필)

## 구성

| 폴더 | 내용 | 기술 |
|---|---|---|
| `backend/` | API 서버 | Java 25, Spring Boot 4.1, Spring Security, JPA, Flyway |
| `frontend/` | 화면 | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query |
| `compose.yaml` | 개발용 DB | PostgreSQL 18 (포트 **5433**) |

## 준비물

- JDK 25, Node.js 24, Docker Desktop (실행 중이어야 함)

## 실행

터미널 두 개에서 각각 실행합니다.

```powershell
# 0) 가짜 게시물용 사진 300장 받기 (처음 한 번, 약 56MB, 무료 라이선스)
#    건너뛰면 사진 대신 그라데이션 그림이 나온다
node seed-media/download.mjs

# 1) 백엔드 (http://localhost:8080) — PostgreSQL 컨테이너를 자동으로 띄우고,
#    DB가 비어 있으면 가짜 데이터(medium: 사용자 200명, 게시물 약 2,200개, 약 20초)를 만든다
cd backend
.\gradlew bootRun

# 2) 화면 (http://localhost:5173)
cd frontend
npm install   # 처음 한 번만
npm run dev
```

브라우저에서 http://localhost:5173 을 열고 **"가짜 계정으로 바로 둘러보기"** 를 누르면 됩니다.

- 가짜 계정 비밀번호: `yang1234` (모든 가짜 계정 공통)
- 오른쪽 아래 🧪 버튼: 어느 화면에서든 다른 가짜 계정으로 전환
- `/lab`: 데이터 현황, **시간 가속**(스토리 24시간 만료·15분 수정 제한 확인), 가짜 데이터 다시 만들기 (규모·시드값 선택)
- DM 실시간 확인: 브라우저 두 개(또는 일반 창 + 시크릿 창)에서 서로 다른 가짜 계정으로 로그인해 대화하면 입력 중·읽음 표시가 바로 보입니다.

## 테스트

```powershell
cd backend
.\gradlew test        # Docker가 필요 (테스트용 PostgreSQL을 따로 띄움)

cd frontend
npx tsc -b            # 타입 검사
```

## 알아 둘 것

- 5432 포트는 다른 프로젝트(yangpang)가 쓰고 있어서 Yangstagram DB는 **5433**을 씁니다.
- 백엔드를 꺼도 DB 컨테이너는 계속 켜져 있습니다. 끄려면 `docker compose down` (데이터 유지), 데이터까지 지우려면 `docker compose down -v`.
- 이 PC의 Windows 보안 정책(Application Control)이 서명 없는 네이티브 모듈을 막아서 `npm run lint`(oxlint)는 실행되지 않습니다. 타입 검사(`npx tsc -b`)와 빌드는 정상입니다.
- 사진을 새로 받았거나, 기존 DB에 게시물·스토리·DM이 없으면(이전 단계에서 만든 DB) `/lab` → "가짜 데이터 다시 만들기"를 누르세요.
- 백엔드 라이브러리가 바뀐 뒤(예: M2의 WebSocket)에는 `gradlew bootRun`을 껐다 다시 켜야 합니다. 코드만 바뀌면 자동으로 다시 시작됩니다.
- 직접 올린 사진은 `storage/uploads/`에 저장됩니다 (Git 제외).
