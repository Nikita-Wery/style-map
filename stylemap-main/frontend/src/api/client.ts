import type {
  AdminHashtag,
  AdminPost,
  AdminUser,
  ApiMessage,
  CurrencyConversion,
  CurrentUser,
  FeedPost,
  FollowResult,
  HashtagSearchResult,
  Page,
  PostDetails,
  PostPayload,
  RegistrationPayload,
  UserProfile,
} from './types'

/**
 * База для запросов. По умолчанию пустая строка — запросы уходят на тот же origin,
 * который проксирует nginx (production) или Vite dev-server (разработка).
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

export class ApiError extends Error {
  readonly status: number
  readonly details?: string
  readonly validationErrors: string[]

  constructor(status: number, message: string, details?: string, validationErrors: string[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
    this.validationErrors = validationErrors
  }

  /** Текст, который имеет смысл показать пользователю. */
  get displayMessage(): string {
    if (this.validationErrors.length > 0) {
      return this.validationErrors.join('\n')
    }
    return this.details || this.message
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

function buildQuery(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  headers.set('Accept', 'application/json')

  // CSRF-токен Spring кладёт в читаемую cookie; отправляем его заголовком.
  if (!SAFE_METHODS.has(method)) {
    const token = readCookie(CSRF_COOKIE_NAME)
    if (token) {
      headers.set(CSRF_HEADER_NAME, token)
    }
  }

  const isFormData = init.body instanceof FormData
  if (init.body && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    method,
    headers,
    // Обязательно: сессионная cookie JSESSIONID должна ездить с каждым запросом.
    credentials: 'include',
  })

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

async function toApiError(response: Response): Promise<ApiError> {
  let message = `Ошибка ${response.status}`
  let details: string | undefined
  let validationErrors: string[] = []

  try {
    const data = (await response.json()) as {
      message?: string
      error?: string
      details?: string
      errors?: string[]
    }
    message = data.message ?? data.error ?? message
    details = data.details
    validationErrors = Array.isArray(data.errors) ? data.errors : []
  } catch {
    // Ответ не JSON (например, HTML-страница ошибки) — оставляем статус.
  }

  return new ApiError(response.status, message, details, validationErrors)
}

export const api = {
  auth: {
    me: () => request<CurrentUser>('/api/auth/me'),
    csrf: () => request<{ token: string; headerName: string; parameterName: string }>('/api/auth/csrf'),
    login: (username: string, password: string) =>
      request<CurrentUser>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password }),
      }),
    register: (payload: RegistrationPayload) =>
      request<ApiMessage>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    confirm: (email: string, code: string) =>
      request<ApiMessage>('/api/auth/confirm', {
        method: 'POST',
        body: JSON.stringify({ email, code }),
      }),
    logout: () => request<ApiMessage>('/api/auth/logout', { method: 'POST' }),
  },

  posts: {
    feed: (params: {
      hashtag?: string
      page?: number
      size?: number
      sortBy?: string
      followingOnly?: boolean
    }) => request<Page<FeedPost>>(`/api/posts/feed${buildQuery(params)}`),

    details: (postId: string) => request<PostDetails>(`/api/posts/${postId}`),

    byUser: (userId: string, page = 0, size = 10) =>
      request<Page<FeedPost>>(`/api/posts/user/${userId}${buildQuery({ page, size })}`),

    create: (file: File, payload: PostPayload) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('postData', JSON.stringify(payload))
      return request<ApiMessage>('/api/posts', { method: 'POST', body: formData })
    },

    update: (postId: string, payload: PostPayload) =>
      request<ApiMessage>(`/api/actions/posts/${postId}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      }),

    remove: (postId: string) =>
      request<ApiMessage>(`/api/actions/posts/${postId}`, { method: 'DELETE' }),

    rate: (postId: string, score: number) =>
      request<ApiMessage>(`/api/actions/posts/${postId}/rate${buildQuery({ score })}`, {
        method: 'POST',
      }),
  },

  actions: {
    toggleFollow: (authorId: string) =>
      request<FollowResult>(`/api/actions/users/${authorId}/toggle-follow`, { method: 'POST' }),

    convertCurrency: (amount: number | string, from: string, to: string) =>
      request<CurrencyConversion>(
        `/api/actions/convert-currency${buildQuery({ amount, from, to })}`,
      ),
  },

  hashtags: {
    search: (query: string, page = 0, size = 5) =>
      request<HashtagSearchResult>(`/api/hashtags/search${buildQuery({ q: query, page, size })}`),
  },

  users: {
    profile: (username: string) =>
      request<UserProfile>(`/api/users/${encodeURIComponent(username)}`),
  },

  admin: {
    users: () => request<AdminUser[]>('/api/admin/users'),
    createUser: (payload: RegistrationPayload) =>
      request<ApiMessage>('/api/admin/users', { method: 'POST', body: JSON.stringify(payload) }),
    setUserStatus: (userId: string, active: boolean) =>
      request<ApiMessage>(`/api/admin/users/${userId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ active }),
      }),
    deleteUser: (userId: string) =>
      request<ApiMessage>(`/api/admin/users/${userId}`, { method: 'DELETE' }),

    posts: () => request<AdminPost[]>('/api/admin/posts'),
    createPost: (authorEmail: string, description: string) =>
      request<ApiMessage>('/api/admin/posts', {
        method: 'POST',
        body: JSON.stringify({ authorEmail, description }),
      }),
    updatePost: (postId: string, newDescription: string) =>
      request<ApiMessage>(`/api/admin/posts/${postId}`, {
        method: 'PUT',
        body: JSON.stringify({ newDescription }),
      }),
    deletePost: (postId: string) =>
      request<ApiMessage>(`/api/admin/posts/${postId}`, { method: 'DELETE' }),

    hashtags: () => request<AdminHashtag[]>('/api/admin/hashtags'),
    createHashtag: (name: string, path: string) =>
      request<ApiMessage>('/api/admin/hashtags', {
        method: 'POST',
        body: JSON.stringify({ name, path }),
      }),
    updateHashtag: (hashtagId: number, newName: string) =>
      request<ApiMessage>(`/api/admin/hashtags/${hashtagId}`, {
        method: 'PUT',
        body: JSON.stringify({ newName }),
      }),
    deleteHashtag: (hashtagId: number) =>
      request<ApiMessage>(`/api/admin/hashtags/${hashtagId}`, { method: 'DELETE' }),
  },
}

export { buildQuery, readCookie }
