import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// 화면 단위 테스트 (npm test). E2E(Playwright)는 e2e/ 폴더, 따로 npm run e2e.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test/setup.ts'],
  },
})
