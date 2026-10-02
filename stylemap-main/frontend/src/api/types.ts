/**
 * Типы, зеркалящие DTO бэкенда (ru.itis.stylemap.dto.*).
 * Имена полей совпадают с JSON, который отдаёт Spring.
 */

export type Role = 'USER' | 'ADMIN'
export type MediaType = 'IMAGE' | 'VIDEO'

export interface CurrentUser {
  id: string
  username: string
  email: string | null
  avatarUrl: string | null
  role: Role
}

export interface FeedPost {
  id: string
  mediaUrl: string
  mediaType: MediaType
}

export interface PostMedia {
  url: string
  type: MediaType
}

export interface LinkDto {
  url: string
  price: number
  currency: string
}

/** Ключ — код страны (RU/US/EU/CN/...), значение — ссылка на товар. */
export type Links = Record<string, LinkDto>

export interface OutfitItem {
  name: string
  brand: string | null
  links: Links
}

export interface PostDetails {
  id: string
  description: string | null
  authorUsername: string
  authorId: string
  authorAvatarUrl: string | null
  isFollowingAuthor: boolean
  averageRating: number | null
  currentUserRating: number | null
  mediaFiles: PostMedia[]
  hashtagPaths: string[]
  outfitItems: OutfitItem[]
  isAuthor: boolean
}

export interface UserProfile {
  id: string
  username: string
  avatarUrl: string | null
  followersCount: number
  followingCount: number
  isCurrentUserProfile: boolean
  isFollowing: boolean
}

/** Spring Data Page<T> */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface HashtagSuggestion {
  name: string
  path: string
}

export interface HashtagSearchResult {
  tags: HashtagSuggestion[]
  currentPage: number
  hasNext: boolean
}

export interface PostPayload {
  description: string
  hashtags: string[]
  outfitItems: OutfitItem[]
}

export interface RegistrationPayload {
  username: string
  email: string
  password: string
}

export interface AdminUser {
  id: string
  username: string
  email: string
  role: string
  active: boolean
}

export interface AdminPost {
  id: string
  description: string | null
  authorUsername: string | null
  averageRating: number | null
  createdAt: string | null
}

export interface AdminHashtag {
  id: number
  name: string
  path: string
}

export interface CurrencyConversion {
  status: string
  result: number
  currency: string
}

export interface FollowResult {
  status: string
  action: 'followed' | 'unfollowed'
}

export interface ApiMessage {
  status: string
  message?: string
  email?: string
}
