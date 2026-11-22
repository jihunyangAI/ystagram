import { useState } from 'react'
import { Link } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { Music2, Pause, Play, Search, X } from 'lucide-react'
import clsx from 'clsx'
import { api } from '../../lib/api'
import { audioLabel, playTrack, stopTrack, usePlayingTrack, useStopOnUnmount } from '../../lib/music'
import type { AudioRef } from '../../lib/types'
import { Modal } from '../Modal'

export function PlayButton({ track, className, size = 16 }: { track: AudioRef; className?: string; size?: number }) {
  const playing = usePlayingTrack() === track.id
  return (
    <button
      type="button"
      aria-label={playing ? `${track.title} 멈춤` : `${track.title} 미리 듣기`}
      onClick={(e) => {
        e.preventDefault()
        e.stopPropagation()
        if (playing) stopTrack()
        else playTrack(track)
      }}
      className={clsx('grid shrink-0 place-items-center rounded-full', className)}
    >
      {playing ? <Pause size={size} className="fill-current" /> : <Play size={size} className="fill-current" />}
    </button>
  )
}

/** 음악 고르기 창: 검색 + 미리 듣기 + 선택 */
export function MusicPicker({ open, onClose, onPick }: { open: boolean; onClose: () => void; onPick: (a: AudioRef) => void }) {
  const [q, setQ] = useState('')
  const { data, isLoading } = useQuery({
    queryKey: ['audio-search', q.trim()],
    queryFn: () => api.get<AudioRef[]>(`/api/audio?q=${encodeURIComponent(q.trim())}`),
    enabled: open,
    staleTime: 60_000,
  })
  return (
    <Modal
      open={open}
      onClose={() => {
        stopTrack()
        onClose()
      }}
      title="음악"
      sheet
      className="h-[75dvh] md:h-[70dvh]"
    >
      {open && <StopOnClose />}
      <label className="m-3 flex items-center gap-2 rounded-lg bg-surface-2 px-3 py-2 text-sm">
        <Search size={16} className="text-muted" />
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="음악 검색"
          aria-label="음악 검색"
          className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-muted"
        />
      </label>
      <p className="px-4 pb-1 text-xs text-muted">연구용 가짜 음원입니다. 미리 듣기 소리는 화면에서 합성한 짧은 멜로디예요.</p>
      <ul className="min-h-0 flex-1 overflow-y-auto pb-3">
        {isLoading && <li className="p-6 text-center text-sm text-muted">불러오는 중…</li>}
        {data?.length === 0 && <li className="p-6 text-center text-sm text-muted">검색 결과가 없습니다.</li>}
        {data?.map((a) => (
          <li key={a.id} className="flex items-center gap-3 px-4 py-2 hover:bg-surface-2">
            <span className="grid size-11 shrink-0 place-items-center rounded-md bg-gradient-to-br from-primary/80 to-primary/30 text-white">
              <Music2 size={20} />
            </span>
            <button
              className="min-w-0 flex-1 text-left"
              onClick={() => {
                stopTrack()
                onPick(a)
                onClose()
              }}
            >
              <b className="block truncate text-sm">{a.title}</b>
              <span className="block truncate text-xs text-muted">
                {a.original ? `${a.artist} · 원본 오디오` : a.artist} · {Math.round(a.durationMs / 1000)}초
              </span>
            </button>
            <PlayButton track={a} className="size-8 bg-surface-2" />
          </li>
        ))}
      </ul>
    </Modal>
  )
}

function StopOnClose() {
  useStopOnUnmount()
  return null
}

/** 만들기 화면의 "음악 추가" 줄 */
export function MusicField({ value, onChange }: { value: AudioRef | null; onChange: (a: AudioRef | null) => void }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="flex items-center gap-2 border-t border-border pt-4 text-sm">
      <Music2 size={18} className="shrink-0" />
      {value ? (
        <>
          <button className="min-w-0 flex-1 truncate text-left" onClick={() => setOpen(true)}>
            {audioLabel(value)}
          </button>
          <PlayButton track={value} className="size-7 bg-surface-2" size={13} />
          <button aria-label="음악 빼기" onClick={() => onChange(null)}>
            <X size={16} />
          </button>
        </>
      ) : (
        <button className="flex-1 text-left text-muted" onClick={() => setOpen(true)}>
          음악 추가
        </button>
      )}
      <MusicPicker open={open} onClose={() => setOpen(false)} onPick={onChange} />
    </div>
  )
}

/** 게시물·릴스·스토리에 붙는 음악 표시 (오디오 페이지로) */
export function AudioChip({ audio, className }: { audio: AudioRef; className?: string }) {
  return (
    <Link to={`/audio/${audio.id}`} className={clsx('flex min-w-0 items-center gap-1', className)} aria-label={`오디오: ${audioLabel(audio)}`}>
      <Music2 size={12} className="shrink-0" />
      <span className="truncate">{audioLabel(audio)}</span>
    </Link>
  )
}
