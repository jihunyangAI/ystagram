import { useEffect, useRef, useState } from 'react'
import { Mic, MicOff, Phone, PhoneOff, Video, VideoOff } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../Avatar'
import { toast } from '../Toast'
import { api } from '../../lib/api'
import { useRealtimeEvent } from '../../lib/realtime'
import {
  acceptAnswer,
  getLocalMedia,
  makeAnswer,
  makeOffer,
  newPeer,
  onSignal,
  sendSignal,
  stopStream,
  type SignalData,
} from '../../lib/rtc'
import type { CallStatus, CallView } from '../../lib/types'

type Phase = 'outgoing' | 'incoming' | 'connecting' | 'active'

let starter: ((conversationId: number, video: boolean) => Promise<void>) | null = null

/** 1:1 대화방에서 전화 걸기 (CallManager가 화면을 맡는다) */
export function startCall(conversationId: number, video: boolean) {
  if (!starter) return
  void starter(conversationId, video)
}

export const CALL_STATUS_LABEL: Record<CallStatus, string> = {
  RINGING: '연결 중',
  ACTIVE: '통화 중',
  ENDED: '통화 종료',
  MISSED: '부재중 통화',
  DECLINED: '거절된 통화',
  CANCELED: '취소된 통화',
}

export function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

/**
 * 음성·영상 통화 (기획서 §4.11, WebRTC). 로그인한 동안 항상 떠 있다.
 * 거는 쪽: 카메라 켜기 → 서버에 통화 만들기(RINGING) → 상대가 받으면 offer.
 * 받는 쪽: 벨 → 받기를 누르면 카메라를 켜고 연결을 준비한 뒤 서버에 수락 → offer가 오면 answer.
 * 같은 PC에서 시험하려면 다른 브라우저(또는 시크릿 창)에서 다른 계정으로 로그인한다.
 * 창을 닫으면 서버가 연결이 끊긴 것을 보고 약 9초 뒤 통화를 끝낸다.
 */
