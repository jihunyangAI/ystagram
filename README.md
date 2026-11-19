# Yangstagram

인스타그램의 구조와 기능을 참고해 만드는 **연구용** 사진·영상 SNS입니다.
실제 서비스로 운영하지 않으며, **모든 사용자와 콘텐츠는 가짜 데이터**입니다.

- 기획 문서: [yangstagram.md](yangstagram.md)
- 인스타그램 분석 문서: [reference/instagram.md](reference/instagram.md)
- 현재 단계: **M0 (기반)** 완료

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
# 1) 백엔드 (http://localhost:8080) — PostgreSQL 컨테이너를 자동으로 띄우고,
#    DB가 비어 있으면 가짜 데이터(사용자 50명)를 만든다
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
- `/lab`: 데이터 현황, 가짜 데이터 다시 만들기 (규모·시드값 선택)

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
