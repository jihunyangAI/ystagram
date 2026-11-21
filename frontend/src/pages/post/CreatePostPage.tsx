import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, ImagePlus, MapPin, Plus, X, ZoomIn } from 'lucide-react'
import clsx from 'clsx'
import { Avatar } from '../../components/Avatar'
import { CaptionField, MAX_CAPTION, Toggle, countHashtags, useHashtagLimit } from '../../components/post/CaptionField'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import type { LocationItem, PostView, ScheduledPost, UserSummary } from '../../lib/types'
import { fromLocalInput, laterInput, scheduleLabel } from '../../lib/datetime'
import { EditStep } from './EditStep'
import { NO_ADJUST, cssFilter, drawVignette, vignetteStyle } from './filters'
import type { Adjust } from './filters'
import { CollaboratorField, UserSearchBox } from './PeopleFields'
import type { DraftTag } from './PeopleFields'

const MAX_FILES = 20
const OUTPUT_WIDTH = 1080

/** 자르기 비율 (분석 문서 §6: 1:1, 4:5, 3:4, 1.91:1) */
const RATIOS = [
  { label: '1:1', value: 1 },
  { label: '4:5', value: 4 / 5 },
  { label: '3:4', value: 3 / 4 },
  { label: '1.91:1', value: 1.91 },
]

interface Picked {
  id: string
  url: string
  img: HTMLImageElement
  zoom: number
  /** 화면 틀 크기에 대한 이동량 비율 (-0.5 ~ 0.5 근처) */
  fx: number
  fy: number
  alt: string
  /** ③ 편집: 필터 이름과 조정 값 */
  filter: string
  adjust: Adjust
}

type Step = 'select' | 'crop' | 'edit' | 'details'

function loadImage(file: File): Promise<Picked> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => resolve({ id: url, url, img, zoom: 1, fx: 0, fy: 0, alt: '', filter: 'normal', adjust: NO_ADJUST })
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error(`${file.name}: 열 수 없는 사진입니다`))
    }
    img.src = url
  })
}

/** 틀(W×H)을 꽉 채우는(cover) 크기 × 확대, 그리고 이동 범위 안으로 제한한 위치 */
function layout(p: Picked, W: number, H: number) {
  const scale = Math.max(W / p.img.naturalWidth, H / p.img.naturalHeight) * p.zoom
  const dw = p.img.naturalWidth * scale
  const dh = p.img.naturalHeight * scale
  const maxX = (dw - W) / 2
  const maxY = (dh - H) / 2
  const px = Math.max(-maxX, Math.min(maxX, p.fx * W))
  const py = Math.max(-maxY, Math.min(maxY, p.fy * H))
  return { dw, dh, x: (W - dw) / 2 + px, y: (H - dh) / 2 + py, px, py }
}

/** 보이는 대로 잘라서 JPEG로 만든다 (긴 변 1080px) */
function render(p: Picked, ratio: number): Promise<Blob> {
  const W = OUTPUT_WIDTH
  const H = Math.round(OUTPUT_WIDTH / ratio)
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')!
  ctx.imageSmoothingQuality = 'high'
  const l = layout(p, W, H)
  ctx.filter = cssFilter(p.filter, p.adjust)
  ctx.drawImage(p.img, l.x, l.y, l.dw, l.dh)
  ctx.filter = 'none'
  drawVignette(ctx, W, H, p.adjust.vignette)
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('사진을 만들지 못했습니다'))), 'image/jpeg', 0.9),
  )
}

/**
 * 새 게시물 만들기: ① 사진 선택(최대 20장) → ② 자르기(비율·확대·위치) → ③ 필터·조정
 * → ④ 문구·위치·사람 태그·공동 작업자·고급 설정 → 공유
 */
