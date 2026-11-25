import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellOff, ChevronLeft, FileClock, Moon, Timer } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import type { CommentView, PostView, ScheduledPost } from '../../lib/types'
import { Toggle } from '../../components/post/CaptionField'
import { Modal } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { timeAgo } from '../../lib/format'
import { Avatar } from '../../components/Avatar'
import { browserNotifyEnabled, setBrowserNotify } from '../../lib/browserNotify'

export interface UserSettings {
  hideOffensive: boolean
  hiddenWords: string[]
  commentAudience: 'EVERYONE' | 'FOLLOWING' | 'FOLLOWERS' | 'BOTH' | 'OFF'
  notifOff: string[]
  notifPausedUntil: string | null
  sensitiveLevel: 'LESS' | 'STANDARD' | 'MORE'
  sensitiveLocked: boolean
  dailyLimitMin: number | null
  sleepOn: boolean
  sleepStart: number
  sleepEnd: number
  sleepingNow: boolean
}

export function useUserSettings() {
  return useQuery({ queryKey: ['user-settings'], queryFn: () => api.get<UserSettings>('/api/me/settings') })
}

function useSaveSettings() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (patch: Record<string, unknown>) => api.patch<UserSettings>('/api/me/settings', patch),
    // 누르자마자 화면에 반영하고(실패하면 되돌림), 서버 응답으로 맞춘다
    onMutate: (patch) => {
      const before = qc.getQueryData<UserSettings>(['user-settings'])
      const known = Object.fromEntries(Object.entries(patch).filter(([k]) => before && k in before))
      if (before) qc.setQueryData(['user-settings'], { ...before, ...known })
      return { before }
    },
    onSuccess: (s) => qc.setQueryData(['user-settings'], s),
    onError: (e, _p, ctx) => {
      if (ctx?.before) qc.setQueryData(['user-settings'], ctx.before)
      toast(e.message)
    },
  })
}

function Page({ title, children }: { title: string; children: React.ReactNode }) {
  const navigate = useNavigate()
  return (
    <div className="mx-auto max-w-[600px] pb-10">
      <header className="flex items-center gap-2 px-2 pt-4 pb-3 md:pt-8">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
          <ChevronLeft size={26} />
        </button>
        <h1 className="text-lg font-bold">{title}</h1>
      </header>
      <div className="space-y-5 px-4">{children}</div>
    </div>
  )
}

function Choice<T extends string>({ name, value, options, onChange, disabled }: {
  name: string
  value: T
  options: [T, string, string?][]
  onChange: (v: T) => void
  disabled?: boolean
}) {
  return (
    <div role="radiogroup" aria-label={name} className="overflow-hidden rounded-lg border border-border">
      {options.map(([v, label, hint]) => (
        <label key={v} className={clsx('flex cursor-pointer items-start gap-3 border-b border-border px-3 py-2.5 last:border-b-0', disabled && 'opacity-60')}>
          <input type="radio" name={name} checked={value === v} disabled={disabled} onChange={() => onChange(v)} className="mt-1 accent-[var(--color-primary)]" />
          <span>
            <span className="block text-sm">{label}</span>
            {hint && <span className="block text-xs text-muted">{hint}</span>}
          </span>
        </label>
      ))}
    </div>
  )
}

// ------------------------------------------------------------ 숨겨진 단어

export function HiddenWordsPage() {
  const { data } = useUserSettings()
  const save = useSaveSettings()
  const [draft, setDraft] = useState('')
  useEffect(() => {
    if (data) setDraft(data.hiddenWords.join(', '))
  }, [data])
  if (!data) return null
  return (
    <Page title="숨겨진 단어">
      <p className="text-sm text-muted">
        불쾌한 말이 들어간 댓글을 숨깁니다. 숨긴 댓글은 쓴 사람에게만 보이고, 회원님은 게시물의 "숨겨진 댓글"에서 볼 수 있습니다.
      </p>
      <Toggle
        checked={data.hideOffensive}
        onChange={(v) => save.mutate({ hideOffensive: v })}
        label="불쾌한 댓글 숨기기"
        help="흔히 쓰는 욕설·비하 표현(연구용 짧은 목록)이 들어간 댓글"
      />
      <label className="block">
        <span className="mb-1 block text-sm font-semibold">직접 정한 단어</span>
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          rows={3}
          aria-label="숨길 단어"
          placeholder="쉼표로 구분 (예: 스포일러, 광고)"
          className="w-full resize-none rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-muted"
        />
      </label>
      <button
        className="btn btn-primary w-full"
        onClick={() =>
          save.mutate(
            { hiddenWords: draft.split(',').map((w) => w.trim()).filter(Boolean) },
            { onSuccess: () => toast('숨길 단어를 저장했습니다') },
          )
        }
      >
        저장
      </button>
    </Page>
  )
}

