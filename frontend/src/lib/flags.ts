// 기능 플래그 (Lab L09). 화면이 따라야 하는 정책 값.
import { useQuery } from '@tanstack/react-query'
import { api } from './api'

export interface Flags {
  hashtagLimit: number
  navLayout: 'NEW' | 'CLASSIC'
  hideLikeCounts: boolean
  feedSuggestedPerPage: number
}

export const DEFAULT_FLAGS: Flags = { hashtagLimit: 5, navLayout: 'NEW', hideLikeCounts: false, feedSuggestedPerPage: 2 }

export function useFlags() {
  return useQuery({ queryKey: ['flags'], queryFn: () => api.get<Flags>('/api/flags'), staleTime: 60_000 })
}

/** 값이 아직 없으면 기본값 */
export function useFlagValues(): Flags {
  return useFlags().data ?? DEFAULT_FLAGS
}
