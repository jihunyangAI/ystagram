// WebRTC 도우미 (통화·라이브).
// 연구용으로 같은 PC(또는 같은 네트워크)의 브라우저끼리만 연결한다: STUN/TURN 없이 호스트 후보만 쓰고,
// 후보를 다 모은 뒤 SDP를 한 번에 보낸다(trickle ICE 없음) — 신호가 offer/answer 두 번으로 끝난다.
// 신호는 REST(/api/rtc/signal)로 보내고 상대는 실시간 이벤트 "rtc:signal"로 받는다.

import { api } from './api'
import { onRealtime } from './realtime'

export type SignalScope = 'call' | 'live'

export interface SignalData {
  type: 'hello' | 'offer' | 'answer' | 'bye'
  sdp?: string
}

interface SignalEvent {
  scope: SignalScope
  id: number
  from: number
  data: SignalData
}

export function sendSignal(scope: SignalScope, id: number, to: number, data: SignalData) {
  return api.post('/api/rtc/signal', { scope, id, to, data })
}

/** 이 통화·라이브의 신호만 받는다. 구독을 끊는 함수를 돌려준다 */
export function onSignal(scope: SignalScope, id: number, handler: (from: number, data: SignalData) => void) {
  return onRealtime((e) => {
    if (e.type !== 'rtc:signal') return
    const s = e.data as SignalEvent
    if (s.scope === scope && s.id === id) handler(s.from, s.data)
  })
}

export function newPeer(): RTCPeerConnection {
  return new RTCPeerConnection({ iceServers: [] })
}

/** 후보 모으기가 끝날 때까지 (최대 2초) 기다린 뒤의 SDP */
async function gathered(pc: RTCPeerConnection): Promise<SignalData> {
  if (pc.iceGatheringState !== 'complete') {
    await new Promise<void>((resolve) => {
      const done = () => {
        if (pc.iceGatheringState === 'complete') {
          pc.removeEventListener('icegatheringstatechange', done)
          resolve()
        }
      }
      pc.addEventListener('icegatheringstatechange', done)
      setTimeout(resolve, 2000)
    })
  }
  const d = pc.localDescription!
  return { type: d.type as 'offer' | 'answer', sdp: d.sdp }
}

export async function makeOffer(pc: RTCPeerConnection): Promise<SignalData> {
  await pc.setLocalDescription(await pc.createOffer())
  return gathered(pc)
}

export async function makeAnswer(pc: RTCPeerConnection, offer: SignalData): Promise<SignalData> {
  await pc.setRemoteDescription({ type: 'offer', sdp: offer.sdp })
  await pc.setLocalDescription(await pc.createAnswer())
  return gathered(pc)
}

export async function acceptAnswer(pc: RTCPeerConnection, answer: SignalData) {
  if (pc.signalingState === 'have-local-offer') {
    await pc.setRemoteDescription({ type: 'answer', sdp: answer.sdp })
  }
}

export interface LocalMedia {
  stream: MediaStream
  /** 카메라·마이크를 못 써서 테스트 화면을 보내는 중 */
  synthetic: boolean
}

/**
 * 카메라·마이크. 권한이 없거나(10초 안에 허용하지 않은 경우 포함) 장치가 없으면(같은 PC에서 두 창이 카메라를 동시에 못 쓰는 경우 등)
 * 움직이는 테스트 화면 + 무음으로 대신한다 — 연결 자체는 시험할 수 있도록.
 */
export async function getLocalMedia(video: boolean, label: string): Promise<LocalMedia> {
  try {
    const request = navigator.mediaDevices.getUserMedia({
      audio: true,
      video: video ? { width: { ideal: 720 }, height: { ideal: 1280 }, facingMode: 'user' } : false,
    })
    // 권한 창에 아무도 답하지 않으면 멈춰 있지 않도록 10초 뒤 테스트 화면으로
    let timedOut = false
    const timeout = new Promise<never>((_, reject) =>
      setTimeout(() => {
        timedOut = true
        reject(new Error('timeout'))
      }, 10_000),
    )
    // 늦게 허용된 카메라는 바로 끈다 (이미 테스트 화면으로 넘어갔으므로)
    request.then((s) => timedOut && stopStream(s)).catch(() => undefined)
    const stream = await Promise.race([request, timeout])
    return { stream, synthetic: false }
  } catch {
    return { stream: syntheticStream(video, label), synthetic: true }
  }
}

function syntheticStream(video: boolean, label: string): MediaStream {
  const tracks: MediaStreamTrack[] = []
  const AudioCtx = window.AudioContext
  if (AudioCtx) {
    const ctx = new AudioCtx()
    const dest = ctx.createMediaStreamDestination()
    const gain = ctx.createGain()
    gain.gain.value = 0
    const osc = ctx.createOscillator()
    osc.connect(gain).connect(dest)
    osc.start()
    tracks.push(...dest.stream.getAudioTracks())
  }
  if (video) {
    const canvas = document.createElement('canvas')
    canvas.width = 360
    canvas.height = 640
    const g = canvas.getContext('2d')!
    const started = performance.now()
    const draw = () => {
      const t = (performance.now() - started) / 1000
      const grad = g.createLinearGradient(0, 0, 360, 640)
      grad.addColorStop(0, `hsl(${(t * 30) % 360} 80% 60%)`)
      grad.addColorStop(1, `hsl(${(t * 30 + 120) % 360} 80% 45%)`)
      g.fillStyle = grad
      g.fillRect(0, 0, 360, 640)
      g.fillStyle = 'rgba(255,255,255,0.9)'
      g.beginPath()
      g.arc(180 + Math.sin(t * 2) * 80, 300 + Math.cos(t * 1.5) * 60, 40, 0, Math.PI * 2)
      g.fill()
      g.font = 'bold 22px sans-serif'
      g.textAlign = 'center'
      g.fillText(label, 180, 480)
      g.font = '15px sans-serif'
      g.fillText('카메라 대신 테스트 화면', 180, 510)
      g.fillText(new Date().toLocaleTimeString('ko-KR'), 180, 535)
    }
    draw()
    const timer = window.setInterval(draw, 66)
    const track = canvas.captureStream(15).getVideoTracks()[0]
    track.addEventListener('ended', () => clearInterval(timer))
    const stop = track.stop.bind(track)
    track.stop = () => {
      clearInterval(timer)
      stop()
    }
    tracks.push(track)
  }
  return new MediaStream(tracks)
}

export function stopStream(stream: MediaStream | null | undefined) {
  stream?.getTracks().forEach((t) => t.stop())
}
