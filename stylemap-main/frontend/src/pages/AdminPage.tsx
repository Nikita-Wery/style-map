import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import type { AdminHashtag, AdminPost, AdminUser } from '../api/types'
import { Alert } from '../components/Alert'
import { TopNav } from '../components/TopNav'

type Tab = 'users' | 'posts' | 'hashtags'

const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'users', label: 'Пользователи' },
  { id: 'posts', label: 'Посты' },
  { id: 'hashtags', label: 'Хештеги' },
]

function errorText(caught: unknown, fallback: string): string {
  return caught instanceof ApiError ? caught.displayMessage : fallback
}

export default function AdminPage() {
  const [tab, setTab] = useState<Tab>('users')

  return (
    <div className="p-5">
      <TopNav showBack>
        <Link to="/" className="nav-btn">
          На главную
        </Link>
      </TopNav>

      <div className="mx-auto max-w-[1200px]">
        <h1 className="mb-6 text-3xl">Панель администратора</h1>

        <div className="mb-6 flex flex-wrap gap-2">
          {TABS.map(item => (
            <button
              key={item.id}
              type="button"
              className={`nav-btn ${tab === item.id ? 'bg-white shadow-[0_2px_8px_rgba(0,0,0,0.06)]' : ''}`}
              onClick={() => setTab(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>

        {tab === 'users' && <UsersSection />}
        {tab === 'posts' && <PostsSection />}
        {tab === 'hashtags' && <HashtagsSection />}
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ users */

function UsersSection() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [creating, setCreating] = useState(false)

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setUsers(await api.admin.users())
      setError(null)
    } catch (caught) {
      setError(errorText(caught, 'Не удалось загрузить пользователей'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setCreating(true)
    setError(null)
    try {
      await api.admin.createUser({ username: username.trim(), email: email.trim(), password })
      setUsername('')
      setEmail('')
      setPassword('')
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось создать пользователя'))
    } finally {
      setCreating(false)
    }
  }

  async function handleToggleStatus(user: AdminUser) {
    setBusyId(user.id)
    setError(null)
    try {
      await api.admin.setUserStatus(user.id, !user.active)
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось изменить статус'))
    } finally {
      setBusyId(null)
    }
  }

  async function handleDelete(user: AdminUser) {
    if (!window.confirm(`Удалить пользователя ${user.username}?`)) return
    setBusyId(user.id)
    setError(null)
    try {
      await api.admin.deleteUser(user.id)
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось удалить пользователя'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="card p-6">
      <h3 className="mb-4 text-xl">Создать пользователя</h3>

      <form className="mb-6 flex flex-wrap items-end gap-3" onSubmit={handleCreate}>
        <input
          className="field flex-1"
          placeholder="Username"
          required
          value={username}
          onChange={event => setUsername(event.target.value)}
        />
        <input
          className="field flex-1"
          type="email"
          placeholder="Email"
          required
          value={email}
          onChange={event => setEmail(event.target.value)}
        />
        <input
          className="field flex-1"
          type="password"
          placeholder="Password"
          required
          value={password}
          onChange={event => setPassword(event.target.value)}
        />
        <button type="submit" className="btn" disabled={creating}>
          {creating ? 'Создаём…' : 'Создать'}
        </button>
      </form>

      {error && (
        <div className="mb-4">
          <Alert variant="error">{error}</Alert>
        </div>
      )}

      {loading ? (
        <div className="loader">Загружаем…</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-line text-left text-muted">
                <th className="px-3 py-2">Username</th>
                <th className="px-3 py-2">Email</th>
                <th className="px-3 py-2">Роль</th>
                <th className="px-3 py-2">Статус</th>
                <th className="px-3 py-2">Действия</th>
              </tr>
            </thead>
            <tbody>
              {users.map(user => (
                <tr key={user.id} className="border-b border-[#f5f5f7]">
                  <td className="px-3 py-2.5 font-medium">{user.username}</td>
                  <td className="px-3 py-2.5 text-muted">{user.email}</td>
                  <td className="px-3 py-2.5">{user.role}</td>
                  <td className="px-3 py-2.5">
                    {user.active ? (
                      <span className="text-success">активен</span>
                    ) : (
                      <span className="text-danger">забанен</span>
                    )}
                  </td>
                  <td className="flex gap-2 px-3 py-2.5">
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      disabled={busyId === user.id}
                      onClick={() => handleToggleStatus(user)}
                    >
                      {user.active ? 'Забанить' : 'Разбанить'}
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      disabled={busyId === user.id}
                      onClick={() => handleDelete(user)}
                    >
                      Удалить
                    </button>
                  </td>
                </tr>
              ))}
              {users.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-6 text-center text-muted">
                    Пользователей нет
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ posts */

function PostsSection() {
  const [posts, setPosts] = useState<AdminPost[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [authorEmail, setAuthorEmail] = useState('')
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editingText, setEditingText] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setPosts(await api.admin.posts())
      setError(null)
    } catch (caught) {
      setError(errorText(caught, 'Не удалось загрузить посты'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setCreating(true)
    setError(null)
    try {
      await api.admin.createPost(authorEmail.trim(), description)
      setAuthorEmail('')
      setDescription('')
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось создать пост'))
    } finally {
      setCreating(false)
    }
  }

  async function handleUpdate(postId: string) {
    setError(null)
    try {
      await api.admin.updatePost(postId, editingText)
      setEditingId(null)
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось обновить пост'))
    }
  }

  async function handleDelete(post: AdminPost) {
    if (!window.confirm('Удалить этот пост?')) return
    setError(null)
    try {
      await api.admin.deletePost(post.id)
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось удалить пост'))
    }
  }

  return (
    <div className="card p-6">
      <h3 className="mb-4 text-xl">Создать пост</h3>

      <form className="mb-6 flex flex-wrap items-end gap-3" onSubmit={handleCreate}>
        <input
          className="field flex-1"
          type="email"
          placeholder="Email автора"
          required
          value={authorEmail}
          onChange={event => setAuthorEmail(event.target.value)}
        />
        <input
          className="field flex-[2]"
          placeholder="Описание"
          required
          value={description}
          onChange={event => setDescription(event.target.value)}
        />
        <button type="submit" className="btn" disabled={creating}>
          {creating ? 'Создаём…' : 'Создать'}
        </button>
      </form>

      {error && (
        <div className="mb-4">
          <Alert variant="error">{error}</Alert>
        </div>
      )}

      {loading ? (
        <div className="loader">Загружаем…</div>
      ) : (
        <div className="flex flex-col gap-3">
          {posts.map(post => (
            <div key={post.id} className="rounded-xl border border-line p-4">
              {editingId === post.id ? (
                <div className="flex flex-wrap items-center gap-2.5">
                  <input
                    className="field flex-1"
                    value={editingText}
                    onChange={event => setEditingText(event.target.value)}
                  />
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => handleUpdate(post.id)}
                  >
                    Сохранить
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => setEditingId(null)}
                  >
                    Отмена
                  </button>
                </div>
              ) : (
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="m-0 truncate font-medium">
                      {post.description || <i className="text-muted">без описания</i>}
                    </p>
                    <p className="m-0 mt-1 text-xs text-muted">
                      {post.authorUsername ?? '—'} · рейтинг {post.averageRating ?? 0} ·{' '}
                      {post.createdAt ? new Date(post.createdAt).toLocaleString('ru-RU') : '—'}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <Link to={`/post/${post.id}`} className="btn btn-ghost btn-sm">
                      Открыть
                    </Link>
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => {
                        setEditingId(post.id)
                        setEditingText(post.description ?? '')
                      }}
                    >
                      Изменить
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      onClick={() => handleDelete(post)}
                    >
                      Удалить
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
          {posts.length === 0 && <div className="loader">Постов нет</div>}
        </div>
      )}
    </div>
  )
}

/* --------------------------------------------------------------- hashtags */

function HashtagsSection() {
  const [hashtags, setHashtags] = useState<AdminHashtag[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [name, setName] = useState('')
  const [path, setPath] = useState('')
  const [creating, setCreating] = useState(false)

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editingName, setEditingName] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      setHashtags(await api.admin.hashtags())
      setError(null)
    } catch (caught) {
      setError(errorText(caught, 'Не удалось загрузить хештеги'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function handleCreate(event: FormEvent) {
    event.preventDefault()
    setCreating(true)
    setError(null)
    try {
      await api.admin.createHashtag(name.trim(), path.trim())
      setName('')
      setPath('')
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось создать хештег'))
    } finally {
      setCreating(false)
    }
  }

  async function handleUpdate(hashtagId: number) {
    setError(null)
    try {
      await api.admin.updateHashtag(hashtagId, editingName)
      setEditingId(null)
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось обновить хештег'))
    }
  }

  async function handleDelete(hashtag: AdminHashtag) {
    if (!window.confirm(`Удалить хештег ${hashtag.path}?`)) return
    setError(null)
    try {
      await api.admin.deleteHashtag(hashtag.id)
      await reload()
    } catch (caught) {
      setError(errorText(caught, 'Не удалось удалить хештег'))
    }
  }

  return (
    <div className="card p-6">
      <h3 className="mb-4 text-xl">Создать хештег</h3>

      <form className="mb-6 flex flex-wrap items-end gap-3" onSubmit={handleCreate}>
        <input
          className="field flex-1"
          placeholder="Имя (напр. bleach)"
          required
          value={name}
          onChange={event => setName(event.target.value)}
        />
        <input
          className="field flex-1"
          placeholder="Путь ltree (напр. anime.bleach)"
          required
          value={path}
          onChange={event => setPath(event.target.value)}
        />
        <button type="submit" className="btn" disabled={creating}>
          {creating ? 'Создаём…' : 'Создать'}
        </button>
      </form>

      {error && (
        <div className="mb-4">
          <Alert variant="error">{error}</Alert>
        </div>
      )}

      {loading ? (
        <div className="loader">Загружаем…</div>
      ) : (
        <div className="flex flex-col gap-2">
          {hashtags.map(hashtag => (
            <div
              key={hashtag.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line px-4 py-3"
            >
              {editingId === hashtag.id ? (
                <div className="flex flex-1 flex-wrap items-center gap-2.5">
                  <input
                    className="field flex-1"
                    value={editingName}
                    onChange={event => setEditingName(event.target.value)}
                  />
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => handleUpdate(hashtag.id)}
                  >
                    Сохранить
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => setEditingId(null)}
                  >
                    Отмена
                  </button>
                </div>
              ) : (
                <>
                  <div>
                    <b>{hashtag.name}</b>{' '}
                    <span className="text-sm text-muted">#{hashtag.path}</span>
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="btn btn-ghost btn-sm"
                      onClick={() => {
                        setEditingId(hashtag.id)
                        setEditingName(hashtag.name)
                      }}
                    >
                      Изменить
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger btn-sm"
                      onClick={() => handleDelete(hashtag)}
                    >
                      Удалить
                    </button>
                  </div>
                </>
              )}
            </div>
          ))}
          {hashtags.length === 0 && <div className="loader">Хештегов нет</div>}
        </div>
      )}
    </div>
  )
}
