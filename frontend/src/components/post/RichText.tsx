import { Fragment } from 'react'
import { Link } from 'react-router'

/** #해시태그 · @사용자 를 찾는 패턴 (백엔드 Hashtags와 같은 규칙) */
const TOKEN = /([#@])([\p{L}\p{N}_.]+)/gu

/**
 * 캡션·댓글 본문. @사용자는 프로필 링크, #해시태그는 강조 표시
 * (해시태그 페이지는 M2 검색 단계에서 연결).
 */
export function RichText({ text }: { text: string }) {
  const parts: React.ReactNode[] = []
  let last = 0
  for (const m of text.matchAll(TOKEN)) {
    const start = m.index ?? 0
    if (start > last) parts.push(text.slice(last, start))
    const [whole, sign, word] = m
    if (sign === '@') {
      const username = word.replace(/\.+$/, '')
      parts.push(
        <Link key={start} to={`/${username.toLowerCase()}`} className="whitespace-nowrap text-primary hover:underline">
          @{username}
        </Link>,
      )
      if (username.length < word.length) parts.push(word.slice(username.length))
    } else {
      // 해시태그에는 마침표가 들어가지 않는다
      const tag = word.split('.')[0]
      parts.push(
        <span key={start} className="whitespace-nowrap text-primary">
          #{tag}
        </span>,
      )
      if (tag.length < word.length) parts.push(word.slice(tag.length))
    }
    last = start + whole.length
  }
  if (last < text.length) parts.push(text.slice(last))
  return <>{parts.map((p, i) => (typeof p === 'string' ? <Fragment key={`t${i}`}>{p}</Fragment> : p))}</>
}