export function CreatePostPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data: me } = useMe()
  const [step, setStep] = useState<Step>('select')
  const [items, setItems] = useState<Picked[]>([])
  const [current, setCurrent] = useState(0)
  const [ratio, setRatio] = useState(RATIOS[1].value)
  const [caption, setCaption] = useState('')
  const [location, setLocation] = useState('')
  const [hideLikeCount, setHide] = useState(false)
  const [commentsDisabled, setNoComments] = useState(false)
  const [aiLabel, setAiLabel] = useState(false)
  const [scheduleOn, setScheduleOn] = useState(false)
  const [scheduleAt, setScheduleAt] = useState(() => laterInput(60))
  const [sharing, setSharing] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [tags, setTags] = useState<DraftTag[]>([])
  const [pendingTag, setPendingTag] = useState<{ mediaIndex: number; x: number; y: number } | null>(null)
  const [collaborators, setCollaborators] = useState<UserSummary[]>([])
  const [previewIndex, setPreviewIndex] = useState(0)
  const fileInput = useRef<HTMLInputElement>(null)

  // 화면을 떠나면 미리보기 주소 정리
  const urls = useRef<string[]>([])
  useEffect(() => {
    urls.current = items.map((i) => i.url)
  }, [items])
  useEffect(() => () => urls.current.forEach((u) => URL.revokeObjectURL(u)), [])

  const addFiles = async (files: FileList | File[]) => {
    const list = Array.from(files).filter((f) => f.type.startsWith('image/'))
    const room = MAX_FILES - items.length
    if (list.length > room) toast(`사진은 최대 ${MAX_FILES}장까지 선택할 수 있습니다`)
    const loaded: Picked[] = []
    for (const f of list.slice(0, Math.max(0, room))) {
      try {
        loaded.push(await loadImage(f))
      } catch (e) {
        toast((e as Error).message)
      }
    }
    if (loaded.length === 0) return
    if (items.length === 0) {
      // 첫 장 비율에 가장 가까운 허용 비율을 고른다
      const r = loaded[0].img.naturalWidth / loaded[0].img.naturalHeight
      setRatio(RATIOS.reduce((a, b) => (Math.abs(b.value - r) < Math.abs(a.value - r) ? b : a)).value)
    }
    setItems((prev) => [...prev, ...loaded])
    setStep('crop')
  }

  const update = (index: number, patch: Partial<Picked>) =>
    setItems((prev) => prev.map((p, i) => (i === index ? { ...p, ...patch } : p)))

  const removeAt = (index: number) => {
    URL.revokeObjectURL(items[index].url)
    const next = items.filter((_, i) => i !== index)
    setItems(next)
    setCurrent((c) => Math.min(c, Math.max(0, next.length - 1)))
    if (next.length === 0) setStep('select')
  }

  const hashtagLimit = useHashtagLimit()
  const tooManyTags = countHashtags(caption) > hashtagLimit
  const tooLong = caption.length > MAX_CAPTION

  const share = async () => {
    if (tooManyTags || tooLong) return
    setSharing(true)
    try {
      const form = new FormData()
      for (let i = 0; i < items.length; i++) {
        form.append('files', await render(items[i], ratio), `photo-${i + 1}.jpg`)
      }
      const data = {
        caption,
        location,
        altTexts: items.map((i) => i.alt),
        hideLikeCount,
        commentsDisabled,
        tags: tags.map((t) => ({ username: t.user.username, mediaIndex: t.mediaIndex, x: t.x, y: t.y })),
        collaborators: collaborators.map((u) => u.username),
        filter: items[0]?.filter === 'normal' ? null : items[0]?.filter,
        aiLabel,
        scheduledAt: scheduleOn ? new Date(fromLocalInput(scheduleAt)).toISOString() : null,
      }
      form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
      if (scheduleOn) {
        const s = await api.upload<ScheduledPost>('POST', '/api/posts/scheduled', form)
        qc.invalidateQueries({ queryKey: ['scheduled'] })
        toast(`${scheduleLabel(s.scheduledAt)}에 게시되도록 예약했습니다`)
        navigate('/accounts/scheduled')
        return
      }
      const post = await api.upload<PostView>('POST', '/api/posts', form)
      qc.invalidateQueries({ queryKey: ['feed'] })
      qc.invalidateQueries({ queryKey: ['grid'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
      toast('게시물이 공유되었습니다')
      navigate(`/p/${post.shortcode}`)
    } catch (e) {
      toast((e as Error).message)
      setSharing(false)
    }
  }

  const back = () => {
    if (step === 'details') setStep('edit')
    else if (step === 'edit') setStep('crop')
    else if (step === 'crop') {
      items.forEach((i) => URL.revokeObjectURL(i.url))
      setItems([])
      setStep('select')
    } else navigate(-1)
  }

  const title = step === 'select' ? '새 게시물 만들기' : step === 'crop' ? '자르기' : step === 'edit' ? '편집' : '새 게시물 만들기'
  const next =
    step === 'crop' || step === 'edit' ? (
      <button className="font-semibold text-primary hover:text-text" onClick={() => setStep(step === 'crop' ? 'edit' : 'details')}>
        다음
      </button>
    ) : step === 'details' ? (
      <button
        className="font-semibold text-primary hover:text-text disabled:opacity-40"
        disabled={sharing || tooManyTags || tooLong}
        onClick={share}
      >
        {sharing ? (scheduleOn ? '예약 중…' : '공유 중…') : scheduleOn ? '예약하기' : '공유하기'}
      </button>
    ) : (
      <span className="w-10" />
    )

  return (
    <div className="mx-auto flex w-full max-w-[900px] flex-col md:px-4 md:py-8">
      <div className="flex flex-col overflow-hidden bg-surface md:rounded-xl md:border md:border-border md:shadow-sm">
        <header className="flex h-11 items-center justify-between border-b border-border px-3">
          <button aria-label="뒤로" onClick={back} className="p-1">
            {step === 'select' ? <X size={24} /> : <ChevronLeft size={26} />}
          </button>
          <h1 className="font-semibold">{title}</h1>
          {next}
        </header>

        {step === 'select' && (
          <div
            onDragOver={(e) => {
              e.preventDefault()
              setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault()
              setDragging(false)
              addFiles(e.dataTransfer.files)
            }}
            className={clsx(
              'flex min-h-[60dvh] flex-col items-center justify-center gap-4 p-8 text-center md:min-h-[520px]',
              dragging && 'bg-primary-soft',
            )}
          >
            <ImagePlus size={72} strokeWidth={1} />
            <p className="text-xl">사진을 여기에 끌어다 놓으세요</p>
            <p className="text-sm text-muted">JPG·PNG, 최대 {MAX_FILES}장</p>
            <button className="btn btn-primary" onClick={() => fileInput.current?.click()}>
              컴퓨터에서 선택
            </button>
          </div>
        )}

        {step === 'crop' && items[current] && (
          <CropStep
            items={items}
            current={current}
            setCurrent={setCurrent}
            ratio={ratio}
            setRatio={setRatio}
            update={update}
            removeAt={removeAt}
            onAdd={() => fileInput.current?.click()}
          />
        )}

        {step === 'edit' && items[current] && (
          <EditStep
            items={items}
            current={current}
            setCurrent={setCurrent}
            update={update}
            preview={(style, overlay) => <Preview items={items} ratio={ratio} index={current} setIndex={setCurrent} imgStyle={style} overlay={overlay} />}
          />
        )}

        {step === 'details' && me && (
          <div className="flex flex-col md:flex-row">
            <div className="md:w-[55%] md:shrink-0">
              <Preview
                items={items}
                ratio={ratio}
                index={previewIndex}
                setIndex={setPreviewIndex}
                tags={tags.filter((t) => t.mediaIndex === previewIndex)}
                pending={pendingTag?.mediaIndex === previewIndex ? pendingTag : null}
                onTap={(x, y) => setPendingTag({ mediaIndex: previewIndex, x, y })}
              />
              <p className="px-3 py-2 text-center text-xs text-muted">사진을 누르면 그 자리에 사람을 태그할 수 있습니다.</p>
            </div>
            <div className="flex-1 space-y-5 p-4 md:max-h-[640px] md:overflow-y-auto md:border-l md:border-border">
              <div className="flex items-center gap-2 text-sm font-semibold">
                <Avatar user={me.user} size={28} /> {me.user.username}
              </div>
              <CaptionField value={caption} onChange={setCaption} autoFocus />
              <LocationField value={location} onChange={setLocation} />
              <section className="border-t border-border pt-4">
                <h2 className="mb-2 text-sm font-semibold">사람 태그 ({tags.length}/20)</h2>
                {pendingTag ? (
                  <div className="space-y-1">
                    <UserSearchBox
                      autoFocus
                      placeholder="태그할 사람 검색"
                      exclude={tags.map((t) => t.user.id)}
                      onPick={(u) => {
                        setTags((t) => [...t, { user: u, ...pendingTag }])
                        setPendingTag(null)
                      }}
                    />
                    <button className="text-xs text-muted" onClick={() => setPendingTag(null)}>
                      취소
                    </button>
                  </div>
                ) : (
                  <p className="text-xs text-muted">왼쪽 사진에서 태그할 위치를 누르세요.</p>
                )}
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {tags.map((t) => (
                    <span key={t.user.id} className="flex items-center gap-1 rounded-full bg-surface-2 px-2.5 py-1 text-xs">
                      {t.user.username}
                      {items.length > 1 && <span className="text-muted">· {t.mediaIndex + 1}번째</span>}
                      <button aria-label={`${t.user.username} 태그 삭제`} onClick={() => setTags((x) => x.filter((y) => y.user.id !== t.user.id))}>
                        <X size={12} />
                      </button>
                    </span>
                  ))}
                </div>
              </section>
              <section className="border-t border-border pt-4">
                <h2 className="mb-2 text-sm font-semibold">공동 작업자 초대</h2>
                <CollaboratorField value={collaborators} onChange={setCollaborators} meId={me.user.id} />
              </section>
              <details className="border-t border-border pt-4">
                <summary className="cursor-pointer text-sm font-semibold">접근성</summary>
                <p className="mt-2 text-xs text-muted">
                  대체 텍스트는 사진을 볼 수 없는 사람을 위해 사진을 설명합니다. 화면 낭독기가 읽어 줍니다.
                </p>
                <div className="mt-3 space-y-2">
                  {items.map((p, i) => (
                    <div key={p.id} className="flex items-center gap-2">
                      <img src={p.url} alt="" className="h-11 w-11 shrink-0 rounded object-cover" />
                      <input
                        className="input"
                        value={p.alt}
                        maxLength={200}
                        onChange={(e) => update(i, { alt: e.target.value })}
                        placeholder="대체 텍스트 입력…"
                        aria-label={`${i + 1}번째 사진의 대체 텍스트`}
                      />
                    </div>
                  ))}
                </div>
              </details>
              <details className="border-t border-border pt-4" open>
                <summary className="cursor-pointer text-sm font-semibold">고급 설정</summary>
                <div className="mt-3 space-y-4">
                  <Toggle
                    checked={hideLikeCount}
                    onChange={setHide}
                    label="이 게시물의 좋아요 수 숨기기"
                    help="회원님만 이 게시물의 좋아요 수를 볼 수 있습니다."
                  />
                  <Toggle checked={commentsDisabled} onChange={setNoComments} label="댓글 기능 해제" help="나중에 게시물 메뉴에서 바꿀 수 있습니다." />
                  <Toggle
                    checked={aiLabel}
                    onChange={setAiLabel}
                    label="AI 정보 라벨 추가"
                    help="AI로 만들었거나 크게 수정한 사진이면 켜 주세요. 게시물에 'AI 정보'가 표시됩니다."
                  />
                </div>
              </details>
              <div className="border-t border-border pt-4">
                <Toggle
                  checked={scheduleOn}
                  onChange={setScheduleOn}
                  label="이 게시물 예약"
                  help="정한 시각에 자동으로 게시됩니다 (10분 뒤 ~ 75일 안). Lab 시간 가속으로 바로 확인할 수 있어요."
                />
                {scheduleOn && (
                  <input
                    type="datetime-local"
                    className="input mt-3"
                    value={scheduleAt}
                    min={laterInput(10)}
                    onChange={(e) => setScheduleAt(e.target.value)}
                    aria-label="게시할 날짜와 시각"
                  />
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      <input
        ref={fileInput}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        hidden
        onChange={(e) => {
          if (e.target.files) addFiles(e.target.files)
          e.target.value = ''
        }}
      />
    </div>
  )
}

// ------------------------------------------------------------ ② 자르기

function CropStep({
  items,
  current,
  setCurrent,
  ratio,
  setRatio,
  update,
  removeAt,
  onAdd,
}: {
  items: Picked[]
  current: number
  setCurrent: (i: number) => void
  ratio: number
  setRatio: (r: number) => void
  update: (i: number, p: Partial<Picked>) => void
  removeAt: (i: number) => void
  onAdd: () => void
}) {
  const frame = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState({ w: 0, h: 0 })
  const drag = useRef<{ x: number; y: number; fx: number; fy: number } | null>(null)
  const p = items[current]

  useEffect(() => {
    const el = frame.current
    if (!el) return
    const ro = new ResizeObserver(() => setSize({ w: el.clientWidth, h: el.clientHeight }))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const l = size.w ? layout(p, size.w, size.h) : null

  const onPointerDown = (e: React.PointerEvent) => {
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
    drag.current = { x: e.clientX, y: e.clientY, fx: l ? l.px / size.w : 0, fy: l ? l.py / size.h : 0 }
  }
  const onPointerMove = (e: React.PointerEvent) => {
    if (!drag.current || !size.w) return
    update(current, {
      fx: drag.current.fx + (e.clientX - drag.current.x) / size.w,
      fy: drag.current.fy + (e.clientY - drag.current.y) / size.h,
    })
  }
  const onPointerUp = () => {
    drag.current = null
    // 범위를 벗어난 이동량은 실제 보이는 위치로 정리
    if (l) update(current, { fx: l.px / size.w, fy: l.py / size.h })
  }

  return (
    <div className="flex flex-col items-center gap-4 p-4">
      <div className="flex w-full max-w-[560px] items-center justify-center bg-surface-2" style={{ height: 'min(70dvh, 560px)' }}>
        <div
          ref={frame}
          className="relative cursor-grab touch-none overflow-hidden bg-black active:cursor-grabbing"
          style={ratio >= 1 ? { width: '100%', aspectRatio: String(ratio) } : { height: '100%', aspectRatio: String(ratio) }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
          aria-label="사진을 끌어서 위치를 맞추세요"
        >
          {l && (
            <img
              src={p.url}
              alt=""
              draggable={false}
              className="pointer-events-none absolute max-w-none"
              style={{ width: l.dw, height: l.dh, left: l.x, top: l.y }}
            />
          )}
          {/* 3분할 안내선 */}
          <div className="pointer-events-none absolute inset-0 grid grid-cols-3 grid-rows-3">
            {Array.from({ length: 9 }, (_, i) => (
              <span key={i} className="border border-white/15" />
            ))}
          </div>
          {items.length > 1 && (
            <>
              {current > 0 && (
                <button
                  aria-label="이전 사진"
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={() => setCurrent(current - 1)}
                  className="absolute top-1/2 left-2 -translate-y-1/2 rounded-full bg-black/60 p-1 text-white"
                >
                  <ChevronLeft size={18} />
                </button>
              )}
              {current < items.length - 1 && (
                <button
                  aria-label="다음 사진"
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={() => setCurrent(current + 1)}
                  className="absolute top-1/2 right-2 -translate-y-1/2 rounded-full bg-black/60 p-1 text-white"
                >
                  <ChevronRight size={18} />
                </button>
              )}
            </>
          )}
        </div>
      </div>

      <div className="flex w-full max-w-[560px] flex-wrap items-center gap-3">
        <div className="flex gap-1" role="radiogroup" aria-label="비율">
          {RATIOS.map((r) => (
            <button
              key={r.label}
              role="radio"
              aria-checked={Math.abs(ratio - r.value) < 0.001}
              onClick={() => setRatio(r.value)}
              className={clsx(
                'rounded-md px-2.5 py-1 text-xs font-semibold',
                Math.abs(ratio - r.value) < 0.001 ? 'bg-text text-bg' : 'bg-surface-2',
              )}
            >
              {r.label}
            </button>
          ))}
        </div>
        <label className="flex flex-1 items-center gap-2 text-xs text-muted">
          <ZoomIn size={16} aria-hidden />
          <input
            type="range"
            min={1}
            max={3}
            step={0.01}
            value={p.zoom}
            onChange={(e) => update(current, { zoom: Number(e.target.value) })}
            aria-label="확대"
            className="w-full accent-[var(--primary)]"
          />
        </label>
      </div>

      <div className="flex w-full max-w-[560px] items-center gap-2 overflow-x-auto pt-2 pr-2 pb-1" aria-label="선택한 사진">
        {items.map((it, i) => (
          <div key={it.id} className="relative shrink-0">
            <button
              onClick={() => setCurrent(i)}
              aria-label={`${i + 1}번째 사진`}
              aria-current={i === current}
              className={clsx('block h-16 w-16 overflow-hidden rounded', i === current ? 'ring-2 ring-primary' : 'opacity-70')}
            >
              <img src={it.url} alt="" className="h-full w-full object-cover" />
            </button>
            <button
              aria-label={`${i + 1}번째 사진 빼기`}
              onClick={() => removeAt(i)}
              className="absolute -top-1.5 -right-1.5 rounded-full bg-black/80 p-0.5 text-white"
            >
              <X size={12} />
            </button>
          </div>
        ))}
        {items.length < MAX_FILES && (
          <button
            onClick={onAdd}
            aria-label="사진 추가"
            className="flex h-16 w-16 shrink-0 items-center justify-center rounded border border-dashed border-border text-muted hover:text-text"
          >
            <Plus size={22} />
          </button>
        )}
        <span className="ml-auto shrink-0 text-xs text-muted">
          {items.length}/{MAX_FILES}
        </span>
      </div>
    </div>
  )
}

// ------------------------------------------------------------ ③ 미리보기·위치

/** 잘린 모양 그대로의 미리보기. 편집 단계에서는 필터를, 문구 단계에서는 사람 태그를 얹는다 */
function Preview({
  items,
  ratio,
  index,
  setIndex,
  imgStyle,
  overlay,
  tags = [],
  pending,
  onTap,
}: {
  items: Picked[]
  ratio: number
  index: number
  setIndex: (i: number) => void
  imgStyle?: React.CSSProperties
  overlay?: React.CSSProperties
  tags?: DraftTag[]
  pending?: { x: number; y: number } | null
  onTap?: (x: number, y: number) => void
}) {
  const frame = useRef<HTMLDivElement>(null)
  const [w, setW] = useState(0)
  useEffect(() => {
    const el = frame.current
    if (!el) return
    const ro = new ResizeObserver(() => setW(el.clientWidth))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  const h = w / ratio
  const p = items[Math.min(index, items.length - 1)]
  const l = w ? layout(p, w, h) : null
  return (
    <div
      ref={frame}
      className={clsx('relative w-full overflow-hidden bg-black', onTap && 'cursor-crosshair')}
      style={{ aspectRatio: String(ratio) }}
      onClick={(e) => {
        if (!onTap || e.target !== e.currentTarget.querySelector('[data-photo]')) return
        const r = e.currentTarget.getBoundingClientRect()
        onTap((e.clientX - r.left) / r.width, (e.clientY - r.top) / r.height)
      }}
    >
      {l && (
        <img
          data-photo
          src={p.url}
          alt=""
          className="absolute max-w-none"
          style={{ width: l.dw, height: l.dh, left: l.x, top: l.y, ...(imgStyle ?? { filter: cssFilter(p.filter, p.adjust) }) }}
        />
      )}
      {(overlay ?? vignetteStyle(p.adjust.vignette)) && (
        <div className="pointer-events-none absolute inset-0" style={overlay ?? vignetteStyle(p.adjust.vignette)} />
      )}
      {tags.map((t) => (
        <span
          key={t.user.id}
          className="pointer-events-none absolute -translate-x-1/2 rounded bg-black/75 px-2 py-0.5 text-xs font-semibold text-white"
          style={{ left: `${t.x * 100}%`, top: `${t.y * 100}%` }}
        >
          {t.user.username}
        </span>
      ))}
      {pending && (
        <span
          className="pointer-events-none absolute h-3 w-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-primary"
          style={{ left: `${pending.x * 100}%`, top: `${pending.y * 100}%` }}
        />
      )}
      {items.length > 1 && (
        <>
          {index > 0 && (
            <button aria-label="이전 사진" onClick={() => setIndex(index - 1)} className="absolute top-1/2 left-2 -translate-y-1/2 rounded-full bg-white/80 p-1 text-black">
              <ChevronLeft size={18} />
            </button>
          )}
          {index < items.length - 1 && (
            <button aria-label="다음 사진" onClick={() => setIndex(index + 1)} className="absolute top-1/2 right-2 -translate-y-1/2 rounded-full bg-white/80 p-1 text-black">
              <ChevronRight size={18} />
            </button>
          )}
          <span className="absolute top-3 right-3 rounded-full bg-black/60 px-2 py-0.5 text-xs text-white">
            {index + 1}/{items.length}
          </span>
        </>
      )}
    </div>
  )
}

function LocationField({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [focused, setFocused] = useState(false)
  const q = value.trim()
  const { data } = useQuery({
    queryKey: ['locations', q],
    queryFn: () => api.get<LocationItem[]>(`/api/locations?q=${encodeURIComponent(q)}`),
    enabled: focused,
    staleTime: 60_000,
  })
  const options = useMemo(() => (data ?? []).filter((l) => l.name !== value).slice(0, 6), [data, value])
  return (
    <div className="relative border-t border-border pt-4">
      <label className="flex items-center gap-2 text-sm">
        <span className="sr-only">위치 추가</span>
        <input
          className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-muted"
          value={value}
          maxLength={100}
          placeholder="위치 추가"
          onFocus={() => setFocused(true)}
          onBlur={() => window.setTimeout(() => setFocused(false), 150)}
          onChange={(e) => onChange(e.target.value)}
        />
        {value ? (
          <button aria-label="위치 지우기" onClick={() => onChange('')}>
            <X size={16} />
          </button>
        ) : (
          <MapPin size={18} />
        )}
      </label>
      {focused && options.length > 0 && (
        <ul className="absolute inset-x-0 top-full z-10 mt-1 overflow-hidden rounded-lg border border-border bg-surface shadow-lg">
          {options.map((l) => (
            <li key={l.id}>
              <button
                className="flex w-full flex-col px-3 py-2 text-left text-sm hover:bg-surface-2"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  onChange(l.name)
                  setFocused(false)
                }}
              >
                <span className="font-semibold">{l.name}</span>
                <span className="text-xs text-muted">
                  {l.city} · 게시물 {l.posts}개
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
