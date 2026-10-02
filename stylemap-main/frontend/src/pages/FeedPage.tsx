import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { HashtagSearchBox } from '../components/HashtagSearchBox'
import { PostGrid } from '../components/PostGrid'
import { TopNav } from '../components/TopNav'

const SORT_OPTIONS = [
  { value: 'date_desc', label: 'Сначала новые' },
  { value: 'date_asc', label: 'Сначала старые' },
  { value: 'rating_desc', label: 'Самые высокооценённые' },
]

export default function FeedPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const hashtag = searchParams.get('hashtag') ?? ''

  const [sortBy, setSortBy] = useState('date_desc')
  const [followingOnly, setFollowingOnly] = useState(false)

  // useCallback обязателен: PostGrid перезапускает загрузку при смене ссылки на функцию.
  const loadPage = useCallback(
    (page: number) =>
      api.posts.feed({
        hashtag: hashtag || undefined,
        page,
        size: 10,
        sortBy,
        followingOnly,
      }),
    [hashtag, sortBy, followingOnly],
  )

  const resetKey = `${hashtag}|${sortBy}|${followingOnly}`

  function setHashtag(next: string | null) {
    const params = new URLSearchParams(searchParams)
    if (next) {
      params.set('hashtag', next)
    } else {
      params.delete('hashtag')
    }
    setSearchParams(params)
  }

  return (
    <div className="p-5">
      <TopNav />

      <HashtagSearchBox onSelect={setHashtag} />

      {hashtag && (
        <div className="mb-5 text-center">
          <span className="inline-flex items-center gap-2.5 rounded-full bg-ink px-4 py-2 text-sm text-white">
            Поиск по: <b>#{hashtag}</b>
            <button
              type="button"
              className="cursor-pointer border-none bg-transparent p-0 text-white"
              title="Сбросить поиск"
              onClick={() => setHashtag(null)}
            >
              ✖
            </button>
          </span>
        </div>
      )}

      <div className="card mx-auto mb-5 flex max-w-[1200px] flex-wrap items-center justify-between gap-4 px-5 py-4">
        <div className="flex items-center gap-2.5">
          <label htmlFor="sortSelect" className="font-semibold text-muted">
            Сортировать:
          </label>
          <select
            id="sortSelect"
            className="field w-auto"
            value={sortBy}
            onChange={event => setSortBy(event.target.value)}
          >
            {SORT_OPTIONS.map(option => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <label className="flex cursor-pointer items-center gap-2.5 font-semibold">
          <input
            type="checkbox"
            className="h-4 w-4 accent-black"
            checked={followingOnly}
            onChange={event => setFollowingOnly(event.target.checked)}
          />
          Только мои подписки
        </label>
      </div>

      <PostGrid loadPage={loadPage} resetKey={resetKey} />
    </div>
  )
}
