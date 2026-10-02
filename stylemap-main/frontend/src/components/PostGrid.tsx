import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { FeedPost, Page } from '../api/types'

interface PostGridProps {
  /** Загрузчик одной страницы. Должен быть стабильным (оберните в useCallback). */
  loadPage: (page: number) => Promise<Page<FeedPost>>
  /** При изменении ключа накопленные посты сбрасываются (смена фильтра/пользователя). */
  resetKey: string
  emptyText?: string
}

/**
 * Кладочная (masonry) сетка постов с бесконечной прокруткой.
 * Подгрузка следующей страницы происходит, когда в зону видимости попадает «маячок» внизу.
 */
export function PostGrid({ loadPage, resetKey, emptyText = 'Тут пока пусто 📭' }: PostGridProps) {
  const navigate = useNavigate()

  const [posts, setPosts] = useState<FeedPost[]>([])
  const [nextPage, setNextPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const sentinelRef = useRef<HTMLDivElement>(null)
  const inFlightRef = useRef(false)
  const resetKeyRef = useRef(resetKey)

  const [trackedKey, setTrackedKey] = useState(resetKey)

  // Смена фильтра: сбрасываем состояние прямо во время рендера,
  // чтобы не успеть запустить загрузку со старыми данными.
  if (trackedKey !== resetKey) {
    setTrackedKey(resetKey)
    setPosts([])
    setNextPage(0)
    setHasMore(true)
    setError(null)
    setLoading(false)
    inFlightRef.current = false
  }

  resetKeyRef.current = resetKey

  const loadMore = useCallback(async () => {
    if (inFlightRef.current || !hasMore) return

    inFlightRef.current = true
    const requestKey = resetKeyRef.current
    setLoading(true)
    setError(null)

    try {
      const result = await loadPage(nextPage)
      // Фильтр успел измениться — ответ устарел, выбрасываем его.
      if (requestKey !== resetKeyRef.current) return

      setPosts(previous => [...previous, ...result.content])
      setNextPage(previous => previous + 1)
      setHasMore(!result.last)
    } catch (caught) {
      if (requestKey !== resetKeyRef.current) return
      setError(caught instanceof Error ? caught.message : 'Не удалось загрузить аутфиты')
      setHasMore(false)
    } finally {
      inFlightRef.current = false
      if (requestKey === resetKeyRef.current) {
        setLoading(false)
      }
    }
  }, [loadPage, nextPage, hasMore])

  // Наблюдаем за нижним «маячком» и подгружаем следующую страницу.
  useEffect(() => {
    const node = sentinelRef.current
    if (!node || !hasMore) return

    const observer = new IntersectionObserver(
      entries => {
        if (entries[0].isIntersecting) {
          void loadMore()
        }
      },
      { rootMargin: '200px' },
    )

    observer.observe(node)
    return () => observer.disconnect()
  }, [loadMore, hasMore])

  const showEmpty = !loading && posts.length === 0 && !hasMore && !error

  return (
    <>
      <div className="masonry mx-auto max-w-[1200px]">
        {posts.map(post => (
          <article
            key={post.id}
            className="post-card card"
            onClick={() => navigate(`/post/${post.id}`)}
          >
            {post.mediaType === 'VIDEO' ? (
              <video src={post.mediaUrl} className="block w-full" autoPlay muted loop playsInline />
            ) : (
              <img src={post.mediaUrl} className="block w-full" loading="lazy" alt="Аутфит" />
            )}
          </article>
        ))}
      </div>

      {showEmpty && <div className="loader">{emptyText}</div>}

      {error && <div className="loader text-danger">{error}</div>}

      {/* Маячок для IntersectionObserver */}
      <div ref={sentinelRef} className="loader">
        {loading && 'Загружаем аутфиты…'}
        {!loading && !hasMore && posts.length > 0 && 'Вы посмотрели все аутфиты! 🎉'}
        {!loading && hasMore && posts.length > 0 && 'Крутите вниз для загрузки…'}
      </div>
    </>
  )
}
