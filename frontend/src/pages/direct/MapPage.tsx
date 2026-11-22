import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, LocateFixed, MapPinOff } from 'lucide-react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import clsx from 'clsx'
import { avatarSrc } from '../../components/Avatar'
import { Modal } from '../../components/Modal'
import { toast } from '../../components/Toast'
import { api } from '../../lib/api'
import { useMe } from '../../lib/auth'
import { timeAgo } from '../../lib/format'
import type { UserSummary } from '../../lib/types'

interface MyLocation {
  lat: number
  lng: number
  placeName: string | null
  sharing: 'OFF' | 'MUTUAL' | 'CLOSE_FRIENDS'
  updatedAt: string
}

interface FriendLocation {
  user: UserSummary
  lat: number
  lng: number
  placeName: string | null
  updatedAt: string
}

interface PlacePin {
  id: number
  name: string
  lat: number
  lng: number
  postCount: number
  thumbUrl: string | null
}

interface MapView {
  me: MyLocation | null
  friends: FriendLocation[]
  places: PlacePin[]
}

const SEOUL: L.LatLngTuple = [37.5536, 126.9918]

/** 핀 안에 넣을 글자 (이름·장소)를 안전하게 */
const esc = (s: string) => s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)

function avatarIcon(user: UserSummary, mine = false) {
  return L.divIcon({
    className: '',
    iconSize: [48, 62],
    iconAnchor: [24, 56],
    html: `<div class="map-pin ${mine ? 'map-pin-me' : ''}"><img src="${avatarSrc(user)}" alt=""/><span>${esc(
      mine ? '나' : user.username,
    )}</span></div>`,
  })
}

function placeIcon(p: PlacePin) {
  return L.divIcon({
    className: '',
    iconSize: [44, 44],
    iconAnchor: [22, 22],
    html: `<div class="map-place">${p.thumbUrl ? `<img src="${p.thumbUrl}" alt=""/>` : ''}<b>${p.postCount}</b></div>`,
  })
}

/**
 * 지도 (DM → 지도, 분석 문서 §15): 서로 팔로우하는 친구가 공유한 마지막 위치와 게시물이 올라온 장소.
 * 연구용이라 실제 GPS를 쓰지 않고, 지도를 눌러 내 위치를 정한다. 지도 그림은 OpenStreetMap.
 */