export function CallManager({ meId }: { meId: number }) {
  const [call, setCall] = useState<CallView | null>(null)
  const [phase, setPhase] = useState<Phase | null>(null)
  const [local, setLocal] = useState<MediaStream | null>(null)
  const [remote, setRemote] = useState<MediaStream | null>(null)
  const [synthetic, setSynthetic] = useState(false)
  const [muted, setMuted] = useState(false)
  const [camOff, setCamOff] = useState(false)
  const [since, setSince] = useState<number | null>(null)
  const [, tick] = useState(0)

  const callRef = useRef<CallView | null>(null)
  const pcRef = useRef<RTCPeerConnection | null>(null)
  const localRef = useRef<MediaStream | null>(null)
  const unsubRef = useRef<(() => void) | null>(null)

  const other = call ? (call.caller.id === meId ? call.callee : call.caller) : null

  function reset() {
    unsubRef.current?.()
    unsubRef.current = null
    pendingOffer.current = null
    pcRef.current?.close()
    pcRef.current = null
    stopStream(localRef.current)
    localRef.current = null
    callRef.current = null
    setCall(null)
    setPhase(null)
    setLocal(null)
    setRemote(null)
    setSince(null)
    setMuted(false)
    setCamOff(false)
    setSynthetic(false)
  }

  async function prepareMedia(video: boolean) {
    const m = await getLocalMedia(video, '나')
    localRef.current = m.stream
    setLocal(m.stream)
    setSynthetic(m.synthetic)
    return m.stream
  }

  /** 이 통화의 신호 받기. 연결이 아직 준비되지 않았을 때 온 offer는 모아 두었다가 준비되면 처리한다 */
  const pendingOffer = useRef<SignalData | null>(null)

  function listen(c: CallView) {
    const otherId = c.caller.id === meId ? c.callee.id : c.caller.id
    unsubRef.current?.()
    unsubRef.current = onSignal('call', c.id, async (from, data) => {
      if (from !== otherId) return
      if (!pcRef.current) {
        if (data.type === 'offer') pendingOffer.current = data
        return
      }
      await handleSignal(c, otherId, pcRef.current, data)
    })
  }

  async function handleSignal(c: CallView, otherId: number, pc: RTCPeerConnection, data: SignalData) {
    try {
      if (data.type === 'offer') {
        await sendSignal('call', c.id, otherId, await makeAnswer(pc, data))
      } else if (data.type === 'answer') {
        await acceptAnswer(pc, data)
      }
    } catch (e) {
      toast(`연결 실패: ${(e as Error).message}`)
    }
  }

  function preparePeer(c: CallView, stream: MediaStream) {
    const pc = newPeer()
    stream.getTracks().forEach((t) => pc.addTrack(t, stream))
    pc.ontrack = (e) => setRemote(e.streams[0])
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'connected') {
        setPhase('active')
        setSince((s) => s ?? Date.now())
      }
    }
    pcRef.current = pc
    if (!unsubRef.current) listen(c)
    const queued = pendingOffer.current
    pendingOffer.current = null
    if (queued) void handleSignal(c, c.caller.id === meId ? c.callee.id : c.caller.id, pc, queued)
    return pc
  }

  useEffect(() => {
    starter = async (conversationId, video) => {
      if (callRef.current) {
        toast('이미 통화 중입니다')
        return
      }
      try {
        await prepareMedia(video)
        const c = await api.post<CallView>(`/api/direct/conversations/${conversationId}/calls`, { video })
        callRef.current = c
        setCall(c)
        setPhase('outgoing')
      } catch (e) {
        reset()
        toast((e as Error).message)
      }
    }
    return () => {
      starter = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => () => reset(), []) // eslint-disable-line react-hooks/exhaustive-deps

  // 통화 시간 표시
  useEffect(() => {
    if (since == null) return
    const t = setInterval(() => tick((n) => n + 1), 1000)
    return () => clearInterval(t)
  }, [since])

  useRealtimeEvent((e) => {
    if (e.type === 'call:incoming') {
      const c = e.data as CallView
      if (callRef.current) return
      callRef.current = c
      setCall(c)
      setPhase('incoming')
    } else if (e.type === 'call:accepted') {
      const c = e.data as CallView
      const mine = callRef.current
      if (!mine || mine.id !== c.id || !localRef.current) return
      callRef.current = c
      setCall(c)
      setPhase('connecting')
      const pc = preparePeer(c, localRef.current)
      void makeOffer(pc)
        .then((offer) => sendSignal('call', c.id, c.callee.id, offer))
        .catch((err) => toast(`연결 실패: ${(err as Error).message}`))
    } else if (e.type === 'call:ended') {
      const { id, status } = e.data as { id: number; status: CallStatus }
      if (callRef.current?.id !== id) return
      if (status !== 'ENDED') toast(CALL_STATUS_LABEL[status])
      reset()
    }
  })

  async function accept() {
    const c = callRef.current
    if (!c) return
    try {
      // 벨을 먼저 멈추고(수락), 카메라 권한을 기다리는 동안 온 offer는 listen이 모아 둔다
      setPhase('connecting')
      listen(c)
      const updated = await api.post<CallView>(`/api/calls/${c.id}/accept`)
      callRef.current = updated
      setCall(updated)
      const stream = await prepareMedia(c.video)
      if (callRef.current?.id !== c.id) return stopStream(stream)
      preparePeer(updated, stream)
    } catch (e) {
      toast((e as Error).message)
      reset()
    }
  }

  async function hangUp(action: 'end' | 'decline' = 'end') {
    const c = callRef.current
    reset()
    if (c) await api.post(`/api/calls/${c.id}/${action}`).catch(() => undefined)
  }

  function toggleMute() {
    localRef.current?.getAudioTracks().forEach((t) => (t.enabled = muted))
    setMuted(!muted)
  }

  function toggleCam() {
    localRef.current?.getVideoTracks().forEach((t) => (t.enabled = camOff))
    setCamOff(!camOff)
  }

  if (!call || !phase || !other) return null

  if (phase === 'incoming') {
    return (
      <div className="fixed inset-x-0 top-3 z-[1100] mx-auto w-[min(92vw,380px)] rounded-2xl bg-neutral-900 p-4 text-white shadow-2xl">
        <div className="flex items-center gap-3">
          <span className="relative">
            <span className="absolute inset-0 animate-ping rounded-full bg-green-400/40" />
            <Avatar user={other} size={48} className="relative" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold">{other.username}</p>
            <p className="text-sm text-white/70">{call.video ? '영상 통화' : '음성 통화'} 수신 중…</p>
          </div>
          <button aria-label="거절" className="rounded-full bg-red-500 p-3" onClick={() => void hangUp('decline')}>
            <PhoneOff size={20} />
          </button>
          <button aria-label="받기" className="rounded-full bg-green-500 p-3" onClick={() => void accept()}>
            {call.video ? <Video size={20} /> : <Phone size={20} />}
          </button>
        </div>
      </div>
    )
  }

  const status =
    phase === 'outgoing'
      ? '벨이 울리는 중…'
      : phase === 'connecting'
        ? '연결 중…'
        : since != null
          ? formatDuration(Math.floor((Date.now() - since) / 1000))
          : '통화 중'

  return (
    <div className="fixed inset-0 z-[1100] flex flex-col bg-neutral-950 text-white">
      {call.video && remote ? (
        <video
          className="absolute inset-0 h-full w-full object-cover"
          autoPlay
          playsInline
          ref={(el) => {
            if (el && el.srcObject !== remote) el.srcObject = remote
          }}
        />
      ) : (
        <>
          {remote && (
            <audio
              autoPlay
              ref={(el) => {
                if (el && el.srcObject !== remote) el.srcObject = remote
              }}
            />
          )}
          <div className="absolute inset-0 bg-gradient-to-b from-neutral-800 to-neutral-950" />
        </>
      )}

      <div className="relative z-10 flex flex-col items-center gap-2 pt-16 text-center drop-shadow">
        {!(call.video && remote) && <Avatar user={other} size={96} />}
        <p className="text-xl font-semibold">{other.username}</p>
        <p className="text-sm text-white/80">{status}</p>
        {synthetic && <p className="rounded-full bg-white/15 px-3 py-1 text-xs">카메라·마이크를 쓸 수 없어 테스트 화면을 보내고 있어요</p>}
      </div>

      {call.video && local && (
        <video
          className={clsx(
            'absolute right-4 bottom-28 z-10 w-28 rounded-xl border border-white/30 object-cover shadow-lg md:w-40',
            camOff && 'opacity-30',
          )}
          style={{ aspectRatio: '3 / 4', transform: 'scaleX(-1)' }}
          autoPlay
          muted
          playsInline
          ref={(el) => {
            if (el && el.srcObject !== local) el.srcObject = local
          }}
        />
      )}

      <div className="relative z-10 mt-auto flex items-center justify-center gap-5 pb-10">
        <button
          aria-label={muted ? '마이크 켜기' : '마이크 끄기'}
          className={clsx('rounded-full p-4', muted ? 'bg-white text-black' : 'bg-white/20')}
          onClick={toggleMute}
        >
          {muted ? <MicOff size={24} /> : <Mic size={24} />}
        </button>
        {call.video && (
          <button
            aria-label={camOff ? '카메라 켜기' : '카메라 끄기'}
            className={clsx('rounded-full p-4', camOff ? 'bg-white text-black' : 'bg-white/20')}
            onClick={toggleCam}
          >
            {camOff ? <VideoOff size={24} /> : <Video size={24} />}
          </button>
        )}
        <button aria-label="통화 끊기" className="rounded-full bg-red-500 p-4" onClick={() => void hangUp()}>
          <PhoneOff size={24} />
        </button>
      </div>
    </div>
  )
}