// ------------------------------------------------------------ 댓글 허용 범위

export function CommentControlsPage() {
  const { data } = useUserSettings()
  const save = useSaveSettings()
  if (!data) return null
  return (
    <Page title="댓글">
      <p className="text-sm font-semibold">내 게시물에 댓글을 달 수 있는 사람</p>
      <Choice
        name="댓글 허용 범위"
        value={data.commentAudience}
        onChange={(v) => save.mutate({ commentAudience: v }, { onSuccess: () => toast('댓글 허용 범위를 바꿨습니다') })}
        options={[
          ['EVERYONE', '모든 사람'],
          ['FOLLOWING', '내가 팔로우하는 사람'],
          ['FOLLOWERS', '나를 팔로우하는 사람'],
          ['BOTH', '내가 팔로우하는 사람과 내 팔로워'],
          ['OFF', '아무도 안 됨', '게시물마다 댓글 기능을 끄는 것과 같습니다'],
        ]}
      />
      <Link to="/accounts/hidden-words" className="block text-sm font-semibold text-primary">
        숨겨진 단어 설정 →
      </Link>
    </Page>
  )
}

// ------------------------------------------------------------ 알림

const GROUPS: [string, string][] = [
  ['LIKES', '좋아요 (게시물·댓글·스토리)'],
  ['COMMENTS', '댓글·답글·언급·태그'],
  ['FOLLOWS', '팔로우·팔로우 요청'],
  ['SHARING', '리포스트·리믹스·공동 작업 초대'],
  ['SUPPORT', '구독·선물'],
]

export function NotificationSettingsPage() {
  const { data } = useUserSettings()
  const save = useSaveSettings()
  if (!data) return null
  const toggle = (g: string, on: boolean) =>
    save.mutate({ notifOff: on ? data.notifOff.filter((x) => x !== g) : [...data.notifOff, g] })
  return (
    <Page title="알림">
      <BrowserNotifyToggle />
      <div className="rounded-lg border border-border p-3">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <BellOff size={18} /> 모두 일시 중지
        </p>
        {data.notifPausedUntil ? (
          <div className="mt-2 flex items-center justify-between text-sm">
            <span>{new Date(data.notifPausedUntil).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })}까지 일시 중지됨</span>
            <button className="font-semibold text-primary" onClick={() => save.mutate({ resumeNotifications: true })}>
              다시 받기
            </button>
          </div>
        ) : (
          <div className="mt-2 flex flex-wrap gap-2">
            {[15, 60, 120, 480].map((m) => (
              <button
                key={m}
                className="rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-surface-2"
                onClick={() => save.mutate({ pauseMinutes: m }, { onSuccess: () => toast('알림을 일시 중지했습니다') })}
              >
                {m < 60 ? `${m}분` : `${m / 60}시간`}
              </button>
            ))}
          </div>
        )}
        <p className="mt-2 text-xs text-muted">일시 중지 중에도 알림은 활동 화면에 쌓이고, 실시간 알림(소리·배지)만 오지 않습니다.</p>
      </div>
      <div className="space-y-3">
        {GROUPS.map(([g, label]) => (
          <Toggle key={g} checked={!data.notifOff.includes(g)} onChange={(on) => toggle(g, on)} label={label} />
        ))}
      </div>
      <p className="text-xs text-muted">끈 종류의 알림은 활동 화면에도 남지 않습니다.</p>
    </Page>
  )
}

/** 이 기기의 브라우저 알림 (탭이 안 보일 때 알림 창) */
function BrowserNotifyToggle() {
  const [on, setOn] = useState(browserNotifyEnabled())
  const supported = typeof Notification !== 'undefined'
  return (
    <div className="rounded-lg border border-border p-3">
      <Toggle
        checked={on}
        onChange={async (v) => {
          const r = await setBrowserNotify(v)
          setOn(r)
          if (v && !r) toast(supported ? '브라우저에서 알림을 허용해야 켤 수 있습니다' : '이 브라우저는 알림을 지원하지 않습니다')
        }}
        label="브라우저 알림 (이 기기)"
        help="이 탭이 뒤에 있을 때 새 알림·메시지를 브라우저 알림 창으로 알려 줍니다 (연구용: 푸시 서버 없이 탭이 열려 있을 때만)"
      />
    </div>
  )
}

// ------------------------------------------------------------ 민감한 콘텐츠

