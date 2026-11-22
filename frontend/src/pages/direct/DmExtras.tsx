import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Eye, Languages, Mic, Pause, Pin, Play, Square, Timer } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { upsertMessage } from '../../lib/dm'
import { Modal, SheetButton } from '../../components/Modal'
import { toast } from '../../components/Toast'
import type { ConversationView, MessageView } from '../../lib/types'

// ------------------------------------------------------------ 음성 메시지

const MAX_VOICE_MS = 60_000

function fmt(ms: number) {
  const s = Math.max(0, Math.round(ms / 1000))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** 마이크 버튼: 누르면 녹음 시작, ■ 보내기, ✕ 취소. 1분이 되면 자동으로 보낸다 */
export function VoiceRecorder({ id }: { id: number }) {
  const qc = useQueryClient()
  const [rec, setRec] = useState<{ started: number } | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [busy, setBusy] = useState(false)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const cancelled = useRef(false)

  useEffect(() => {
    if (!rec) return
    const t = setInterval(() => {
      const ms = Date.now() - rec.started
      setElapsed(ms)
      if (ms >= MAX_VOICE_MS) recorder.current?.stop()
    }, 200)
    return () => clearInterval(t)
  }, [rec])

  useEffect(() => () => recorder.current?.stream.getTracks().forEach((t) => t.stop()), [])

  const start = async () => {
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      toast('마이크를 쓸 수 없습니다. 브라우저의 마이크 권한을 확인하세요')
      return
    }
    const mime = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg', 'audio/mp4'].find((t) => MediaRecorder.isTypeSupported(t)) ?? ''
    const r = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined)
    chunks.current = []
    cancelled.current = false
    const started = Date.now()
    r.ondataavailable = (e) => e.data.size > 0 && chunks.current.push(e.data)
    r.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop())
      setRec(null)
      const duration = Math.min(Date.now() - started, MAX_VOICE_MS)
      if (cancelled.current || duration < 500) return
      const blob = new Blob(chunks.current, { type: (r.mimeType || 'audio/webm').split(';')[0] })
      const form = new FormData()
      form.append('file', blob, 'voice.webm')
      setBusy(true)
      try {
        const m = await api.upload<MessageView>('POST', `/api/direct/conversations/${id}/audio?durationMs=${duration}`, form)
        upsertMessage(qc, m)
        void qc.invalidateQueries({ queryKey: ['inbox'] })
      } catch (e) {
        toast((e as Error).message)
      } finally {
        setBusy(false)
      }
    }
    r.start(250)
    recorder.current = r
    setElapsed(0)
    setRec({ started })
  }

  if (!rec) {
    return (
      <button type="button" aria-label="음성 메시지 녹음" disabled={busy} onClick={start} className="p-1">
        <Mic size={22} />
      </button>
    )
  }
  return (
    <span className="flex items-center gap-2" aria-live="polite">
      <span className="flex items-center gap-1.5 text-sm text-danger">
        <span className="size-2 animate-pulse rounded-full bg-danger" /> 녹음 중 {fmt(elapsed)}
      </span>
      <button
        type="button"
        aria-label="녹음 취소"
        className="text-xs text-muted"
        onClick={() => {
          cancelled.current = true
          recorder.current?.stop()
        }}
      >
        취소
      </button>
      <button type="button" aria-label="녹음 보내기" className="rounded-full bg-primary p-1.5 text-white" onClick={() => recorder.current?.stop()}>
        <Square size={12} className="fill-white" />
      </button>
    </span>
  )
}

