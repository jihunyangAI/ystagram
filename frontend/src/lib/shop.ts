// 쇼핑·구독·선물 (결제는 모두 가짜)

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './api'
import type { ProductView, SubscriptionInfo } from './types'

export const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

export function useSubscription(username: string | undefined) {
  return useQuery({
    queryKey: ['subscription', username],
    queryFn: () => api.get<SubscriptionInfo>(`/api/users/${username}/subscription`),
    enabled: !!username,
  })
}

export function useWish() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, on }: { id: number; on: boolean }) =>
      on ? api.put(`/api/products/${id}/wishlist`) : api.delete(`/api/products/${id}/wishlist`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['product'] })
      void qc.invalidateQueries({ queryKey: ['wishlist'] })
      void qc.invalidateQueries({ queryKey: ['shop'] })
    },
  })
}

export interface OrderView {
  id: number
  product: ProductView
  quantity: number
  amount: number
  createdAt: string
}

/** 별 선물 묶음 (별 1개 = 110원) */
export const GIFT_OPTIONS = [10, 50, 100, 500]
export const STAR_PRICE = 110
