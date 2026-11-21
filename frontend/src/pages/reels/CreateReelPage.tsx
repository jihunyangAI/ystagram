import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Film, TriangleAlert } from 'lucide-react'
import { toast } from '../../components/Toast'
import { CaptionField, MAX_CAPTION, Toggle, countHashtags, useHashtagLimit } from '../../components/post/CaptionField'
import { api } from '../../lib/api'
import type { PostView } from '../../lib/types'

const MAX_MS = 20 * 60_000
const RECOMMEND_MS = 3 * 60_000
const MAX_BYTES = 200 * 1024 * 1024

function formatDuration(ms: number) {
  const s = Math.round(ms / 1000)
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/**
 * 릴스 만들기: 영상 선택 → 커버(첫 화면) 고르기 → 캡션 → 공유.
 * 영상은 변환 없이 올리고, 커버는 고른 순간의 화면을 브라우저에서 JPEG로 만든다.
 * 3분이 넘으면 팔로워가 아닌 사람에게 추천되지 않고, 20분이 넘으면 올릴 수 없다 (기획서 §6.3).
 */
export function CreateReelPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [url, setUrl] = useState<string | null>(null)
  const [duration, setDuration] = useState(0)
  const [coverAt, setCoverAt] = useState(0)
  const [caption, setCaption] = useState('')
  const [hideLikes, setHideLikes] = useState(false)
  const [noComments, setNoComments] = useState(false)
  const [sharing, setSharing] = useState(false)
  const video = useRef<HTMLVideoElement>(null)
  const input = useRef<HTMLInputElement>(null)

  useEffect(() => () => {
    if (url) URL.revokeObjectURL(url)
  }, [url])

  const pick = (f: File | undefined) => {
    if (!f) return
    if (!['video/mp4', 'video/webm', 'video/quicktime'].includes(f.type)) return toast('MP4 또는 WebM 영상만 올릴 수 있습니다')
    if (f.size > MAX_BYTES) return toast('영상은 200MB까지 올릴 수 있습니다')
    setFile(f)
    setUrl(URL.createObjectURL(f))
    setCoverAt(0)
  }

  const seek = (ms: number) => {
    setCoverAt(ms)
    if (video.current) video.current.currentTime = ms / 1000
  }

  /** 지금 보이는 화면을 커버 사진으로 (세로 9:16, 긴 변 1080px) */
  const capturePoster = async (): Promise<Blob> => {
    const v = video.current!
    const w = v.videoWidth || 360
    const h = v.videoHeight || 640
    const scale = Math.min(1, 1080 / Math.max(w, h))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(w * scale)
    canvas.height = Math.round(h * scale)
    canvas.getContext('2d')!.drawImage(v, 0, 0, canvas.width, canvas.height)
    return new Promise((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('커버를 만들지 못했습니다'))), 'image/jpeg', 0.88),
    )
  }

  const tooLong = duration > MAX_MS
  const hashtagLimit = useHashtagLimit()
  const captionInvalid = caption.length > MAX_CAPTION || countHashtags(caption) > hashtagLimit

  const share = async () => {
    if (!file || sharing || tooLong || captionInvalid) return
    setSharing(true)
    try {
      const form = new FormData()
      form.append('video', file, file.name)
      form.append('poster', await capturePoster(), 'cover.jpg')
      const data = { caption, durationMs: Math.round(duration), hideLikeCount: hideLikes, commentsDisabled: noComments }
      form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
      const reel = await api.upload<PostView>('POST', '/api/reels', form)
      qc.invalidateQueries({ queryKey: ['grid'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
      toast('릴스를 공유했습니다')
      navigate(`/reels/${reel.shortcode}`)
    } catch (e) {
      toast((e as Error).message)
    } finally {
      setSharing(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-[935px] flex-col items-center gap-4 px-4 py-4 md:py-8">
      <header className="flex w-full max-w-[720px] items-center justify-between">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-1">
          <ChevronLeft size={26} />
        </button>
        <h1 className="font-bold">새 릴스</h1>
        <button
          onClick={share}
          disabled={!file || sharing || tooLong || captionInvalid || !duration}
          className="rounded-lg bg-primary px-4 py-1.5 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-40"
        >
          {sharing ? '올리는 중…' : '공유'}
        </button>
      </header>
      <input ref={input} type="file" accept="video/mp4,video/webm,video/quicktime" hidden onChange={(e) => pick(e.target.files?.[0])} />

      {!url ? (
        <button
          onClick={() => input.current?.click()}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            pick(e.dataTransfer.files[0])
          }}
          className="flex aspect-[9/16] w-full max-w-[320px] flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-border text-muted hover:bg-surface-2"
        >
          <Film size={48} strokeWidth={1.4} />
          <span className="text-sm">영상을 선택하거나 끌어다 놓으세요</span>
          <span className="text-xs">MP4·WebM, 20분·200MB까지</span>
          <span className="rounded-lg bg-primary px-4 py-1.5 text-sm font-semibold text-white">컴퓨터에서 선택</span>
        </button>
      ) : (
        <div className="flex w-full max-w-[720px] flex-col gap-6 md:flex-row">
          <div className="w-full max-w-[320px] self-center md:self-start">
            <video
              ref={video}
              src={url}
              muted
              playsInline
              preload="auto"
              onLoadedMetadata={(e) => setDuration(e.currentTarget.duration * 1000)}
              className="aspect-[9/16] w-full rounded-xl bg-black object-cover"
            />
            <label className="mt-3 block text-sm">
              <span className="mb-1 block font-semibold">커버 (첫 화면) 고르기</span>
              <input
                type="range"
                min={0}
                max={Math.max(0, duration - 100)}
                step={100}
                value={coverAt}
                onChange={(e) => seek(Number(e.target.value))}
                className="w-full"
              />
              <span className="text-xs text-muted">
                {formatDuration(coverAt)} / {formatDuration(duration)}
              </span>
            </label>
          </div>
          <div className="flex min-w-0 flex-1 flex-col gap-4">
            {duration > RECOMMEND_MS && !tooLong && (
              <p className="flex gap-2 rounded-lg bg-surface-2 p-3 text-xs text-muted">
                <TriangleAlert size={16} className="shrink-0" />
                3분이 넘는 릴스는 팔로워에게만 보이고, 팔로우하지 않는 사람에게는 추천되지 않습니다.
              </p>
            )}
            {tooLong && <p className="rounded-lg bg-surface-2 p-3 text-xs text-danger">릴스는 20분까지 올릴 수 있습니다.</p>}
            <CaptionField value={caption} onChange={setCaption} />
            <Toggle checked={hideLikes} onChange={setHideLikes} label="좋아요 수 숨기기" />
            <Toggle checked={noComments} onChange={setNoComments} label="댓글 기능 해제" />
            <button className="self-start text-sm text-muted underline" onClick={() => input.current?.click()}>
              다른 영상 선택
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