/** 음성 메시지 재생 막대 */
export function VoiceBubble({ m, mine }: { m: MessageView; mine: boolean }) {
  const audio = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const [pos, setPos] = useState(0)
  const total = m.durationMs ?? 0
  if (!m.media) return null
  return (
    <span className={clsx('flex w-56 items-center gap-2 rounded-[20px] px-3 py-2', mine ? 'bg-primary text-white' : 'bg-surface-2')}>
      <button
        aria-label={playing ? '음성 메시지 멈춤' : '음성 메시지 재생'}
        onClick={() => {
          const a = audio.current
          if (!a) return
          if (a.paused) void a.play().catch(() => toast('재생할 수 없습니다'))
          else a.pause()
        }}
      >
        {playing ? <Pause size={18} className="fill-current" /> : <Play size={18} className="fill-current" />}
      </button>
      <span className="relative h-1 flex-1 overflow-hidden rounded-full bg-current/30">
        <span className="absolute inset-y-0 left-0 bg-current" style={{ width: total ? `${Math.min(100, (pos / total) * 100)}%` : 0 }} />
      </span>
      <span className="text-xs tabular-nums">{fmt(playing || pos ? pos : total)}</span>
      <audio
        ref={audio}
        src={m.media.url}
        preload="none"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => {
          setPlaying(false)
          setPos(0)
        }}
        onTimeUpdate={(e) => setPos(e.currentTarget.currentTime * 1000)}
      />
    </span>
  )
}

// ------------------------------------------------------------ 1회 보기 사진

export function ViewOnceBubble({ m, mine }: { m: MessageView; mine: boolean }) {
  const qc = useQueryClient()
  const [shown, setShown] = useState<MessageView | null>(null)
  const state = m.viewOnce?.state
  const label = mine ? (state === 'OPENED' ? '열어봄' : '1회 보기 사진 · 보냄') : state === 'OPENED' ? '열어봄' : '사진 보기'
  const canOpen = !mine && state === 'UNOPENED'
  return (
    <>
      <button
        disabled={!canOpen}
        onClick={async () => {
          try {
            setShown(await api.post<MessageView>(`/api/direct/messages/${m.id}/open`))
          } catch (e) {
            toast((e as Error).message)
          }
        }}
        className={clsx(
          'flex items-center gap-2 rounded-[20px] border px-3.5 py-2 text-sm',
          canOpen ? 'border-primary font-semibold text-primary' : 'border-border text-muted',
        )}
      >
        <Eye size={16} /> {label}
      </button>
      <Modal
        open={shown !== null}
        onClose={() => {
          setShown(null)
          // 닫으면 다시 볼 수 없다
          void qc.invalidateQueries({ queryKey: ['messages', m.conversationId] })
        }}
        title="1회 보기 사진"
        className="md:max-w-[520px]"
      >
        {shown?.media && <img src={shown.media.url} alt="1회 보기 사진" className="max-h-[70dvh] w-full bg-black object-contain" />}
        <p className="p-3 text-center text-xs text-muted">닫으면 다시 볼 수 없습니다.</p>
      </Modal>
    </>
  )
}

// ------------------------------------------------------------ 메시지 더 보기: 고정·번역

export function MessageMore({ m, open, onClose, onTranslated }: { m: MessageView; open: boolean; onClose: () => void; onTranslated: (t: string) => void }) {
  const qc = useQueryClient()
  return (
    <Modal open={open} onClose={onClose} bare title="메시지 옵션">
      <SheetButton
        onClick={async () => {
          onClose()
          try {
            const x = m.pinned
              ? await api.delete<MessageView>(`/api/direct/messages/${m.id}/pin`)
              : await api.put<MessageView>(`/api/direct/messages/${m.id}/pin`)
            upsertMessage(qc, x)
            void qc.invalidateQueries({ queryKey: ['conversation', m.conversationId] })
            toast(x.pinned ? '메시지를 고정했습니다' : '고정을 해제했습니다')
          } catch (e) {
            toast((e as Error).message)
          }
        }}
      >
        {m.pinned ? '고정 해제' : '고정'}
      </SheetButton>
      {m.body && (
        <SheetButton
          onClick={async () => {
            onClose()
            try {
              const t = await api.post<{ text: string; complete: boolean }>(`/api/direct/messages/${m.id}/translate`)
              onTranslated(t.complete ? t.text : `${t.text} (일부만 번역됨)`)
            } catch (e) {
              toast((e as Error).message)
            }
          }}
        >
          번역 보기
        </SheetButton>
      )}
      <SheetButton onClick={onClose}>취소</SheetButton>
    </Modal>
  )
}

export function TranslationLine({ text }: { text: string }) {
  return (
    <span className="flex max-w-[260px] items-start gap-1 px-1 text-xs text-muted">
      <Languages size={13} className="mt-0.5 shrink-0" /> {text}
    </span>
  )
}

