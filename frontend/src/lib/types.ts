// 백엔드 DTO와 같은 모양의 타입

export type AccountType = 'PERSONAL' | 'CREATOR' | 'BUSINESS'

export interface UserSummary {
  id: number
  username: string
  fullName: string
  avatarUrl: string | null
  avatarSeed: string
  verified: boolean
  isPrivate: boolean
}

export interface Me {
  user: UserSummary
  email: string | null
  accountType: AccountType
  fake: boolean
}

export interface Profile {
  user: UserSummary
  bio: string | null
  pronouns: string | null
  accountType: AccountType
  category: string | null
  aiProfile: boolean
  followersCount: number
  followingCount: number
  postsCount: number
  me: boolean
}

export interface LabUser {
  user: UserSummary
  accountType: AccountType
  followersCount: number
  followingCount: number
  interests: string | null
  fake: boolean
}

export interface LabStatus {
  users: number
  fakeUsers: number
  follows: number
  pendingFollows: number
  now: string
  clockOffsetMinutes: number
  fakePassword: string
}

export type SeedSize = 'SMALL' | 'MEDIUM' | 'LARGE'

export interface SeedResult {
  size: SeedSize
  seed: number
  users: number
  follows: number
  elapsedMillis: number
}
