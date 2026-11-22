import { describe, expect, it } from 'vitest'
import { mergeReads } from './dm'
import type { ConversationView } from './types'

const conv = (reads: Record<number, number>, myLastRead = 0) =>
  ({
    id: 1,
    isGroup: false,
    title: null,
    folder: 'PRIMARY',
    myLastRead,
    members: Object.entries(reads).map(([id, last]) => ({
      user: { id: Number(id), username: `u${id}` },
      lastReadMessageId: last,
      online: false,
      lastActiveAt: null,
    })),
  }) as unknown as ConversationView

describe('mergeReads — 늦게 온 대화방 응답이 "읽음"을 되돌리지 않는다', () => {
  it('실시간으로 받은 읽음(10)이 오래된 응답(5)에 덮이지 않는다', () => {
    const cached = conv({ 2: 10 }, 7)
    const stale = conv({ 2: 5 }, 3)
    const merged = mergeReads(cached, stale)
    expect(merged.members[0].lastReadMessageId).toBe(10)
    expect(merged.myLastRead).toBe(7)
  })

  it('새 응답이 더 앞서 있으면 새 값', () => {
    expect(mergeReads(conv({ 2: 5 }), conv({ 2: 12 })).members[0].lastReadMessageId).toBe(12)
  })

  it('처음 불러올 때·다른 대화방이면 그대로', () => {
    const next = conv({ 2: 4 })
    expect(mergeReads(undefined, next)).toBe(next)
    expect(mergeReads({ ...conv({ 2: 9 }), id: 99 } as ConversationView, next)).toBe(next)
  })
})
