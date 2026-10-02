import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { OutfitItem, PostDetails } from '../api/types'
import { Alert } from '../components/Alert'
import { CurrencyPrice } from '../components/CurrencyPrice'
import { FullPageLoader } from '../components/FullPageLoader'
import { HashtagInput } from '../components/HashtagInput'
import { OutfitItemEditor } from '../components/OutfitItemEditor'
import { RatingStars } from '../components/RatingStars'
import { TopNav } from '../components/TopNav'

export default function PostDetailsPage() {
  const { postId } = useParams<{ postId: string }>()
  const navigate = useNavigate()

  const [post, setPost] = useState<PostDetails | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [draftDescription, setDraftDescription] = useState('')
  const [draftHashtags, setDraftHashtags] = useState<string[]>([])
  const [draftItems, setDraftItems] = useState<OutfitItem[]>([])

  const [followBusy, setFollowBusy] = useState(false)

  const loadPost = useCallback(async () => {
    if (!postId) return
    try {
      const data = await api.posts.details(postId)
      setPost(data)
      setError(null)
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.displayMessage : 'Не удалось загрузить пост',
      )
    }
  }, [postId])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    loadPost().finally(() => {
      if (!cancelled) setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [loadPost])

  function startEditing() {
    if (!post) return
    setDraftDescription(post.description ?? '')
    setDraftHashtags([...post.hashtagPaths])
    setDraftItems(
      post.outfitItems.map(item => ({
        name: item.name,
        brand: item.brand,
        links: { ...item.links },
      })),
    )
    setActionError(null)
    setEditing(true)
  }

  async function handleSave() {
    if (!postId) return
    setSaving(true)
    setActionError(null)
    try {
      await api.posts.update(postId, {
        description: draftDescription,
        hashtags: draftHashtags,
        outfitItems: draftItems,
      })
      await loadPost()
      setEditing(false)
    } catch (caught) {
      setActionError(
        caught instanceof ApiError ? caught.displayMessage : 'Не удалось сохранить изменения',
      )
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!postId) return
    if (!window.confirm('Удалить этот аутфит? Действие необратимо.')) return
    try {
      await api.posts.remove(postId)
      navigate('/', { replace: true })
    } catch (caught) {
      setActionError(
        caught instanceof ApiError ? caught.displayMessage : 'Не удалось удалить пост',
      )
    }
  }

  async function handleRate(score: number) {
    if (!postId) return
    setActionError(null)
    try {
      await api.posts.rate(postId, score)
      await loadPost()
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.displayMessage : 'Не удалось поставить оценку')
    }
  }

  async function handleToggleFollow() {
    if (!post) return
    setFollowBusy(true)
    setActionError(null)
    try {
      const result = await api.actions.toggleFollow(post.authorId)
      setPost(current =>
        current ? { ...current, isFollowingAuthor: result.action === 'followed' } : current,
      )
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.displayMessage : 'Не удалось изменить подписку')
    } finally {
      setFollowBusy(false)
    }
  }

  if (loading) {
    return <FullPageLoader label="Загружаем аутфит…" />
  }

  if (error || !post) {
    return (
      <div className="p-5">
        <TopNav showBack />
        <div className="card mx-auto max-w-[600px] p-8 text-center">
          <h3 className="mb-4 text-danger">Упс, ошибка! 🛠</h3>
          <p className="mb-6 text-muted">{error ?? 'Пост не найден'}</p>
          <Link to="/" className="btn">
            На главную
          </Link>
        </div>
      </div>
    )
  }

  const media = post.mediaFiles[0]

  return (
    <div className="p-5">
      <TopNav showBack>
        <Link to="/" className="nav-btn">
          На главную
        </Link>
      </TopNav>

      <div className="card mx-auto flex max-w-[1200px] flex-col overflow-hidden lg:flex-row">
        <div className="flex min-h-[300px] flex-1 items-center justify-center bg-black lg:min-h-[500px]">
          {media ? (
            media.type === 'VIDEO' ? (
              <video src={media.url} className="max-h-[85vh] max-w-full" controls autoPlay muted loop />
            ) : (
              <img src={media.url} alt="Аутфит" className="max-h-[85vh] max-w-full object-contain" />
            )
          ) : (
            <span className="text-muted">Нет медиа</span>
          )}
        </div>

        <div className="scroll-thin flex-1 overflow-y-auto p-8">
          <div className="mb-7 flex items-center justify-between gap-4">
            <Link
              to={`/user/${encodeURIComponent(post.authorUsername)}`}
              className="flex items-center gap-2.5"
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#eee]">
                👤
              </span>
              <span className="text-lg font-semibold">{post.authorUsername}</span>
            </Link>

            {!post.isAuthor && (
              <button
                type="button"
                className={`btn btn-sm ${post.isFollowingAuthor ? 'btn-ghost' : ''}`}
                disabled={followBusy}
                onClick={handleToggleFollow}
              >
                {post.isFollowingAuthor ? 'Отписаться' : 'Подписаться'}
              </button>
            )}
          </div>

          {actionError && (
            <div className="mb-5">
              <Alert variant="error">{actionError}</Alert>
            </div>
          )}

          {!editing ? (
            <>
              <p className="text-base leading-relaxed">{post.description}</p>

              {post.hashtagPaths.length > 0 && (
                <div className="my-5 flex flex-wrap gap-2">
                  {post.hashtagPaths.map(tag => (
                    <Link
                      key={tag}
                      to={`/?hashtag=${encodeURIComponent(tag)}`}
                      className="text-sm text-muted hover:underline"
                    >
                      #{tag}
                    </Link>
                  ))}
                </div>
              )}

              <h3 className="mb-4 mt-10 text-xl">Где купить:</h3>

              {post.outfitItems.length === 0 && (
                <p className="text-sm text-muted">Автор не указал вещи.</p>
              )}

              {post.outfitItems.map((item, index) => (
                <div key={index} className="mb-3 rounded-xl bg-canvas p-4">
                  <div className="mb-2.5 flex items-center justify-between gap-3">
                    <b>{item.name}</b>
                    {item.brand && <span className="text-muted">{item.brand}</span>}
                  </div>

                  {Object.entries(item.links).map(([country, link]) => (
                    <div
                      key={country}
                      className="flex flex-wrap items-center justify-between gap-3 border-t border-black/5 pt-2"
                    >
                      <CurrencyPrice price={link.price} currency={link.currency} />
                      <div className="flex items-center gap-2.5">
                        <span className="text-xs text-muted">{country}</span>
                        <a
                          href={link.url}
                          target="_blank"
                          rel="noreferrer"
                          className="text-[13px] font-semibold uppercase tracking-wide"
                        >
                          Купить
                        </a>
                      </div>
                    </div>
                  ))}
                </div>
              ))}

              {post.isAuthor ? (
                <div className="mt-10 flex gap-2.5">
                  <button type="button" className="btn btn-ghost flex-1" onClick={startEditing}>
                    Редактировать
                  </button>
                  <button type="button" className="btn btn-danger flex-1" onClick={handleDelete}>
                    🗑 Удалить
                  </button>
                </div>
              ) : (
                <div className="mt-10 rounded-xl border border-line p-5">
                  <RatingStars
                    average={post.averageRating}
                    current={post.currentUserRating}
                    onRate={handleRate}
                  />
                </div>
              )}
            </>
          ) : (
            <>
              <h3 className="mb-5 text-xl">Редактирование</h3>

              <label className="mb-2 block font-semibold">Описание</label>
              <textarea
                className="field mb-5"
                rows={4}
                value={draftDescription}
                onChange={event => setDraftDescription(event.target.value)}
              />

              <div className="mb-5 rounded-xl border border-line p-4">
                <HashtagInput value={draftHashtags} onChange={setDraftHashtags} />
              </div>

              <div className="mb-5 rounded-xl border border-line p-4">
                <OutfitItemEditor items={draftItems} onChange={setDraftItems} />
              </div>

              <div className="flex gap-2.5">
                <button
                  type="button"
                  className="btn flex-1"
                  disabled={saving}
                  onClick={handleSave}
                >
                  {saving ? 'Сохраняем…' : 'Сохранить'}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost flex-1"
                  onClick={() => setEditing(false)}
                >
                  Отмена
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
