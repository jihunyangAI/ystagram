import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ImagePlus, Star, Sticker as StickerIcon, Trash2, Type, X } from 'lucide-react'
import clsx from 'clsx'
import { toast } from '../../components/Toast'
import { StickerView } from '../../components/story/StickerView'
import { storyApi } from '../../lib/stories'
import type { Sticker, StickerType } from '../../lib/types'

/** 편집 중인 스티커 (서버가 id를 붙인다) */
type Draft = Omit<Sticker, 'id'> & { key: number }

const STICKER_TYPES: { type: StickerType; label: string; make: () => Omit<Draft, 'key' | 'x' | 'y'> }[] = [
  { type: 'POLL', label: '설문', make: () => ({ type: 'POLL', question: '어느 쪽?', options: ['예', '아니요'] }) },
  { type: 'QUIZ', label: '퀴즈', make: () => ({ type: 'QUIZ', question: '정답은?', options: ['A', 'B', 'C'], answer: 0 }) },
  { type: 'SLIDER', label: '이모지 슬라이더', make: () => ({ type: 'SLIDER', question: '얼마나 좋아요?', emoji: '😍' }) },
  { type: 'QUESTION', label: '질문', make: () => ({ type: 'QUESTION', question: '무엇이든 물어보세요' }) },
  { type: 'MENTION', label: '언급', make: () => ({ type: 'MENTION', username: '' }) },
  { type: 'HASHTAG', label: '해시태그', make: () => ({ type: 'HASHTAG', tag: '' }) },
  { type: 'LOCATION', label: '위치', make: () => ({ type: 'LOCATION', name: '' }) },
  { type: 'LINK', label: '링크', make: () => ({ type: 'LINK', url: 'https://', label: '' }) },
  {
    type: 'COUNTDOWN',
    label: '카운트다운',
    make: () => ({ type: 'COUNTDOWN', question: '카운트다운', endsAt: new Date(Date.now() + 7 * 86_400_000).toISOString() }),
  },
]

const W = 1080
const H = 1920

interface Picked {
  img: HTMLImageElement
  url: string
}

const COLORS = ['#ffffff', '#111111', '#ffd400', '#ff3b6b', '#3d7bff']
const POSITIONS = [
  { label: '위', y: 0.2 },
  { label: '가운데', y: 0.5 },
  { label: '아래', y: 0.78 },
]