export function SensitiveContentPage() {
  const { data } = useUserSettings()
  const save = useSaveSettings()
  if (!data) return null
  return (
    <Page title="민감한 콘텐츠">
      <p className="text-sm text-muted">
        탐색·릴스·홈 추천에 나오는, 팔로우하지 않는 사람의 민감할 수 있는 콘텐츠를 얼마나 볼지 정합니다. (연구용: 게시물 약 3%에 "민감" 표시가
        있습니다)
      </p>
      <Choice
        name="민감한 콘텐츠"
        value={data.sensitiveLevel}
        disabled={data.sensitiveLocked}
        onChange={(v) => save.mutate({ sensitiveLevel: v }, { onSuccess: () => toast('저장했습니다') })}
        options={[
          ['MORE', '더 보기', '추천에서 거르지 않습니다'],
          ['STANDARD', '기본', '탐색에서는 빼고, 릴스·홈 추천에는 나올 수 있습니다'],
          ['LESS', '덜 보기', '추천 어디에도 나오지 않습니다'],
        ]}
      />
      {data.sensitiveLocked && <p className="text-xs text-muted">10대 계정은 "덜 보기"로 고정됩니다.</p>}
    </Page>
  )
}

// ------------------------------------------------------------ 사용 시간·수면 모드

const HOURS = Array.from({ length: 24 }, (_, h) => h)

export function TimeManagementPage() {
  const { data } = useUserSettings()
  const save = useSaveSettings()
  if (!data) return null
  return (
    <Page title="사용 시간 관리">
      <div className="rounded-lg border border-border p-3">
        <p className="flex items-center gap-2 text-sm font-semibold">
          <Timer size={18} /> 하루 사용 시간 알림
        </p>
        <p className="mt-1 text-xs text-muted">오늘 이 시간만큼 쓰면 한 번 알려 드립니다 (이 브라우저에서 화면을 보고 있던 시간 기준).</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {[15, 30, 60, 120].map((m) => (
            <button
              key={m}
              aria-pressed={data.dailyLimitMin === m}
              className={clsx('rounded-lg border px-3 py-1.5 text-sm', data.dailyLimitMin === m ? 'border-primary font-semibold text-primary' : 'border-border')}
              onClick={() => save.mutate({ dailyLimitMin: m })}
            >
              {m < 60 ? `${m}분` : `${m / 60}시간`}
            </button>
          ))}
          <button
            aria-pressed={data.dailyLimitMin === null}
            className={clsx('rounded-lg border px-3 py-1.5 text-sm', data.dailyLimitMin === null ? 'border-primary font-semibold text-primary' : 'border-border')}
            onClick={() => save.mutate({ clearDailyLimit: true })}
          >
            끄기
          </button>
        </div>
      </div>
      <div className="rounded-lg border border-border p-3">
        <Toggle
          checked={data.sleepOn}
          onChange={(v) => save.mutate({ sleepOn: v })}
          label="수면 모드"
          help="정한 시간에는 실시간 알림이 오지 않습니다 (알림은 활동 화면에 쌓임)"
        />
        <div className="mt-3 flex items-center gap-2 text-sm">
          <Moon size={16} />
          <select aria-label="수면 시작" className="input w-24 py-1" value={data.sleepStart} onChange={(e) => save.mutate({ sleepStart: Number(e.target.value) })}>
            {HOURS.map((h) => (
              <option key={h} value={h}>
                {h}시
              </option>
            ))}
          </select>
          부터
          <select aria-label="수면 끝" className="input w-24 py-1" value={data.sleepEnd} onChange={(e) => save.mutate({ sleepEnd: Number(e.target.value) })}>
            {HOURS.map((h) => (
              <option key={h} value={h}>
                {h}시
              </option>
            ))}
          </select>
          까지
        </div>
        {data.sleepingNow && <p className="mt-2 text-xs font-semibold text-primary">지금 수면 모드 중입니다</p>}
      </div>
    </Page>
  )
}

