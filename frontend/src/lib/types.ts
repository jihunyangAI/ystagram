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

/** 보는 사람 → 상대 관계 */
export type Relationship = 'SELF' | 'NONE' | 'FOLLOWING' | 'REQUESTED'

export interface Profile {
  user: UserSummary
  bio: string | null
  pronouns: string | null
  website: string | null
  accountType: AccountType
  category: string | null
  aiProfile: boolean
  followersCount: number
  followingCount: number
  postsCount: number
  me: boolean
  relationship: Relationship
  followsMe: boolean
  canViewContent: boolean
}

export interface EditableProfile {
  me: Me
  bio: string | null
  pronouns: string | null
  website: string | null
}

export interface UserListItem {
  user: UserSummary
  relationship: Relationship
}

export interface SuggestedUser {
  user: UserSummary
  mutualCount: number
  followsMe: boolean
}

export interface Slice<T> {
  items: T[]
  nextCursor: string | null
}

export interface MediaView {
  url: string
  thumbUrl: string
  width: number
  height: number
  altText: string | null
}

export interface CommentView {
  id: number
  author: UserSummary
  body: string
  createdAt: string
  parentId: number | null
  canDelete: boolean
}

export interface PostView {
  id: number
  shortcode: string
  author: UserSummary
  authorRelationship: Relationship
  caption: string | null
  location: { id: number; name: string } | null
  media: MediaView[]
  /** 좋아요 수 숨김이면 null (본인 글은 보임) */
  likeCount: number | null
  commentCount: number
  likedByMe: boolean
  likedBy: UserSummary | null
  hideLikeCount: boolean
  commentsDisabled: boolean
  createdAt: string
  mine: boolean
  previewComments: CommentView[]
}

export interface GridItem {
  shortcode: string
  thumbUrl: string
  mediaCount: number
  likeCount: number | null
  commentCount: number
}

export interface LikeResult {
  liked: boolean
  likeCount: number
}

export interface LocationItem {
  id: number
  name: string
  city: string | null
  posts: number
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
  posts: number
  likes: number
  comments: number
  hashtags: number
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
  posts: number
  likes: number
  comments: number
  elapsedMillis: number
}
