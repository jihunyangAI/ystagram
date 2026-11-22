import { useEffect, useState } from 'react'
import type { AudioRef } from './types'

/**
 * 가짜 음원 재생기. 실제 소리 파일 없이 곡의 seed·bpm으로 짧은 멜로디를 Web Audio로 합성한다
 * (같은 곡은 언제나 같은 멜로디). 한 번에 한 곡만, 최대 30초.
 */

const SCALES = [
  [0, 2, 4, 7, 9], // 장조 5음
  [0, 3, 5, 7, 10], // 단조 5음
  [0, 2, 3, 7, 8],
]

function rng(seed: number) {
  let s = seed >>> 0 || 1
  return () => {
    s ^= s << 13
    s ^= s >>> 17
    s ^= s << 5
    return (s >>> 0) / 4294967296
  }
}

let ctx: AudioContext | null = null
let current: { id: number; stop: () => void } | null = null
const listeners = new Set<(id: number | null) => void>()

function emit() {
  for (const l of listeners) l(current?.id ?? null)
}

export function stopTrack() {
  current?.stop()
  current = null
  emit()
}

export function playTrack(track: Pick<AudioRef, 'id' | 'seed' | 'bpm' | 'durationMs'>, maxMs = 30_000) {
  stopTrack()
  ctx ??= new AudioContext()
  const ac = ctx
  void ac.resume()
  const r = rng(track.seed + 1)
  const scale = SCALES[track.seed % SCALES.length]
  const root = 52 + (track.seed % 12) // MIDI
  const beat = 60 / Math.max(60, Math.min(160, track.bpm))
  const total = Math.min(track.durationMs || 15_000, maxMs) / 1000
  // 4마디 동기를 만들어 반복 (조금씩 변주)
  const motif = Array.from({ length: 8 }, () => ({ step: Math.floor(r() * scale.length), oct: r() < 0.25 ? 12 : 0, len: r() < 0.3 ? 2 : 1 }))
  const master = ac.createGain()
  master.gain.value = 0.18
  master.connect(ac.destination)
  const t0 = ac.currentTime + 0.05
  const nodes: AudioScheduledSourceNode[] = []
  const note = (midi: number, at: number, dur: number, type: OscillatorType, vol: number) => {
    const o = ac.createOscillator()
    const g = ac.createGain()
    o.type = type
    o.frequency.value = 440 * 2 ** ((midi - 69) / 12)
    g.gain.setValueAtTime(0, at)
    g.gain.linearRampToValueAtTime(vol, at + 0.02)
    g.gain.exponentialRampToValueAtTime(0.001, at + dur)
    o.connect(g).connect(master)
    o.start(at)
    o.stop(at + dur + 0.05)
    nodes.push(o)
  }
  let t = 0
  let i = 0
  while (t < total) {
    const m = motif[i % motif.length]
    const vary = i >= motif.length && r() < 0.2 ? 1 : 0
    const deg = scale[(m.step + vary) % scale.length]
    const len = m.len * beat * 0.5
    note(root + 12 + deg + m.oct, t0 + t, len * 0.95, 'triangle', 0.7)
    if (i % 4 === 0) note(root - 12 + scale[(Math.floor(i / 8) * 2) % scale.length], t0 + t, beat * 1.8, 'sine', 0.9)
    t += len
    i++
  }
  const timer = window.setTimeout(() => {
    if (current?.id === track.id) stopTrack()
  }, total * 1000 + 300)
  current = {
    id: track.id,
    stop: () => {
      window.clearTimeout(timer)
      master.gain.cancelScheduledValues(ac.currentTime)
      master.gain.setTargetAtTime(0, ac.currentTime, 0.03)
      window.setTimeout(() => {
        nodes.forEach((n) => {
          try {
            n.stop()
          } catch {
            /* 이미 끝남 */
          }
        })
        master.disconnect()
      }, 120)
    },
  }
  emit()
}

/** 지금 재생 중인 곡 id */
export function usePlayingTrack() {
  const [id, setId] = useState<number | null>(current?.id ?? null)
  useEffect(() => {
    listeners.add(setId)
    return () => {
      listeners.delete(setId)
    }
  }, [])
  return id
}

/** 화면을 떠나면 멈춤 */
export function useStopOnUnmount() {
  useEffect(() => () => stopTrack(), [])
}

export function audioLabel(a: AudioRef) {
  return a.original ? `${a.artist} · 원본 오디오` : `${a.artist} · ${a.title}`
}