export function MapPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data: me } = useMe()
  const { data } = useQuery({ queryKey: ['map'], queryFn: () => api.get<MapView>('/api/map') })
  const box = useRef<HTMLDivElement>(null)
  const map = useRef<L.Map | null>(null)
  const layer = useRef<L.LayerGroup | null>(null)
  const [picked, setPicked] = useState<{ lat: number; lng: number } | null>(null)
  const [settings, setSettings] = useState(false)

  const save = useMutation({
    mutationFn: (body: { lat: number; lng: number; placeName?: string | null; sharing: MyLocation['sharing'] }) =>
      api.put<MyLocation>('/api/map/me', body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['map'] })
      setPicked(null)
      toast('위치를 공유했습니다')
    },
    onError: (e) => toast((e as Error).message),
  })
  const stop = useMutation({
    mutationFn: () => api.delete('/api/map/me'),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['map'] })
      setSettings(false)
      toast('위치 공유를 껐습니다')
    },
  })

  // 지도 한 번 만들기
  useEffect(() => {
    if (!box.current || map.current) return
    const m = L.map(box.current, { zoomControl: false, attributionControl: true }).setView(SEOUL, 12)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(m)
    L.control.zoom({ position: 'bottomright' }).addTo(m)
    m.on('click', (e: L.LeafletMouseEvent) => setPicked({ lat: e.latlng.lat, lng: e.latlng.lng }))
    layer.current = L.layerGroup().addTo(m)
    map.current = m
    return () => {
      m.remove()
      map.current = null
    }
  }, [])

  // 핀 다시 그리기
  useEffect(() => {
    const g = layer.current
    if (!g || !data || !me) return
    g.clearLayers()
    for (const p of data.places) {
      L.marker([p.lat, p.lng], { icon: placeIcon(p), zIndexOffset: -100, title: p.name })
        .on('click', () => navigate(`/explore/locations/${p.id}`))
        .addTo(g)
    }
    for (const f of data.friends) {
      L.marker([f.lat, f.lng], { icon: avatarIcon(f.user), title: f.user.username })
        .bindPopup(
          `<b>${esc(f.user.username)}</b><br/>${esc(f.placeName ?? '')}<br/><small>${esc(timeAgo(f.updatedAt))}</small>`,
        )
        .addTo(g)
    }
    if (data.me) {
      L.marker([data.me.lat, data.me.lng], { icon: avatarIcon(me.user, true), zIndexOffset: 1000 }).addTo(g)
    }
  }, [data, me, navigate])

  if (!me) return null
  const sharing = data?.me?.sharing ?? 'OFF'

  return (
    <div className="relative h-[calc(100dvh-60px-32px)] md:h-[calc(100dvh-32px)]">
      <div ref={box} className="absolute inset-0 z-0" aria-label="친구 지도" />

      <header className="pointer-events-none absolute inset-x-0 top-0 z-[500] flex items-center gap-2 p-3">
        <button
          aria-label="뒤로"
          className="pointer-events-auto rounded-full bg-surface p-2 shadow"
          onClick={() => navigate('/direct/inbox')}
        >
          <ChevronLeft size={22} />
        </button>
        <span className="pointer-events-auto rounded-full bg-surface px-3 py-1.5 text-sm font-semibold shadow">
          지도 · 친구 {data?.friends.length ?? 0}명 · 장소 {data?.places.length ?? 0}곳
        </span>
        <button
          className="pointer-events-auto ml-auto flex items-center gap-1 rounded-full bg-surface px-3 py-1.5 text-sm shadow"
          onClick={() => setSettings(true)}
        >
          {sharing === 'OFF' ? <MapPinOff size={16} /> : <LocateFixed size={16} className="text-primary" />}
          {sharing === 'OFF' ? '위치 공유 꺼짐' : sharing === 'MUTUAL' ? '맞팔에게 공유 중' : '친한 친구에게 공유 중'}
        </button>
      </header>

      <p className="pointer-events-none absolute inset-x-0 bottom-3 z-[500] mx-auto w-fit max-w-[90%] rounded-full bg-surface/90 px-3 py-1.5 text-center text-xs text-muted shadow">
        지도를 누르면 그곳을 내 위치로 공유할 수 있어요 (연구용 가짜 위치)
      </p>

      {picked && (
        <Modal open onClose={() => setPicked(null)} title="이 위치 공유">
          <SharePicker
            busy={save.isPending}
            initial={sharing === 'OFF' ? 'MUTUAL' : sharing}
            onSave={(s, name) => save.mutate({ ...picked, sharing: s, placeName: name })}
          />
        </Modal>
      )}
      {settings && (
        <Modal open onClose={() => setSettings(false)} title="위치 공유">
          <div className="space-y-3 p-4 text-sm">
            {data?.me ? (
              <p>
                마지막 공유: <b>{data.me.placeName ?? '지도에서 고른 곳'}</b> · {timeAgo(data.me.updatedAt)}
              </p>
            ) : (
              <p className="text-muted">아직 위치를 공유하지 않았습니다. 지도를 눌러 위치를 골라 보세요.</p>
            )}
            <p className="text-muted">공유한 위치는 3일 동안 보이고, 그 뒤에는 친구 지도에서 사라집니다.</p>
            {data?.me && (
              <SharePicker
                busy={save.isPending}
                initial={data.me.sharing}
                onSave={(s) => {
                  if (data.me) save.mutate({ lat: data.me.lat, lng: data.me.lng, placeName: data.me.placeName, sharing: s })
                  setSettings(false)
                }}
              />
            )}
            {data?.me && (
              <button className="btn w-full text-danger" onClick={() => stop.mutate()}>
                위치 공유 끄기
              </button>
            )}
          </div>
        </Modal>
      )}
    </div>
  )
}

function SharePicker({
  initial,
  busy,
  onSave,
}: {
  initial: MyLocation['sharing']
  busy: boolean
  onSave: (sharing: MyLocation['sharing'], placeName: string | null) => void
}) {
  const [sharing, setSharing] = useState<MyLocation['sharing']>(initial === 'OFF' ? 'MUTUAL' : initial)
  const [name, setName] = useState('')
  return (
    <div className="space-y-3 p-4 text-sm">
      <input className="input" placeholder="장소 이름 (선택, 예: 성수동 카페)" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
      <fieldset className="space-y-2">
        <legend className="mb-1 font-semibold">누구에게 보일까요?</legend>
        {(
          [
            ['MUTUAL', '서로 팔로우하는 친구'],
            ['CLOSE_FRIENDS', '친한 친구만'],
          ] as const
        ).map(([v, label]) => (
          <label key={v} className={clsx('flex items-center gap-2 rounded-lg border px-3 py-2', sharing === v ? 'border-primary' : 'border-border')}>
            <input type="radio" checked={sharing === v} onChange={() => setSharing(v)} />
            {label}
          </label>
        ))}
      </fieldset>
      <button className="btn btn-primary w-full" disabled={busy} onClick={() => onSave(sharing, name.trim() || null)}>
        공유
      </button>
    </div>
  )
}
