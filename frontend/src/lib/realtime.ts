// 실시간 이벤트 연결 (STOMP over WebSocket).
// 로그인하면 /ws에 연결하고 /user/queue/events 하나로 모든 개인 이벤트({type, data})를 받는다.
// 화면 쪽에서는 onRealtime / useRealtimeEvent로 구독한다.

import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useSyncExternalStore } from 'react'

export interface RealtimeEvent {
  type:
    | 'notification:new'
    | 'message:new'
    | 'message:edit'
    | 'message:unsend'
    | 'message:read'
    | 'conversation:refresh'
    | 'conversation:vanish'
    | 'typing'
    | 'story:new'
    | 'channel:new'
    | 'live:comment'
    | 'live:heart'
    | 'live:viewers'
    | 'live:end'
    | 'live:guest'
    | 'live:guest-invite'
    | 'call:incoming'
    | 'call:accepted'
    | 'call:ended'
    | 'rtc:signal'
  data: unknown
}

type Listener = (e: RealtimeEvent) => void

const listeners = new Set<Listener>()
const statusListeners = new Set<() => void>()
let client: Client | null = null
let connectedUser: number | null = null
let connected = false

function setConnected(value: boolean) {
  connected = value
  statusListeners.forEach((l) => l())
}

/** 로그인 사용자가 바뀌면 다시 연결, null이면 끊는다 */
export function connectRealtime(userId: number | null) {
  if (userId === connectedUser) return
  disconnect()
  connectedUser = userId
  if (userId === null) return

  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: 3000,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
    onConnect: () => {
      setConnected(true)
      client?.subscribe('/user/queue/events', (frame) => {
        try {
          const event = JSON.parse(frame.body) as RealtimeEvent
          listeners.forEach((l) => l(event))
        } catch {
          /* 알 수 없는 형식은 무시 */
        }
      })
    },
    onWebSocketClose: () => setConnected(false),
  })
  client.activate()
}

function disconnect() {
  if (client) {
    void client.deactivate()
    client = null
  }
  connectedUser = null
  setConnected(false)
}

export function onRealtime(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** 컴포넌트가 살아 있는 동안 이벤트를 받는다 (핸들러가 바뀌어도 다시 구독하지 않음) */
export function useRealtimeEvent(handler: Listener) {
  const ref = useRef(handler)
  useEffect(() => {
    ref.current = handler
  })
  useEffect(() => onRealtime((e) => ref.current(e)), [])
}

/** 연결 상태 (Lab·DM 화면 표시용) */
export function useRealtimeConnected(): boolean {
  return useSyncExternalStore(
    (cb) => {
      statusListeners.add(cb)
      return () => statusListeners.delete(cb)
    },
    () => connected,
  )
}

let lastTyping = 0

/** 입력 중 표시. 너무 자주 보내지 않도록 2초에 한 번 */
export function sendTyping(conversationId: number) {
  const now = Date.now()
  if (!client?.connected || now - lastTyping < 2000) return
  lastTyping = now
  client.publish({ destination: '/app/typing', body: JSON.stringify({ conversationId }) })
}
