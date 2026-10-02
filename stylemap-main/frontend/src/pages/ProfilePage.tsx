import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { FeedPost, Page, UserProfile } from '../api/types'
import { Alert } from '../components/Alert'
import { FullPageLoader } from '../components/FullPageLoader'
import { PostGrid } from '../components/PostGrid'
import { TopNav } from '../components/TopNav'

const EMPTY_PAGE: Page<FeedPost> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 10,
  first: true,
  last: true,
  empty: true,
}

export default function ProfilePage() {
  const { username } = useParams<{ username: string }>()

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [followBusy, setFollowBusy] = useState(false)

  useEffect(() => {
    if (!username) return
    let cancelled = false
    setLoading(true)

    api.users
      .profile(username)
      .then(data => {
        if (!cancelled) {
          setProfile(data)
          setError(null)
        }
      })
      .catch(caught => {
        if (!cancelled) {
          setError(
            caught instanceof ApiError ? caught.displayMessage : 'Не удалось загрузить профиль',
          )
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [username])

  const userId = profile?.id

  const loadPage = useCallback(
    (page: number) => (userId ? api.posts.byUser(userId, page, 10) : Promise.resolve(EMPTY_PAGE)),
    [userId],
  )

  async function handleToggleFollow() {
    if (!profile) return
    setFollowBusy(true)
    try {
      const result = await api.actions.toggleFollow(profile.id)
      const followed = result.action === 'followed'
      setProfile(current =>
        current
          ? {
              ...current,
              isFollowing: followed,
              followersCount: current.followersCount + (followed ? 1 : -1),
            }
          : current,
      )
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.displayMessage : 'Не удалось изменить подписку')
    } finally {
      setFollowBusy(false)
    }
  }

  if (loading) {
    return <FullPageLoader label="Загружаем профиль…" />
  }

  if (!profile) {
    return (
      <div className="p-5">
        <TopNav showBack />
        <div className="card mx-auto max-w-[600px] p-8 text-center">
          <h3 className="mb-4 text-danger">Профиль не найден</h3>
          <p className="mb-6 text-muted">{error ?? 'Пользователь не существует.'}</p>
          <Link to="/" className="btn">
            На главную
          </Link>
        </div>
      </div>
    )
  }

  const initial = profile.username.slice(0, 1).toUpperCase()

  return (
    <div className="p-5">
      <TopNav showBack>
        <Link to="/" className="nav-btn">
          На главную
        </Link>
      </TopNav>

      <div className="card mx-auto mb-8 flex max-w-[1200px] flex-col items-center gap-8 p-8 sm:flex-row sm:items-center">
        <div className="flex h-[120px] w-[120px] shrink-0 items-center justify-center overflow-hidden rounded-full bg-canvas text-4xl text-muted">
          {profile.avatarUrl ? (
            <img
              src={profile.avatarUrl}
              alt={profile.username}
              className="h-full w-full object-cover"
            />
          ) : (
            initial
          )}
        </div>

        <div className="flex-1 text-center sm:text-left">
          <h2 className="mb-3 text-2xl">{profile.username}</h2>

          <div className="mb-5 flex justify-center gap-5 text-muted sm:justify-start">
            <span>
              <b className="text-lg text-ink">{profile.followersCount}</b> подписчиков
            </span>
            <span>
              <b className="text-lg text-ink">{profile.followingCount}</b> подписок
            </span>
          </div>

          {error && (
            <div className="mb-4">
              <Alert variant="error">{error}</Alert>
            </div>
          )}

          {profile.isCurrentUserProfile ? (
            <Link to="/create" className="btn">
              ➕ Создать аутфит
            </Link>
          ) : (
            <button
              type="button"
              className={`btn ${profile.isFollowing ? 'btn-ghost' : ''}`}
              disabled={followBusy}
              onClick={handleToggleFollow}
            >
              {profile.isFollowing ? 'Отписаться' : 'Подписаться'}
            </button>
          )}
        </div>
      </div>

      <PostGrid
        loadPage={loadPage}
        resetKey={profile.id}
        emptyText={profile.isCurrentUserProfile ? 'У вас пока нет аутфитов 📭' : 'Тут пока пусто 📭'}
      />
    </div>
  )
}
