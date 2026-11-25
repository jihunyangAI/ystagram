import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Eye, Heart, Radio, Send, Volume2, VolumeX, X } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { ConfirmModal, Modal } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import { formatCount } from '../../lib/format'
import { BADGE_PRICES, hostMedia, type LiveComment, type LiveView } from '../../lib/live'
import { useRealtimeEvent } from '../../lib/realtime'
import { LiveGuestLayer } from './LiveGuest'
import {
  acceptAnswer,
  getLocalMedia,
  makeAnswer,
  makeOffer,
  newPeer,
  onSignal,
  sendSignal,
  stopStream,
} from '../../lib/rtc'

function attach(stream: MediaStream | null) {
  return (el: HTMLVideoElement | null) => {
    if (el && stream && el.srcObject !== stream) el.srcObject = stream
  }
}

function elapsed(fromIso: string, toIso?: string | null) {
  const sec = Math.max(0, Math.floor(((toIso ? Date.parse(toIso) : Date.now()) - Date.parse(fromIso)) / 1000))
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`
}

/**
 * 라이브 시작 (/live/new): 카메라 미리 보기 + 제목 → 방송 시작.
 * 카메라가 없으면 움직이는 테스트 화면으로 방송한다.
 */
export function LiveStartPage() {
  const navigate = useNavigate()
  const { data: me } = useMe()
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [synthetic, setSynthetic] = useState(false)
  const [title, setTitle] = useState('')
  const [busy, setBusy] = useState(false)
  const handedOver = useRef(false)

  useEffect(() => {
    let alive = true
    let s: MediaStream | null = null
    void getLocalMedia(true, me?.user.username ?? '라이브').then((m) => {
      if (!alive) return stopStream(m.stream)
      s = m.stream
      setStream(m.stream)
      setSynthetic(m.synthetic)
    })
    return () => {
      alive = false
      if (!handedOver.current) stopStream(s)
    }
  }, [me?.user.username])

  async function start() {
    if (!stream) return
    setBusy(true)
    try {
      const live = await api.post<LiveView>('/api/lives', { title: title.trim() || null })
      hostMedia.liveId = live.id
      hostMedia.stream = stream
      hostMedia.synthetic = synthetic
      handedOver.current = true
      navigate(`/live/${live.id}`, { replace: true })
    } catch (e) {
      toast((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black text-white">
      <video ref={attach(stream)} autoPlay muted playsInline className="absolute inset-0 h-full w-full object-cover" style={{ transform: 'scaleX(-1)' }} />
      <div className="relative z-10 flex items-center justify-between p-4">
        <button aria-label="닫기" onClick={() => navigate(-1)}>
          <X size={28} />
        </button>
        <span className="rounded bg-white/20 px-2 py-0.5 text-sm font-semibold">라이브</span>
        <span className="w-7" />
      </div>
      {synthetic && (
        <p className="relative z-10 mx-auto rounded-full bg-black/50 px-3 py-1 text-xs">카메라를 쓸 수 없어 테스트 화면으로 방송합니다</p>
      )}
      <div className="relative z-10 mt-auto space-y-3 bg-gradient-to-t from-black/80 to-transparent p-5 pt-16">
        <input
          className="w-full rounded-full bg-white/15 px-4 py-2.5 text-sm text-white placeholder:text-white/60 outline-none"
          placeholder="제목 추가 (선택)"
          maxLength={100}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <button
          className="flex w-full items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#d62976] to-[#fa7e1e] py-3 font-semibold disabled:opacity-60"
          disabled={!stream || busy}
          onClick={() => void start()}
        >
          <Radio size={20} /> 라이브 방송 시작
        </button>
        <p className="text-center text-xs text-white/60">
          연구용: 영상은 서버를 거치지 않고 시청자 브라우저로 바로 갑니다 (같은 PC·네트워크). 가짜 팔로워가 들어와 댓글을 남겨요.
        </p>
      </div>
    </div>
  )
}

interface FloatingHeart {
  id: number
  dx: number
  color: string
}

const HEART_COLORS = ['#ff3040', '#ff6b81', '#ff9f43', '#feca57', '#ff4d94', '#a55eea']

/** 라이브 방송·시청 화면 (/live/:id) */
export function LiveRoomPage() {
  const { id: idParam } = useParams()
  const id = Number(idParam)
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data: me } = useMe()
  const [live, setLive] = useState<LiveView | null>(null)
  const [comments, setComments] = useState<LiveComment[]>([])
  const [viewers, setViewers] = useState(0)
  const [ended, setEnded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hearts, setHearts] = useState<FloatingHeart[]>([])
  const [text, setText] = useState('')
  const [local, setLocal] = useState<MediaStream | null>(null)
  const [remote, setRemote] = useState<MediaStream | null>(null)
  const [synthetic, setSynthetic] = useState(false)
  const [muted, setMuted] = useState(true)
  const [confirmEnd, setConfirmEnd] = useState(false)
  const [badgeOpen, setBadgeOpen] = useState(false)
  const [, tick] = useState(0)
  const pendingHearts = useRef(0)
  const heartSeq = useRef(0)
  const listEnd = useRef<HTMLDivElement>(null)

  const hosting = !!live?.mine

  // 입장 (진행자는 방송 정보만)
  useEffect(() => {
    let cancelled = false
    setLive(null)
    setEnded(false)
    setError(null)
    api
      .post<{ live: LiveView; comments: LiveComment[] }>(`/api/lives/${id}/join`)
      .then((r) => {
        if (cancelled) return
        setLive(r.live)
        setComments(r.comments)
        setViewers(r.live.viewers)
      })
      .catch(async (e) => {
        if (cancelled) return
        // 이미 끝난 방송이면 요약을 보여 준다
        try {
          const l = await api.get<LiveView>(`/api/lives/${id}`)
          setLive(l)
          setEnded(true)
        } catch {
          setError((e as Error).message)
        }
      })
    return () => {
      cancelled = true
    }
  }, [id])

  // 진행자: 카메라 + 시청자마다 연결
  useEffect(() => {
    if (!live || !hosting || ended || live.kind !== 'CAMERA') return
    let stream = hostMedia.liveId === live.id ? hostMedia.stream : null
    let cancelled = false
    const peers = new Map<number, RTCPeerConnection>()
    const ready = (s: MediaStream, synth: boolean) => {
      stream = s
      setLocal(s)
      setSynthetic(synth)
    }
    if (stream) ready(stream, hostMedia.synthetic)
    else void getLocalMedia(true, me?.user.username ?? '라이브').then((m) => (cancelled ? stopStream(m.stream) : ready(m.stream, m.synthetic)))

    const unsub = onSignal('live', live.id, async (from, data) => {
      try {
        if (data.type === 'hello' && stream) {
          peers.get(from)?.close()
          const pc = newPeer()
          stream.getTracks().forEach((t) => pc.addTrack(t, stream!))
          peers.set(from, pc)
          await sendSignal('live', live.id, from, await makeOffer(pc))
        } else if (data.type === 'answer') {
          const pc = peers.get(from)
          if (pc) await acceptAnswer(pc, data)
        } else if (data.type === 'bye') {
          peers.get(from)?.close()
          peers.delete(from)
        }
      } catch {
        /* 시청자 한 명 연결 실패는 무시 */
      }
    })
    return () => {
      cancelled = true
      unsub()
      peers.forEach((pc) => pc.close())
      stopStream(stream)
      hostMedia.liveId = null
      hostMedia.stream = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [live?.id, hosting, ended])

  // 시청자: 진행자에게 인사하고 offer를 기다린다
  useEffect(() => {
    if (!live || hosting || ended || live.kind !== 'CAMERA') return
    let pc: RTCPeerConnection | null = null
    const unsub = onSignal('live', live.id, async (from, data) => {
      if (from !== live.host.id || data.type !== 'offer') return
      pc?.close()
      pc = newPeer()
      pc.ontrack = (e) => setRemote(e.streams[0])
      try {
        await sendSignal('live', live.id, live.host.id, await makeAnswer(pc, data))
      } catch {
        /* 다음 hello에서 다시 */
      }
    })
    void sendSignal('live', live.id, live.host.id, { type: 'hello' }).catch(() => undefined)
    return () => {
      unsub()
      pc?.close()
      void sendSignal('live', live.id, live.host.id, { type: 'bye' }).catch(() => undefined)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [live?.id, hosting, ended])

  // 나갈 때
  useEffect(() => {
    if (!live || hosting) return
    return () => {
      void api.post(`/api/lives/${live.id}/leave`).catch(() => undefined)
      void qc.invalidateQueries({ queryKey: ['lives'] })
    }
  }, [live?.id, hosting]) // eslint-disable-line react-hooks/exhaustive-deps

  // 하트는 1초에 한 번 모아서 보낸다
  useEffect(() => {
    if (!live || ended) return
    const t = setInterval(() => {
      const n = pendingHearts.current
      if (n <= 0) return
      pendingHearts.current = 0
      void api.post(`/api/lives/${live.id}/hearts`, { count: Math.min(n, 10) }).catch(() => undefined)
    }, 1000)
    return () => clearInterval(t)
  }, [live, ended])

  // 방송 시간 표시
  useEffect(() => {
    if (!live || ended) return
    const t = setInterval(() => tick((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [live, ended])

  useEffect(() => {
    listEnd.current?.scrollIntoView({ block: 'end' })
  }, [comments.length])

  function floatHeart(n = 1) {
    const add: FloatingHeart[] = []
    for (let i = 0; i < Math.min(n, 5); i++) {
      add.push({ id: ++heartSeq.current, dx: Math.round(Math.random() * 60 - 30), color: HEART_COLORS[heartSeq.current % HEART_COLORS.length] })
    }
    setHearts((h) => [...h.slice(-30), ...add])
    const ids = new Set(add.map((h) => h.id))
    setTimeout(() => setHearts((h) => h.filter((x) => !ids.has(x.id))), 2300)
  }

  useRealtimeEvent((e) => {
    const d = e.data as { liveId?: number }
    if (!live || d?.liveId !== live.id) return
    switch (e.type) {
      case 'live:comment':
        setComments((c) => (c.some((x) => x.id === (e.data as LiveComment).id) ? c : [...c.slice(-80), e.data as LiveComment]))
        break
      case 'live:heart': {
        const h = e.data as { userId: number; count: number }
        if (h.userId !== me?.user.id) floatHeart(h.count)
        break
      }
      case 'live:viewers':
        setViewers((e.data as { viewers: number }).viewers)
        break
      case 'live:end':
        void api.get<LiveView>(`/api/lives/${live.id}`).then(setLive).catch(() => undefined)
        setEnded(true)
        break
    }
  })

  async function sendComment() {
    const body = text.trim()
    if (!body || !live) return
    setText('')
    try {
      const c = await api.post<LiveComment>(`/api/lives/${live.id}/comments`, { body })
      setComments((list) => (list.some((x) => x.id === c.id) ? list : [...list, c]))
    } catch (e) {
      toast((e as Error).message)
    }
  }

  async function endLive() {
    if (!live) return
    try {
      const l = await api.post<LiveView>(`/api/lives/${live.id}/end`)
      setLive(l)
      setEnded(true)
      void qc.invalidateQueries({ queryKey: ['lives'] })
    } catch (e) {
      toast((e as Error).message)
    }
  }

  async function buyBadge(tier: number) {
    if (!live) return
    try {
      const c = await api.post<LiveComment>(`/api/lives/${live.id}/badges`, { tier })
      setComments((list) => (list.some((x) => x.id === c.id) ? list : [...list, c]))
      setBadgeOpen(false)
      floatHeart(5)
      toast('배지를 보냈어요 (가짜 결제)')
    } catch (e) {
      toast((e as Error).message)
    }
  }

  if (error) {
    return (
      <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-3 bg-black p-6 text-center text-white">
        <p>{error}</p>
        <button className="btn btn-secondary" onClick={() => navigate('/')}>
          홈으로
        </button>
      </div>
    )
  }
  if (!live) return <div className="fixed inset-0 z-50 bg-black" />

  if (ended) {
    return (
      <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 bg-neutral-950 p-6 text-center text-white">
        <Avatar user={live.host} size={80} />
        <p className="text-lg font-semibold">{live.mine ? '라이브 방송을 종료했습니다' : `${live.host.username}님의 라이브 방송이 종료되었습니다`}</p>
        <dl className="grid w-full max-w-xs grid-cols-2 gap-3 text-sm">
          {[
            ['방송 시간', elapsed(live.startedAt, live.endedAt)],
            ['시청자', formatCount(live.totalViewers)],
            ['최대 동시 시청', formatCount(live.peakViewers)],
            ['하트', formatCount(live.heartCount)],
            ['댓글', formatCount(live.commentCount)],
            ['배지', formatCount(live.badgeCount)],
          ].map(([k, v]) => (
            <div key={k} className="rounded-xl bg-white/10 p-3">
              <dt className="text-xs text-white/60">{k}</dt>
              <dd className="text-lg font-semibold">{v}</dd>
            </div>
          ))}
        </dl>
        <div className="flex gap-2">
          <Link to={`/${live.host.username}`} className="btn btn-secondary">
            프로필 보기
          </Link>
          <button className="btn btn-primary" onClick={() => navigate('/')}>
            홈으로
          </button>
        </div>
      </div>
    )
  }

  const visible = comments.slice(-40)
  return (
    <div className="fixed inset-0 z-50 flex justify-center bg-black text-white">
      <div className="relative h-full w-full max-w-[480px] overflow-hidden">
        {/* 영상 */}
        {live.kind === 'VIDEO' && live.videoUrl ? (
          <video
            src={live.videoUrl}
            poster={live.posterUrl ?? undefined}
            autoPlay
            loop
            muted={muted}
            playsInline
            className="absolute inset-0 h-full w-full object-cover"
          />
        ) : hosting ? (
          <video ref={attach(local)} autoPlay muted playsInline className="absolute inset-0 h-full w-full object-cover" style={{ transform: 'scaleX(-1)' }} />
        ) : remote ? (
          <video ref={attach(remote)} autoPlay muted={muted} playsInline className="absolute inset-0 h-full w-full object-cover" />
        ) : (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-neutral-900">
            <Avatar user={live.host} size={88} />
            <p className="text-sm text-white/70">영상 연결 중… (진행자와 같은 PC·네트워크에서만 보여요)</p>
          </div>
        )}
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-black/50 via-transparent to-black/70" />

        {/* 위쪽 */}
        <header className="absolute inset-x-0 top-0 z-10 flex items-center gap-2 p-3">
          <Link to={`/${live.host.username}`} className="flex min-w-0 items-center gap-2">
            <Avatar user={live.host} size={34} />
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold">{live.host.username}</span>
              {live.title && <span className="block truncate text-xs text-white/80">{live.title}</span>}
            </span>
          </Link>
          <span className="ml-auto rounded bg-gradient-to-r from-[#d62976] to-[#fa7e1e] px-1.5 py-0.5 text-xs font-bold">LIVE</span>
          <span className="flex items-center gap-1 rounded bg-black/40 px-1.5 py-0.5 text-xs">
            <Eye size={13} /> {formatCount(viewers)}
          </span>
          <span className="rounded bg-black/40 px-1.5 py-0.5 text-xs tabular-nums">{elapsed(live.startedAt)}</span>
          {!hosting && (live.kind === 'VIDEO' || remote) && (
            <button aria-label={muted ? '소리 켜기' : '소리 끄기'} className="p-1" onClick={() => setMuted((m) => !m)}>
              {muted ? <VolumeX size={20} /> : <Volume2 size={20} />}
            </button>
          )}
          <button
            aria-label={hosting ? '방송 끝내기' : '나가기'}
            className="p-1"
            onClick={() => (hosting ? setConfirmEnd(true) : navigate(-1))}
          >
            <X size={26} />
          </button>
        </header>
        {synthetic && hosting && (
          <p className="absolute top-16 left-1/2 z-10 -translate-x-1/2 rounded-full bg-black/50 px-3 py-1 text-xs whitespace-nowrap">
            카메라 대신 테스트 화면으로 방송 중
          </p>
        )}

        <LiveGuestLayer live={live} hosting={hosting} meId={me?.user.id} comments={comments} onChange={setLive} />

        {/* 날아가는 하트 */}
        <div className="pointer-events-none absolute right-4 bottom-20 z-10 h-0 w-10">
          {hearts.map((h) => (
            <Heart
              key={h.id}
              size={28}
              fill={h.color}
              color={h.color}
              className="live-heart absolute bottom-0 left-1"
              style={{ '--dx': `${h.dx}px` } as React.CSSProperties}
            />
          ))}
        </div>

        {/* 댓글 */}
        <div className="absolute inset-x-0 bottom-16 z-10 max-h-[42%] overflow-hidden px-3 [mask-image:linear-gradient(to_bottom,transparent,black_25%)]">
          <ul className="space-y-2 overflow-y-auto pr-14">
            {visible.map((c) => (
              <li key={c.id} className="flex items-start gap-2 text-sm">
                <Avatar user={c.user} size={26} />
                {c.kind === 'JOIN' ? (
                  <span className="pt-1 text-white/80">
                    <b>{c.user.username}</b>님이 참여했습니다 👋
                  </span>
                ) : c.kind === 'BADGE' ? (
                  <span className="rounded-xl bg-gradient-to-r from-[#7b2ff7]/80 to-[#d62976]/80 px-2.5 py-1">
                    <b>{c.user.username}</b> {c.body}
                  </span>
                ) : (
                  <span className="pt-0.5">
                    <b className="mr-1">{c.user.username}</b>
                    {c.body}
                  </span>
                )}
              </li>
            ))}
            <div ref={listEnd} />
          </ul>
        </div>

        {/* 아래쪽 */}
        <div className="absolute inset-x-0 bottom-0 z-10 flex items-center gap-2 p-3">
          <form
            className="flex min-w-0 flex-1 items-center rounded-full border border-white/40 bg-black/20 pr-1 pl-4"
            onSubmit={(e) => {
              e.preventDefault()
              void sendComment()
            }}
          >
            <input
              className="min-w-0 flex-1 bg-transparent py-2.5 text-sm text-white placeholder:text-white/70 outline-none"
              placeholder="댓글 달기…"
              maxLength={300}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
            {text.trim() && (
              <button aria-label="보내기" className="p-1.5">
                <Send size={18} />
              </button>
            )}
          </form>
          {!hosting && (
            <button
              aria-label="배지 보내기"
              title="배지 (가짜 결제)"
              className="rounded-full bg-white/15 px-3 py-2 text-sm font-semibold"
              onClick={() => setBadgeOpen(true)}
            >
              ❤️ 배지
            </button>
          )}
          <button
            aria-label="하트"
            className="p-1.5"
            onClick={() => {
              floatHeart()
              pendingHearts.current += 1
            }}
          >
            <Heart size={28} />
          </button>
        </div>
      </div>

      <ConfirmModal
        open={confirmEnd}
        title="라이브 방송을 끝낼까요?"
        confirmLabel="지금 끝내기"
        onConfirm={() => {
          setConfirmEnd(false)
          void endLive()
        }}
        onClose={() => setConfirmEnd(false)}
      />
      {badgeOpen && (
        <Modal open onClose={() => setBadgeOpen(false)} title="배지 보내기">
          <div className="space-y-3 p-4 text-sm">
            <p className="text-muted">배지를 사면 댓글이 강조되고 {live.host.username}님을 응원할 수 있어요. 연구용 가짜 결제라 실제로 돈이 나가지 않습니다.</p>
            {[1, 2, 3].map((tier) => (
              <button
                key={tier}
                className={clsx('flex w-full items-center justify-between rounded-xl border border-border px-4 py-3 hover:bg-surface-2')}
                onClick={() => void buyBadge(tier)}
              >
                <span>{'❤️'.repeat(tier)} 배지 {tier}단계</span>
                <b>{BADGE_PRICES[tier].toLocaleString('ko-KR')}원</b>
              </button>
            ))}
          </div>
        </Modal>
      )}
    </div>
  )
}