/** 사진을 9:16으로 맞춰 그리고(끌어서 위치·확대), 글자를 얹는다. 미리보기와 결과물이 같은 함수를 쓴다 */
function draw(
  ctx: CanvasRenderingContext2D,
  p: Picked,
  offset: { x: number; y: number },
  zoom: number,
  text: string,
  color: string,
  boxed: boolean,
  posY: number,
) {
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, W, H)
  const scale = Math.max(W / p.img.naturalWidth, H / p.img.naturalHeight) * zoom
  const dw = p.img.naturalWidth * scale
  const dh = p.img.naturalHeight * scale
  const maxX = (dw - W) / 2
  const maxY = (dh - H) / 2
  const x = (W - dw) / 2 + Math.max(-maxX, Math.min(maxX, offset.x))
  const y = (H - dh) / 2 + Math.max(-maxY, Math.min(maxY, offset.y))
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(p.img, x, y, dw, dh)

  const lines = text.trim() ? wrap(ctx, text.trim(), W - 160) : []
  if (lines.length === 0) return
  const size = 72
  const lineH = size * 1.3
  ctx.font = `700 ${size}px "Pretendard", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  const top = posY * H - ((lines.length - 1) * lineH) / 2
  lines.forEach((line, i) => {
    const ly = top + i * lineH
    if (boxed) {
      const w = ctx.measureText(line).width + 48
      ctx.fillStyle = color === '#ffffff' ? 'rgba(0,0,0,0.75)' : 'rgba(255,255,255,0.9)'
      roundRect(ctx, W / 2 - w / 2, ly - lineH / 2, w, lineH, 18)
      ctx.fill()
    } else {
      ctx.shadowColor = 'rgba(0,0,0,0.45)'
      ctx.shadowBlur = 12
    }
    ctx.fillStyle = color
    ctx.fillText(line, W / 2, ly)
    ctx.shadowBlur = 0
  })
}

function wrap(ctx: CanvasRenderingContext2D, text: string, max: number): string[] {
  ctx.font = `700 72px "Pretendard", "Apple SD Gothic Neo", "Malgun Gothic", sans-serif`
  const out: string[] = []
  for (const para of text.split('\n')) {
    let line = ''
    for (const ch of para) {
      if (ctx.measureText(line + ch).width > max && line) {
        out.push(line)
        line = ch
      } else line += ch
    }
    out.push(line)
  }
  return out.slice(0, 8)
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

/** 스토리 만들기: 사진 한 장 → 위치·확대 → 글자(선택) → 공유. 24시간 뒤 사라지고 아카이브에 남는다 */
export function CreateStoryPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [picked, setPicked] = useState<Picked | null>(null)
  const [offset, setOffset] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [text, setText] = useState('')
  const [color, setColor] = useState(COLORS[0])
  const [boxed, setBoxed] = useState(false)
  const [posY, setPosY] = useState(0.5)
  const [editingText, setEditingText] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [audience, setAudience] = useState<'PUBLIC' | 'CLOSE_FRIENDS'>('PUBLIC')
  const [drafts, setDrafts] = useState<Draft[]>([])
  const [adding, setAdding] = useState(false)
  const stickerDrag = useRef<{ key: number; rect: DOMRect } | null>(null)
  const canvas = useRef<HTMLCanvasElement>(null)

  const addSticker = (make: () => Omit<Draft, 'key' | 'x' | 'y'>) => {
    if (drafts.length >= 5) return toast('스티커는 5개까지 붙일 수 있습니다')
    setDrafts((d) => [...d, { ...make(), key: Date.now(), x: 0.5, y: 0.35 + d.length * 0.1 }])
    setAdding(false)
  }
  const updateDraft = (key: number, patch: Partial<Draft>) => setDrafts((d) => d.map((x) => (x.key === key ? { ...x, ...patch } : x)))
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => () => {
    if (picked) URL.revokeObjectURL(picked.url)
  }, [picked])

  const pick = (file: File | undefined) => {
    if (!file || !file.type.startsWith('image/')) return
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      setPicked({ img, url })
      setOffset({ x: 0, y: 0 })
      setZoom(1)
    }
    img.onerror = () => toast('사진을 열 수 없습니다')
    img.src = url
  }

  const redraw = useCallback(() => {
    const ctx = canvas.current?.getContext('2d')
    if (ctx && picked) draw(ctx, picked, offset, zoom, text, color, boxed, posY)
  }, [picked, offset, zoom, text, color, boxed, posY])
  useEffect(redraw, [redraw])

  // 끌어서 위치 옮기기 (화면 크기 → 1080×1920 좌표로 환산)
  const drag = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null)
  const onPointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId)
    drag.current = { x: e.clientX, y: e.clientY, ox: offset.x, oy: offset.y }
  }
  const onPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drag.current) return
    const k = W / e.currentTarget.getBoundingClientRect().width
    setOffset({ x: drag.current.ox + (e.clientX - drag.current.x) * k, y: drag.current.oy + (e.clientY - drag.current.y) * k })
  }

  const share = async () => {
    if (!picked || sharing) return
    setSharing(true)
    try {
      const out = document.createElement('canvas')
      out.width = W
      out.height = H
      draw(out.getContext('2d')!, picked, offset, zoom, text, color, boxed, posY)
      const blob = await new Promise<Blob>((resolve, reject) =>
        out.toBlob((b) => (b ? resolve(b) : reject(new Error('사진을 만들지 못했습니다'))), 'image/jpeg', 0.9),
      )
      const stickers = drafts.map(({ key: _key, ...rest }) => rest)
      await storyApi.create(blob, { altText: text.trim() || undefined, audience, stickers })
      qc.invalidateQueries({ queryKey: ['story-tray'] })
      qc.invalidateQueries({ queryKey: ['story-archive'] })
      qc.invalidateQueries({ queryKey: ['profile'] })
      toast('스토리를 공유했습니다. 24시간 동안 보입니다')
      navigate('/')
    } catch (e) {
      toast((e as Error).message)
    } finally {
      setSharing(false)
    }
  }

  return (
    <div className="mx-auto flex max-w-[935px] flex-col items-center gap-4 px-4 py-4 md:py-8">
      <header className="flex w-full max-w-[420px] items-center justify-between">
        <button aria-label="뒤로" onClick={() => navigate(-1)} className="p-1">
          <ChevronLeft size={26} />
        </button>
        <h1 className="font-bold">스토리 만들기</h1>
        <button
          onClick={share}
          disabled={!picked || sharing}
          className="rounded-lg bg-primary px-4 py-1.5 text-sm font-semibold text-white hover:bg-primary-hover disabled:opacity-40"
        >
          {sharing ? '공유 중…' : '공유'}
        </button>
      </header>

      <input ref={fileInput} type="file" accept="image/*" hidden onChange={(e) => pick(e.target.files?.[0])} />

      {!picked ? (
        <button
          onClick={() => fileInput.current?.click()}
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault()
            pick(e.dataTransfer.files[0])
          }}
          className="flex aspect-[9/16] w-full max-w-[360px] flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-border text-muted hover:bg-surface-2"
        >
          <ImagePlus size={48} strokeWidth={1.4} />
          <span className="text-sm">사진을 선택하거나 끌어다 놓으세요</span>
          <span className="rounded-lg bg-primary px-4 py-1.5 text-sm font-semibold text-white">컴퓨터에서 선택</span>
        </button>
      ) : (
        <div className="flex w-full flex-col items-center gap-4 md:flex-row md:items-start md:justify-center">
          <div className="relative w-full max-w-[360px]">
            <canvas
              ref={canvas}
              width={W}
              height={H}
              aria-label="스토리 미리보기 (끌어서 위치 조정)"
              className="aspect-[9/16] w-full cursor-grab touch-none rounded-xl bg-black active:cursor-grabbing"
              onPointerDown={onPointerDown}
              onPointerMove={onPointerMove}
              onPointerUp={() => (drag.current = null)}
            />
            {drafts.map((d) => (
              <div
                key={d.key}
                className="absolute z-10 -translate-x-1/2 -translate-y-1/2 cursor-move touch-none"
                style={{ left: `${d.x * 100}%`, top: `${d.y * 100}%` }}
                onPointerDown={(e) => {
                  e.stopPropagation()
                  e.currentTarget.setPointerCapture(e.pointerId)
                  stickerDrag.current = { key: d.key, rect: e.currentTarget.parentElement!.getBoundingClientRect() }
                }}
                onPointerMove={(e) => {
                  const st = stickerDrag.current
                  if (!st || st.key !== d.key) return
                  updateDraft(d.key, {
                    x: Math.min(0.95, Math.max(0.05, (e.clientX - st.rect.left) / st.rect.width)),
                    y: Math.min(0.95, Math.max(0.05, (e.clientY - st.rect.top) / st.rect.height)),
                  })
                }}
                onPointerUp={() => (stickerDrag.current = null)}
              >
                <div className="pointer-events-none origin-center scale-75">
                  <StickerView sticker={{ ...d, id: String(d.key) }} />
                </div>
              </div>
            ))}
            <button
              aria-label="다른 사진 선택"
              onClick={() => setPicked(null)}
              className="absolute top-3 left-3 rounded-full bg-black/50 p-1.5 text-white"
            >
              <X size={18} />
            </button>
            <button
              aria-label="글자 넣기"
              onClick={() => setEditingText(true)}
              className={clsx('absolute top-3 right-3 rounded-full p-1.5 text-white', editingText ? 'bg-primary' : 'bg-black/50')}
            >
              <Type size={18} />
            </button>
          </div>

          <div className="flex w-full max-w-[360px] flex-col gap-4 text-sm md:w-[280px]">
            <div className="flex gap-2" role="radiogroup" aria-label="공개 범위">
              <button
                role="radio"
                aria-checked={audience === 'PUBLIC'}
                onClick={() => setAudience('PUBLIC')}
                className={clsx('flex-1 rounded-lg border py-2 text-sm', audience === 'PUBLIC' ? 'border-primary font-semibold text-primary' : 'border-border')}
              >
                내 스토리
              </button>
              <button
                role="radio"
                aria-checked={audience === 'CLOSE_FRIENDS'}
                onClick={() => setAudience('CLOSE_FRIENDS')}
                className={clsx(
                  'flex flex-1 items-center justify-center gap-1 rounded-lg border py-2 text-sm',
                  audience === 'CLOSE_FRIENDS' ? 'border-[#1fc25b] bg-[#1fc25b] font-semibold text-white' : 'border-border',
                )}
              >
                <Star size={14} className={audience === 'CLOSE_FRIENDS' ? 'fill-white' : ''} /> 친한 친구
              </button>
            </div>

            <div>
              <button className="btn btn-secondary w-full" onClick={() => setAdding((a) => !a)}>
                <StickerIcon size={16} /> 스티커 추가 ({drafts.length}/5)
              </button>
              {adding && (
                <div className="mt-2 grid grid-cols-3 gap-1.5">
                  {STICKER_TYPES.map((t) => (
                    <button key={t.type} className="rounded-lg border border-border px-2 py-1.5 text-xs hover:bg-surface-2" onClick={() => addSticker(t.make)}>
                      {t.label}
                    </button>
                  ))}
                </div>
              )}
              {drafts.map((d) => (
                <DraftEditor key={d.key} draft={d} onChange={(p) => updateDraft(d.key, p)} onRemove={() => setDrafts((x) => x.filter((y) => y.key !== d.key))} />
              ))}
              {drafts.length > 0 && <p className="mt-1 text-xs text-muted">미리보기에서 스티커를 끌어 위치를 옮길 수 있습니다.</p>}
            </div>

            <label className="flex flex-col gap-1">
              <span className="font-semibold">확대</span>
              <input type="range" min={1} max={3} step={0.01} value={zoom} onChange={(e) => setZoom(Number(e.target.value))} />
            </label>
            <label className="flex flex-col gap-1">
              <span className="font-semibold">글자</span>
              <textarea
                value={text}
                onFocus={() => setEditingText(true)}
                onChange={(e) => setText(e.target.value)}
                maxLength={120}
                rows={3}
                placeholder="사진 위에 쓸 글자 (선택)"
                className="resize-none rounded-lg border border-border bg-surface px-3 py-2 outline-none focus:border-muted"
              />
            </label>
            {text.trim() && (
              <>
                <div className="flex items-center gap-2" role="radiogroup" aria-label="글자 색">
                  {COLORS.map((c) => (
                    <button
                      key={c}
                      role="radio"
                      aria-checked={color === c}
                      aria-label={`색 ${c}`}
                      onClick={() => setColor(c)}
                      className={clsx('h-7 w-7 rounded-full border-2', color === c ? 'border-primary' : 'border-border')}
                      style={{ background: c }}
                    />
                  ))}
                  <button
                    onClick={() => setBoxed((b) => !b)}
                    aria-pressed={boxed}
                    className={clsx('ml-auto rounded-md border px-2 py-1 text-xs', boxed ? 'border-primary text-primary' : 'border-border')}
                  >
                    배경
                  </button>
                </div>
                <div className="flex gap-2" role="radiogroup" aria-label="글자 위치">
                  {POSITIONS.map((p) => (
                    <button
                      key={p.label}
                      role="radio"
                      aria-checked={posY === p.y}
                      onClick={() => setPosY(p.y)}
                      className={clsx('flex-1 rounded-md border py-1.5 text-xs', posY === p.y ? 'border-primary text-primary' : 'border-border')}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </>
            )}
            <p className="text-xs text-muted">
              스토리는 24시간 동안 팔로워에게(친한 친구를 고르면 친한 친구에게만) 보이고, 그 뒤에는 나만 볼 수 있는 보관함에 남습니다.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

/** 스티커 하나의 설정 칸 */
function DraftEditor({ draft: d, onChange, onRemove }: { draft: Draft; onChange: (p: Partial<Draft>) => void; onRemove: () => void }) {
  const label = STICKER_TYPES.find((t) => t.type === d.type)?.label
  const field = 'w-full rounded-md border border-border bg-surface px-2 py-1 text-sm outline-none'
  return (
    <div className="mt-2 rounded-lg border border-border p-2 text-sm">
      <div className="mb-1.5 flex items-center justify-between">
        <b className="text-xs">{label}</b>
        <button aria-label={`${label} 스티커 삭제`} onClick={onRemove} className="text-muted hover:text-danger">
          <Trash2 size={14} />
        </button>
      </div>
      <div className="flex flex-col gap-1.5">
        {['POLL', 'QUIZ', 'SLIDER', 'QUESTION', 'COUNTDOWN'].includes(d.type) && (
          <input className={field} value={d.question ?? ''} maxLength={80} placeholder="질문·제목" onChange={(e) => onChange({ question: e.target.value })} />
        )}
        {(d.type === 'POLL' || d.type === 'QUIZ') &&
          (d.options ?? []).map((o, i) => (
            <div key={i} className="flex items-center gap-1">
              {d.type === 'QUIZ' && (
                <input type="radio" name={`answer-${d.key}`} checked={d.answer === i} onChange={() => onChange({ answer: i })} aria-label={`정답: 선택지 ${i + 1}`} />
              )}
              <input
                className={field}
                value={o}
                maxLength={30}
                placeholder={`선택지 ${i + 1}`}
                onChange={(e) => onChange({ options: (d.options ?? []).map((x, j) => (j === i ? e.target.value : x)) })}
              />
            </div>
          ))}
        {d.type === 'QUIZ' && (d.options ?? []).length < 4 && (
          <button className="self-start text-xs text-primary" onClick={() => onChange({ options: [...(d.options ?? []), ''] })}>
            + 선택지
          </button>
        )}
        {d.type === 'SLIDER' && (
          <input className={field} value={d.emoji ?? ''} maxLength={4} placeholder="이모지" onChange={(e) => onChange({ emoji: e.target.value })} />
        )}
        {d.type === 'MENTION' && (
          <input className={field} value={d.username ?? ''} maxLength={30} placeholder="사용자 이름" onChange={(e) => onChange({ username: e.target.value })} />
        )}
        {d.type === 'HASHTAG' && (
          <input className={field} value={d.tag ?? ''} maxLength={50} placeholder="해시태그" onChange={(e) => onChange({ tag: e.target.value })} />
        )}
        {d.type === 'LOCATION' && (
          <input className={field} value={d.name ?? ''} maxLength={100} placeholder="위치 이름" onChange={(e) => onChange({ name: e.target.value })} />
        )}
        {d.type === 'LINK' && (
          <>
            <input className={field} value={d.url ?? ''} maxLength={300} placeholder="https://" onChange={(e) => onChange({ url: e.target.value })} />
            <input className={field} value={d.label ?? ''} maxLength={30} placeholder="표시할 글자 (선택)" onChange={(e) => onChange({ label: e.target.value })} />
          </>
        )}
        {d.type === 'COUNTDOWN' && (
          <input
            type="datetime-local"
            className={field}
            value={toLocal(d.endsAt)}
            onChange={(e) => onChange({ endsAt: new Date(e.target.value).toISOString() })}
          />
        )}
      </div>
    </div>
  )
}

function toLocal(iso?: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