/** 앱 전체: 오늘 화면을 본 시간을 세어 하루 사용 시간 알림을 한 번 띄운다 */
export function UsageReminder() {
  const { data } = useUserSettings()
  const [show, setShow] = useState(false)
  const limit = data?.dailyLimitMin ?? null
  useEffect(() => {
    if (!limit) return
    const today = new Date().toLocaleDateString('sv-SE')
    const read = () => {
      try {
        const v = JSON.parse(localStorage.getItem('usage') ?? '{}') as { day?: string; ms?: number; shown?: boolean }
        return v.day === today ? v : { day: today, ms: 0, shown: false }
      } catch {
        return { day: today, ms: 0, shown: false }
      }
    }
    const t = setInterval(() => {
      if (document.visibilityState !== 'visible') return
      const v = read()
      v.ms = (v.ms ?? 0) + 10_000
      if (!v.shown && v.ms >= limit * 60_000) {
        v.shown = true
        setShow(true)
      }
      try {
        localStorage.setItem('usage', JSON.stringify(v))
      } catch {
        /* 저장 못 해도 계속 */
      }
    }, 10_000)
    return () => clearInterval(t)
  }, [limit])
  return (
    <Modal open={show} onClose={() => setShow(false)} title="사용 시간 알림">
      <div className="space-y-3 p-5 text-center">
        <Timer size={40} className="mx-auto text-primary" />
        <p className="font-semibold">오늘 정해 둔 {limit}분을 다 썼어요</p>
        <p className="text-sm text-muted">잠시 쉬어 가는 건 어떨까요? 알림 시간은 설정 → 사용 시간 관리에서 바꿀 수 있습니다.</p>
        <button className="btn btn-primary w-full" onClick={() => setShow(false)}>
          닫기
        </button>
      </div>
    </Modal>
  )
}

// ------------------------------------------------------------ 임시 저장

export function DraftsPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({ queryKey: ['drafts'], queryFn: () => api.get<ScheduledPost[]>('/api/me/drafts') })
  const refresh = () => qc.invalidateQueries({ queryKey: ['drafts'] })
  return (
    <Page title="임시 저장">
      {isLoading && <p className="text-center text-sm text-muted">불러오는 중…</p>}
      {data?.length === 0 && (
        <div className="flex flex-col items-center gap-3 py-12 text-center text-muted">
          <FileClock size={44} strokeWidth={1.3} />
          <p className="text-sm">임시 저장한 게시물이 없습니다. 새 게시물 만들기에서 "임시 저장"을 누르면 여기에 남습니다.</p>
        </div>
      )}
      <ul className="space-y-2">
        {data?.map((d) => (
          <li key={d.id} className="flex items-center gap-3 rounded-lg border border-border p-2">
            <img src={d.thumbUrl} alt="" className="size-16 shrink-0 rounded object-cover" />
            <div className="min-w-0 flex-1 text-sm">
              <p className="truncate">{d.captionStart ?? '(문구 없음)'}</p>
              <p className="text-xs text-muted">
                사진 {d.mediaCount}장 · {timeAgo(d.createdAt, 'long')} 저장
              </p>
            </div>
            <button
              className="btn btn-primary shrink-0 px-3 text-xs"
              onClick={() =>
                api.post<PostView>(`/api/me/scheduled/${d.id}/publish`).then(
                  (p) => {
                    toast('게시물이 공유되었습니다')
                    void refresh()
                    qc.invalidateQueries({ queryKey: ['grid'] })
                    navigate(`/p/${p.shortcode}`)
                  },
                  (e: Error) => toast(e.message),
                )
              }
            >
              게시
            </button>
            <button
              className="btn btn-secondary shrink-0 px-3 text-xs"
              onClick={() =>
                api.delete<void>(`/api/me/scheduled/${d.id}`).then(
                  () => {
                    toast('임시 저장을 삭제했습니다')
                    void refresh()
                  },
                  (e: Error) => toast(e.message),
                )
              }
            >
              삭제
            </button>
          </li>
        ))}
      </ul>
    </Page>
  )
}

// ------------------------------------------------------------ 게시물 주인: 숨겨진 댓글

export function HiddenCommentsButton({ shortcode }: { shortcode: string }) {
  const [open, setOpen] = useState(false)
  const { data } = useQuery({
    queryKey: ['hidden-comments', shortcode],
    queryFn: () => api.get<CommentView[]>(`/api/posts/${shortcode}/comments/hidden`),
  })
  if (!data?.length) return null
  return (
    <>
      <button className="my-2 w-full rounded-lg py-2 text-sm text-muted hover:bg-surface-2" onClick={() => setOpen(true)}>
        숨겨진 댓글 보기 ({data.length})
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title="숨겨진 댓글">
        <p className="px-4 pt-3 text-xs text-muted">숨겨진 단어 설정에 걸린 댓글입니다. 쓴 사람에게만 보입니다.</p>
        <ul className="max-h-[60dvh] overflow-y-auto p-2">
          {data.map((c) => (
            <li key={c.id} className="flex gap-3 px-2 py-2 text-sm">
              <Avatar user={c.author} size={32} />
              <p className="min-w-0 break-words">
                <b className="mr-1.5">{c.author.username}</b>
                {c.body}
              </p>
            </li>
          ))}
        </ul>
      </Modal>
    </>
  )
}
