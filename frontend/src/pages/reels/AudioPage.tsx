import { Link, useNavigate, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, Clapperboard, Music2 } from 'lucide-react'
import { api } from '../../lib/api'
import { useStopOnUnmount } from '../../lib/music'
import type { AudioRef } from '../../lib/types'
import { InfiniteGrid } from '../../components/post/Grid'
import { PlayButton } from '../../components/music/MusicPicker'

interface AudioPageData {
  audio: AudioRef
  genre: string | null
  originShortcode: string | null
  reelCount: number
}

/** 오디오 페이지: 이 소리를 쓴 릴스 모음 + "이 오디오 사용" */
export function AudioPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  useStopOnUnmount()
  const { data, isError, error } = useQuery({
    queryKey: ['audio', id],
    queryFn: () => api.get<AudioPageData>(`/api/audio/${id}`),
  })
  if (isError) return <p className="p-10 text-center text-sm text-muted">{error.message}</p>
  if (!data) return <p className="p-10 text-center text-sm text-muted">불러오는 중…</p>
  const a = data.audio
  return (
    <div className="mx-auto max-w-[935px] pb-8">
      <header className="flex items-center gap-2 px-2 pt-4 pb-2 md:pt-8">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-2">
          <ChevronLeft size={26} />
        </button>
        <h1 className="font-bold">오디오</h1>
      </header>
      <section className="flex items-center gap-4 px-4 pb-5">
        <div className="relative grid size-28 shrink-0 place-items-center rounded-lg bg-gradient-to-br from-primary to-primary/40 text-white md:size-36">
          <Music2 size={44} />
          <PlayButton track={a} className="absolute right-2 bottom-2 size-9 bg-black/50 text-white" size={16} />
        </div>
        <div className="min-w-0">
          <h2 className="truncate text-xl font-bold">{a.title}</h2>
          <p className="truncate text-sm">
            {a.original ? (
              <Link to={`/${a.artist}`} className="font-semibold">
                {a.artist}
              </Link>
            ) : (
              a.artist
            )}
            {data.genre && <span className="text-muted"> · {data.genre}</span>}
          </p>
          <p className="mt-1 text-sm text-muted">릴스 {data.reelCount.toLocaleString('ko-KR')}개</p>
          {data.originShortcode && (
            <Link to={`/reels/${data.originShortcode}`} className="mt-1 inline-block text-xs font-semibold text-primary">
              원본 릴스 보기
            </Link>
          )}
        </div>
      </section>
      <div className="px-4 pb-4">
        <Link to={`/create/reel?audio=${a.id}`} className="btn btn-primary w-full md:w-auto">
          <Clapperboard size={16} /> 이 오디오 사용
        </Link>
      </div>
      <InfiniteGrid
        queryKey={['audio-reels', id]}
        url={`/api/audio/${id}/reels`}
        variant="reels"
        empty={<p className="py-12 text-center text-sm text-muted">아직 이 오디오를 쓴 릴스가 없습니다.</p>}
      />
    </div>
  )
}
