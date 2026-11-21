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
  /** 프로필 사진 둘레 스토리 링 */
  story: StoryRing
}

export type StoryRing = 'NONE' | 'UNSEEN' | 'SEEN'

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
  /** 내 댓글이고 쓴 지 15분 안 */
  canEdit: boolean
  /** 게시물 주인이 보는 최상위 댓글 */
  canPin: boolean
  edited: boolean
  pinned: boolean
  likeCount: number
  likedByMe: boolean
  replyCount: number
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
  savedByMe: boolean
  repostedByMe: boolean
  repostCount: number
  /** 팔로우하는 사람의 리포스트로 피드에 들어온 경우 */
  repost: { by: UserSummary; note: string | null; createdAt: string } | null
  authorStory: StoryRing
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
  activeStories: number
  conversations: number
  messages: number
  notifications: number
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
  stories: number
  conversations: number
  messages: number
  notifications: number
  elapsedMillis: number
}

// ------------------------------------------------------------ M2: 알림

export type NotificationType =
  | 'LIKE'
  | 'COMMENT'
  | 'REPLY'
  | 'COMMENT_LIKE'
  | 'MENTION'
  | 'FOLLOW'
  | 'FOLLOW_REQUEST'
  | 'FOLLOW_ACCEPTED'
  | 'REPOST'
  | 'STORY_LIKE'

export interface ActivityItem {
  id: number
  type: NotificationType
  /** 최근 2명 */
  actors: UserSummary[]
  /** 묶인 전체 인원 */
  actorCount: number
  shortcode: string | null
  thumbUrl: string | null
  commentId: number | null
  body: string | null
  createdAt: string
  unread: boolean
  /** 팔로우 알림의 "맞팔로우" 버튼용 */
  relationship: Relationship | null
}

export interface Activity {
  requests: { count: number; latest: UserSummary | null }
  items: ActivityItem[]
}

export interface NotificationUnread {
  count: number
  requests: number
}

export interface NotificationPing {
  type: NotificationType
  actor: UserSummary
  body: string | null
  unread: number
}

// ------------------------------------------------------------ 검색

export interface UserHit {
  user: UserSummary
  followersCount: number
  relationship: Relationship
  followsMe: boolean
}

export interface TagHit {
  name: string
  postCount: number
}

export interface PlaceHit {
  id: number
  name: string
  city: string | null
  postCount: number
}

export interface SearchResult {
  users: UserHit[]
  tags: TagHit[]
  places: PlaceHit[]
}

export type RecentKind = 'USER' | 'TAG' | 'PLACE'

export interface RecentItem {
  kind: RecentKind
  term: string
  user: UserHit | null
  tag: TagHit | null
  place: PlaceHit | null
}

export interface TagPage {
  name: string
  postCount: number
  top: GridItem[]
}

export interface PlacePage {
  id: number
  name: string
  city: string | null
  postCount: number
  top: GridItem[]
}

// ------------------------------------------------------------ 저장·컬렉션

export interface CollectionView {
  /** "모든 게시물"은 null */
  id: number | null
  name: string
  count: number
  covers: string[]
}

export interface SavedOverview {
  all: CollectionView
  collections: CollectionView[]
}

// ------------------------------------------------------------ DM

export type DmFolder = 'PRIMARY' | 'GENERAL' | 'REQUEST'

export type MessageKind = 'TEXT' | 'IMAGE' | 'POST' | 'STORY_REPLY'

export interface InboxItem {
  id: number
  isGroup: boolean
  title: string | null
  members: UserSummary[]
  folder: DmFolder
  last: { id: number; senderId: number; kind: MessageKind; text: string | null; createdAt: string }
  unread: boolean
  online: boolean
  lastActiveAt: string | null
}

export interface DmUnread {
  primary: number
  general: number
  requests: number
}

export interface DmMember {
  user: UserSummary
  lastReadMessageId: number
  online: boolean
  lastActiveAt: string | null
}

export interface ConversationView {
  id: number
  isGroup: boolean
  title: string | null
  members: DmMember[]
  folder: DmFolder
  myLastRead: number
}

export interface SharedPost {
  shortcode: string | null
  thumbUrl: string | null
  author: UserSummary | null
  caption: string | null
  available: boolean
}

export interface MessageView {
  id: number
  conversationId: number
  sender: UserSummary
  kind: MessageKind
  body: string | null
  media: { url: string; width: number; height: number } | null
  post: SharedPost | null
  story: { id: number; mediaUrl: string | null; author: UserSummary; expired: boolean } | null
  replyTo: { id: number; senderUsername: string; text: string | null } | null
  createdAt: string
  edited: boolean
  unsent: boolean
  reactions: { userId: number; emoji: string }[]
  canEdit: boolean
}

export interface MessagePage {
  items: MessageView[]
  nextBefore: number | null
}

// ------------------------------------------------------------ 스토리

export interface TrayItem {
  user: UserSummary
  ring: StoryRing
  count: number
  latestAt: string
  me: boolean
}

export interface StoryView {
  id: number
  author: UserSummary
  mediaUrl: string
  width: number
  height: number
  altText: string | null
  createdAt: string
  expiresAt: string
  seen: boolean
  liked: boolean
  mine: boolean
  viewerCount: number | null
  expired: boolean
}

export interface StoryViewer {
  user: UserSummary
  liked: boolean
  viewedAt: string
}
