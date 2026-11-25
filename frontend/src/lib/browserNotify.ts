/**
 * 브라우저 알림 (기획서 ⚪ "브라우저 푸시" 흉내): 푸시 서버 없이, 이 탭이 열려 있지만 안 보일 때(다른 탭·창)
 * 브라우저의 알림 창으로 알려 준다. 설정 → 알림에서 이 기기만 켜고 끈다.
 */
const KEY = 'browserNotify'

export function browserNotifyEnabled(): boolean {
  try {
    return localStorage.getItem(KEY) === '1' && typeof Notification !== 'undefined' && Notification.permission === 'granted'
  } catch {
    return false
  }
}

export async function setBrowserNotify(on: boolean): Promise<boolean> {
  if (on) {
    if (typeof Notification === 'undefined') return false
    const p = Notification.permission === 'granted' ? 'granted' : await Notification.requestPermission()
    if (p !== 'granted') return false
  }
  try {
    localStorage.setItem(KEY, on ? '1' : '0')
  } catch {
    /* 저장 못 하면 이번 화면에서만 */
  }
  return on
}

export function browserNotify(title: string, body: string) {
  if (!browserNotifyEnabled() || document.visibilityState === 'visible') return
  try {
    new Notification(title, { body, icon: '/favicon.svg', tag: 'yangstagram' })
  } catch {
    /* 알림을 못 띄우는 환경은 무시 */
  }
}
