// 화면 테마: 시스템 설정 / 라이트 / 다크. 이 브라우저에만 저장한다.

export type Theme = 'system' | 'light' | 'dark'

const KEY = 'yangstagram:theme'

export function getTheme(): Theme {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

export function setTheme(theme: Theme) {
  try {
    if (theme === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, theme)
  } catch {
    /* 저장이 막혀 있어도 이번 화면에는 적용 */
  }
  if (theme === 'system') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = theme
}
