import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from './api'
import type { Me } from './types'

export const ME_KEY = ['me'] as const

/** 로그인한 본인. 로그인 안 했으면 null. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async (): Promise<Me | null> => {
      try {
        // 로그인 안 했으면 204(빈 응답) → null
        return (await api.get<Me | undefined>('/api/auth/me')) ?? null
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }
    },
    staleTime: 60_000,
  })
}

/** 로그인 상태가 바뀌면 본인 정보를 바꾸고, 다른 화면 데이터는 모두 새로 받는다. */
export function useSetSession() {
  const qc = useQueryClient()
  return (me: Me | null) => {
    qc.removeQueries({ predicate: (q) => q.queryKey[0] !== ME_KEY[0] })
    qc.setQueryData(ME_KEY, me)
  }
}

export function useLogin() {
  const setSession = useSetSession()
  return useMutation({
    mutationFn: (body: { login: string; password: string }) => api.post<Me>('/api/auth/login', body),
    onSuccess: setSession,
  })
}

export interface SignupInput {
  email: string
  fullName: string
  username: string
  password: string
  birthDate: string
}

export function useSignup() {
  const setSession = useSetSession()
  return useMutation({
    mutationFn: (body: SignupInput) => api.post<Me>('/api/auth/signup', body),
    onSuccess: setSession,
  })
}

export function useLogout() {
  const setSession = useSetSession()
  return useMutation({
    mutationFn: () => api.post<void>('/api/auth/logout'),
    onSuccess: () => setSession(null),
  })
}

export function useImpersonate() {
  const setSession = useSetSession()
  return useMutation({
    mutationFn: (username: string) => api.post<Me>(`/api/lab/impersonate/${encodeURIComponent(username)}`),
    onSuccess: setSession,
  })
}
