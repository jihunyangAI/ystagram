// 테스트가 중간에 멈춰도 가상 시계는 실제 시간으로 되돌린다
import { resetClock } from './helpers'

export default async function teardown() {
  await resetClock().catch(() => undefined)
}
