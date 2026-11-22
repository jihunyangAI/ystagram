import { defineConfig, devices } from '@playwright/test'

/**
 * E2E (기획서 §16.2 데모 시나리오 + §16.3 비기능 기준).
 * 실행 중인 개발 서버(백엔드 8080 + 화면 5173, medium 시드)에 붙어서 돈다: `npm run e2e`.
 * - 가상 시계·가중치처럼 전체에 걸리는 상태를 바꾸므로 한 번에 하나씩(workers 1) 돌리고, 끝나면 되돌린다.
 * - 브라우저는 새로 받지 않고 PC에 설치된 Chrome을 쓴다.
 */
export default defineConfig({
  testDir: './e2e',
  testIgnore: '**/.tmp/**',
  timeout: 90_000,
  expect: { timeout: 10_000 },
  workers: 1,
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    ...devices['Pixel 7'],
    viewport: { width: 390, height: 844 },
    channel: 'chrome',
    // 라이브·통화 시험용: 실제 카메라 대신 Chrome의 가짜 카메라·마이크 (권한 창 없이)
    launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] },
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    // 버튼을 못 찾으면 테스트 시간(90초)을 다 쓰지 않고 빨리 실패
    actionTimeout: 15_000,
    screenshot: 'only-on-failure',
  },
  globalTeardown: './e2e/teardown.ts',
})
