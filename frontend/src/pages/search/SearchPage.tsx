import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Hash, MapPin, Search, X } from 'lucide-react'
import { Avatar } from '../../components/Avatar'
import { UserBadges } from '../../components/UserBadges'
import { formatCount } from '../../lib/format'
import { useRecentSearchActions, useRecentSearches, useSearch } from '../../lib/social'
import type { PlaceHit, RecentKind, TagHit, UserHit } from '../../lib/types'

/** 짧게 기다렸다가 검색 (글자마다 요청하지 않도록) */
function useDebounced(value: string, ms = 200) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

/**
 * 검색 (분석 문서 §11): 계정·태그·장소 통합 결과, 비어 있으면 최근 검색.
 * #으로 시작하면 태그만, @로 시작하면 계정만 찾는다. 탐색 그리드는 M3.
 */
export function SearchPage() {
  const [q, setQ] = useState('')
  const term = useDebounced(q)
  const search = useSearch(term)
  const recent = useRecentSearches()
  const actions = useRecentSearchActions()
  const navigate = useNavigate()

  const go = (kind: RecentKind, value: string, to: string) => {
    void actions.add(kind, value)
    navigate(to)
  }

  const r = search.data
  const nothing = r && r.users.length + r.tags.length + r.places.length === 0

  return (
    <div className="mx-auto max-w-[600px] pb-8">
      <h1 className="hidden px-4 pt-8 pb-4 text-2xl font-bold md:block">검색</h1>
      <div className="sticky top-0 z-10 bg-bg px-4 py-3">
        <label className="flex items-center gap-2 rounded-lg bg-surface-2 px-3 py-2">
          <Search size={18} className="text-muted" />
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="검색"
            aria-label="검색어"
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted"
          />
          {q && (
            <button aria-label="지우기" onClick={() => setQ('')} className="rounded-full bg-muted p-0.5 text-bg">
              <X size={12} />
            </button>
          )}
        </label>
      </div>

      {!term.trim() ? (
        <section>
          <div className="flex items-center justify-between px-4 pt-2 pb-2">
            <h2 className="font-bold">최근 검색 항목</h2>
            {!!recent.data?.length && (
              <button className="text-sm font-semibold text-primary" onClick={() => void actions.clear()}>
                모두 지우기
              </button>
            )}
          </div>
          {recent.data?.length === 0 && <p className="py-12 text-center text-sm text-muted">최근 검색 내역 없음.</p>}
          <ul>
            {recent.data?.map((item) => (
              <li key={`${item.kind}:${item.term}`} className="flex items-center">
                <div className="min-w-0 flex-1">
                  {item.user && <UserResult hit={item.user} onClick={() => go('USER', item.term, `/${item.user!.user.username}`)} />}
                  {item.tag && <TagResult tag={item.tag} onClick={() => go('TAG', item.term, `/explore/tags/${item.tag!.name}`)} />}
                  {item.place && (
                    <PlaceResult place={item.place} onClick={() => go('PLACE', item.term, `/explore/locations/${item.place!.id}`)} />
                  )}
                </div>
                <button aria-label="검색 기록 삭제" className="p-3 text-muted" onClick={() => void actions.remove(item.kind, item.term)}>
                  <X size={18} />
                </button>
              </li>
            ))}
          </ul>
          <p className="mt-10 px-6 text-center text-xs text-muted">탐색 그리드(추천 게시물·릴스)는 M3 단계에서 추가됩니다.</p>
        </section>
      ) : (
        <section aria-live="polite">
          {search.isLoading && <p className="p-6 text-center text-sm text-muted">검색 중…</p>}
          {nothing && <p className="p-10 text-center text-sm text-muted">결과를 찾을 수 없습니다.</p>}
          {r?.users.map((u) => (
            <UserResult key={u.user.id} hit={u} onClick={() => go('USER', u.user.username, `/${u.user.username}`)} />
          ))}
          {r?.tags.map((t) => (
            <TagResult key={t.name} tag={t} onClick={() => go('TAG', t.name, `/explore/tags/${t.name}`)} />
          ))}
          {r?.places.map((p) => (
            <PlaceResult key={p.id} place={p} onClick={() => go('PLACE', String(p.id), `/explore/locations/${p.id}`)} />
          ))}
        </section>
      )}
    </div>
  )
}

const rowClass = 'flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-2'

function UserResult({ hit, onClick }: { hit: UserHit; onClick: () => void }) {
  const sub =
    hit.relationship === 'FOLLOWING'
      ? '팔로잉'
      : hit.followsMe
        ? '회원님을 팔로우합니다'
        : `팔로워 ${formatCount(hit.followersCount)}명`
  return (
    <button className={rowClass} onClick={onClick}>
      <Avatar user={hit.user} size={44} />
      <span className="min-w-0 flex-1 text-sm leading-tight">
        <span className="flex items-center gap-1 font-semibold">
          <span className="truncate">{hit.user.username}</span>
          <UserBadges user={{ verified: hit.user.verified, isPrivate: false }} size={13} />
        </span>
        <span className="block truncate text-muted">
          {hit.user.fullName} · {sub}
        </span>
      </span>
    </button>
  )
}

function TagResult({ tag, onClick }: { tag: TagHit; onClick: () => void }) {
  return (
    <button className={rowClass} onClick={onClick}>
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-border">
        <Hash size={20} />
      </span>
      <span className="min-w-0 flex-1 text-sm leading-tight">
        <b className="block truncate">#{tag.name}</b>
        <span className="text-muted">게시물 {formatCount(tag.postCount)}개</span>
      </span>
    </button>
  )
}

function PlaceResult({ place, onClick }: { place: PlaceHit; onClick: () => void }) {
  return (
    <button className={rowClass} onClick={onClick}>
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-border">
        <MapPin size={20} />
      </span>
      <span className="min-w-0 flex-1 text-sm leading-tight">
        <b className="block truncate">{place.name}</b>
        <span className="text-muted">
          {place.city ? `${place.city} · ` : ''}게시물 {formatCount(place.postCount)}개
        </span>
      </span>
    </button>
  )
}

/** 해시태그·장소 페이지 공통 머리 */
export function TopicHeader({ icon, title, subtitle }: { icon: React.ReactNode; title: string; subtitle: string }) {
  return (
    <header className="flex items-center gap-5 px-4 py-6 md:gap-10 md:py-10">
      <span className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full border border-border bg-surface-2 md:h-36 md:w-36">
        {icon}
      </span>
      <div>
        <h1 className="text-xl font-bold md:text-2xl">{title}</h1>
        <p className="mt-1 text-sm text-muted">{subtitle}</p>
        <Link to="/explore/search" className="mt-3 inline-block text-sm font-semibold text-primary">
          다른 검색
        </Link>
      </div>
    </header>
  )
}