/** 고정한 메시지 막대 (대화방 위) */
export function PinnedBar({ c, onJump }: { c: ConversationView; onJump: (id: number) => void }) {
  const [i, setI] = useState(0)
  if (c.pins.length === 0) return null
  const p = c.pins[i % c.pins.length]
  const text = p.body ?? (p.kind === 'IMAGE' ? '사진' : p.kind === 'AUDIO' ? '음성 메시지' : '첨부')
  return (
    <button
      aria-label="고정된 메시지"
      onClick={() => {
        onJump(p.id)
        setI((x) => x + 1)
      }}
      className="flex w-full items-center gap-2 border-b border-border px-4 py-2 text-left text-sm hover:bg-surface-2"
    >
      <Pin size={14} className="shrink-0 rotate-45 text-muted" />
      <span className="min-w-0 flex-1 truncate">
        <b className="mr-1">{p.sender.username}</b>
        {text}
      </span>
      {c.pins.length > 1 && <span className="text-xs text-muted">{(i % c.pins.length) + 1}/{c.pins.length}</span>}
    </button>
  )
}

// ------------------------------------------------------------ 대화방 정보: 채팅방 고정·사라지는 메시지

export function ThreadInfoSheet({ c, open, onClose }: { c: ConversationView; open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['conversation', c.id] })
    void qc.invalidateQueries({ queryKey: ['messages', c.id] })
    void qc.invalidateQueries({ queryKey: ['inbox'] })
  }
  return (
    <Modal open={open} onClose={onClose} bare title="대화 옵션">
      <SheetButton
        onClick={async () => {
          onClose()
          try {
            if (c.pinned) await api.delete<void>(`/api/direct/conversations/${c.id}/pin`)
            else await api.put<void>(`/api/direct/conversations/${c.id}/pin`)
            toast(c.pinned ? '채팅방 고정을 해제했습니다' : '채팅방을 고정했습니다')
            refresh()
          } catch (e) {
            toast((e as Error).message)
          }
        }}
      >
        {c.pinned ? '채팅방 고정 해제' : '채팅방 고정'}
      </SheetButton>
      {!c.isGroup && (
        <SheetButton
          onClick={async () => {
            onClose()
            try {
              await api.put(`/api/direct/conversations/${c.id}/vanish`, { on: !c.vanishMode })
              toast(c.vanishMode ? '사라지는 메시지를 껐습니다. 그동안 보낸 메시지가 사라졌어요' : '사라지는 메시지를 켰습니다')
              refresh()
            } catch (e) {
              toast((e as Error).message)
            }
          }}
        >
          {c.vanishMode ? '사라지는 메시지 끄기' : '사라지는 메시지 켜기'}
        </SheetButton>
      )}
      <SheetButton onClick={onClose}>취소</SheetButton>
    </Modal>
  )
}

export function VanishBanner() {
  return (
    <p className="flex items-center justify-center gap-1.5 bg-text px-3 py-1.5 text-center text-xs text-bg">
      <Timer size={13} /> 사라지는 메시지 모드 · 끄면 그동안 보낸 메시지가 사라집니다
    </p>
  )
}

/** 1회 보기 사진 보내기 버튼 */
export function ViewOnceButton({ id }: { id: number }) {
  const qc = useQueryClient()
  const file = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  return (
    <>
      <input
        ref={file}
        type="file"
        accept="image/*"
        hidden
        aria-label="1회 보기 사진 파일"
        onChange={async (e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (!f) return
          const form = new FormData()
          form.append('file', f, 'photo.jpg')
          setBusy(true)
          try {
            const m = await api.upload<MessageView>('POST', `/api/direct/conversations/${id}/images?viewOnce=true`, form)
            upsertMessage(qc, m)
            void qc.invalidateQueries({ queryKey: ['inbox'] })
          } catch (err) {
            toast((err as Error).message)
          } finally {
            setBusy(false)
          }
        }}
      />
      <button type="button" aria-label="1회 보기 사진 보내기" title="1회 보기 사진" disabled={busy} onClick={() => file.current?.click()} className="p-1">
        <Eye size={21} />
      </button>
    </>
  )
}
