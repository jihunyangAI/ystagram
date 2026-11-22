import { useEffect, useRef, useState } from 'react'
import { Circle, Square } from 'lucide-react'
import { Modal } from '../Modal'
import { getLocalMedia, stopStream } from '../../lib/rtc'

const MAX_MS = 15_000

/**
 * 카메라로 짧은 영상 찍기 (최대 15초, WebM). 카메라가 없으면 테스트 화면을 찍는다.
 * MediaRecorder가 만든 WebM에는 길이 정보가 없어서, 찍은 시간을 함께 돌려준다.
 */
export function VideoRecorder({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: (file: File, ms: number) => void }) {
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [recording, setRecording] = useState(false)
  const [elapsed, setElapsed] = useState(0)
  const recorder = useRef<MediaRecorder | null>(null)
  const started = useRef(0)

  useEffect(() => {
    if (!open) return
    let s: MediaStream | null = null
    let alive = true
    void getLocalMedia(true, '리믹스').then((m) => {
      if (!alive) return stopStream(m.stream)
      s = m.stream
      setStream(m.stream)
    })
    return () => {
      alive = false
      if (recorder.current?.state === 'recording') recorder.current.stop()
      stopStream(s)
      setStream(null)
      setRecording(false)
      setElapsed(0)
    }
  }, [open])

  useEffect(() => {
    if (!recording) return
    const t = setInterval(() => {
      const ms = Date.now() - started.current
      setElapsed(ms)
      if (ms >= MAX_MS) recorder.current?.stop()
    }, 100)
    return () => clearInterval(t)
  }, [recording])

  function start() {
    if (!stream) return
    const type = MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus') ? 'video/webm;codecs=vp8,opus' : 'video/webm'
    const rec = new MediaRecorder(stream, { mimeType: type })
    const chunks: Blob[] = []
    rec.ondataavailable = (e) => e.data.size > 0 && chunks.push(e.data)
    rec.onstop = () => {
      const ms = Math.min(MAX_MS, Date.now() - started.current)
      setRecording(false)
      if (ms < 500) return
      onDone(new File(chunks, `remix-${Date.now()}.webm`, { type: 'video/webm' }), ms)
    }
    recorder.current = rec
    started.current = Date.now()
    rec.start(250)
    setRecording(true)
  }

  return (
    <Modal open={open} onClose={onClose} title="카메라로 찍기">
      <div className="space-y-3 p-4">
        <div className="relative mx-auto aspect-[9/16] w-full max-w-[260px] overflow-hidden rounded-xl bg-black">
          <video
            autoPlay
            muted
            playsInline
            className="h-full w-full object-cover"
            style={{ transform: 'scaleX(-1)' }}
            ref={(el) => {
              if (el && stream && el.srcObject !== stream) el.srcObject = stream
            }}
          />
          {recording && (
            <span className="absolute top-2 left-2 rounded bg-red-500 px-1.5 py-0.5 text-xs font-semibold text-white tabular-nums">
              ● {(elapsed / 1000).toFixed(1)}초
            </span>
          )}
        </div>
        <div className="h-1 overflow-hidden rounded bg-surface-2">
          <div className="h-full bg-red-500" style={{ width: `${Math.min(100, (elapsed / MAX_MS) * 100)}%` }} />
        </div>
        <button
          className="mx-auto flex items-center gap-2 rounded-full bg-red-500 px-5 py-2 text-sm font-semibold text-white disabled:opacity-50"
          disabled={!stream}
          onClick={() => (recording ? recorder.current?.stop() : start())}
        >
          {recording ? <Square size={16} fill="currentColor" /> : <Circle size={16} fill="currentColor" />}
          {recording ? '그만 찍기' : '녹화 시작 (최대 15초)'}
        </button>
      </div>
    </Modal>
  )
}
